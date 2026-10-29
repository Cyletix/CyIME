"""Cut the per-segment screen recordings (demo_typer.py run --record DIR) into one vertical video:
title card, each segment sped up with its heading and one-line caption, end card, background music.

  python tools/demo_typer/compose.py --clips DIR --music a.mp3 [--music b.mp3] --out final.mp4
"""
import argparse
import glob
import json
import os
import re
import subprocess
import tempfile

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = "C\\:/Windows/Fonts/msyh.ttc"
FONT_BOLD = "C\\:/Windows/Fonts/msyhbd.ttc"
W, H, CLIP_H = 1080, 1920, 1726  # 1752x2800 recordings scaled to 1080 wide; captions in the band below


def probe(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration:stream=codec_type",
                          "-of", "json", path], capture_output=True, text=True, check=True).stdout
    info = json.loads(out)
    return float(info["format"]["duration"]), any(s["codec_type"] == "audio" for s in info["streams"])


def text_file(tmp, name, text):
    path = os.path.join(tmp, name)
    with open(path, "w", encoding="utf-8") as out:
        out.write(text)
    return path.replace("\\", "/").replace(":", "\\:")


def caption(segment):
    """Heading plus the first sentence of the segment's prose (no markdown, at most ~28 characters)."""
    lines = [l.strip() for l in segment["text"].splitlines() if l.strip()]
    title = re.sub(r"^[#>\s]*(\[!\w+\]\s*)?", "", lines[0]) if lines else segment["name"]
    body = ""
    for l in lines[1:]:
        if l.startswith(("#", ">", "-", "|", "---")):
            continue
        body = re.split(r"(?<=[。：！？])", l)[0]
        break
    body = re.sub(r"[*`]", "", body)
    if len(body) > 25:  # 38 px glyphs: 25 fit in 1080 with margins
        body = body[:24] + "…"
    return title, body


def card(tmp, name, lines, seconds):
    out = os.path.join(tmp, name + ".mp4")
    draws, y = [], H // 2 - 60 * len(lines) // 2
    for n, (text, size, bold) in enumerate(lines):
        f = text_file(tmp, f"{name}{n}.txt", text)
        draws.append(f"drawtext=fontfile='{FONT_BOLD if bold else FONT}':textfile='{f}':fontsize={size}:"
                     f"fontcolor=white:x=(w-text_w)/2:y={y}")
        y += size + 40
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", f"color=c=0x101014:s={W}x{H}:d={seconds}:r=30",
                    "-f", "lavfi", "-i", f"anullsrc=r=48000:cl=stereo:d={seconds}",
                    "-vf", ",".join(draws) + f",fade=in:0:15,fade=out:st={seconds - 0.5}:d=0.5",
                    "-shortest", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", out], check=True)
    return out


def segment_clip(tmp, n, path, title, body, speed, start=None, end=None):
    duration, has_audio = probe(path)
    tf, bf = text_file(tmp, f"t{n}.txt", title), text_file(tmp, f"b{n}.txt", body)
    out = os.path.join(tmp, f"seg{n:02d}.mp4")
    vf = (f"setpts=PTS/{speed},scale={W}:{CLIP_H},fps=30,pad={W}:{H}:0:0:color=0x101014,"
          f"drawtext=fontfile='{FONT_BOLD}':textfile='{tf}':fontsize=54:fontcolor=white:x=(w-text_w)/2:y={CLIP_H + 30},"
          f"drawtext=fontfile='{FONT}':textfile='{bf}':fontsize=38:fontcolor=0xd0d0d8:x=(w-text_w)/2:y={CLIP_H + 112}")
    cut = (["-ss", str(start)] if start else []) + (["-to", str(end)] if end else [])
    cmd = ["ffmpeg", "-y", "-loglevel", "error", *cut, "-i", path]
    if has_audio:
        af = f"atempo={speed}" if speed != 1 else "anull"
        cmd += ["-vf", vf, "-af", af + ",aresample=48000,aformat=channel_layouts=stereo"]
    else:
        cmd += ["-f", "lavfi", "-i", f"anullsrc=r=48000:cl=stereo", "-vf", vf, "-shortest", "-map", "0:v", "-map", "1:a"]
    subprocess.run(cmd + ["-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
                          "-c:a", "aac", out], check=True)
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--clips", required=True)
    parser.add_argument("--script", default=os.path.join(HERE, "demo.yaml"))
    parser.add_argument("--music", action="append", required=True)
    parser.add_argument("--speed", type=float, default=1.5, help="typing segments; voice segments stay at 1x")
    parser.add_argument("--out", required=True)
    parser.add_argument("--extras", help="JSON: {segment name: [{path, title, body, start?, end?, speed?}]} "
                                         "clips spliced in after that segment (the user's own recordings)")
    args = parser.parse_args()
    extras = json.load(open(args.extras, encoding="utf-8")) if args.extras else {}
    plan = yaml.safe_load(open(args.script, encoding="utf-8"))
    segments = plan["segments"]
    with tempfile.TemporaryDirectory() as tmp:
        parts = [card(tmp, "open", [("CyIME", 110, True), ("做世界上最好用的开源输入法", 52, False),
                                    ("全程键盘实打 · 无剪辑替换文字", 34, False)], 4)]
        for n, seg in enumerate(segments, 1):
            name = f"{n:02d}-{seg['name'].replace(' ', '')}"
            path = os.path.join(args.clips, name + ".mp4")
            if not os.path.exists(path):
                continue
            speed = 1.0 if any("voice:" in l for l in seg["do"]) else args.speed
            parts.append(segment_clip(tmp, n, path, *caption(seg), speed))
            for k, extra in enumerate(extras.get(seg["name"], [])):
                parts.append(segment_clip(tmp, n * 10 + k + 1000, extra["path"], extra["title"], extra["body"],
                                          extra.get("speed", 1.0), extra.get("start"), extra.get("end")))
        parts.append(card(tmp, "end", [("这就是当前最强输入法！", 64, True), ("github.com/Cyletix/CyIME", 40, False)], 5))
        listing = os.path.join(tmp, "parts.txt")
        with open(listing, "w", encoding="utf-8") as out:
            out.writelines(f"file '{p.replace(os.sep, '/')}'\n" for p in parts)
        body = os.path.join(tmp, "body.mp4")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", listing,
                        "-c", "copy", body], check=True)
        total, _ = probe(body)
        # Music bed: the given tracks back to back (give enough to cover the video), faded in and out.
        music_inputs = []
        for m in args.music:
            music_inputs += ["-i", m]
        n = len(args.music)
        chain = "".join(f"[{i + 1}:a]" for i in range(n)) + f"concat=n={n}:v=0:a=1,atrim=0:{total},volume=0.35," \
                f"afade=t=in:d=2,afade=t=out:st={total - 4}:d=4[m];[0:a][m]amix=inputs=2:normalize=0:duration=first[a]"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", body, *music_inputs, "-filter_complex", chain,
                        "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                        "-movflags", "+faststart", args.out], check=True)
    print(args.out, f"{total / 60:.1f} min")


if __name__ == "__main__":
    main()
