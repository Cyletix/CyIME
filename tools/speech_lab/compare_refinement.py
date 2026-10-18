"""Chinese paired replay using production Kotlin VAD gain, segmentation and transcript assembly.

Synchronous native inference evaluates text quality only; it does not emulate Android
queues, microphone capture, wall-clock deadlines or device memory. No product edits.
"""
import argparse, base64, hashlib, importlib.util, json, os, subprocess, time
from pathlib import Path
import numpy as np
import sherpa_onnx as so
import soundfile as sf

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--java',type=Path,required=True)
    p.add_argument('--language',choices=['zh','ja'],default='zh')
    a=p.parse_args();a.output.mkdir(parents=True,exist_ok=False);out=a.output.resolve()
    rows={r['id']:r for r in json.loads(a.manifest.read_text(encoding='utf8'))['clips'] if r['language']==a.language}
    for name,row in rows.items():
        pcm,sr=sf.read(row['path'],dtype='float32');assert sr==16000 and pcm.ndim==1
        # Optional fixed attenuation reproduces a quiet microphone with the same clips.
        (pcm*float(os.environ.get('CYIME_REPLAY_GAIN','1'))).astype('<f4').tofile(out/(name+'.raw.f32'))
    spec=importlib.util.spec_from_file_location('glide',ROOT/'tools/glide_lab/run.py')
    helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
    cache=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches/modules-2/files-2.1'
    _,jars,stdlib=helper.compiler_paths(cache)
    sources=[ROOT/'app/src/main/java/com/kingzcheung/xime/speech'/f for f in ['SpeechDetectionLevel.kt','SpeechSegmentation.kt','JapaneseZipformerDecoding.kt']]+[HERE/'RefinementReplay.kt']
    jar=out/'refinement-replay.jar'
    helper.execute([a.java,'-Xmx512m','-Dfile.encoding=UTF-8','-cp',os.pathsep.join(map(str,jars)),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17',
        '-classpath',os.pathsep.join(map(str,[stdlib,jars[-1]])),'-d',jar,*sources],out/'compile.log')
    models=Path('D:/GitHub/ASRInput/models')
    para=models/'sherpa-onnx-streaming-paraformer-bilingual-zh-en'
    sense=models/'sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17'
    vad=ROOT/'app/build/generated/speech-assets/speech/silero_vad.onnx'
    if a.language=='zh':
        first=so.OnlineRecognizer.from_paraformer(encoder=str(para/'encoder.int8.onnx'),decoder=str(para/'decoder.int8.onnx'),tokens=str(para/'tokens.txt'),num_threads=1,enable_endpoint_detection=False)
        second=so.OfflineRecognizer.from_sense_voice(model=str(sense/'model.int8.onnx'),tokens=str(sense/'tokens.txt'),language='zh',use_itn=True,num_threads=1)
    else:
        folder=Path('D:/models/ASRInput/sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01')
        recognizers={mode:so.OfflineRecognizer.from_transducer(**{name:str(folder/(name+'-epoch-99-avg-1'+suffix)) for name in ['encoder','decoder','joiner']},tokens=str(folder/'tokens.txt'),num_threads=2) for mode,suffix in [('int8','.int8.onnx'),('fp32','.onnx')]}
    config=so.VadModelConfig();config.sample_rate=16000;config.num_threads=1
    v=config.silero_vad;v.model=str(vad);v.threshold=.6;v.min_silence_duration=.064;v.min_speech_duration=.15;v.window_size=512;v.max_speech_duration=30
    if a.language=='ja':v.threshold=.5;v.min_silence_duration=.2
    results=[]
    command=[str(a.java),'-Dfile.encoding=UTF-8','-cp',os.pathsep.join(map(str,[jar,stdlib])),
        'com.kingzcheung.xime.speech.RefinementReplayKt',str(out),a.language]
    def unpack(s):return np.frombuffer(base64.b64decode(s),dtype='<f4').copy()
    def pack(s):return base64.b64encode(s.encode('utf8')).decode('ascii')
    with (out/'bridge.log').open('w',encoding='utf8') as errors:
        bridge=subprocess.Popen(command,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=errors,text=True,encoding='utf8',bufsize=1)
        try:
            for line in bridge.stdout:
                parts=line.rstrip('\n').split('\t');op=parts[0];reply=''
                if op=='CASE':
                    name,mode=parts[1:];detector=so.VoiceActivityDetector(config,buffer_size_in_seconds=60)
                    stream=None;regions=[];started=time.perf_counter()
                elif op=='DECODE':
                    r=recognizers[mode];audio=unpack(parts[1]);s=r.create_stream();s.accept_waveform(16000,audio);r.decode_stream(s)
                    text=s.result.text;stamps=list(s.result.timestamps)
                    regions.append(dict(duration_s=len(audio)/16000,text=text,timestamps=stamps))
                    reply=pack(text)+'|'+','.join(map(str,stamps));del s
                elif op=='SPANS':
                    detector.reset();audio=unpack(parts[1]);spans=[]
                    for offset in range(0,len(audio),512):
                        chunk=audio[offset:offset+512];detector.accept_waveform(np.pad(chunk,(0,512-len(chunk))))
                    detector.flush()
                    while not detector.empty():
                        segment=detector.front;spans.append((segment.start/16000,(segment.start+len(segment.samples))/16000));detector.pop()
                    reply=';'.join(str(start)+','+str(end) for start,end in spans)
                elif op=='VAD':
                    detector.accept_waveform(unpack(parts[1]));reply=str(int(detector.is_speech_detected()))
                    while not detector.empty():detector.pop()
                elif op=='RESET':detector.reset()
                elif op=='AUDIO':
                    if stream is None:stream=first.create_stream()
                    stream.accept_waveform(16000,unpack(parts[1]))
                    while first.is_ready(stream):first.decode_stream(stream)
                    reply=pack(first.get_result(stream))
                elif op=='END':
                    stream.set_option('is_final','1');stream.input_finished()
                    while first.is_ready(stream):first.decode_stream(stream)
                    initial=first.get_result(stream);stream=None
                    audio=unpack(parts[2]);refined=''
                    if mode.startswith('refinement'):
                        s=second.create_stream();s.accept_waveform(16000,audio);second.decode_stream(s)
                        refined='' if 'nospeech' in s.result.lang.lower() else s.result.text
                        del s
                    regions.append(dict(boundary=parts[1],duration_s=len(audio)/16000,first=initial,second=refined))
                    reply=pack(initial)+'|'+pack(refined)
                elif op in ['DONE','DONE_JA']:
                    result=dict(**rows[name],mode=mode,first=base64.b64decode(parts[1]).decode('utf8'),
                        second=base64.b64decode(parts[2]).decode('utf8') if op=='DONE' else '',regions=regions,decode_s=time.perf_counter()-started)
                    results.append(result);print(name,mode,len(regions),flush=True)
                    (out/'progress.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf8')
                else:raise RuntimeError(line)
                bridge.stdin.write(reply+'\n');bridge.stdin.flush()
            if bridge.wait()!=0:raise RuntimeError((out/'bridge.log').read_text(encoding='utf8'))
        finally:
            if bridge.poll() is None:bridge.kill()
    report=dict(complete=True,runtime=so.__version__,language=a.language,sense_language='zh' if a.language=='zh' else None,
        threads=1 if a.language=='zh' else 2,scope=__doc__ if a.language=='zh' else 'Production JapaneseZipformerDecoding on whole clips, with native Silero VAD; no microphone segmentation, no preview scheduling, no memory comparison.',clips=results,
        sha256={str(f):hashlib.file_digest(f.open('rb'),'sha256').hexdigest() for f in [*sources,vad,a.manifest]})
    (out/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')

if __name__=='__main__':main()
