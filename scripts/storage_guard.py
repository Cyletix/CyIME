"""Read-only disk preflight for CyIME builds and host replays. Sizes are GiB."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

GIB = 1024 ** 3
GENERATED_LIMIT = 160 * GIB
RESERVATION = 5 * GIB


def windows_path(path):
    value = Path(path).as_posix()
    if len(value) < 7 or not value.startswith("/mnt/") or value[6] != "/" or not value[5].isalpha():
        raise ValueError("Not a mounted Windows path: " + value)
    return value[5].upper() + ":/" + value[7:]


def allocated_inputs(paths):
    """Count hard-linked files once; never traverse symlink/junction directories."""
    paths = [Path(p) for p in paths]
    # DrvFS per-file metadata is slow: let Windows scan its own filesystem.
    win_python = shutil.which("python.exe") if os.name != "nt" else None
    if win_python and str(Path(__file__).resolve()).startswith("/mnt/") and all(str(p).startswith("/mnt/") for p in paths):
        result = subprocess.run([win_python, "-B", windows_path(Path(__file__).resolve()),
                                 "--measure", *[windows_path(p) for p in paths]],
                                check=True, capture_output=True, text=True, timeout=120)
        return int(result.stdout.strip())
    seen = set()
    total = 0
    for root in paths:
        for base, dirs, files in os.walk(root, followlinks=False):
            dirs[:] = [d for d in dirs if not Path(base, d).is_symlink()
                       and not (getattr(os.path, "isjunction", lambda p: False)(os.path.join(base, d)))]
            for name in files:
                path = Path(base, name)
                if path.is_symlink():
                    continue
                try:
                    info = path.lstat()
                    if getattr(info, "st_file_attributes", 0) & 0x400:
                        continue  # WSL reparse links are not owned file contents.
                except FileNotFoundError:
                    continue
                key = (info.st_dev, info.st_ino) if info.st_ino else str(path)
                if key not in seen:
                    seen.add(key)
                    total += info.st_size
    return total


def check_storage(repo):
    repo = Path(repo).resolve()
    locations = [(Path("C:/"), 40), (Path("D:/"), 100)] if os.name == "nt" else [
        (Path("/mnt/c"), 40), (Path("/mnt/d"), 100), (Path.home(), 20)]
    for path, floor in locations:
        if path.exists() and shutil.disk_usage(path).free < floor * GIB + RESERVATION:
            raise ValueError(f"Storage budget: {path} must retain {floor} GiB plus 5 GiB task headroom; clean redundant outputs first.")
    size = allocated_inputs([repo / ".codex-artifacts", repo / "app/build", repo / "app/.cxx", repo / ".gradle"])
    if size + RESERVATION > GENERATED_LIMIT:
        raise ValueError(f"Storage budget: generated files use {size/GIB:.1f} GiB; limit is 160 GiB including 5 GiB task headroom.")
    print(f"[STORAGE] generated={size/GIB:.1f} GiB / 160 GiB; disk reserve checked", flush=True)
    return size


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--measure":
        print(allocated_inputs([Path(p) for p in sys.argv[2:]]))
    else:
        check_storage(Path(__file__).resolve().parents[1])
