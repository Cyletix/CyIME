"""Extract a numbered, timestamped contact sheet from a GitHub issue recording."""
import argparse
import io
import subprocess
import urllib.request
from pathlib import Path
from PIL import Image, ImageDraw

ATTACHMENTS = {
    228: "f1276a86-af0f-4e06-99c8-688288f71a75",
    229: "37124b93-bd13-4cee-8938-a0978e4501ec",
    230: "8fdc6efd-5097-40ec-9ad9-fdf6a4fb822a",
}


def extract(issue, directory):
    directory.mkdir(parents=True, exist_ok=False)
    video = directory / "recording.mp4"
    url = "https://github.com/user-attachments/assets/" + ATTACHMENTS[issue]
    with urllib.request.urlopen(url, timeout=30) as response, video.open("xb") as stream:
        while block := response.read(1024 * 1024):
            if stream.tell() + len(block) > 64 * 1024 * 1024:
                raise ValueError("Recording exceeds 64 MiB")
            stream.write(block)
    duration = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                              "-of", "csv=p=0", str(video)], text=True).strip())
    count = 12
    sheet = Image.new("RGB", (4 * 290, 3 * 275), "#333333")
    draw = ImageDraw.Draw(sheet)
    for i in range(count):
        t = max(0, (duration - .25) * i / (count - 1))
        data = subprocess.check_output(["ffmpeg", "-v", "error", "-ss", str(t), "-i", str(video),
                                        "-frames:v", "1", "-vf", "scale=280:247:force_original_aspect_ratio=decrease",
                                        "-f", "image2pipe", "-c:v", "png", "-"])
        image = Image.open(io.BytesIO(data))
        x, y = (i % 4) * 290, (i // 4) * 275
        sheet.paste(image, (x, y+18))
        draw.text((x+2, y+2), f"#{issue}   {t:.1f}s", fill="white")
    sheet.save(directory / "frames.jpg", quality=92)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    for number in ATTACHMENTS:
        extract(number, args.output / str(number))
