#!/usr/bin/env python3
"""Single entry point for the manager, IME settings and clipboard backend."""

import argparse
import json
from pathlib import Path
import sys

from cyime_linux.core import default_state_dir


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", nargs="?", default="gui", choices=["gui", "clipboard", "settings", "daemon", "qr", "status", "stop"])
    parser.add_argument("--state-dir", type=Path, default=default_state_dir())
    parser.add_argument("--port", type=int, default=8788)
    args, extra = parser.parse_known_args()
    sys.argv = [sys.argv[0], "--state-dir", str(args.state_dir), "--port", str(args.port)] + extra
    if args.command in ("gui", "clipboard", "settings"):
        if args.command == "clipboard":
            sys.argv.append("--toggle-clipboard")
        elif args.command == "settings":
            sys.argv.append("--open-settings")
        from cyime_linux.gui import main as gui_main
        return gui_main()
    if args.command == "daemon":
        from cyime_linux.daemon import main as daemon_main
        return daemon_main()
    from cyime_linux.control import request
    if args.command == "qr":
        import qrcode
        value = request("offer", args.state_dir)
        code = qrcode.QRCode(border=2)
        code.add_data(value["uri"])
        code.make(fit=True)
        code.print_ascii(invert=True)
        print("QR expires in 5 minutes. Do not share this code.")
    else:
        print(json.dumps(request(args.command, args.state_dir), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
