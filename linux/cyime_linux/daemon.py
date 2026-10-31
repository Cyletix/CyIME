"""Session-local clipboard owner, TLS phone endpoint and private GUI control socket."""

import argparse
from collections import deque
import fcntl
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import hashlib
import ipaddress
import os
from pathlib import Path
import queue
import signal
import socket
import socketserver
import ssl
import struct
import sys
import threading
import time

from PyQt6.QtCore import QTimer
from PyQt6.QtWidgets import QApplication
from PyQt6.QtCore import QMimeData
from PyQt6.QtGui import QImage

from .core import Store, default_state_dir, lan_addresses, private_dir, profile, validate_profile
from .clipboard_media import MAX_IMAGE, decode, encode, image_record, validate_image


class Dispatch:
    """All Qt clipboard and SQLite mutations execute on the session GUI thread."""
    def __init__(self):
        self.pending = queue.Queue(maxsize=32)

    def call(self, operation):
        job = dict(operation=operation, event=threading.Event(), cancelled=False)
        self.pending.put_nowait(job)
        if not job["event"].wait(8):
            job["cancelled"] = True
            raise TimeoutError("Desktop clipboard is not responding")
        if "error" in job:
            raise job["error"]
        return job.get("result")

    def drain(self):
        for _ in range(8):
            try:
                job = self.pending.get_nowait()
            except queue.Empty:
                return
            try:
                if not job["cancelled"]:
                    job["result"] = job["operation"]()
            except Exception as error:
                job["error"] = error
            finally:
                job["event"].set()


class Backend:
    def __init__(self, store, clipboard, app, port):
        self.store, self.clipboard, self.app, self.port = store, clipboard, app, port
        self.dispatch = Dispatch()
        self.suppressed = None
        self.suppressed_image = None
        self.clipboard_error = None
        self.discovery_error = None
        self.addresses = []
        self.advertiser = self.advertisement = None
        self.image_requests = {}
        self.clipboard.dataChanged.connect(self.capture)
        self.timer = QTimer()
        self.timer.timeout.connect(self.dispatch.drain)
        self.timer.start(10)
        self.network_timer = QTimer()
        self.network_timer.timeout.connect(self.refresh_network)
        self.network_timer.start(15000)
        self.refresh_network()
        self.capture()

    def capture(self):
        try:
            mime = self.clipboard.mimeData()
            if mime and mime.hasImage():
                image = self.clipboard.image()
                if image.width() * image.height() > 16 * 1024 * 1024: raise ValueError("图片尺寸超限")
                data = encode(image)
                if hashlib.sha256(data).hexdigest() == self.suppressed_image: return
                self.suppressed_image = None
                for format in ("image/png", "image/jpeg", "image/webp", "image/gif"):
                    if mime.hasFormat(format):
                        raw = bytes(mime.data(format))
                        if raw:
                            data = raw
                            break
                value, data = image_record(data)
                if value["hash"] == self.suppressed: return
                self.store.record_image(value, "linux", data)
                self.clipboard_error = None
                return
            text = self.clipboard.text()
            value = profile(text)
            if value["hash"] == self.suppressed:
                return
            self.suppressed = None
            self.store.record(text, "linux")
            self.clipboard_error = None
        except (ValueError, OverflowError):
            pass
        except Exception as error:
            self.clipboard_error = str(error)

    def put(self, value):
        result = validate_profile(value)
        self.suppressed = result["hash"]
        self.clipboard.setText(result["text"])
        return self.store.record(result["text"], "phone")

    def permissions(self):
        return dict(v=2, send_enabled=self.store.get("send_enabled", True),
                    receive_enabled=self.store.get("receive_enabled", True), images_v=1)

    def copy_request(self, identifier):
        row = self.dispatch.call(lambda: self.store.item(identifier))
        if row["kind"] == "image":
            hash = row["hash"]
            def need():
                image = self.store.image(hash)
                if image["original"] is not None: return True
                if not image["owner"] or not self.permissions()["receive_enabled"]:
                    raise ValueError("只有预览，原图未缓存；请连接来源手机并启用手机→电脑同步")
                if not self.store.db.execute("SELECT 1 FROM devices WHERE id=?", (image["owner"],)).fetchone():
                    raise ValueError("来源手机已解除配对；预览已保留")
                self.image_requests[hash] = (image["owner"], time.monotonic() + 45)
                return False
            if not self.dispatch.call(need):
                deadline = time.monotonic() + 45
                while time.monotonic() < deadline:
                    def available():
                        image = self.store.image(hash)
                        if image["original"] is not None: return True
                        if not self.permissions()["receive_enabled"] or not self.store.db.execute(
                                "SELECT 1 FROM devices WHERE id=?", (image["owner"],)).fetchone():
                            self.image_requests.pop(hash, None)
                            raise ValueError("同步已暂停或来源手机已解除配对；预览已保留")
                        return False
                    ready = self.dispatch.call(available)
                    if ready: break
                    time.sleep(.1)
                else: raise TimeoutError("原图下载超时；预览已保留，请确认手机联网并打开输入法后重试")
        return self.dispatch.call(lambda: self.control(dict(action="copy", id=identifier)))

    def refresh_network(self):
        try:
            addresses = lan_addresses()
            if addresses == self.addresses and self.advertiser is not None:
                return
            self.addresses = addresses
            self.close_discovery()
            from zeroconf import ServiceInfo, Zeroconf, IPVersion
            if not addresses:
                self.discovery_error = "No LAN connection"
                return
            self.advertiser = Zeroconf(ip_version=IPVersion.V4Only)
            self.advertisement = ServiceInfo("_cyime-clip._tcp.local.",
                f"CyIME-{self.store.get('server_id')}._cyime-clip._tcp.local.",
                addresses=[socket.inet_aton(a) for a in addresses], port=self.port,
                properties={"v": "2", "kind": "linux", "id": self.store.get("server_id"),
                            "name": self.store.name, "fp": self.store.fingerprint})
            self.advertiser.register_service(self.advertisement)
            self.discovery_error = None
        except Exception as error:
            self.discovery_error = str(error)

    def close_discovery(self):
        if self.advertiser:
            if self.advertisement:
                self.advertiser.unregister_service(self.advertisement)
            self.advertiser.close()
        self.advertiser = self.advertisement = None

    def control(self, body):
        action = body.get("action")
        if action == "status":
            return dict(**self.permissions(), name=self.store.name, devices=self.store.devices(),
                        pending_pairings=self.store.pending_pairings(),
                        endpoints=[f"https://{a}:{self.port}" for a in self.addresses],
                        clipboard_error=self.clipboard_error, discovery_error=self.discovery_error,
                        platform=self.app.platformName())
        if action == "offer":
            return self.store.offer([f"https://{a}:{self.port}" for a in self.addresses])
        if action == "approve_pairing":
            self.store.approve_pairing(body["id"], body["code"])
            return True
        if action == "reject_pairing":
            self.store.reject_pairing(body["id"])
            return True
        if action == "history":
            return self.store.history(body.get("source", "all"), body.get("search", ""), body.get("category", "all"))
        if action == "import_image":
            path = Path(body["path"])
            if not path.is_file() or path.stat().st_size > MAX_IMAGE: raise ValueError("图片超过 32 MiB 或无法读取")
            value, data = image_record(path.read_bytes())
            self.store.record_image(value, "linux", data)
            return True
        if action == "copy":
            row = self.store.item(int(body["id"]))
            if row["kind"] == "image":
                data = self.store.image(row["hash"])["original"]
                if data is None: raise ValueError("原图尚未下载")
                image = decode(data)
                mime = QMimeData()
                mime.setImageData(image)
                png = encode(image)
                mime.setData("image/png", png)
                self.suppressed = row["hash"]
                self.suppressed_image = hashlib.sha256(png).hexdigest()
                self.clipboard.setMimeData(mime)
                return True
            text = row["text"]
            self.suppressed = None
            self.clipboard.setText(text)
            self.store.record(text, "linux")
            return True
        if action == "edit":
            return self.store.edit(int(body["id"]), body["text"], body["expected_hash"])
        if action == "pin":
            self.store.pin(int(body["id"]), body["value"])
            return True
        if action == "delete":
            self.store.delete(int(body["id"]))
            return True
        if action == "revoke":
            self.store.revoke(body["id"])
            return True
        if action == "settings":
            for key in ("send_enabled", "receive_enabled"):
                if key in body:
                    if type(body[key]) is not bool:
                        raise ValueError("Invalid switch value")
                    self.store.set(key, body[key])
            return self.permissions()
        if action == "backup":
            self.store.backup(body["path"])
            return True
        if action == "restore":
            return self.store.restore(body["path"])
        if action == "stop":
            QTimer.singleShot(100, self.app.quit)
            return True
        raise ValueError("Unknown control command")


class PhoneServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 16

    def __init__(self, address, backend):
        self.backend = backend
        self.slots = threading.BoundedSemaphore(16)
        self.attempts = {}
        self.attempt_lock = threading.Lock()
        super().__init__(address, PhoneHandler)

    def get_request(self):
        connection, address = self.socket.accept()
        if not ipaddress.ip_address(address[0]).is_private:
            connection.close()
            raise OSError("Non-LAN client refused")
        connection.settimeout(5)
        try:
            # TLS handshakes happen in worker threads, not in the accept loop.
            return self.tls.wrap_socket(connection, server_side=True, do_handshake_on_connect=False), address
        except Exception:
            connection.close()
            raise

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.slots.release()

    def handle_error(self, request, client_address):
        # Unauthenticated garbage must not print tokens or clipboard content.
        pass

    def allow_pair_attempt(self, address):
        with self.attempt_lock:
            now = time.monotonic()
            self.attempts = {key: times for key, times in self.attempts.items()
                             if times and times[-1] > now - 60}
            if address not in self.attempts and len(self.attempts) >= 256:
                return False
            times = self.attempts.setdefault(address, deque(maxlen=10))
            if len(times) == 10 and times[0] > now - 60:
                return False
            times.append(now)
            return True


class PhoneHandler(BaseHTTPRequestHandler):
    server_version = "CyIME-Linux/0.1"

    def setup(self):
        self.request.do_handshake()
        super().setup()

    def log_message(self, *args):
        pass

    def reply(self, status, value=None, etag=None):
        data = json.dumps(value, ensure_ascii=False).encode() if value is not None else b""
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        if etag:
            self.send_header("ETag", '"' + etag + '"')
        self.end_headers()
        self.wfile.write(data)

    def body(self):
        if self.headers.get_content_type() != "application/json" or self.headers.get("Transfer-Encoding"):
            raise ValueError("Expected bounded JSON body")
        size = int(self.headers.get("Content-Length", "0"))
        # JSON escaping can expand 64 KiB of text sixfold.
        if not 0 < size <= 400000:
            raise OverflowError("Request too large")
        data = self.rfile.read(size)
        if len(data) != size:
            raise ValueError("Incomplete request")
        value = json.loads(data)
        if not isinstance(value, dict):
            raise ValueError("Expected JSON object")
        return value

    def route(self, method):
        backend = self.server.backend
        store = backend.store
        call = backend.dispatch.call
        try:
            if method == "POST" and self.path in ("/api/pair", "/api/pair/nearby"):
                if not self.server.allow_pair_attempt(self.client_address[0]):
                    self.reply(429, dict(error="Too many attempts"))
                    return
                body = self.body()
                if self.path == "/api/pair/nearby":
                    status, result = call(lambda: store.nearby_pair(body))
                    self.reply(status, result)
                else:
                    self.reply(201, call(lambda: store.pair(body)))
                return
            if not call(lambda: store.authorize(self.headers.get("Authorization", ""))):
                self.reply(401, dict(error="Pairing required"))
                return
            if method == "GET" and self.path == "/api/status":
                self.reply(200, call(backend.permissions))
                return
            if self.path.startswith("/api/images"):
                self.images(method)
                return
            if self.path != "/api/clipboard" or method not in ("GET", "PUT"):
                self.reply(404, dict(error="Endpoint unavailable; use QR pairing"))
                return
            body = self.body() if method == "PUT" else None

            def transfer():
                if not store.authorize(self.headers.get("Authorization", "")):
                    return 401, dict(error="Pairing revoked")
                permissions = backend.permissions()
                if not permissions["send_enabled" if method == "GET" else "receive_enabled"]:
                    return 403, dict(**permissions, sync_paused=True)
                if method == "PUT":
                    return 200, backend.put(body)
                return 200, store.get("current")

            status, value = call(transfer)
            if status == 200 and value is None:
                status = 204
            elif method == "GET" and status == 200 and self.headers.get("If-None-Match") == '"' + value["hash"] + '"':
                status, value = 304, None
            self.reply(status, value, value.get("hash") if value and status == 200 else None)
        except PermissionError:
            self.reply(401, dict(error="Pairing code invalid or expired"))
        except OverflowError:
            self.reply(413, dict(error="Clipboard exceeds limit"))
        except (ValueError, TypeError, KeyError, UnicodeError):
            self.reply(400, dict(error="Invalid request"))
        except (TimeoutError, queue.Full):
            self.reply(503, dict(error="Clipboard busy"))
        except Exception:
            self.reply(500, dict(error="Clipboard operation failed"))

    def images(self, method):
        backend = self.server.backend
        store, call = backend.store, backend.dispatch.call
        header = self.headers.get("Authorization", "")
        def identity():
            if not store.authorize(header): raise PermissionError()
            return store.db.execute("SELECT id FROM devices WHERE token_hash=?", (hashlib.sha256(header[7:].encode()).hexdigest(),)).fetchone()[0]
        owner = call(identity)
        sending = method == "GET" and self.path != "/api/images/needs"
        if not call(backend.permissions)["send_enabled" if sending else "receive_enabled"]:
            self.reply(403, dict(error="Image sync paused")); return
        if self.path == "/api/images/current":
            if method == "GET":
                self.reply(200, dict(image=call(lambda: None if store.get("current_image_owner") == owner else store.get("current_image"))))
                return
            if method == "PUT":
                value = validate_image(self.body())
                self.reply(200, call(lambda: store.record_image(value, "phone", owner=owner))); return
        if method == "GET" and self.path == "/api/images/needs":
            def needs():
                backend.image_requests = {key: value for key, value in backend.image_requests.items() if value[1] > time.monotonic()}
                return [key for key, value in backend.image_requests.items() if value[0] == owner]
            self.reply(200, dict(hashes=call(needs))); return
        import re
        match = re.fullmatch(r"/api/images/([0-9a-f]{64})/original", self.path)
        if not match: self.reply(404); return
        hash = match[1]
        if method == "GET":
            data = call(lambda: store.image(hash))["original"]
            if data is None: self.reply(409, dict(error="Original unavailable")); return
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers(); self.wfile.write(data); return
        if method == "PUT":
            pending = call(lambda: backend.image_requests.get(hash))
            if not pending or pending[0] != owner or pending[1] <= time.monotonic(): raise PermissionError()
            size = int(self.headers.get("Content-Length", "0"))
            if self.headers.get("Transfer-Encoding") or not 0 < size <= MAX_IMAGE: raise ValueError("Invalid image body")
            data = self.rfile.read(size)
            if len(data) != size: raise ValueError("Incomplete original")
            decode(data)
            def accept():
                identity()
                if not backend.permissions()["receive_enabled"]: raise PermissionError()
                store.accept_original(hash, data, owner)
                backend.image_requests.pop(hash, None)
            call(accept)
            self.reply(200, dict(hash=hash)); return
        self.reply(404)

    def do_GET(self):
        self.route("GET")

    def do_POST(self):
        self.route("POST")

    def do_PUT(self):
        self.route("PUT")


class ControlHandler(socketserver.StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(10)
        credentials = self.connection.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, struct.calcsize("3i"))
        if struct.unpack("3i", credentials)[1] != os.getuid():
            return
        try:
            data = self.rfile.readline(262145)
            if len(data) > 262144 or not data.endswith(b"\n"):
                raise ValueError("Control command too large")
            body = json.loads(data)
            backend = self.server.backend
            result = backend.copy_request(int(body["id"])) if body.get("action") == "copy" else backend.dispatch.call(lambda: backend.control(body))
            response = dict(ok=True, result=result)
        except Exception as error:
            response = dict(ok=False, error=str(error))
        self.wfile.write(json.dumps(response, ensure_ascii=False).encode() + b"\n")


class ControlServer(socketserver.ThreadingUnixStreamServer):
    daemon_threads = True


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--state-dir", type=Path, default=default_state_dir())
    parser.add_argument("--port", type=int, default=8788)
    args = parser.parse_args(argv)
    os.umask(0o077)
    directory = private_dir(args.state_dir)
    lock = open(directory / "daemon.lock", "a+")
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        raise SystemExit("CyIME Linux backend is already running")
    store = Store(directory)
    if not os.environ.get("DISPLAY") and not os.environ.get("WAYLAND_DISPLAY"):
        raise SystemExit("Start the backend inside your desktop session")
    # Qt's generic Wayland clipboard cannot observe unfocused apps. XWayland cannot
    # reliably bridge native Wayland either; do not pretend that support is complete.
    if os.environ.get("XDG_SESSION_TYPE") == "wayland" and not os.environ.get("CYIME_ALLOW_WAYLAND_PREVIEW"):
        raise SystemExit("Wayland clipboard support is not complete; use an X11 session for now")
    app = QApplication(["cyime-linux-backend"])
    app.setQuitOnLastWindowClosed(False)
    app.setDesktopFileName("cyime-linux")
    from .tray import BackendTray
    tray = BackendTray(app, directory, args.port)
    backend = Backend(store, app.clipboard(), app, args.port)
    phone = PhoneServer(("0.0.0.0", args.port), backend)
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(store.cert_path, store.key_path)
    phone.tls = tls
    control_path = store.directory / "control.sock"
    control_path.unlink(missing_ok=True)
    control = ControlServer(str(control_path), ControlHandler)
    control.backend = backend
    control_path.chmod(0o600)
    threading.Thread(target=phone.serve_forever, daemon=True).start()
    threading.Thread(target=control.serve_forever, daemon=True).start()
    signal.signal(signal.SIGTERM, lambda *_: app.quit())
    signal.signal(signal.SIGINT, lambda *_: app.quit())
    print(f"CyIME Linux backend listening on TLS port {args.port}", flush=True)
    try:
        return app.exec()
    finally:
        tray.hide()
        phone.shutdown()
        control.shutdown()
        phone.server_close()
        control.server_close()
        backend.close_discovery()
        control_path.unlink(missing_ok=True)
        store.close()
        lock.close()


if __name__ == "__main__":
    sys.exit(main())
