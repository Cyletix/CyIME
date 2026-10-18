"""Fetch a fixed, human-transcribed FLEURS pilot; retain only selected WAVs, not full archives.

Selection is archive order, unique prompt ID, 3-25 seconds, first 32 clips per language.
It is fixed before running recognizers and is NOT a full benchmark or conversational corpus.
"""
import argparse, concurrent.futures, csv, hashlib, io, json, tarfile, urllib.request
from pathlib import Path
import soundfile as sf


def read_url(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'CyIME-ASR-comparison'}), timeout=90)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--revision', default='70bb2e84b976b7e960aa89f1c648e09c59f894dd')
    args = p.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    revision = args.revision
    def prepare(pair):
        language, config = pair
        base = f'https://huggingface.co/datasets/google/fleurs/resolve/{revision}/data/{config}/'
        with read_url(base + 'test.tsv') as f: labels = f.read()
        (args.output / f'{language}-test.tsv').write_bytes(labels)
        rows = {r[1]: r for r in csv.reader(io.StringIO(labels.decode('utf8')), delimiter='\t')}
        result, seen = [], set()
        directory = args.output / language
        directory.mkdir()
        # Sequential gzip reading stops once the predetermined selection is complete.
        with read_url(base + 'audio/test.tar.gz') as response, tarfile.open(fileobj=response, mode='r|gz') as archive:
            for member in archive:
                row = rows.get(Path(member.name).name)
                if not member.isfile() or row is None or row[0] in seen or not 3 <= int(row[5])/16000 <= 25:
                    continue
                raw = archive.extractfile(member).read()
                samples, rate = sf.read(io.BytesIO(raw), dtype='float32')
                assert rate == 16000 and samples.ndim == 1
                target = directory / row[1]
                sf.write(target, samples, rate, subtype='PCM_16')
                result.append(dict(id=f'{language}-{row[0]}', language=language, prompt_id=row[0],
                    path=str(target.resolve()), reference=row[2], normalized_reference=row[3], gender=row[6],
                    duration_s=len(samples)/rate, source_audio_sha256=hashlib.sha256(raw).hexdigest(),
                    sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
                seen.add(row[0])
                if len(result) == 32: break
        assert len(result) == 32
        print(language, len(result), round(sum(r['duration_s'] for r in result), 2), 'seconds', flush=True)
        return result
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        rows = sum(pool.map(prepare, [('zh','cmn_hans_cn'), ('ja','ja_jp'), ('en','en_us')]), [])
    (args.output/'manifest.json').write_text(json.dumps(dict(dataset='google/fleurs', revision=revision,
        license='CC-BY-4.0', selection=__doc__, clips=rows), ensure_ascii=False, indent=2), encoding='utf8')


if __name__ == '__main__': main()
