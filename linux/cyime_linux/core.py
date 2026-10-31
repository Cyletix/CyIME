"""Persistent clipboard history and the CyIME v2 pairing identity."""

import base64
import datetime as dt
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import secrets
import socket
import struct
import sqlite3
import threading
import time
import uuid
from .clipboard_media import CATEGORIES, matches, validate_image, validate_original
from urllib.parse import urlsplit

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID


MAX_TEXT = 65536


def digest(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def profile(text):
    if not isinstance(text, str) or not text.strip() or "\0" in text:
        raise ValueError("Clipboard must contain non-empty text")
    size = len(text.encode("utf-8"))
    if size > MAX_TEXT:
        raise OverflowError("Clipboard exceeds 64 KiB")
    return dict(type="text", text=text, hash=digest(text), size=size,
                has_data=False, data_name=None, source="CyIME-Linux")


def validate_profile(value):
    result = profile(value.get("text"))
    if (value.get("type") != "text" or value.get("has_data") is not False
            or value.get("data_name") is not None or "data_name" not in value
            or value.get("hash") != result["hash"]
            or type(value.get("size")) is not int or value["size"] != result["size"]):
        raise ValueError("Invalid clipboard profile")
    return result


def default_state_dir():
    return Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share")) / "cyime-linux"


def private_dir(path):
    path = Path(path)
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    if path.is_symlink() or path.stat().st_uid != os.getuid():
        raise ValueError("State directory must be owned by the current user")
    path.chmod(0o700)
    return path


def write_private(path, data):
    pending = path.with_name(path.name + ".pending")
    fd = os.open(pending, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(pending, path)
    finally:
        pending.unlink(missing_ok=True)


def lan_addresses():
    import subprocess
    try:
        interfaces = json.loads(subprocess.check_output(
            ["ip", "-j", "address", "show", "up"], timeout=3))
        candidates = [a["local"] for i in interfaces
                      if i.get("operstate") != "DOWN" and "LOWER_UP" in i.get("flags", [])
                      for a in i.get("addr_info", [])
                      if a.get("family") == "inet"]
    except (OSError, subprocess.SubprocessError, ValueError):
        candidates = socket.gethostbyname_ex(socket.gethostname())[2]
    result = []
    for value in candidates:
        address = ipaddress.ip_address(value)
        if address.is_private and not address.is_loopback and not address.is_link_local:
            result.append(str(address))
    return sorted(set(result))[:8]


class Store:
    def __init__(self, directory):
        self.directory = private_dir(directory)
        self.lock = threading.RLock()
        self.db = sqlite3.connect(self.directory / "history.sqlite3", check_same_thread=False)
        (self.directory / "history.sqlite3").chmod(0o600)
        self.db.row_factory = sqlite3.Row
        self.db.executescript("""
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS history (
                id INTEGER PRIMARY KEY, hash TEXT NOT NULL, text TEXT NOT NULL,
                source TEXT NOT NULL, created REAL NOT NULL, pinned INTEGER NOT NULL DEFAULT 0,
                UNIQUE(hash, source));
            CREATE TABLE IF NOT EXISTS devices (
                id TEXT PRIMARY KEY, client_id TEXT UNIQUE NOT NULL, name TEXT NOT NULL,
                token_hash TEXT NOT NULL UNIQUE, last_seen REAL NOT NULL);
        """)
        columns = {row[1] for row in self.db.execute("PRAGMA table_info(history)")}
        if "kind" not in columns:
            self.db.execute("ALTER TABLE history ADD COLUMN kind TEXT NOT NULL DEFAULT 'text'")
        self.db.execute("""CREATE TABLE IF NOT EXISTS images (
            hash TEXT PRIMARY KEY, descriptor TEXT NOT NULL, original BLOB, owner TEXT)""")
        self.db.commit()
        if self.get("server_id") is None:
            self.set("server_id", str(uuid.uuid4()))
        self.offers = {}
        self.nearby = {}
        self._certificate()

    def _certificate(self):
        self.key_path = self.directory / "tls-key.pem"
        self.cert_path = self.directory / "tls-cert.pem"
        if not self.cert_path.exists() and not self.key_path.exists():
            key = ec.generate_private_key(ec.SECP256R1())
            name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "CyIME Linux")])
            now = dt.datetime.utcnow()
            cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
                    .public_key(key.public_key()).serial_number(x509.random_serial_number())
                    .not_valid_before(now - dt.timedelta(days=1))
                    .not_valid_after(now + dt.timedelta(days=3650))
                    .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
                    .sign(key, hashes.SHA256()))
            write_private(self.key_path, key.private_bytes(serialization.Encoding.PEM,
                          serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
            write_private(self.cert_path, cert.public_bytes(serialization.Encoding.PEM))
        # A partial/lost identity requires intervention, never silently trust a new one.
        cert = x509.load_pem_x509_certificate(self.cert_path.read_bytes())
        self.fingerprint = cert.fingerprint(hashes.SHA256()).hex()
        self.key_path.chmod(0o600)

    def get(self, key, default=None):
        with self.lock:
            row = self.db.execute("SELECT value FROM meta WHERE key=?", (key,)).fetchone()
            return json.loads(row[0]) if row else default

    def set(self, key, value):
        with self.lock, self.db:
            self.db.execute("INSERT OR REPLACE INTO meta VALUES (?, ?)", (key, json.dumps(value)))

    @property
    def name(self):
        return self.get("name", socket.gethostname())

    def offer(self, endpoints):
        if not endpoints:
            raise ValueError("No LAN address available; connect to Wi-Fi or Ethernet")
        with self.lock:
            identifier, secret = str(uuid.uuid4()), secrets.token_urlsafe(32)
            # Only the most recently displayed QR code can pair a new device.
            self.offers = {identifier: (digest(secret), time.monotonic() + 300)}
            value = dict(v=2, server_id=self.get("server_id"), pairing_id=identifier,
                         certificate_sha256=self.fingerprint, secret=secret,
                         endpoints=endpoints, name=self.name, expires_at=int(time.time()) + 300)
            # Android's v2 compact format avoids dense, hard-to-scan JSON QR codes.
            packed = bytes((2, len(endpoints)))
            for endpoint in endpoints:
                address = urlsplit(endpoint)
                packed += socket.inet_aton(address.hostname) + struct.pack("!H", address.port)
            packed += (uuid.UUID(value["server_id"]).bytes + bytes.fromhex(self.fingerprint)
                       + uuid.UUID(identifier).bytes + base64.urlsafe_b64decode(secret + "=")
                       + struct.pack("!q", value["expires_at"]))
            data = base64.urlsafe_b64encode(packed).decode().rstrip("=")
            return dict(uri="cyime://pair?v=2&c=" + data, expires_at=value["expires_at"])

    def pending_pairings(self):
        with self.lock:
            self.nearby = {key: value for key, value in self.nearby.items()
                           if value["deadline"] > time.monotonic()}
            return [dict(id=key, name=value["name"], expires_in=max(0, int(value["deadline"] - time.monotonic())))
                    for key, value in self.nearby.items() if value["state"] == "pending"]

    def nearby_pair(self, body):
        with self.lock:
            self.pending_pairings()
            if body.get("v") != 2 or body.get("client_kind") != "android":
                raise ValueError("Unsupported pairing version/client")
            client = str(uuid.UUID(body.get("client_id", "")))
            identifier = str(uuid.UUID(body.get("pairing_id", "")))
            proof = body.get("secret", "")
            if not isinstance(proof, str) or len(proof) != 43 or any(c not in
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-" for c in proof):
                raise ValueError("Invalid pairing proof")
            name = body.get("client_name", "Android")
            if not isinstance(name, str):
                raise ValueError("Invalid device name")
            name = "".join(c for c in name if c.isprintable())[:64] or "Android"
            identity = (client, digest(proof), name)
            attempt = self.nearby.get(identifier)
            if attempt is None:
                if len(self.nearby) >= 8:
                    raise OverflowError("Too many pending pairings")
                raw = hashlib.sha256(f"cyime-nearby-v1\n{self.fingerprint}\n{proof}".encode()).digest()
                attempt = dict(identity=identity, name=name, code=f"{int.from_bytes(raw[:4], 'big') % 100000000:08d}",
                               deadline=time.monotonic() + 120, state="pending", failures=0)
                self.nearby[identifier] = attempt
            if attempt["identity"] != identity or attempt["state"] == "rejected":
                raise PermissionError("Pairing rejected")
            if attempt["state"] == "pending":
                return 202, dict(pending=True)
            if "response" not in attempt:
                attempt["response"] = self._register(client, name)
            return 201, attempt["response"]

    def approve_pairing(self, identifier, code):
        with self.lock:
            self.pending_pairings()
            attempt = self.nearby.get(identifier)
            if not attempt or attempt["state"] != "pending":
                raise ValueError("连接请求已过期，请在手机重新连接")
            if not isinstance(code, str) or not secrets.compare_digest(attempt["code"], code):
                attempt["failures"] += 1
                if attempt["failures"] >= 5:
                    attempt["state"] = "rejected"
                raise ValueError("连接码不一致，请核对手机显示的 8 位数字")
            attempt["state"] = "approved"

    def reject_pairing(self, identifier):
        with self.lock:
            if identifier in self.nearby:
                self.nearby[identifier]["state"] = "rejected"

    def _register(self, client, name):
        token, device = secrets.token_urlsafe(32), str(uuid.uuid4())
        with self.db:
            self.db.execute("DELETE FROM devices WHERE client_id=?", (client,))
            self.db.execute("INSERT INTO devices VALUES (?, ?, ?, ?, ?)",
                            (device, client, name, digest(token), time.time()))
        return dict(v=2, server_id=self.get("server_id"), device_id=device,
                    access_token=token, server_name=self.name)

    def pair(self, body):
        with self.lock:
            if body.get("v") != 2 or body.get("client_kind") != "android":
                raise ValueError("Unsupported pairing version/client")
            client = str(uuid.UUID(body.get("client_id", "")))
            secret = body.get("secret")
            if not isinstance(secret, str) or len(secret) != 43:
                raise PermissionError("Pairing code invalid or expired")
            offer = self.offers.get(body.get("pairing_id"))
            if not offer or offer[1] < time.monotonic() or not secrets.compare_digest(offer[0], digest(secret)):
                raise PermissionError("Pairing code invalid or expired")
            name = body.get("client_name", "Android")
            if not isinstance(name, str):
                raise ValueError("Invalid device name")
            name = "".join(c for c in name if c.isprintable())[:64] or "Android"
            response = self._register(client, name)
            del self.offers[body["pairing_id"]]
            return response

    def authorize(self, header):
        if not header.startswith("Bearer ") or len(header) != 50:
            return False
        with self.lock, self.db:
            cursor = self.db.execute("UPDATE devices SET last_seen=? WHERE token_hash=?",
                                     (time.time(), digest(header[7:])))
            return cursor.rowcount == 1

    def devices(self):
        with self.lock:
            return [dict(row) for row in self.db.execute("SELECT id,name,last_seen FROM devices ORDER BY last_seen DESC")]

    def revoke(self, identifier):
        with self.lock, self.db:
            self.db.execute("DELETE FROM devices WHERE id=?", (identifier,))

    def record(self, text, source):
        value = profile(text)
        with self.lock, self.db:
            self.db.execute("""INSERT INTO history(hash,text,source,created) VALUES(?,?,?,?)
                ON CONFLICT(hash,source) DO UPDATE SET created=excluded.created""",
                            (value["hash"], text, source, time.time()))
            # The Linux tab is the last locally copied content, not received phone content.
            self.db.execute("INSERT OR REPLACE INTO meta VALUES ('current', ?)", (json.dumps(value),))
        return value

    def history(self, source="all", search="", category="all"):
        if source not in ("all", "linux", "phone"):
            raise ValueError("Invalid clipboard source")
        if category not in dict((value, label) for label, value in CATEGORIES):
            raise ValueError("Invalid clipboard category")
        with self.lock:
            rows = self.db.execute("""SELECT * FROM history
                WHERE (?='all' OR source=?) AND instr(lower(text),lower(?))>0
                ORDER BY pinned DESC, created DESC""", (source, source, search))
            result = []
            for row in rows:
                if not matches(row["text"], row["kind"], category): continue
                item = dict(row)
                if row["kind"] == "image":
                    image = self.db.execute("SELECT descriptor,original IS NOT NULL AS cached FROM images WHERE hash=?", (row["hash"],)).fetchone()
                    if not image: continue
                    item["image"] = json.loads(image["descriptor"])
                    item["cached"] = bool(image["cached"])
                result.append(item)
                if len(result) == 100: break
            return result

    def record_image(self, value, source, original=None, owner=None):
        value = validate_image(value)
        if source not in ("linux", "phone"): raise ValueError("Invalid image source")
        if original is not None: validate_original(value, original)
        with self.lock, self.db:
            used = self.db.execute("SELECT coalesce(sum(length(original)),0) FROM images").fetchone()[0]
            existing = self.db.execute("SELECT descriptor,original IS NOT NULL AS cached FROM images WHERE hash=?", (value["hash"],)).fetchone()
            if existing:
                previous = json.loads(existing["descriptor"])
                if any(previous[key] != value[key] for key in ("size", "width", "height", "mime")):
                    raise ValueError("同一原图的描述不一致")
                value = previous
            if original is not None and not (existing and existing["cached"]) and used + len(original) > 512 * 1024 * 1024:
                raise ValueError("原图缓存已达 512 MiB，请先删除不需要的图片记录")
            if not existing and self.db.execute("SELECT coalesce(sum(length(descriptor)),0) FROM images").fetchone()[0] + len(json.dumps(value)) > 16 * 1024 * 1024:
                raise ValueError("图片预览缓存已满，请删除不需要的记录")
            self.db.execute("""INSERT INTO images(hash,descriptor,original,owner) VALUES(?,?,?,?)
                ON CONFLICT(hash) DO UPDATE SET original=coalesce(excluded.original,images.original),
                owner=coalesce(excluded.owner,images.owner)""",
                (value["hash"], json.dumps(value), original, owner))
            self.db.execute("""INSERT INTO history(hash,text,source,created,kind) VALUES(?,?,?,?, 'image')
                ON CONFLICT(hash,source) DO UPDATE SET created=excluded.created""",
                (value["hash"], "图片", source, time.time()))
            self.db.execute("INSERT OR REPLACE INTO meta VALUES ('current_image', ?)", (json.dumps(value),))
            self.db.execute("INSERT OR REPLACE INTO meta VALUES ('current_image_owner', ?)", (json.dumps(owner),))
        return value

    def image(self, hash):
        with self.lock:
            row = self.db.execute("SELECT * FROM images WHERE hash=?", (hash,)).fetchone()
            if not row: raise ValueError("图片不存在")
            return dict(row)

    def accept_original(self, hash, data, owner):
        with self.lock, self.db:
            row = self.image(hash)
            value = json.loads(row["descriptor"])
            if row["owner"] != owner: raise PermissionError("图片来源不匹配")
            validate_original(value, data)
            used = self.db.execute("SELECT coalesce(sum(length(original)),0) FROM images").fetchone()[0]
            if row["original"] is None and used + len(data) > 512 * 1024 * 1024:
                raise ValueError("原图缓存已达 512 MiB")
            self.db.execute("UPDATE images SET original=? WHERE hash=?", (data, hash))

    def item(self, identifier):
        with self.lock:
            row = self.db.execute("SELECT * FROM history WHERE id=?", (identifier,)).fetchone()
            if row is None:
                raise ValueError("Clipboard item no longer exists")
            return dict(row)

    def delete(self, identifier):
        with self.lock, self.db:
            row = self.item(identifier)
            self.db.execute("DELETE FROM history WHERE id=?", (identifier,))
            if row["kind"] == "image" and not self.db.execute("SELECT 1 FROM history WHERE hash=?", (row["hash"],)).fetchone():
                self.db.execute("DELETE FROM images WHERE hash=?", (row["hash"],))
                current = self.get("current_image")
                if current and current["hash"] == row["hash"]:
                    self.db.execute("DELETE FROM meta WHERE key IN ('current_image','current_image_owner')")

    def edit(self, identifier, text, expected_hash):
        value = profile(text)
        with self.lock, self.db:
            row = self.item(identifier)
            if row["kind"] != "text": raise ValueError("图片不可作为文字编辑")
            if row["hash"] != expected_hash:
                raise ValueError("条目已经更新，请重新打开编辑")
            duplicate = self.db.execute(
                "SELECT id FROM history WHERE hash=? AND source=? AND id<>?",
                (value["hash"], row["source"], identifier)).fetchone()
            if duplicate:
                raise ValueError("相同来源已有此内容，未覆盖已有条目")
            self.db.execute("UPDATE history SET hash=?,text=? WHERE id=?",
                            (value["hash"], text, identifier))
            return self.item(identifier)

    def pin(self, identifier, value):
        with self.lock, self.db:
            self.db.execute("UPDATE history SET pinned=? WHERE id=?", (int(bool(value)), identifier))

    def backup(self, target):
        target = Path(target).expanduser()
        # History/settings only: pairing credentials are deliberately not exported.
        if target.exists():
            raise ValueError("Backup target already exists")
        fd = os.open(target, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        os.close(fd)
        with self.lock, sqlite3.connect(target) as output:
            self.db.backup(output)
            output.execute("DELETE FROM devices")
            output.execute("DELETE FROM meta WHERE key='server_id'")
            output.commit()

    def restore(self, source):
        source = Path(source).expanduser().resolve()
        if source == (self.directory / "history.sqlite3").resolve():
            raise ValueError("Choose an exported backup, not the active database")
        restored = 0
        # Import only validated text/history, never execute SQL from an external file.
        with sqlite3.connect(source.as_uri() + "?mode=ro", uri=True) as backup:
            if backup.execute("PRAGMA quick_check").fetchone()[0] != "ok":
                raise ValueError("Backup database is damaged")
            columns = {row[1] for row in backup.execute("PRAGMA table_info(history)")}
            rows = backup.execute("SELECT hash,text,source,created,pinned," + ("kind" if "kind" in columns else "'text'") + " FROM history")
            with self.lock, self.db:
                for hash, text, origin, created, pinned, kind in rows:
                    value = profile(text) if kind == "text" else None
                    if origin not in ("linux", "phone") or not isinstance(created, (int, float)):
                        raise ValueError("Invalid backup history")
                    if kind == "image":
                        image = backup.execute("SELECT descriptor,original FROM images WHERE hash=?", (hash,)).fetchone()
                        if not image: raise ValueError("Missing backup image")
                        value = validate_image(json.loads(image[0]))
                        if value["hash"] != hash: raise ValueError("Invalid image hash")
                        if image[1] is not None: validate_original(value, image[1])
                        existing = self.db.execute("SELECT descriptor,original IS NOT NULL FROM images WHERE hash=?", (hash,)).fetchone()
                        if existing and any(json.loads(existing[0])[key] != value[key] for key in ("size", "width", "height", "mime")):
                            raise ValueError("Invalid backup image description")
                        if image[1] is not None and not (existing and existing[1]):
                            used = self.db.execute("SELECT coalesce(sum(length(original)),0) FROM images").fetchone()[0]
                            if used + len(image[1]) > 512 * 1024 * 1024: raise ValueError("图片缓存已满")
                        if not existing:
                            used = self.db.execute("SELECT coalesce(sum(length(descriptor)),0) FROM images").fetchone()[0]
                            if used + len(image[0]) > 16 * 1024 * 1024: raise ValueError("图片预览缓存已满")
                        self.db.execute("""INSERT INTO images(hash,descriptor,original) VALUES(?,?,?)
                            ON CONFLICT(hash) DO UPDATE SET original=coalesce(images.original,excluded.original)""", (hash, image[0], image[1]))
                    elif kind != "text": raise ValueError("Invalid backup kind")
                    self.db.execute("""INSERT INTO history(hash,text,source,created,pinned,kind) VALUES(?,?,?,?,?,?)
                        ON CONFLICT(hash,source) DO UPDATE SET
                        created=max(history.created,excluded.created), pinned=max(history.pinned,excluded.pinned)""",
                        (value["hash"], text, origin, created, int(bool(pinned)), kind))
                    restored += 1
        return restored

    def close(self):
        with self.lock:
            self.db.close()
