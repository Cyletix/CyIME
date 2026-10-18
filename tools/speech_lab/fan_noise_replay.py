#!/usr/bin/env python3
"""Controlled synthetic fan-like noise test; NOT a user/device fan recording.

Replays actual Kotlin detection gain and segmentation, then decodes identical PCM
without the VAD gate. No download, microphone, ADB, or production parameter edits.
"""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import time
import unicodedata

import numpy as np
import sherpa_onnx as so
import soundfile as sf

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
SR = 16000


def rms(x):
    return float(np.sqrt(np.mean(np.asarray(x, dtype=np.float64) ** 2)))


def quantize(x):
    return (np.clip(np.round(x * 32768), -32768, 32767).astype(np.int16)
            .astype(np.float32) / 32768)


def normalized(text):
    # This fixture spells hours with Chinese numerals; normalize equivalent ITN.
    text = text.translate(str.maketrans('0123456789', '零一二三四五六七八九'))
    return ''.join(c.lower() for c in text if not c.isspace()
                   and not unicodedata.category(c).startswith('P'))


def cer(text, expected):
    target, actual = normalized(expected), normalized(text)
    previous = list(range(len(actual) + 1))
    for i, a in enumerate(target, 1):
        row = [i]
        for j, b in enumerate(actual, 1):
            row.append(min(row[-1] + 1, previous[j] + 1,
                           previous[j - 1] + (a != b)))
        previous = row
    return previous[-1] / max(1, len(target))


def synthetic_fan(count, seed):
    """Stationary colored airflow + blade harmonics, slowly amplitude modulated.

    Not a physical fan model or recording. Noise spectrum/seed are fixed for
    repeatability and the same samples are reused across SNR/volume comparisons.
    """
    rng = np.random.default_rng(seed)
    t = np.arange(count) / SR
    hz = np.fft.rfftfreq(count, 1 / SR)
    spectrum = np.fft.rfft(rng.normal(size=count))
    # Broad airflow with a low-frequency emphasis; no speech/music components.
    shape = (hz / (hz + 45)) / np.sqrt(1 + (hz / 450) ** 2)
    airflow = np.fft.irfft(spectrum * shape, n=count)
    airflow /= rms(airflow)
    hum = sum(np.sin(2 * np.pi * f * t + phase) * weight
              for f, phase, weight in [(120, .2, 1), (240, .7, .5), (360, 1.1, .25)])
    hum /= rms(hum)
    noise = (.85 * airflow + .15 * hum) * (1 + .06 * np.sin(2 * np.pi * .7 * t))
    return (noise / rms(noise)).astype(np.float32)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--speech', type=Path, required=True)
    p.add_argument('--expected', default='开放时间早上九点至下午五点')
    p.add_argument('--model', type=Path, required=True)
    p.add_argument('--vad', type=Path, required=True)
    p.add_argument('--java', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--vad-threshold', type=float, default=.6,
                   help='Diagnostic override in this replay only; does not edit the app')
    p.add_argument('--case', action='append', dest='selected_cases',
                   help='Run only the named case; may be repeated')
    p.add_argument('--skip-direct', action='store_true',
                   help='For VAD-only comparisons after running the identical direct-ASR control')
    p.add_argument('--stationary-suppression', action='store_true',
                   help='Experimental bounded causal spectral suppression before both VAD and ASR')
    a = p.parse_args()
    if not 0 < a.vad_threshold < 1:
        p.error('--vad-threshold must be between zero and one')
    source, sr = sf.read(a.speech, dtype='float32')
    if sr != SR or source.ndim != 1:
        raise ValueError('Fixture must be 16 kHz mono')
    a.output.mkdir(parents=True, exist_ok=False)
    out = a.output.resolve()
    padding = SR * 2
    signal = np.pad(source, (padding, padding))
    noise = synthetic_fan(len(signal), 20261008)
    speech_slice = slice(padding, padding + len(source))
    cases, metadata = {}, {}
    for scale in [1, .03]:
        speech = signal * scale
        for snr in [None, 20, 10, 5, 0, -5]:
            name = f'volume-{scale:g}-' + ('clean' if snr is None else f'fan-snr-{snr:+d}')
            gain = 0 if snr is None else rms(source * scale) / rms(noise[speech_slice]) / 10 ** (snr / 20)
            added = noise * gain
            mixed = speech + added
            cases[name] = quantize(mixed)
            metadata[name] = dict(kind='speech', scale=scale, target_snr_db=snr,
                speech_rms=rms(source * scale), noise_rms=rms(added[speech_slice]),
                clipped_samples=int(np.count_nonzero(np.abs(mixed) >= 1)),
                expected=a.expected, speech_interval_s=[2, 2 + len(source) / SR])
        name = f'volume-{scale:g}-fan-only'
        cases[name] = quantize(noise * rms(source * scale))
        metadata[name] = dict(kind='noise_only', scale=scale, expected='',
                              noise_rms=rms(cases[name]))
    cases['silence'] = np.zeros(len(signal), dtype=np.float32)
    metadata['silence'] = dict(kind='silence', expected='')
    if a.selected_cases:
        missing = set(a.selected_cases) - cases.keys()
        if missing:
            raise ValueError('Unknown cases: ' + ', '.join(sorted(missing)))
        cases = {name: pcm for name, pcm in cases.items() if name in a.selected_cases}
        metadata = {name: data for name, data in metadata.items() if name in cases}
    if a.stationary_suppression:
        from stationary_noise import suppress_stationary_noise
        for name, pcm in cases.items():
            pcm.astype('<f4').tofile(out / (name + '.original.f32'))
            started = time.perf_counter()
            processed, details = suppress_stationary_noise(pcm)
            details['wall_s'] = time.perf_counter() - started
            details['original_pcm_sha256'] = hashlib.sha256(pcm.astype('<f4').tobytes()).hexdigest()
            cases[name] = quantize(processed)
            metadata[name]['preprocessing'] = details
    for name, pcm in cases.items():
        pcm.astype('<f4').tofile(out / (name + '.raw.f32'))
        sf.write(out / (name + '.wav'), pcm, SR, subtype='PCM_16')

    spec = importlib.util.spec_from_file_location('glide_tools', ROOT / 'tools/glide_lab/run.py')
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
    _, jars, stdlib = helper.compiler_paths(cache)
    sources = [ROOT / 'app/src/main/java/com/kingzcheung/xime/speech' / name for name in
               ['SpeechDetectionLevel.kt', 'SpeechSegmentation.kt']] + [HERE / 'SpeechReplay.kt']
    artifact = out / 'speech-replay.jar'
    helper.execute([a.java, '-Xmx512m', '-Dfile.encoding=UTF-8', '-cp', os.pathsep.join(map(str, jars)),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
        '-classpath', os.pathsep.join(map(str, [stdlib, jars[-1]])), '-d', artifact, *sources], out / 'compile.log')
    config = so.VadModelConfig()
    config.sample_rate = SR
    config.num_threads = 1
    v = config.silero_vad
    v.model = str(a.vad)
    v.threshold = a.vad_threshold
    v.min_silence_duration = .064
    v.min_speech_duration = .15
    v.window_size = 512
    v.max_speech_duration = 30
    recognizer = so.OnlineRecognizer.from_paraformer(tokens=str(a.model / 'tokens.txt'),
        encoder=str(a.model / 'encoder.int8.onnx'), decoder=str(a.model / 'decoder.int8.onnx'),
        num_threads=1, enable_endpoint_detection=False)
    results = {name: dict(**metadata[name]) for name in cases}

    def accept(stream, pcm):
        stream.accept_waveform(SR, pcm)
        while recognizer.is_ready(stream):
            recognizer.decode_stream(stream)
        return recognizer.get_result(stream)

    def finish(stream):
        stream.set_option('is_final', '1')
        stream.input_finished()
        while recognizer.is_ready(stream):
            recognizer.decode_stream(stream)
        return recognizer.get_result(stream)

    command = [str(a.java), '-Dfile.encoding=UTF-8', '-cp', os.pathsep.join(map(str, [artifact, stdlib])),
               'com.kingzcheung.xime.speech.SpeechReplayKt', str(out), 'after']
    with (out / 'bridge.log').open('w', encoding='utf8') as errors:
        bridge = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=errors,
                                  text=True, encoding='utf8', bufsize=1)
        try:
            for line in bridge.stdout:
                parts = line.rstrip('\n').split('\t')
                op, reply = parts[0], ''
                if op == 'CASE':
                    name, mode = parts[1:]
                    detector = so.VoiceActivityDetector(config, buffer_size_in_seconds=60)
                    stream, frames, decoded = None, 0, 0
                    gains, activity = [], []
                    started = time.perf_counter()
                    current = dict(text='', regions=[], first_partial_audio_s=None)
                elif op == 'VAD':
                    pcm = np.frombuffer(base64.b64decode(parts[1]), dtype='<f4').copy()
                    raw = cases[name][frames * 512:(frames + 1) * 512]
                    peak = float(np.max(np.abs(raw)))
                    if peak:
                        gains.append(float(np.max(np.abs(pcm))) / peak)
                    detector.accept_waveform(pcm)
                    active = detector.is_speech_detected()
                    activity.append(bool(active))
                    frames += 1
                    reply = str(int(active))
                    while not detector.empty():
                        detector.pop()
                elif op == 'RESET':
                    detector.reset()
                elif op == 'AUDIO':
                    if stream is None:
                        stream = recognizer.create_stream()
                    pcm = np.frombuffer(base64.b64decode(parts[2]), dtype='<f4').copy()
                    decoded += len(pcm)
                    text = accept(stream, pcm)
                    if text.strip() and current['first_partial_audio_s'] is None:
                        current['first_partial_audio_s'] = int(parts[1]) / SR
                    reply = base64.b64encode(text.encode('utf8')).decode('ascii')
                elif op == 'END':
                    text = finish(stream)
                    current['text'] = (current['text'] + ' ' + text).strip()
                    end = min(frames * 512, len(cases[name]))
                    current['regions'].append(dict(start_s=(end - int(parts[1])) / SR,
                        end_s=end / SR, boundary=parts[2], text=text))
                    stream = None
                elif op == 'DONE':
                    current.update(wall_s=time.perf_counter() - started, decoded_audio_s=decoded / SR,
                        vad_active_frames=sum(activity), vad_total_frames=len(activity),
                        vad_first_active_audio_s=next(((i + 1) * 512 / SR for i, x in enumerate(activity) if x), None),
                        vad_gain_range=[min(gains), max(gains)] if gains else [1, 1],
                        cer=cer(current['text'], metadata[name]['expected']))
                    current['vad_activity'] = activity
                    results[name]['production'] = current
                    print(name, 'production', current['text'], 'CER', current['cer'], 'regions', len(current['regions']), flush=True)
                else:
                    raise RuntimeError('Unexpected bridge line: ' + line)
                bridge.stdin.write(reply + '\n')
                bridge.stdin.flush()
            if bridge.wait() != 0:
                raise RuntimeError((out / 'bridge.log').read_text(encoding='utf8'))
        finally:
            if bridge.poll() is None:
                bridge.kill()
    for name, pcm in ([] if a.skip_direct else cases.items()):
        started = time.perf_counter()
        stream = recognizer.create_stream()
        first = None
        for offset in range(0, len(pcm), 512):
            text = accept(stream, pcm[offset:offset + 512])
            if text.strip() and first is None:
                first = min(offset + 512, len(pcm)) / SR
        text = finish(stream)
        results[name]['direct_without_vad'] = dict(text=text, cer=cer(text, metadata[name]['expected']),
            first_partial_audio_s=first, decoded_audio_s=len(pcm) / SR,
            wall_s=time.perf_counter() - started)
        print(name, 'direct', text, 'CER', results[name]['direct_without_vad']['cer'], flush=True)
        (out / 'progress.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf8')
    model_files = [a.model / name for name in ['encoder.int8.onnx', 'decoder.int8.onnx', 'tokens.txt']]
    preprocessing_sources = [HERE / 'stationary_noise.py'] if a.stationary_suppression else []
    report = dict(runtime=so.__version__, scope=__doc__, fixture=str(a.speech),
        synthetic_seed=20261008, snr_definition='RMS over the complete source clip including its original pauses',
        padding='2 seconds of the same continuous noise before and after speech; digital silence for clean controls',
        limitations='One fixed utterance, synthetic noise, no device acoustic preprocessing or asynchronous deadlines; no user fan reproduction claim.',
        parameters=dict(vad_threshold=a.vad_threshold, min_silence_s=.064, min_speech_s=.15,
                        useEnergyPause=False, resetDetectorOnPause=True, maxSegmentMs=12000),
        sha256={str(f): hashlib.sha256(f.read_bytes()).hexdigest() for f in
                [*sources, Path(__file__), *preprocessing_sources, a.speech, a.vad, *model_files]}, cases=results)
    (out / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')


if __name__ == '__main__':
    main()
