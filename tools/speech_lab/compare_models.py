"""Isolated CPU model comparison on a frozen manifest. No microphone, app settings or product changes.

Each worker uses two CPU threads, no hotwords, and identical PCM. Native streaming
models receive 100 ms chunks with endpoint detection off; finalization follows each
model's public example. Offline recognizers see the whole clip (Reazon: 0.9 s padding).
These are model-only desktop costs, NOT phone latency or production VAD accuracy.
"""
import argparse, gc, hashlib, json, os, platform, subprocess, sys, threading, time
from pathlib import Path

sys.path.insert(0, 'D:/CyIME-Data/speech-lab/deps')
import numpy as np
import soundfile as sf
import psutil
import sherpa_onnx as so

SHARED = Path('D:/models/ASRInput')
EXISTING = Path('D:/GitHub/ASRInput/models')
MODELS = {
    'paraformer': (EXISTING/'sherpa-onnx-streaming-paraformer-bilingual-zh-en', ['zh','en']),
    'sense-auto': (EXISTING/'sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17', ['zh','ja','en']),
    'sense-zh': (EXISTING/'sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17', ['zh']),
    'sense-ja': (EXISTING/'sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17', ['ja']),
    'sense-en': (EXISTING/'sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17', ['en']),
    'zip-zh': (SHARED/'sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30', ['zh']),
    'zip-zh-xl': (SHARED/'sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30', ['zh']),
    'zip-en-kroko': (SHARED/'sherpa-onnx-streaming-zipformer-en-kroko-2025-08-06', ['en']),
    'zip-ja-int8': (SHARED/'sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01', ['ja']),
    'zip-ja-fp32': (SHARED/'sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01', ['ja']),
    'zip-ja-ctc': (SHARED/'japanese-zipformer-base-k2-rs35kh', ['ja']),
    'nemotron': (EXISTING/'sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-320ms-int8-2026-06-11', ['zh','ja','en']),
    'qwen3': (SHARED/'sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25', ['zh','ja','en']),
    'zip-2026-stream-rnnt': (SHARED/'zipformer-large-streaming', ['zh','en']),
    'zip-2026-stream-ctc': (SHARED/'zipformer-large-streaming', ['zh','en']),
    'zip-2026-xl-ctc': (SHARED/'zipformer-xlarge', ['zh','en']),
    'funasr-nano': (SHARED/'sherpa-onnx-funasr-nano-int8-2025-12-30', ['zh','ja','en']),
}


def worker(a):
    folder, languages = MODELS[a.model]
    rows = [r for r in json.loads(a.manifest.read_text(encoding='utf8'))['clips'] if r['language'] in languages]
    if a.limit: rows = [r for lang in languages for r in [q for q in rows if q['language']==lang][:a.limit]]
    def path(name):
        target=folder/name
        assert target.is_file(), target
        return str(target)
    files = []
    online = a.model in ['paraformer','zip-zh','zip-zh-xl','zip-en-kroko','nemotron','zip-2026-stream-rnnt','zip-2026-stream-ctc']
    process = psutil.Process()
    baseline = process.memory_info().rss
    peak = [baseline]
    end_monitor = threading.Event()
    def monitor():
        while not end_monitor.wait(.02): peak[0] = max(peak[0], process.memory_info().rss)
    threading.Thread(target=monitor, daemon=True).start()
    loaded = time.perf_counter()
    if a.model == 'paraformer':
        files = ['encoder.int8.onnx','decoder.int8.onnx','tokens.txt']
        recognizer = so.OnlineRecognizer.from_paraformer(encoder=path(files[0]),decoder=path(files[1]),tokens=path(files[2]),num_threads=2,enable_endpoint_detection=False)
    elif a.model.startswith('sense-'):
        files = ['model.int8.onnx','tokens.txt']
        recognizer = so.OfflineRecognizer.from_sense_voice(model=path(files[0]),tokens=path(files[1]),language=a.model.split('-')[1],use_itn=True,num_threads=2)
    elif a.model=='zip-2026-stream-ctc':
        files=['ctc-chunk-16-left-64.int8.onnx','data/tokens.txt']
        recognizer=so.OnlineRecognizer.from_zipformer2_ctc(model=path(files[0]),tokens=path(files[1]),num_threads=2,enable_endpoint_detection=False)
    elif a.model=='zip-2026-stream-rnnt':
        files=[x+'-chunk-16-left-64.onnx' for x in ['encoder','decoder','joiner']]+['data/tokens.txt']
        recognizer=so.OnlineRecognizer.from_transducer(encoder=path(files[0]),decoder=path(files[1]),joiner=path(files[2]),tokens=path(files[3]),num_threads=2,enable_endpoint_detection=False)
    elif a.model=='zip-2026-xl-ctc':
        files=['ctc.int8.onnx','data/tokens.txt']
        recognizer=so.OfflineRecognizer.from_zipformer_ctc(model=path(files[0]),tokens=path(files[1]),num_threads=2)
    elif online:
        files = (['encoder.onnx','decoder.onnx','joiner.onnx','tokens.txt'] if a.model=='zip-en-kroko' else
            ['encoder.int8.onnx','decoder.int8.onnx' if a.model=='nemotron' else 'decoder.onnx','joiner.int8.onnx','tokens.txt'])
        recognizer = so.OnlineRecognizer.from_transducer(encoder=path(files[0]),decoder=path(files[1]),joiner=path(files[2]),tokens=path(files[3]),num_threads=2,enable_endpoint_detection=False)
    elif a.model in ['zip-ja-int8','zip-ja-fp32']:
        suffix = '.int8.onnx' if a.model=='zip-ja-int8' else '.onnx'
        files = [x+'-epoch-99-avg-1'+suffix for x in ['encoder','decoder','joiner']] + ['tokens.txt']
        recognizer = so.OfflineRecognizer.from_transducer(encoder=path(files[0]),decoder=path(files[1]),joiner=path(files[2]),tokens=path(files[3]),num_threads=2)
    elif a.model == 'qwen3':
        files = ['conv_frontend.onnx','encoder.int8.onnx','decoder.int8.onnx','tokenizer/merges.txt','tokenizer/vocab.json','tokenizer/tokenizer_config.json']
        recognizer = so.OfflineRecognizer.from_qwen3_asr(conv_frontend=path(files[0]),encoder=path(files[1]),decoder=path(files[2]),tokenizer=str(folder/'tokenizer'),num_threads=2,max_new_tokens=512,max_total_len=768)
    elif a.model == 'funasr-nano':
        files=['encoder_adaptor.int8.onnx','llm.int8.onnx','embedding.int8.onnx']+[str(f.relative_to(folder)).replace('\\','/') for f in (folder/'Qwen3-0.6B').rglob('*') if f.is_file()]
        recognizer=so.OfflineRecognizer.from_funasr_nano(encoder_adaptor=path(files[0]),llm=path(files[1]),embedding=path(files[2]),tokenizer=str(folder/'Qwen3-0.6B'),num_threads=2,max_new_tokens=512,itn=True)
    elif a.model == 'zip-ja-ctc':
        os.environ['HF_HUB_OFFLINE']='1'
        os.environ['HF_MODULES_CACHE']=str(a.output.parent/'transformers-modules')
        import torch
        if not hasattr(torch.amp,'custom_fwd'):
            def fwd(f=None, *, device_type, cast_inputs=None): return torch.cuda.amp.custom_fwd(f,cast_inputs=cast_inputs)
            def bwd(f=None, *, device_type): return torch.cuda.amp.custom_bwd(f) if f is not None else torch.cuda.amp.custom_bwd
            torch.amp.custom_fwd=fwd;torch.amp.custom_bwd=bwd
        from transformers import AutoProcessor, AutoModelForCTC
        torch.set_num_threads(2);torch.set_num_interop_threads(1)
        processor=AutoProcessor.from_pretrained(folder,local_files_only=True)
        recognizer=AutoModelForCTC.from_pretrained(folder,trust_remote_code=True,local_files_only=True,torch_dtype=torch.float32,use_safetensors=True).eval()
        files = ['model.safetensors']
    load_s = time.perf_counter()-loaded
    result = dict(model=a.model,folder=str(folder),runtime=so.__version__,provider='cpu',threads=2,
        platform=platform.platform(),cpu=platform.processor(),load_s=load_s,
        baseline_rss_mib=baseline/1048576,loaded_rss_mib=process.memory_info().rss/1048576,
        model_bytes=sum((folder/f).stat().st_size for f in files),streaming=online,clips=[])
    for row in rows:
        samples,sr=sf.read(row['path'],dtype='float32');assert sr==16000 and samples.ndim==1
        started=time.perf_counter();first=None;updates=0;previous='';flush_s=None
        if online:
            stream=recognizer.create_stream()
            if a.model=='nemotron': stream.set_option('language',row['language'])
            for offset in range(0,len(samples),1600):
                stream.accept_waveform(sr,samples[offset:offset+1600])
                while recognizer.is_ready(stream): recognizer.decode_stream(stream)
                partial=recognizer.get_result(stream)
                if partial.strip() and first is None:first=min(offset+1600,len(samples))/16000
                if partial!=previous:updates+=1;previous=partial
            ending=time.perf_counter()
            if a.model=='paraformer': stream.set_option('is_final','1')
            else: stream.accept_waveform(sr,np.zeros(4800,dtype=np.float32))
            stream.input_finished()
            while recognizer.is_ready(stream): recognizer.decode_stream(stream)
            text=recognizer.get_result(stream);flush_s=time.perf_counter()-ending
            del stream
        elif a.model=='zip-ja-ctc':
            inputs=processor(np.pad(samples,(8000,8000)),return_tensors='pt',sampling_rate=sr)
            with torch.inference_mode():logits=recognizer(inputs.input_values).logits
            text=processor.decode(logits.argmax(-1)[0],skip_special_tokens=True).removeprefix('▁')
        else:
            stream=recognizer.create_stream()
            if a.model in ['zip-ja-int8','zip-ja-fp32']:samples=np.pad(samples,(14400,14400))
            stream.accept_waveform(sr,samples);recognizer.decode_stream(stream);text=stream.result.text
            del stream
        result['clips'].append(dict(**row,text=text,decode_s=time.perf_counter()-started,
            first_partial_audio_s=first,partial_updates=updates,flush_s=flush_s))
        result['peak_rss_mib']=max(peak[0],process.memory_info().rss)/1048576
        a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
        print(a.model,row['id'],round(result['clips'][-1]['decode_s'],3),flush=True)
    end_monitor.set()
    result['weights_sha256']={f:hashlib.file_digest((folder/f).open('rb'),'sha256').hexdigest() for f in files}
    result['complete']=True
    a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--model',choices=MODELS)
    p.add_argument('--models',nargs='+',choices=MODELS)
    p.add_argument('--limit',type=int,default=0)
    a=p.parse_args()
    if a.model: return worker(a)
    a.output.mkdir(parents=True,exist_ok=False)
    for name in a.models or MODELS:
        with (a.output/(name+'.log')).open('w',encoding='utf8') as log:
            command=[sys.executable,str(Path(__file__).resolve()),'--model',name,'--manifest',str(a.manifest),
                     '--output',str(a.output/(name+'.json')),'--limit',str(a.limit)]
            print('START',name,flush=True)
            run=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=3600)
            print('END',name,run.returncode,flush=True)
            if run.returncode: (a.output/(name+'.failed')).write_text(str(run.returncode))


if __name__=='__main__':main()
