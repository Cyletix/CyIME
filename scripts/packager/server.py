#!/usr/bin/env python3
"""CyIME 打包器界面：本机网页（Edge 应用窗口）+ 本地服务。只用标准库。

界面只负责选项和显示日志；每个操作都转成 cyime_packager.py 的命令行参数执行，
所以界面能做的事命令行都能做。服务只监听 127.0.0.1，并要求每次启动随机生成的口令。
"""
from __future__ import annotations

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import threading
import time
import webbrowser

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
CLI = HERE / "cyime_packager.py"
LAST = ROOT / ".gradle/packager/last-gui.json"
ICON = HERE / "web/icon.png"  # CyIME facet launcher icon, same as CyIME-Windows
PORT = 47821  # fixed, so the Edge app window keeps one taskbar identity
TOKEN = secrets.token_urlsafe(16)
ENV = dict(os.environ, PYTHONIOENCODING="utf-8", PYTHONUNBUFFERED="1")
NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def hidden() -> dict:
    """Run a child in its own hidden console. Its descendants (git, PowerShell, Gradle, adb)
    attach to that console instead of each flashing a new window; output still goes to our pipes."""
    if os.name != "nt":
        return {}
    info = subprocess.STARTUPINFO()
    info.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    info.wShowWindow = 0  # SW_HIDE
    return {"startupinfo": info, "creationflags": subprocess.CREATE_NEW_CONSOLE}


def windows_theme() -> str:
    """Follow the Windows app theme like CyIME-Windows, not the browser's own dark mode."""
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize") as key:
            return "light" if winreg.QueryValueEx(key, "AppsUseLightTheme")[0] else "dark"
    except OSError:
        return ""


def cli(*args) -> list[str]:
    # python.exe, not pythonw.exe: the CLI runs in a hidden console that its tools share.
    python = Path(sys.executable).with_name("python.exe")
    return [str(python if python.is_file() else sys.executable), "-B", str(CLI), *args]


class Job:
    def __init__(self):
        self.lock = threading.Lock()
        self.proc: subprocess.Popen | None = None
        self.lines: list[str] = []
        self.code: int | None = None
        self.args: list[str] = []
        self.serial = 0
        self.started = 0.0

    def start(self, args: list[str]) -> bool:
        with self.lock:
            if self.proc and self.proc.poll() is None:
                return False
            self.serial += 1
            self.lines, self.code, self.args, self.started = [], None, args, time.time()
            self.proc = subprocess.Popen(cli(*args), cwd=ROOT, env=ENV, stdout=subprocess.PIPE,
                                         stderr=subprocess.STDOUT, **hidden())
        threading.Thread(target=self.pump, args=(self.proc,), daemon=True).start()
        return True

    def pump(self, proc):
        for raw in proc.stdout:
            self.lines.append(raw.decode("utf-8", errors="replace").rstrip("\r\n"))
        self.code = proc.wait()

    @property
    def running(self) -> bool:
        return bool(self.proc) and self.code is None

    def stop(self):
        if self.running:
            # Only this task's process tree; never other Java/Gradle work on the PC.
            subprocess.run(["taskkill", "/PID", str(self.proc.pid), "/T", "/F"],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=NO_WINDOW)


JOB = Job()
LAST_PING = [time.monotonic()]


def info() -> dict:
    out = subprocess.run(cli("info"), cwd=ROOT, env=ENV, capture_output=True, **hidden())
    if out.returncode:
        raise RuntimeError(out.stderr.decode("utf-8", errors="replace") or "info failed")
    return json.loads(out.stdout.decode("utf-8"))


def pick_file() -> str:
    script = ("Add-Type -AssemblyName System.Windows.Forms;"
              "$d=New-Object System.Windows.Forms.OpenFileDialog;$d.InitialDirectory='D:\\CyIME-Data\\models';"
              "$d.Title='选择要上传的文件';"
              "if($d.ShowDialog() -eq 'OK'){[Console]::OutputEncoding=[Text.Encoding]::UTF8;$d.FileName}")
    out = subprocess.run(["powershell.exe", "-NoProfile", "-STA", "-Command", script],
                         capture_output=True, **hidden())
    return out.stdout.decode("utf-8", errors="replace").strip()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send(self, status, body: bytes, kind="application/json; charset=utf-8"):
        self.send_response(status)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def json(self, value, status=200):
        self.send(status, json.dumps(value, ensure_ascii=False).encode("utf-8"))

    def authorized(self) -> bool:
        return self.headers.get("X-Token") == TOKEN

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/":
            if f"t={TOKEN}" not in self.path:
                return self.send(403, b"forbidden", "text/plain")
            html = (HERE / "web/index.html").read_text(encoding="utf-8").replace("__TOKEN__", TOKEN).replace("__THEME__", windows_theme())
            return self.send(200, html.encode("utf-8"), "text/html; charset=utf-8")
        if path == "/icon.png" and ICON.is_file():
            return self.send(200, ICON.read_bytes(), "image/png")
        if not self.authorized():
            return self.send(403, b"forbidden", "text/plain")
        LAST_PING[0] = time.monotonic()
        if path == "/api/info":
            try:
                data = info()
            except Exception as exc:  # shown in the page
                return self.json({"error": str(exc)}, 500)
            try:
                data["last"] = json.loads(LAST.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                data["last"] = None
            return self.json(data)
        if path == "/api/log":
            start = int(self.path.split("from=", 1)[1]) if "from=" in self.path else 0
            return self.json({"lines": JOB.lines[start:], "next": len(JOB.lines), "running": JOB.running,
                              "code": JOB.code, "args": JOB.args, "serial": JOB.serial, "started": JOB.started})
        self.send(404, b"not found", "text/plain")

    def do_POST(self):
        if not self.authorized():
            return self.send(403, b"forbidden", "text/plain")
        LAST_PING[0] = time.monotonic()
        length = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(length) or b"{}")
        if self.path == "/api/run":
            args = [str(a) for a in body.get("args", [])]
            if not args or args[0] not in ("build", "upload"):
                return self.json({"error": "unsupported"}, 400)
            return self.json({"started": JOB.start(args), "serial": JOB.serial})
        if self.path == "/api/stop":
            JOB.stop()
            return self.json({"ok": True})
        if self.path == "/api/last":
            LAST.parent.mkdir(parents=True, exist_ok=True)
            LAST.write_text(json.dumps(body, ensure_ascii=False, indent=2), encoding="utf-8")
            return self.json({"ok": True})
        if self.path == "/api/reveal":
            # Show a produced APK in Explorer; only files inside this repository.
            target = Path(str(body.get("path", ""))).resolve()
            if target.is_file() and ROOT.resolve() in target.parents:
                subprocess.Popen(["explorer.exe", "/select,", str(target)])
            return self.json({"ok": True})
        if self.path == "/api/pick":
            return self.json({"path": pick_file()})
        self.send(404, b"not found", "text/plain")


def open_window(url: str):
    for edge in (Path(os.getenv("ProgramFiles(x86)", "")) / "Microsoft/Edge/Application/msedge.exe",
                 Path(os.getenv("ProgramFiles", "")) / "Microsoft/Edge/Application/msedge.exe"):
        if edge.is_file():
            subprocess.Popen([str(edge), f"--app={url}", "--window-size=1080,820"], creationflags=NO_WINDOW)
            return
    webbrowser.open(url)


def main():
    try:
        server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    except OSError:  # another packager window is open, or the port is taken
        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    url = f"http://127.0.0.1:{server.server_port}/?t={TOKEN}"
    threading.Thread(target=server.serve_forever, daemon=True).start()
    if "--no-window" in sys.argv:
        print(url, flush=True)
    else:
        open_window(url)
    # The page pings while open; exit once it is closed and no task is running.
    while True:
        time.sleep(5)
        if not JOB.running and time.monotonic() - LAST_PING[0] > 30:
            server.shutdown()
            return


if __name__ == "__main__":
    main()
