#!/usr/bin/env python3
"""Local Paraformer quiet-speech regression. Requires sherpa-onnx 1.13.8, numpy, soundfile and cached Kotlin.
No downloads, microphone, device, or changes to supplied recordings/models. New output directory only.
"""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess

import numpy as np
import sherpa_onnx
import soundfile as sf

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--speech', type=Path, required=True)
    p.add_argument('--model', type=Path, required=True)
    p.add_argument('--vad', type=Path, required=True)
    p.add_argument('--java', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    a.output.mkdir(parents=True, exist_ok=False)
    out = a.output.resolve()
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
    command = [str(a.java), '-Dfile.encoding=UTF-8', '-cp', os.pathsep.join(map(str, [artifact, stdlib])),
               'com.kingzcheung.xime.speech.SpeechReplayKt']
    x, sr = sf.read(a.speech, dtype='float32')
    if sr != 16000 or x.ndim != 1:
        raise ValueError('Fixture must be 16 kHz mono')
    def quantize(y):
        return (np.clip(np.round(y * 32768), -32768, 32767).astype(np.int16).astype(np.float32) / 32768)
    cases = {f'speech-{scale:g}': quantize(x * scale) for scale in [1, .1, .03, .01, .003, .001]}
    cases['loud-quiet'] = np.concatenate([x, np.zeros(16000), quantize(x * .003)]).astype(np.float32)
    cases['quiet-loud'] = np.concatenate([quantize(x * .003), np.zeros(16000), x]).astype(np.float32)
    cases['quiet-with-silent-tail'] = np.concatenate([quantize(x * .003), np.zeros(16000 * 4)]).astype(np.float32)
    rng = np.random.default_rng(20261006)
    noise = rng.normal(0, 1, 16000 * 8).astype(np.float32)
    cases['silence'] = np.zeros(len(noise), dtype=np.float32)
    cases['white-noise'] = quantize(noise * .001)
    cases['loud-noise'] = quantize(noise * .05)
    cases['hum'] = quantize(.002 * np.sin(np.arange(len(noise)) * (2 * np.pi * 60 / 16000)))
    cases['clicks'] = cases['silence'].copy()
    cases['clicks'][::8000] = .5
    for name, pcm in cases.items():
        pcm.astype('<f4').tofile(out / f'{name}.raw.f32')
    config = sherpa_onnx.VadModelConfig()
    config.sample_rate = 16000
    config.num_threads = 1
    config.silero_vad.model = str(a.vad)
    config.silero_vad.threshold = .6
    config.silero_vad.min_silence_duration = .064
    config.silero_vad.min_speech_duration = .15
    config.silero_vad.window_size = 512
    config.silero_vad.max_speech_duration = 30
    r = sherpa_onnx.OnlineRecognizer.from_paraformer(tokens=str(a.model / 'tokens.txt'),
        encoder=str(a.model / 'encoder.int8.onnx'), decoder=str(a.model / 'decoder.int8.onnx'),
        num_threads=1, enable_endpoint_detection=False)
    results = {name: {} for name in cases}
    bridge = subprocess.Popen(command + [str(out)], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, text=True, encoding='utf-8', bufsize=1)
    try:
        for line in bridge.stdout:
            parts = line.rstrip('\n').split('\t')
            op, response = parts[0], ''
            if op == 'CASE':
                name, mode = parts[1:]
                v = sherpa_onnx.VoiceActivityDetector(config, buffer_size_in_seconds=60)
                stream = None
                result = {'text': '', 'first_partial_audio_s': None, 'regions': []}
            elif op == 'VAD':
                v.accept_waveform(np.frombuffer(base64.b64decode(parts[1]), dtype='<f4').copy())
                response = str(int(v.is_speech_detected()))
                while not v.empty():
                    v.pop()
            elif op == 'RESET':
                v.reset()
            elif op == 'AUDIO':
                if stream is None:
                    stream = r.create_stream()
                stream.accept_waveform(16000, np.frombuffer(base64.b64decode(parts[2]), dtype='<f4').copy())
                while r.is_ready(stream):
                    r.decode_stream(stream)
                text = r.get_result(stream)
                if text.strip() and result['first_partial_audio_s'] is None:
                    result['first_partial_audio_s'] = int(parts[1]) / 16000
                response = base64.b64encode(text.encode('utf-8')).decode('ascii')
            elif op == 'END':
                stream.set_option('is_final', '1')
                stream.input_finished()
                while r.is_ready(stream):
                    r.decode_stream(stream)
                result['text'] = (result['text'] + ' ' + r.get_result(stream)).strip()
                result['regions'].append({'samples': int(parts[1]), 'boundary': parts[2]})
                stream = None
            elif op == 'DONE':
                results[name][mode] = result
                print(name, mode, json.dumps(result, ensure_ascii=False), flush=True)
            else:
                raise RuntimeError('Unexpected Kotlin bridge output: ' + line)
            bridge.stdin.write(response + '\n')
            bridge.stdin.flush()
        if bridge.wait() != 0:
            raise RuntimeError(bridge.stderr.read())
    finally:
        if bridge.poll() is None:
            bridge.kill()
    checks = {
        'quiet_starts_before_loud_trigger': results['speech-0.003']['after']['first_partial_audio_s'] is not None and
            results['speech-0.003']['after']['first_partial_audio_s'] < 3,
        'quiet_keeps_early_words': '时间早上' in results['speech-0.003']['after']['text'].replace(' ', ''),
        'quiet_after_loud_keeps_both_phrases': results['loud-quiet']['after']['text'].replace(' ', '').count('时间早上') == 2,
        'quiet_before_loud_keeps_both_phrases': results['quiet-loud']['after']['text'].replace(' ', '').count('时间早上') == 2,
        'normal_speech_still_recognized': '时间早上' in results['speech-1']['after']['text'].replace(' ', ''),
        'quiet_finalizes_on_pause_without_stop': len(results['quiet-with-silent-tail']['after']['regions']) == 1 and
            results['quiet-with-silent-tail']['after']['regions'][0]['boundary'] == 'PAUSE',
        'non_speech_does_not_start_recognition': all(not results[n]['after']['regions'] for n in
            ['silence', 'white-noise', 'loud-noise', 'hum', 'clicks']),
    }
    report = {'runtime': sherpa_onnx.__version__, 'fixture': str(a.speech),
        'sha256': {str(f): hashlib.sha256(f.read_bytes()).hexdigest() for f in [*sources, a.speech, a.vad]},
        'checks': checks, 'cases': results,
        'scope': 'Desktop production Kotlin gain/segmentation and official native VAD/Paraformer; not a microphone or Android acceptance test.'}
    (out / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('checks', checks)
    return 0 if all(checks.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
