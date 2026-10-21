"""Desktop OCR regression, compiling the SAME production Java pipeline; no ADB.

Requires Python/Pillow and a JDK. Dependencies and native extraction stay in --cache on D:.
Run: python tools/ocr_lab/run.py --output .codex-artifacts/ocr-host-regression
"""
import argparse, hashlib, json, os, random, shutil, subprocess, urllib.request
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--cache', type=Path, default=Path('D:/CyIME-Data/ocr-lab'))
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--exploratory', action='store_true', help='Also run known difficult tilt cases; failures remain failures')
managed_jdks = sorted((Path.home()/'.gradle/jdks').glob('jdk-21*'))
default_jdk = managed_jdks[-1] if managed_jdks else Path(os.environ.get('JAVA_HOME', ''))
parser.add_argument('--java-home', type=Path, default=default_jdk)
args = parser.parse_args()
out = args.output.resolve(); out.mkdir(parents=True, exist_ok=True)
cache = args.cache.resolve(); (cache/'java').mkdir(parents=True, exist_ok=True)
models = ROOT/'app/src/main/assets/ocr/ppocr-v5'

DEPENDENCIES = {
 'onnxruntime-1.28.0.jar': 'com/microsoft/onnxruntime/onnxruntime/1.28.0/onnxruntime-1.28.0.jar',
 'opencv-4.9.0-0.jar': 'org/openpnp/opencv/4.9.0-0/opencv-4.9.0-0.jar',
}
EXPECTED_SHA256 = {
 'onnxruntime-1.28.0.jar':'3a9dc00bed7ee467ded8a257f99fc216570d385f8b06053570d33e9b5d7c7a37',
 'opencv-4.9.0-0.jar':'17823d249cb8ba18bc3cdb878db84936694d1cfaeac0f95517e320c8a8c458d9',
}
for name, maven in DEPENDENCIES.items():
    target = cache/'java'/name
    if not target.exists():
        urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/'+maven, target)
    if hashlib.sha256(target.read_bytes()).hexdigest() != EXPECTED_SHA256[name]:
        raise SystemExit(f'Unexpected dependency checksum: {target}')

fixtures = out/'fixtures'; fixtures.mkdir(exist_ok=True)
cases = []
def add(name, image, expected):
    path = fixtures/(name+'.png'); image.save(path)
    cases.append((path, expected))

source = ROOT/'app/src/androidTest/assets/ocr/keycaps-reported.jpg'
strip = Image.open(source).convert('RGB').crop((36, 1017, 625, 1094))
add('keycaps-reported', strip, 'Z X C V B N')
if args.exploratory:
    for angle in (-8, 8):
        add(f'keycaps-tilt-{angle}', strip.rotate(angle, Image.Resampling.BICUBIC, expand=True, fillcolor=(90,90,90)), 'Z X C V B N')
for scale in (.8, 1.5):
    add(f'keycaps-scale-{scale}', strip.resize((round(strip.width*scale),round(strip.height*scale)),Image.Resampling.BICUBIC), 'Z X C V B N')

add('empty-textured-camera', Image.open(ROOT/'app/src/androidTest/assets/ocr/empty-textured-camera.png'), '')
for gray in (0, 128, 255): add(f'blank-{gray}', Image.new('RGB',(640,160),(gray,gray,gray)), '')
rng = random.Random(77)
for name, low, high in [('dark-noise',0,34),('low-contrast-noise',110,119)]:
    im=Image.new('RGB',(640,160)); im.putdata([(g,g,g) for g in (rng.randint(low,high) for _ in range(640*160))]); add(name,im,'')
gradient=Image.new('RGB',(640,160)); gradient.putdata([(i%640*255//639,)*3 for i in range(640*160)]); add('gradient',gradient,'')
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 48)
for inverse in (False, True):
    im=Image.new('RGB',(1000,160),'black' if inverse else 'white')
    ImageDraw.Draw(im).text((30,40),'Hello, world! 123',font=font,fill='white' if inverse else 'black')
    add('punctuation-dark' if inverse else 'punctuation-light', im, 'Hello, world! 123')
# Keycap legends: the detector gives lone symbols no box; they must still be read.
def keycaps(legends, scale, blur=0):
    kw, kh, gap = int(110*scale), int(100*scale), int(14*scale)
    im = Image.new('RGB', ((kw+gap)*len(legends)+kw+40, kh+40), (70,70,72)); draw = ImageDraw.Draw(im)
    big = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', int(30*scale)); small = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', int(22*scale))
    x = 20
    for upper, lower in legends:
        w = kw*2 if upper == 'Shift' else kw
        draw.rounded_rectangle((x,20,x+w,20+kh), radius=int(12*scale), fill=(222,222,220))
        draw.text((x+int(22*scale),20+int(8*scale)), upper, font=small if upper == 'Shift' else big, fill=(40,40,40))
        if lower: draw.text((x+int(26*scale),20+int(50*scale)), lower, font=big, fill=(40,40,40))
        x += w+gap
    return im.filter(ImageFilter.GaussianBlur(blur*scale)) if blur else im
symbol_row = [('M',None),('<',None),('>',None),('?',None),('Shift',None)]
for scale, blur in ((.5,0),(.5,1.2),(.7,1.2),(1,1.2)):
    add(f'keycaps-symbols-{scale}-{blur}', keycaps(symbol_row, scale, blur), 'M < > ? Shift')
if args.exploratory:
    # Known misses: a lone ">" the detector does box is read as "V"; lower legends (, . /)
    # are a few pixels tall with no word beside them.
    add('keycaps-symbols-1-0', keycaps(symbol_row, 1), 'M < > ? Shift')
    add('keycaps-two-legends', keycaps([('M',None),('<',','),('>','.'),('?','/'),('Shift',None)], 1), 'M < , > . ? / Shift')
im=Image.new('RGB',(1000,260),'white'); draw=ImageDraw.Draw(im)
draw.text((30,25),'中文文字识别',font=font,fill='black'); draw.text((30,145),'Hello world 12345',font=font,fill='black')
add('chinese-english-multiline', im, '中文文字识别 Hello world 12345')
(out/'fixtures.tsv').write_text(''.join(f'{path}\t{expected}\n' for path,expected in cases),encoding='utf8')

classes = cache/'classes'; classes.mkdir(exist_ok=True)
classpath = str(cache/'java'/'*')
executable_suffix = '.exe' if os.name == 'nt' else ''
subprocess.run([str(args.java_home/f'bin/javac{executable_suffix}'),'-encoding','UTF-8','-cp',classpath,'-d',str(classes),
 str(ROOT/'app/src/main/java/com/kingzcheung/xime/ocr/DocumentOcrPipeline.java'), str(ROOT/'tools/ocr_lab/OcrHostCheck.java')], check=True)
result = subprocess.run([str(args.java_home/f'bin/java{executable_suffix}'),'-Dfile.encoding=UTF-8',f'-Djava.io.tmpdir={cache}',
 '-cp',str(classes)+os.pathsep+classpath,'OcrHostCheck',str(models),str(out/'fixtures.tsv'),str(out/'results')],
 stdout=subprocess.PIPE, stderr=subprocess.STDOUT, encoding='utf8', errors='replace')
print(result.stdout, end='')
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
(out/'manifest.json').write_text(json.dumps({
 'pipeline_sha256':sha(ROOT/'app/src/main/java/com/kingzcheung/xime/ocr/DocumentOcrPipeline.java'),
 'source_sha256':sha(source),
 'dependencies':{p.name:sha(p) for p in (cache/'java').glob('*.jar')},
 'models':{p.name:sha(p) for p in models.iterdir() if p.is_file()},
 'java_home':str(args.java_home),'device_used':False,'passed':result.returncode==0},ensure_ascii=False,indent=2),encoding='utf8')
raise SystemExit(result.returncode)
