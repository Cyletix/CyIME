import base64
import json
import socket
import struct
from pathlib import Path
import sqlite3
import tempfile
import time
import unittest
import uuid
from unittest.mock import patch

from cyime_linux.core import Store, lan_addresses, profile, validate_profile


def decode_offer(uri):
    encoded = uri.split("&c=")[1]
    raw = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4))
    assert raw[0] == 2 and len(raw) == 106 + 6 * raw[1]
    count, offset = raw[1], 2 + raw[1] * 6
    endpoints = [f"https://{socket.inet_ntoa(raw[2+i*6:6+i*6])}:{struct.unpack('!H', raw[6+i*6:8+i*6])[0]}"
                 for i in range(count)]
    return dict(v=2, endpoints=endpoints, server_id=str(uuid.UUID(bytes=raw[offset:offset+16])),
                certificate_sha256=raw[offset+16:offset+48].hex(),
                pairing_id=str(uuid.UUID(bytes=raw[offset+48:offset+64])),
                secret=base64.urlsafe_b64encode(raw[offset+64:offset+96]).decode().rstrip("="),
                expires_at=struct.unpack("!q", raw[offset+96:])[0])


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary.name)
        self.store = Store(self.directory / "state")

    def tearDown(self):
        self.store.close()
        self.temporary.cleanup()

    def offer(self):
        value = self.store.offer(["https://192.168.1.2:8788"])
        return decode_offer(value["uri"])

    def pair(self, offer=None, **changes):
        offer = offer or self.offer()
        request = dict(v=2, pairing_id=offer["pairing_id"], secret=offer["secret"],
                       client_id=str(uuid.uuid4()), client_name="ASUS", client_kind="android")
        request.update(changes)
        return self.store.pair(request)

    def nearby_request(self):
        return dict(v=2, pairing_id=str(uuid.uuid4()), client_id=str(uuid.uuid4()),
                    client_name="ASUS", client_kind="android", secret="b" * 43)

    def test_disconnected_docker_and_vm_bridges_are_not_qr_endpoints(self):
        interfaces = [dict(operstate=state, flags=flags, addr_info=[dict(family="inet", local=address)])
                      for state, flags, address in [
                          ("DOWN", ["UP"], "172.17.0.1"), ("DOWN", ["UP"], "192.168.122.1"),
                          ("UP", ["UP", "LOWER_UP"], "192.168.128.55")]]
        with patch("subprocess.check_output", return_value=json.dumps(interfaces).encode()):
            self.assertEqual(lan_addresses(), ["192.168.128.55"])

    def test_compact_offer_matches_android_network_order_fixture(self):
        self.store.set("server_id", "00112233-4455-6677-8899-aabbccddeeff")
        self.store.fingerprint = bytes(range(32)).hex()
        secret = base64.urlsafe_b64encode(bytes(range(32))).decode().rstrip("=")
        with patch("cyime_linux.core.uuid.uuid4", return_value=uuid.UUID("11223344-5566-7788-99aa-bbccddeeff00")), \
                patch("cyime_linux.core.secrets.token_urlsafe", return_value=secret), \
                patch("cyime_linux.core.time.time", return_value=1_999_999_700):
            offer = self.store.offer(["https://192.168.1.2:18740"])
        # Same fixture as Android WindowsPairingProtocolTest.
        self.assertEqual(offer["uri"], "cyime://pair?v=2&c=AgHAqAECSTQAESIzRFVmd4iZqrvM3e7_AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8RIjNEVWZ3iJmqu8zd7v8AAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8AAAAAdzWUAA")

    def test_nearby_requires_local_code_and_replays_same_token(self):
        body = self.nearby_request()
        self.assertEqual(self.store.nearby_pair(body), (202, dict(pending=True)))
        pending = self.store.pending_pairings()[0]
        self.assertNotIn("code", pending)
        self.assertFalse(self.store.devices())
        with self.assertRaises(ValueError):
            self.store.approve_pairing(pending["id"], "wrong")
        import hashlib
        raw = hashlib.sha256(f"cyime-nearby-v1\n{self.store.fingerprint}\n{body['secret']}".encode()).digest()
        code = f"{int.from_bytes(raw[:4], 'big') % 100000000:08d}"
        self.store.approve_pairing(pending["id"], code)
        self.assertFalse(self.store.pending_pairings())
        first = self.store.nearby_pair(body)
        self.assertEqual(first[0], 201)
        self.assertTrue(self.store.authorize("Bearer " + first[1]["access_token"]))
        self.assertEqual(self.store.nearby_pair(body), first)
        with self.assertRaises(PermissionError):
            self.store.nearby_pair(dict(body, secret="c" * 43))

    def test_nearby_rejection_expiry_and_wrong_code_limit(self):
        for reject in (True, False):
            body = self.nearby_request()
            self.store.nearby_pair(body)
            if reject:
                self.store.reject_pairing(body["pairing_id"])
            else:
                for _ in range(5):
                    with self.assertRaises(ValueError):
                        self.store.approve_pairing(body["pairing_id"], "wrong")
            with self.assertRaises(PermissionError):
                self.store.nearby_pair(body)
        body = self.nearby_request()
        self.store.nearby_pair(body)
        self.store.nearby[body["pairing_id"]]["deadline"] = time.monotonic() - 1
        with self.assertRaises(ValueError):
            self.store.approve_pairing(body["pairing_id"], "00000000")
        self.assertFalse(self.store.devices())

    def test_android_compatible_pairing(self):
        offer = self.offer()
        self.assertEqual(len(offer["certificate_sha256"]), 64)
        self.assertEqual(len(offer["secret"]), 43)
        result = self.pair(offer)
        self.assertEqual(result["v"], 2)
        self.assertEqual(result["server_id"], offer["server_id"])
        self.assertTrue(self.store.authorize("Bearer " + result["access_token"]))
        with self.assertRaises(PermissionError):
            self.pair(offer)
        self.store.revoke(result["device_id"])
        self.assertFalse(self.store.authorize("Bearer " + result["access_token"]))

    def test_expired_replaced_and_invalid_offers(self):
        old = self.offer()
        new = self.offer()
        with self.assertRaises(PermissionError):
            self.pair(old)
        with self.assertRaises(PermissionError):
            self.pair(new, secret="X" * 43)
        self.store.offers[new["pairing_id"]] = (self.store.offers[new["pairing_id"]][0], time.monotonic() - 1)
        with self.assertRaises(PermissionError):
            self.pair(new)

    def test_repair_revokes_old_token(self):
        client = str(uuid.uuid4())
        first = self.pair(client_id=client)
        second = self.pair(client_id=client)
        self.assertFalse(self.store.authorize("Bearer " + first["access_token"]))
        self.assertTrue(self.store.authorize("Bearer " + second["access_token"]))
        self.assertEqual(len(self.store.devices()), 1)

    def test_utf8_validation(self):
        value = profile("你好\nLinux")
        self.assertEqual(value["size"], 12)
        self.assertEqual(validate_profile(value), value)
        for changed in (dict(value, size=1), dict(value, hash="0" * 64),
                        dict(value, has_data=True), dict(value, size=True)):
            with self.assertRaises(ValueError):
                validate_profile(changed)
        for text in ("", "\0", "   "):
            with self.assertRaises(ValueError):
                profile(text)
        with self.assertRaises(OverflowError):
            profile("字" * 22000)

    def test_cache_survives_restart_and_keeps_origin(self):
        self.store.record("Linux text", "linux")
        self.store.record("手机内容", "phone")
        self.store.record("手机内容", "phone")
        self.store.close()
        self.store = Store(self.directory / "state")
        self.assertEqual(len(self.store.history()), 2)
        self.assertEqual(self.store.history("phone")[0]["text"], "手机内容")
        self.assertEqual(self.store.history("linux", "text")[0]["text"], "Linux text")

    def test_edit_preserves_identity_origin_pin_time_and_current_clipboard(self):
        self.store.record("原手机内容", "phone")
        row = self.store.history()[0]
        self.store.pin(row["id"], True)
        current = self.store.get("current")
        edited = self.store.edit(row["id"], "修改后\n第二行", row["hash"])
        self.assertEqual(edited["id"], row["id"])
        self.assertEqual(edited["source"], "phone")
        self.assertEqual(edited["created"], row["created"])
        self.assertEqual(edited["pinned"], 1)
        self.assertEqual(edited["hash"], profile(edited["text"])["hash"])
        self.assertEqual(self.store.get("current"), current)
        self.store.close()
        self.store = Store(self.directory / "state")
        self.assertEqual(self.store.item(row["id"])["text"], "修改后\n第二行")

    def test_edit_rejects_stale_invalid_missing_and_duplicate_without_data_loss(self):
        self.store.record("original", "linux")
        row = self.store.history()[0]
        self.store.record("duplicate", "linux")
        for text, expected in (("changed", "stale"), ("", row["hash"]),
                               ("duplicate", row["hash"]), ("\0", row["hash"])):
            with self.assertRaises(ValueError):
                self.store.edit(row["id"], text, expected)
            self.assertEqual(self.store.item(row["id"])["text"], "original")
        with self.assertRaises(OverflowError):
            self.store.edit(row["id"], "字" * 22000, row["hash"])
        self.store.delete(row["id"])
        with self.assertRaises(ValueError):
            self.store.edit(row["id"], "changed", row["hash"])

    def test_backup_restore_merges_history_without_device_credentials(self):
        self.pair()
        self.store.record("已缓存", "phone")
        row = self.store.history()[0]
        self.store.pin(row["id"], True)
        target = self.directory / "backup.sqlite3"
        self.store.backup(target)
        self.assertEqual(target.stat().st_mode & 0o777, 0o600)
        with sqlite3.connect(target) as check:
            self.assertEqual(check.execute("SELECT count(*) FROM devices").fetchone()[0], 0)
        self.store.delete(row["id"])
        self.assertEqual(self.store.restore(target), 1)
        self.assertEqual(self.store.history()[0]["pinned"], 1)
        self.assertEqual(len(self.store.devices()), 1)
        with self.assertRaises(ValueError):
            self.store.backup(target)

    def test_identity_and_private_permissions(self):
        pin = self.store.fingerprint
        identifier = self.store.get("server_id")
        self.assertEqual(self.store.directory.stat().st_mode & 0o777, 0o700)
        self.assertEqual(self.store.key_path.stat().st_mode & 0o777, 0o600)
        self.store.close()
        self.store = Store(self.directory / "state")
        self.assertEqual(self.store.fingerprint, pin)
        self.assertEqual(self.store.get("server_id"), identifier)


if __name__ == "__main__":
    unittest.main()
