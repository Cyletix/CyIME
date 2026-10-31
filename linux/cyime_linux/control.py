"""Unprivileged local control client; never exposes a TCP control endpoint."""

import json
import socket

from .core import default_state_dir


def request(action, state_dir=None, **kwargs):
    path = (state_dir or default_state_dir()) / "control.sock"
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
        connection.settimeout(60 if action == "copy" else 12)
        connection.connect(str(path))
        connection.sendall(json.dumps(dict(action=action, **kwargs)).encode() + b"\n")
        with connection.makefile("rb") as stream:
            data = stream.readline(16 * 1024 * 1024)
        response = json.loads(data)
        if not response["ok"]:
            raise RuntimeError(response["error"])
        return response["result"]
