#!/usr/bin/env python3
"""Install user-session desktop/service launchers without changing the default IME."""

import argparse
from pathlib import Path
import os
import sys

from cyime_linux.core import write_private


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--install", action="store_true", help="Create launchers; does not enable/start the service")
    parser.add_argument("--clipboard-shortcut", action="store_true", help="Bind GNOME Super+V to CyIME; replaces copyq toggle only")
    args = parser.parse_args()
    if not args.install and not args.clipboard_shortcut:
        parser.print_help()
        return
    root = Path(__file__).resolve().parent
    # Keep a virtual environment's interpreter path in generated launchers.
    python = Path(sys.executable).absolute()
    # systemd and desktop Exec are not shell commands. Restrict paths to make
    # these generated launchers unambiguous (the repository already requires this).
    if any(c in str(path) for path in (root, python) for c in (' ', '\n', '"', '%', '\\')):
        raise SystemExit("Launcher paths must not contain spaces, quotes, percent signs or backslashes")
    if args.clipboard_shortcut:
        from cyime_linux.desktop_shortcut import install_clipboard_shortcut
        print("Super+V:", install_clipboard_shortcut(python, root / "run.py"))
    if not args.install:
        return
    data = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share"))
    config = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    legacy = {
        data / "applications/cyime-linux.desktop":
            f"[Desktop Entry]\nType=Application\nName=CyIME Linux\nComment=Clipboard and phone pairing\n"
            f"Exec={python} {root / 'run.py'} gui\nIcon=input-keyboard\nTerminal=false\nCategories=Utility;\n",
        data / "applications/cyime-settings.desktop":
            f"[Desktop Entry]\nType=Application\nName=CyIME Input Settings\nName[zh_CN]=CyIME 输入法设置\n"
            f"Exec={python} {root / 'run.py'} settings\nIcon=preferences-desktop-keyboard\nTerminal=false\nCategories=Settings;\n",
        config / "systemd/user/cyime-linux.service":
            f"[Unit]\nDescription=CyIME session clipboard backend\nAfter=graphical-session.target\n"
            f"PartOf=graphical-session.target\n\n[Service]\nType=simple\n"
            f"ExecStart={python} {root / 'run.py'} daemon\nRestart=on-failure\nRestartSec=5\nUMask=0077\n"
            f"\n[Install]\nWantedBy=graphical-session.target\n",
    }
    previous = dict(legacy)
    for path in previous:
        if path.suffix == ".desktop":
            previous[path] += "Keywords=CyIME;Pinyin;Keyboard;输入法;拼音;剪贴簿;\n"
            if path.name == "cyime-linux.desktop":
                previous[path] += "Name[zh_CN]=CyIME 剪贴簿与手机\n"
    targets = dict(previous)
    manager = data / "applications/cyime-linux.desktop"
    targets[manager] = targets[manager].replace("Name=CyIME Linux", "Name=CyIME Clipboard").replace(
        "Name[zh_CN]=CyIME 剪贴簿与手机", "Name[zh_CN]=CyIME 剪贴簿")
    # Keep a hidden compatibility launcher; do not leave two application entries.
    targets[data / "applications/cyime-settings.desktop"] += "NoDisplay=true\n"
    for path, content in targets.items():
        if path.exists() and path.read_text() not in (content, previous[path], legacy[path]):
            raise SystemExit(f"Existing launcher preserved: {path}")
    for path, content in targets.items():
        if not path.exists() or path.read_text() != content:
            path.parent.mkdir(parents=True, exist_ok=True)
            write_private(path, content.encode())
        print(path)
    print("Optional autostart: systemctl --user daemon-reload && systemctl --user enable --now cyime-linux")


if __name__ == "__main__":
    main()
