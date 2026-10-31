"""Real subprocess/TLS checks. Offscreen Qt never touches the user's clipboard."""

import base64
import hashlib
import http.client
import json
import os
from pathlib import Path
import socket
import ssl
import subprocess
import sys
import tempfile
import time
import unittest
import uuid
from concurrent.futures import ThreadPoolExecutor
from test_media import fixture

from cyime_linux.control import request
from cyime_linux.core import profile
from test_core import decode_offer


ROOT = Path(__file__).resolve().parents[1]


class ProtocolTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.directory = Path(cls.temporary.name) / "state"
        with socket.socket() as reserve:
            reserve.bind(("127.0.0.1", 0))
            cls.port = reserve.getsockname()[1]
        cls.output = open(Path(cls.temporary.name) / "backend.log", "wb")
        env = dict(os.environ, QT_QPA_PLATFORM="offscreen", XDG_SESSION_TYPE="x11", DISPLAY=":1")
        cls.process = subprocess.Popen([sys.executable, "-m", "cyime_linux.daemon",
             "--state-dir", str(cls.directory), "--port", str(cls.port)],
             cwd=ROOT, env=env, stdout=cls.output, stderr=cls.output)
        for _ in range(150):
            if cls.process.poll() is not None:
                raise RuntimeError((Path(cls.temporary.name) / "backend.log").read_text())
            try:
                request("status", cls.directory)
                break
            except OSError:
                time.sleep(0.1)
        else:
            cls.process.terminate()
            cls.process.wait(timeout=15)
            raise TimeoutError("Backend did not start")

    @classmethod
    def tearDownClass(cls):
        if cls.process.poll() is None:
            request("stop", cls.directory)
            cls.process.wait(timeout=15)
        cls.output.close()
        cls.temporary.cleanup()

    def offer(self):
        response = request("offer", self.directory)
        return decode_offer(response["uri"])

    def exchange(self, method, path, body=None, token=None, headers=None, pin=None):
        # Trust only this self-signed certificate, mirroring Android pinning.
        context = ssl.create_default_context(cafile=str(self.directory / "tls-cert.pem"))
        context.check_hostname = False
        connection = http.client.HTTPSConnection("127.0.0.1", self.port, context=context, timeout=10)
        connection.connect()
        actual = hashlib.sha256(connection.sock.getpeercert(binary_form=True)).hexdigest()
        if pin is not None and actual != pin:
            connection.close()
            raise ssl.SSLError("Pinned certificate mismatch")
        headers = dict(headers or {})
        if token:
            headers["Authorization"] = "Bearer " + token
        if body is not None:
            if isinstance(body, bytes):
                headers["Content-Type"] = "application/octet-stream"
            else:
                headers["Content-Type"] = "application/json"
                body = json.dumps(body).encode()
        try:
            connection.request(method, path, body, headers)
            response = connection.getresponse()
            data = response.read()
            return response.status, data if response.getheader("Content-Type") == "application/octet-stream" else json.loads(data) if data else None
        finally:
            connection.close()

    def pair(self):
        offer = self.offer()
        status, response = self.exchange("POST", "/api/pair", dict(v=2,
             pairing_id=offer["pairing_id"], secret=offer["secret"],
             client_id=str(uuid.uuid4()), client_name="Test phone", client_kind="android"),
             pin=offer["certificate_sha256"])
        self.assertEqual(status, 201)
        return response

    def test_tls_pin_and_auth_required(self):
        self.assertEqual(self.exchange("GET", "/api/status")[0], 401)
        with self.assertRaises(ssl.SSLError):
            self.exchange("GET", "/api/status", pin="0" * 64)

    def test_nearby_code_entry_over_local_control_and_pinned_tls(self):
        offer = self.offer()
        body = dict(v=2, pairing_id=str(uuid.uuid4()), client_id=str(uuid.uuid4()),
                    client_name="Nearby phone", client_kind="android", secret="b" * 43)
        self.assertEqual(self.exchange("POST", "/api/pair/nearby", body)[0], 202)
        pending = request("status", self.directory)["pending_pairings"]
        self.assertEqual(pending[0]["id"], body["pairing_id"])
        self.assertNotIn("code", pending[0])
        with self.assertRaises(RuntimeError):
            request("approve_pairing", self.directory, id=body["pairing_id"], code="wrong")
        raw = hashlib.sha256(f"cyime-nearby-v1\n{offer['certificate_sha256']}\n{body['secret']}".encode()).digest()
        code = f"{int.from_bytes(raw[:4], 'big') % 100000000:08d}"
        request("approve_pairing", self.directory, id=body["pairing_id"], code=code)
        status, paired = self.exchange("POST", "/api/pair/nearby", body, pin=offer["certificate_sha256"])
        self.assertEqual(status, 201)
        self.assertEqual(self.exchange("GET", "/api/status", token=paired["access_token"])[0], 200)
        self.assertFalse(request("status", self.directory)["pending_pairings"])

    def test_android_push_pull_etag_pause_revoke_and_cache(self):
        pair = self.pair()
        token = pair["access_token"]
        status, value = self.exchange("GET", "/api/status", token=token)
        self.assertEqual(status, 200)
        self.assertEqual(value["v"], 2)
        value = profile("手机 → Linux\n离线也保留")
        self.assertEqual(self.exchange("PUT", "/api/clipboard", value, token)[0], 200)
        status, result = self.exchange("GET", "/api/clipboard", token=token)
        self.assertEqual((status, result["hash"]), (200, value["hash"]))
        self.assertEqual(self.exchange("GET", "/api/clipboard", token=token,
                                      headers={"If-None-Match": '"' + value["hash"] + '"'})[0], 304)
        request("settings", self.directory, send_enabled=False, receive_enabled=False)
        self.assertEqual(self.exchange("GET", "/api/clipboard", token=token)[0], 403)
        self.assertEqual(self.exchange("PUT", "/api/clipboard", value, token)[0], 403)
        request("settings", self.directory, send_enabled=True, receive_enabled=True)
        request("revoke", self.directory, id=pair["device_id"])
        self.assertEqual(self.exchange("GET", "/api/status", token=token)[0], 401)
        self.assertEqual(request("history", self.directory, source="phone")[0]["text"], value["text"])

    def test_bad_profile_never_changes_history(self):
        pair = self.pair()
        value = profile("Invalid hash")
        value["hash"] = "0" * 64
        self.assertEqual(self.exchange("PUT", "/api/clipboard", value, pair["access_token"])[0], 400)
        self.assertFalse(request("history", self.directory, search="Invalid hash"))

    def test_edit_history_then_copy_exposes_new_text_to_phone(self):
        token = self.pair()["access_token"]
        original = profile("edit history integration source")
        self.assertEqual(self.exchange("PUT", "/api/clipboard", original, token)[0], 200)
        row = request("history", self.directory, source="phone", search=original["text"])[0]
        request("pin", self.directory, id=row["id"], value=True)
        edited = request("edit", self.directory, id=row["id"], text="编辑后的测试内容",
                         expected_hash=row["hash"])
        self.assertEqual((edited["source"], edited["pinned"]), ("phone", 1))
        self.assertEqual(self.exchange("GET", "/api/clipboard", token=token)[1]["text"], original["text"])
        with self.assertRaises(RuntimeError):
            request("edit", self.directory, id=row["id"], text="stale", expected_hash=row["hash"])
        request("copy", self.directory, id=row["id"])
        status, translated = self.exchange("GET", "/api/clipboard", token=token)
        self.assertEqual((status, translated["text"]), (200, "编辑后的测试内容"))

    def test_second_backend_and_gui_close(self):
        result = subprocess.run([sys.executable, "-m", "cyime_linux.daemon",
                                "--state-dir", str(self.directory), "--port", str(self.port)],
                                cwd=ROOT, capture_output=True, timeout=15)
        self.assertNotEqual(result.returncode, 0)
        env = dict(os.environ, QT_QPA_PLATFORM="offscreen")
        screenshot = Path(self.temporary.name) / "window.png"
        subprocess.run([sys.executable, "-m", "cyime_linux.gui", "--state-dir", str(self.directory),
                        "--screenshot", str(screenshot)], cwd=ROOT, env=env,
                        check=True, capture_output=True, timeout=20)
        self.assertTrue(screenshot.is_file())
        self.assertEqual(request("status", self.directory)["v"], 2)

    def test_images_preview_first_original_only_after_copy_and_cache_reuse(self):
        token = self.pair()["access_token"]
        stranger = self.pair()["access_token"]
        descriptor, original = fixture()
        hash = descriptor["hash"]
        path = f"/api/images/{hash}/original"
        self.assertEqual(self.exchange("GET", "/api/status", token=token)[1]["images_v"], 1)
        self.assertEqual(self.exchange("PUT", "/api/images/current", descriptor, token)[0], 200)
        self.assertIsNone(self.exchange("GET", "/api/images/current", token=token)[1]["image"])
        self.assertEqual(self.exchange("GET", "/api/images/current", token=stranger)[1]["image"], descriptor)
        rows = request("history", self.directory, source="phone", category="image")
        row = next(row for row in rows if row["hash"] == hash)
        self.assertFalse(row["cached"])
        self.assertEqual(self.exchange("GET", "/api/images/needs", token=token)[1]["hashes"], [])
        self.assertEqual(self.exchange("PUT", path, original, token)[0], 401)
        self.assertEqual(self.exchange("GET", path, token=token)[0], 409)
        with ThreadPoolExecutor(max_workers=1) as executor:
            copying = executor.submit(request, "copy", self.directory, id=row["id"])
            for _ in range(50):
                needs = self.exchange("GET", "/api/images/needs", token=token)[1]["hashes"]
                if hash in needs: break
                time.sleep(.05)
            self.assertIn(hash, needs)
            self.assertEqual(self.exchange("GET", "/api/images/needs", token=stranger)[1]["hashes"], [])
            self.assertEqual(self.exchange("PUT", path, original, stranger)[0], 401)
            self.assertEqual(self.exchange("PUT", path, b"bad data", token)[0], 400)
            self.assertEqual(self.exchange("PUT", path, original, token)[0], 200)
            self.assertTrue(copying.result(timeout=10))
        self.assertEqual(self.exchange("GET", path, token=token), (200, original))
        self.assertTrue(request("copy", self.directory, id=row["id"]))
        self.assertEqual(self.exchange("GET", "/api/images/needs", token=token)[1]["hashes"], [])
        self.assertTrue(next(row for row in request("history", self.directory, category="image") if row["hash"] == hash)["cached"])
        request("settings", self.directory, send_enabled=False, receive_enabled=False)
        try:
            self.assertEqual(self.exchange("GET", path, token=token)[0], 403)
            self.assertEqual(self.exchange("PUT", "/api/images/current", descriptor, token)[0], 403)
        finally:
            request("settings", self.directory, send_enabled=True, receive_enabled=True)
        self.assertEqual(self.exchange("GET", path)[0], 401)
        self.assertEqual(self.exchange("GET", "/api/images/../../tls-key.pem/original", token=token)[0], 404)


if __name__ == "__main__":
    unittest.main()
