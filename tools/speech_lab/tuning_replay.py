"""Pull opt-in speech tuning recordings from a device and replay them with production segmentation.

The device saves each local speech session (exact PCM sent to the recognizer, text timeline,
region boundaries) under its own app folder when "保存语音调优录音" is on. This tool copies those
files to a private data folder (never the repository), builds a manifest, and runs
compare_refinement.py with the requested modes. There is no human reference text: a whole-clip
SenseVoice transcript is used as the pseudo reference, so scores rank strategies on the user's
own voice but are biased toward SenseVoice-final modes. Read the per-clip text, not only CER.
"""
import argparse, json, os, subprocess, sys, unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
PACKAGE = 'com.cyletix.cyime'
DEVICE_DIR = f'/sdcard/Android/data/{PACKAGE}/files/speech_tuning'
SENSE = Path('D:/GitHub/ASRInput/models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17')

def norm(s): return [c for c in unicodedata.normalize('NFKC', s).lower() if c.isalnum()]
def ed(a, b):
    d = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        p, d[0] = d[0], i
        for j, y in enumerate(b, 1): p, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, p + (x != y))
    return d[-1]

def pull(serial, target):
    target.mkdir(parents=True, exist_ok=True)
    subprocess.run(['adb', '-s', serial, 'pull', DEVICE_DIR + '/.', str(target)], check=True)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', help='adb serial; omit to only replay an existing --data folder')
    p.add_argument('--data', type=Path, required=True, help='private folder, e.g. D:/CyIME-Data/speech-tuning/<device>')
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--java', type=Path, required=True)
    p.add_argument('--modes', default='single,single-pause1000,refinement,refinement-pause1000')
    p.add_argument('--gain', default='1')
    a = p.parse_args()
    if a.serial: pull(a.serial, a.data)
    import sherpa_onnx as so, soundfile as sf
    sense = so.OfflineRecognizer.from_sense_voice(model=str(SENSE / 'model.int8.onnx'), tokens=str(SENSE / 'tokens.txt'),
                                                  language='zh', use_itn=True, num_threads=1)
    clips = []
    for wav in sorted(a.data.glob('*.wav')):
        meta = json.loads(wav.with_suffix('.json').read_text(encoding='utf8')) if wav.with_suffix('.json').exists() else {}
        if meta.get('language') not in (None, '', 'zh', 'en'): continue
        pcm, sr = sf.read(wav, dtype='float32'); assert sr == 16000
        s = sense.create_stream(); s.accept_waveform(16000, pcm); sense.decode_stream(s)
        clips.append(dict(id=wav.stem, language='zh', path=str(wav), reference=s.result.text,
                          device_text=meta.get('final_text', ''), device_mode=meta.get('mode', ''),
                          duration_s=len(pcm) / 16000))
    if not clips: sys.exit('没有可回放的录音')
    a.output.mkdir(parents=True, exist_ok=False)
    manifest = a.output / 'manifest.json'
    manifest.write_text(json.dumps(dict(clips=clips), ensure_ascii=False, indent=1), encoding='utf8')
    env = dict(os.environ, CYIME_REPLAY_MODES=a.modes, CYIME_REPLAY_GAIN=a.gain)
    subprocess.run([sys.executable, str(HERE / 'compare_refinement.py'), '--manifest', str(manifest),
                    '--java', str(a.java), '--output', str(a.output / 'replay')], check=True, env=env)
    results = json.loads((a.output / 'replay/results.json').read_text(encoding='utf8'))
    totals = {}
    for c in results['clips']:
        ref = norm(c['reference'])
        outs = [(c['mode'], c['first'])] + ([(c['mode'] + '-final', c['second'])] if c['mode'].startswith('refinement') else [])
        if c['mode'] == a.modes.split(',')[0]: outs.append(('device', c['device_text']))
        for label, hyp in outs:
            e, n = totals.get(label, (0, 0)); totals[label] = (e + ed(ref, norm(hyp)), n + len(ref))
    summary = {k: round(100 * e / max(n, 1), 2) for k, (e, n) in totals.items()}
    (a.output / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding='utf8')
    for k, v in summary.items(): print(f'{k:28s} 相对伪参考 CER {v:6.2f}%')

if __name__ == '__main__': main()
