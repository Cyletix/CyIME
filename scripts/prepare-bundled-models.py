"""Build the offline edition from the app's pinned model catalog (no market API)."""
import argparse
import hashlib
import json
import re
import shutil
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INCLUDED = {'ochwpro', 'predictive-text-base', 'paraformer-zh-en-int8', 'sensevoice-multilingual-int8'}

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def catalog():
    models = []
    for name in ('model/BuiltinModelCatalog.kt', 'speech/SpeechModelCatalog.kt'):
        source = (ROOT / 'app/src/main/java/com/kingzcheung/xime' / name).read_text(encoding='utf-8')
        for block in source.split('ModelInfo(')[1:]:
            model_id = re.match(r'"([^"]+)"', block)[1]
            version = re.search(r'ModelVersion\(version = "([^"]+)"', block)[1]
            files = [dict(name=n, url=u, sha256=s, size=int(z)) for n,u,s,z in
                     re.findall(r'ModelFile\("([^"]+)", "([^"]*)", "([^"]*)", (\d+)L\)', block)]
            archive = re.search(r'archiveUrl = "([^"]+)", sha256 = "([^"]+)"', block)
            if not files or (not archive and any(not f['sha256'] or not f['url'] for f in files)):
                raise ValueError(f'Unsupported/incomplete pinned catalog: {model_id}')
            models.append(dict(id=model_id, version=version, files=files,
                               archive=archive.groups() if archive else None))
    if len(models) != 6 or len({m['id'] for m in models}) != len(models):
        raise ValueError('Catalog changed: review the offline model edition before building')
    return [m for m in models if m['id'] in INCLUDED]

def download(url, target, sha):
    if target.is_file() and digest(target) == sha:
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + '.part')
    print(f'Downloading {target.name}', flush=True)
    with urllib.request.urlopen(url, timeout=180) as src, temporary.open('wb') as dst:
        shutil.copyfileobj(src, dst)
    if digest(temporary) != sha:
        raise ValueError(f'Checksum mismatch: {target.name}')
    temporary.replace(target)

def prepare(output, cache):
    payload = output / 'bundled-models'
    payload.mkdir(parents=True, exist_ok=True)
    # The generated folder can contain a previous six-model edition. Never ship stale models.
    for obsolete in ('predictive-text-small', 'zipformer-zh-int8'):
        target = (payload / obsolete).resolve()
        if target.parent != payload.resolve():
            raise ValueError('Unsafe generated model path')
        if target.is_dir():
            shutil.rmtree(target)
    manifest = []
    for model in catalog():
        directory = payload / model['id']
        directory.mkdir(exist_ok=True)
        archive = None
        if model['archive']:
            url, sha = model['archive']
            archive = cache / (model['id'] + '.tar.bz2')
            download(url, archive, sha)
        for info in model['files']:
            target = directory / info['name']
            if archive:
                # Extract only explicitly catalogued regular files, never archive paths/links.
                with tarfile.open(archive) as tar:
                    matches = [m for m in tar.getmembers() if m.isfile() and Path(m.name).name == info['name']]
                    if len(matches) != 1:
                        raise ValueError(f'Archive member missing/ambiguous: {info["name"]}')
                    with tar.extractfile(matches[0]) as src, target.open('wb') as dst:
                        shutil.copyfileobj(src, dst)
            else:
                cached = cache / model['id'] / info['name']
                download(info['url'], cached, info['sha256'])
                if not target.is_file() or digest(target) != info['sha256']:
                    shutil.copyfile(cached, target)
            if not target.stat().st_size or info['size'] and target.stat().st_size != info['size']:
                raise ValueError(f'Invalid model size: {target}')
            info['sha256'] = digest(target)
            info['size'] = target.stat().st_size
        manifest.append(dict(id=model['id'], version=model['version'], files=model['files']))
    (payload / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Bundled {len(manifest)} models: {sum(f["size"] for m in manifest for f in m["files"]):,} bytes', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT / 'app/build/generated/bundled-model-assets')
    parser.add_argument('--cache', type=Path, default=ROOT / '.gradle/bundled-model-cache')
    args = parser.parse_args()
    prepare(args.output, args.cache)
