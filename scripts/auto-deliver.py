#!/usr/bin/env python3
"""CyIME one-command delivery. Python 3.10+, standard library only.
Default: prepare -> build/reuse Release -> verify -> adb install -r -> verify.
--publish: publish the selected edition; --both explicitly requests both editions.
No automatic commits, pushes, version edits, clean, force checkout or uninstall.
"""
from __future__ import annotations
import argparse
import contextlib
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

PACKAGE = "com.cyletix.cyime"
POLICY = "cyime-auto-delivery-v2"
# Match the existing scripts, including native-only changes. Never use file mtimes.
SOURCE_PATHS = ["app/src/main", "app/build.gradle.kts", "app/build-logic",
                "app/proguard-rules.pro", "plugin-core", "plugins", "patches",
                "gradle", "gradle.properties", "build.gradle.kts", "settings.gradle.kts",
                "gradlew", "gradlew.bat", ".gitmodules", ".gitattributes", "scripts", "tools"]
HELPERS = {"scripts/auto-deliver.py", "scripts/test_auto_deliver.py"}
GRADLE_ARGS = ["--max-workers=1", "--no-parallel", "--no-daemon", "--console=plain",
               "-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8",
               "-Pkotlin.compiler.execution.strategy=in-process"]

class DeliveryError(RuntimeError):
    pass


def complete_java_home(path: Path) -> bool:
    # A language-server JRE may report Java 21 but cannot build Android's JDK image.
    return (all((path / "bin" / name).is_file() for name in ("java.exe", "javac.exe", "jlink.exe"))
            and (path / "release").is_file()
            and re.search(r'JAVA_VERSION="(?:21|22|23|24|25)\.',
                          (path / "release").read_text(encoding="utf-8")) is not None)


def source_input(name: str) -> bool:
    """Exclude control scripts and generated caches, never application sources."""
    parts = Path(name).parts
    return name not in HELPERS and "__pycache__" not in parts and not name.endswith((".pyc", ".pyo")) and not name.startswith((
        "tools/clipboard-sync-fixture/bin/", "tools/clipboard-sync-fixture/obj/"))


def integration_root(entry: Path) -> Path:
    runner = Runner(entry)
    configured = runner.run(["git", "config", "--local", "--get", "cyime.integrationRoot"], check=False)
    if not configured:
        return entry.resolve()
    target = Path(configured).resolve()
    if not (target / "scripts/auto-deliver.py").is_file():
        raise DeliveryError(f"Configured integration directory is unavailable: {target}")
    def common(root):
        path = Path(Runner(root).run(["git", "rev-parse", "--git-common-dir"]))
        return (path if path.is_absolute() else root / path).resolve()
    if common(entry) != common(target):
        raise DeliveryError("Configured integration directory belongs to another Git repository.")
    return target

def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))

def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, path)

def ps_quote(value: str) -> str:
    return "'" + str(value).replace("'", "''") + "'"

def check_signature_schemes(verify_output: str):
    # vivo OriginOS refuses v2-only APKs as 「安装包缺乏开发者证书」: it looks for the v1 (JAR) certificate in META-INF.
    # apksigner skips v1 for minSdk >= 24 and then always prints "v1 ...: false"; verify with --min-sdk-version 23.
    missing = [s for s in ("v1", "v2") if not re.search(rf"Verified using {s} scheme \([^)]*\): true", verify_output)]
    if missing:
        raise DeliveryError("APK is missing " + "/".join(missing) + " signature (vivo needs v1 + v2).")

def parse_version(text: str) -> tuple[str, int]:
    # Only literal assignments, not comments or the androidComponents overrides.
    names = re.findall(r'^\s*versionName\s*=\s*"([^"\r\n]+)"\s*$', text, re.M)
    codes = re.findall(r'^\s*versionCode\s*=\s*(\d+)\s*$', text, re.M)
    if len(names) != 1 or len(codes) != 1 or not re.fullmatch(r"\d+\.\d+\.\d+", names[0]):
        raise DeliveryError("Cannot unambiguously read versionName/versionCode in app/build.gradle.kts.")
    code = int(codes[0])
    if not 1 <= code <= 2_100_000_000:
        raise DeliveryError("versionCode is outside Android/Play's supported positive range.")
    return names[0], code

def choose_device(text: str, requested: str = "") -> str:
    rows = [line.split() for line in text.splitlines() if line.strip()]
    devices = {row[0]: row[1] for row in rows if len(row) >= 2 and row[0] != "List" and not row[0].startswith("*")}
    if requested:
        if devices.get(requested) != "device":
            raise DeliveryError(f"Device {requested} is not online/authorized. Unlock it and allow USB debugging.")
        return requested
    online = [serial for serial, status in devices.items() if status == "device"]
    if len(online) != 1:
        raise DeliveryError("Connect and authorize exactly ONE device, or use --serial SERIAL. Nothing installed.")
    return online[0]

@contextlib.contextmanager
def delivery_lock(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a+b") as f:
        locked = False
        try:
            f.seek(0)
            if not f.read(1):
                f.write(b"0"); f.flush()
            f.seek(0)
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(f.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(f.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            locked = True
        except OSError as exc:
            raise DeliveryError("Another automatic delivery is running in this repository/worktree family.") from exc
        try:
            yield
        finally:
            if locked:
                f.seek(0)
                if os.name == "nt":
                    import msvcrt
                    msvcrt.locking(f.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(f.fileno(), fcntl.LOCK_UN)

def list_adb_devices(runner, adb):
    try:
        return runner.run([adb, "devices"])
    except DeliveryError as error:
        # Another installed client can restart a different-version daemon while
        # this client checks it. Retry only this read-only handshake, once.
        detail = str(error).lower()
        if "protocol fault" not in detail and "cannot connect to daemon" not in detail:
            raise
        print("[DEVICE] ADB server handshake interrupted; restarting the SDK connection once.", flush=True)
        runner.run([adb, "start-server"])
        return runner.run([adb, "devices"])


def read_adb_device(runner, adb, serial, *command):
    """Retry a read once after a competing client's daemon restart; never retry a write here."""
    args = [adb, "-s", serial, "shell", *command]
    try:
        return runner.run(args)
    except DeliveryError as error:
        detail = str(error).lower()
        if not any(token in detail for token in ("server version", "protocol fault", "cannot connect to daemon")):
            raise
        print("[DEVICE] ADB restarted during a device read; waiting for the same device once.", flush=True)
        runner.run([adb, "-s", serial, "wait-for-device"], timeout=10)
        return runner.run(args)


def check_adb_compatibility(runner, adb, companion=None):
    # SuperDisplay auto-starts a private ADB as SYSTEM, irrespective of PATH. Check
    # local version strings BEFORE contacting 5037, so an update cannot restart the fight.
    companion = Path(companion) if companion else Path(os.getenv("ProgramFiles", "C:/Program Files")) / "SuperDisplay/adb/adb.exe"
    if not companion.is_file():
        return
    def protocol(executable):
        text = runner.run([str(executable), "version"])
        match = re.search(r"Android Debug Bridge version (1\.0\.\d+)", text)
        if not match:
            raise DeliveryError(f"Cannot identify local ADB version: {executable}")
        return match[1]
    sdk_version, companion_version = protocol(adb), protocol(companion)
    if sdk_version != companion_version:
        raise DeliveryError(f"SuperDisplay ADB {companion_version} conflicts with SDK ADB {sdk_version}. "
            "Stopped before touching the shared daemon. Run scripts/repair-adb-conflict.ps1 "
            "to back up and synchronize SuperDisplay's runtime with Windows administrator consent.")


def check_adb_forward_port(runner, port=1445):
    # Windows reservations do not appear as listening processes. SuperDisplay
    # retries its fixed forward and resets ADB when bind fails with WSAEACCES.
    for family in ("ipv4", "ipv6"):
        output = runner.run(["netsh.exe", "interface", family, "show", "excludedportrange", "protocol=tcp"])
        for match in re.finditer(r"^\s*(\d+)\s+(\d+)\s*\*?\s*$", output, re.M):
            start, end = map(int, match.groups())
            if start <= port <= end:
                raise DeliveryError(f"Windows {family} reserves SuperDisplay TCP {port} ({start}-{end}). "
                    "Stopped before touching the shared ADB daemon. Run scripts/repair-adb-conflict.ps1 "
                    "-RepairDynamicPorts to restore the Windows default TCP range and refresh WinNAT.")


def check_superdisplay_port(runner):
    companion = Path(os.getenv("ProgramFiles", "C:/Program Files")) / "SuperDisplay/adb/adb.exe"
    if os.name != "nt" or not companion.is_file():
        return
    import winreg
    try:
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\KeloCube\SuperDisplay") as key:
            if winreg.QueryValueEx(key, "DisableADB")[0]:
                return
    except FileNotFoundError:
        pass  # A missing override means the product default (ADB enabled).
    check_adb_forward_port(runner)


class Runner:
    def __init__(self, root: Path):
        self.root = root
        self.env = dict(os.environ, CMAKE_BUILD_PARALLEL_LEVEL="2", GIT_TERMINAL_PROMPT="0",
                        GCM_INTERACTIVE="Never", GH_PROMPT_DISABLED="1", GH_PAGER="cat")

    def run(self, args, *, check=True, live=False, timeout=120) -> str:
        args = [str(a) for a in args]
        proc = subprocess.Popen(args, cwd=self.root, env=self.env,
                                stdout=None if live else subprocess.PIPE,
                                stderr=None if live else subprocess.PIPE)
        try:
            out, err = proc.communicate(timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            # Kill only this invocation's descendants; never kill unrelated Java/IDE processes.
            if os.name == "nt":
                subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            else:
                proc.kill()
            proc.wait()
            raise
        text = (out or b"").decode("utf-8", errors="replace")
        if check and proc.returncode:
            details = (err or b"").decode("utf-8", errors="replace")[-2500:]
            raise DeliveryError(f"Command failed ({proc.returncode}): {args[0]} {args[1] if len(args)>1 else ''}\n{details}")
        if not check and proc.returncode:
            return ""
        return text.rstrip("\r\n") if "-z" not in args else text

class Delivery:
    def __init__(self, root: Path, options, runner=None):
        self.root, self.o = root.resolve(), options
        self.r = runner or Runner(self.root)
        self.work = self.root / ".gradle/auto-deliver"
        self.state_path = self.work / "state.json"
        self.state = read_json(self.state_path) if self.state_path.is_file() else {"builds": {}}
        self.state.setdefault("builds", {})
        self.version, self.code = parse_version((self.root / "app/build.gradle.kts").read_text(encoding="utf-8-sig"))
        self.commit = self.git("rev-parse", "HEAD")
        self.sdk = None
        self.adb = self.signer = self.aapt = self.ps = ""
        self.repo = ""
        self.serial = options.serial

    def git(self, *args):
        return self.r.run(["git", *args])

    def stage(self, name):
        print(f"\n[{name}] CyIME {self.version} / {self.code}", flush=True)
        self.state.pop("error", None)
        self.state["stage"] = name
        write_json(self.state_path, self.state)

    def script(self, name, parameters="", live=True):
        path = self.root / "scripts" / name
        if not path.is_file():
            raise DeliveryError(f"Missing existing project script: {path}")
        command = f"$ErrorActionPreference='Stop'; & {ps_quote(str(path))} {parameters}; if (-not $?) {{ exit 1 }}"
        self.r.run([self.ps, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", command],
                   live=live, timeout=7200)

    def preflight(self):
        self.stage("CHECK")
        if not (self.root / "scripts/build-apk.ps1").is_file():
            raise DeliveryError("Run this from the CyIME integration repository; scripts/build-apk.ps1 is missing.")
        if not (self.root / "app/keystore.properties").is_file():
            raise DeliveryError("Release signing configuration app/keystore.properties is missing. No debug-key fallback.")
        self.ps = shutil.which("powershell.exe") or shutil.which("pwsh.exe") or ""
        if not self.ps:
            raise DeliveryError("Windows PowerShell is required to call the existing build scripts.")
        candidates = []
        prop = self.root / "local.properties"
        if prop.exists():
            m = re.search(r"^sdk\.dir\s*=\s*(.+)$", prop.read_text(encoding="utf-8-sig"), re.M)
            if m:
                candidates.append(m[1].strip().replace("\\:", ":").replace("\\\\", "\\"))
        candidates.extend(filter(None, [os.getenv("ANDROID_SDK_ROOT"), os.getenv("ANDROID_HOME")]))
        if os.getenv("LOCALAPPDATA"):
            candidates.append(str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"))
        self.sdk = next((Path(p) for p in candidates if (Path(p)/"platform-tools/adb.exe").is_file()), None)
        if self.sdk is None:
            adb = shutil.which("adb.exe")
            if adb:
                self.sdk = Path(adb).resolve().parent.parent
        if self.sdk is None:
            raise DeliveryError("Android SDK/adb not found. Set sdk.dir in local.properties; no APK was built or installed.")
        self.adb = str(self.sdk / "platform-tools/adb.exe")
        tools = sorted((self.sdk / "build-tools").glob("*/apksigner.bat"),
                       key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)), reverse=True)
        tools = [p for p in tools if (p.parent / "aapt.exe").is_file()]
        if not tools:
            raise DeliveryError("SDK build-tools aapt.exe/apksigner.bat are missing.")
        self.signer, self.aapt = str(tools[0]), str(tools[0].parent / "aapt.exe")
        self.r.env["ANDROID_HOME"] = str(self.sdk)
        self.r.env["ANDROID_SDK_ROOT"] = str(self.sdk)
        java_candidates = [Path(p) for p in (os.getenv("JAVA_HOME"),) if p]
        java_candidates += sorted((Path.home()/".gradle/jdks").glob("*"), reverse=True)
        java_candidates += [Path(os.getenv("ProgramFiles", "C:/Program Files"))/"Android/Android Studio/jbr"]
        java = next((p for p in java_candidates if complete_java_home(p)), None)
        if not java:
            raise DeliveryError("Complete Java 21+ JDK (java, javac, jlink) not found in JAVA_HOME, Gradle toolchains or Android Studio.")
        self.r.env["JAVA_HOME"] = str(java)
        self.r.env["PATH"] = str(java/"bin") + os.pathsep + self.r.env.get("PATH", "")
        self.r.env["JAVA_TOOL_OPTIONS"] = self.r.env.get("JAVA_TOOL_OPTIONS", "") + " -Dfile.encoding=UTF-8"
        # Apply before daemon/toolchain discovery; JAVA_HOME alone loses to an installed
        # language-server JRE under Gradle's daemon JVM criteria.
        self.r.env["JAVA_TOOL_OPTIONS"] += (' -Dorg.gradle.java.installations.auto-detect=false'
            ' "-Dorg.gradle.java.installations.paths=' + java.as_posix() + '"')
        if os.name == "nt" and "-Djdk.net.unixdomain.tmpdir=" not in self.r.env["JAVA_TOOL_OPTIONS"]:
            # Java's Unix-domain IPC fails on this Windows Gradle setup; use its TCP fallback.
            ipc = Path.home()/".gradle/cyime-unixdomain-disabled"
            self.r.env["JAVA_TOOL_OPTIONS"] += " -Djdk.net.unixdomain.tmpdir=" + str(ipc).replace('\\', '/')
        if not getattr(self.o, "build_only", False):
            check_adb_compatibility(self.r, self.adb)
            check_superdisplay_port(self.r)
            devices = list_adb_devices(self.r, self.adb)
            if not self.serial and not re.search(r"^\S+\s+device\s*$", devices, re.M):
                self.serial = ""
                print("[DEVICE] No authorized device; build can finish, installation will remain pending.", flush=True)
            else:
                self.serial = choose_device(devices, self.serial)
        else:
            self.serial = ""
        if self.serial:
            self.check_device()
        self.check_publication()

    def check_device(self):
        abi = read_adb_device(self.r, self.adb, self.serial, "getprop", "ro.product.cpu.abilist")
        if "arm64-v8a" not in abi.split(","):
            raise DeliveryError("This project's Release delivery script targets ARM64; connected device is not ARM64.")
        dump = read_adb_device(self.r, self.adb, self.serial, "dumpsys", "package", PACKAGE)
        match = re.search(r"\bversionCode=(\d+)", dump)
        if match and int(match[1]) > self.code:
            raise DeliveryError(f"Device has versionCode {match[1]}, source has {self.code}. Refusing downgrade/uninstall.")
    def check_publication(self):
        # Existing native source already bounds Ninja; do not pretend Gradle workers also bound C++.
        cmake = (self.root / "app/src/main/jni/CMakeLists.txt").read_text(encoding="utf-8-sig")
        if "cyime_compile=2 cyime_link=1" not in cmake:
            raise DeliveryError("Native compiler/linker job limits differ from the audited project; refusing an unbounded build.")
        if self.o.publish:
            if not shutil.which("gh"):
                raise DeliveryError("GitHub CLI (gh) is required for --publish. Install and authenticate it once.")
            self.r.run(["gh", "auth", "status"])
            remote = self.git("remote", "get-url", "origin")
            match = re.fullmatch(r"(?:https://github\.com/|git@github\.com:)([^/]+/[^/]+?)(?:\.git)?/?", remote)
            if not match:
                raise DeliveryError("origin must identify an unambiguous github.com repository for publishing.")
            self.repo = match[1]
            self.require_committed_sources()
            remote_commit = self.r.run(["gh", "api", f"repos/{self.repo}/commits/{self.commit}", "--jq", ".sha"])
            if remote_commit != self.commit:
                raise DeliveryError("Current source commit is not available on GitHub. No automatic commit/push is performed.")

    def require_committed_sources(self):
        changes = self.git("diff", "--name-only", "--ignore-submodules=dirty", "HEAD", "--", *SOURCE_PATHS)
        new = self.git("ls-files", "--others", "--exclude-standard", "--", *SOURCE_PATHS)
        paths = [p for p in (changes + "\n" + new).splitlines() if p and source_input(p)]
        if paths:
            raise DeliveryError("Publish needs committed source changes (local build/install does not):\n" + "\n".join(paths[:15]))

    def dependencies(self, prepare=True):
        self.stage("DEPENDENCIES")
        # Initialize only missing repos. Never overwrite an initialized dirty submodule.
        for _ in range(8):
            statuses = self.git("submodule", "status", "--recursive")
            missing = []
            for line in statuses.splitlines():
                m = re.match(r"^([ +U-])([0-9a-f]{40,64})\s+(\S+)", line)
                if not m:
                    continue
                flag, _, path = m.groups()
                if flag == "U":
                    raise DeliveryError(f"Unresolved submodule conflict: {path}")
                if flag == "-":
                    missing.append(path)
                elif flag == "+":
                    raise DeliveryError(f"Submodule commit differs from the integration source: {path}. Not overwriting it.")
            if not missing:
                break
            if not prepare:
                raise DeliveryError("Dependencies need preparation: " + ", ".join(missing))
            for path in missing:
                self.r.run(["git", "-c", "submodule.librime.url=https://github.com/ximeiorg/librime.git",
                            "-c", "url.https://github.com/.insteadOf=git@github.com:",
                            "submodule", "update", "--init", "--recursive", "--checkout", "--", path], live=True, timeout=1800)
        else:
            raise DeliveryError("Submodules are still incomplete after initialization.")
        for name in ("librime-predict", "librime-octagram", "librime-lua", "librime-t9"):
            source = self.root / "app/src/main/jni" / name / "CMakeLists.txt"
            if not source.is_file():
                raise DeliveryError(f"Dependency source is incomplete: {source}. No empty stub or plugin removal was used.")

    def fingerprint(self, include_submodule_changes=True):
        # A commit or a documentation edit does not change compiled inputs.
        # Source provenance is recorded separately in the successful build receipt.
        h = hashlib.sha256(POLICY.encode())
        listing = self.git("ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", *SOURCE_PATHS)
        for name in sorted(set(listing.split("\0"))):
            if not name or not source_input(name):
                continue
            path = self.root / name
            h.update(name.encode("utf-8")); h.update(b"\0")
            if path.is_file():
                h.update(digest(path).encode())
            elif path.is_dir() and (path / ".git").exists():
                h.update(self.r.run(["git", "-C", str(path), "rev-parse", "HEAD"]).encode())
            else:
                h.update(b"MISSING")
        if include_submodule_changes:
            for line in self.git("submodule", "status", "--recursive").splitlines():
                match = re.match(r"^[ +]([0-9a-f]{40,64})\s+(\S+)", line)
                if not match:
                    continue
                revision, name = match.groups()
                path = self.root / name
                h.update(name.encode()); h.update(revision.encode())
                diff = self.r.run(["git", "-C", str(path), "diff", "--binary", "HEAD"])
                h.update(diff.encode("utf-8"))
                untracked = self.r.run(["git", "-C", str(path), "ls-files", "-z", "--others", "--exclude-standard"])
                for item in sorted(filter(None, untracked.split("\0"))):
                    f = path / item
                    if f.is_file():
                        h.update(item.encode()); h.update(digest(f).encode())
        # Signing and SDK choices affect build identity but are never logged or uploaded.
        for name in ("local.properties", "app/keystore.properties"):
            f = self.root / name
            if f.exists():
                h.update(name.encode()); h.update(digest(f).encode())
        signing = self.root / "app/keystore.properties"
        if signing.exists():
            match = re.search(r"^storeFile\s*=\s*(.+)$", signing.read_text(encoding="utf-8-sig"), re.M)
            if match:
                name = match[1].strip().replace("\\:", ":").replace("\\\\", "\\")
                store = Path(name)
                if not store.is_absolute():
                    store = self.root / "app" / store
                if store.is_file():
                    h.update(digest(store).encode())
        return h.hexdigest()

    def receipt_path(self, edition):
        return self.root / f"app/build/outputs/apk/editions/{self.version}/{edition}/release/latest.json"

    def delivered_versions(self):
        versions = dict(self.state.get("deliveredVersions", {}))
        installed = self.state.get("installed", {})
        # Recover pre-policy receipts from their verified installed APK hash.
        for key, build in self.state["builds"].items():
            if installed.get("sha256") and build.get("sha256") == installed["sha256"]:
                version, code, _ = key.split("/")
                versions[code] = version
        return versions

    def check_delivered_version(self):
        versions = self.delivered_versions()
        if not versions:
            return
        previous_code = max(map(int, versions))
        previous = versions[str(previous_code)]
        current_parts = tuple(map(int, self.version.split(".")))
        previous_parts = tuple(map(int, previous.split(".")))
        if self.code < previous_code or (self.code == previous_code and self.version != previous):
            raise DeliveryError(f"Last delivered version is {previous}/{previous_code}; versionCode must advance for a new version.")
        if self.code > previous_code and current_parts <= previous_parts:
            raise DeliveryError(f"Version {previous} was already delivered. Advance versionName for the next delivery "
                                "(PATCH for fixes, MINOR for new functionality); GitHub publication is not the boundary.")

    def load_artifact(self, edition, require_source=True):
        receipt = read_json(self.receipt_path(edition))
        expected = {"version": self.version, "versionCode": self.code, "buildType": "release",
                    "bundledModels": edition == "full"}
        if any(receipt.get(k) != v for k, v in expected.items()):
            raise DeliveryError(f"Stale/wrong {edition} build receipt; refusing to install.")
        if require_source and receipt.get("sourceCommit") != self.commit and receipt.get("sourceFingerprint") != self.fingerprint():
            raise DeliveryError(f"The {edition} receipt cannot prove it contains the current source.")
        files = receipt.get("files", [])
        if len(files) != 1:
            raise DeliveryError("Expected exactly one ARM64 artifact per edition.")
        apk = Path(files[0]["path"]).resolve()
        if self.root not in apk.parents or not apk.name.endswith("-arm64-v8a.apk"):
            raise DeliveryError("Artifact is outside this worktree or has an unexpected ABI.")
        if digest(apk) != files[0]["sha256"].lower():
            raise DeliveryError("APK hash does not match the successful build receipt.")
        return {"path": str(apk), "sha256": digest(apk), "edition": edition}

    def build(self, edition):
        before = self.fingerprint()
        key = f"{self.version}/{self.code}/{edition}"
        cached = self.state["builds"].get(key, {})
        if cached.get("fingerprintPolicy") == POLICY and cached.get("fingerprint") != before:
            if str(self.code) in self.delivered_versions() or self.state.get("published", {}).get("tag") == self.version:
                raise DeliveryError(f"Version {self.version}/{self.code} already has a different source delivery. "
                                    "Update app/build.gradle.kts before building the next package; existing files were not replaced.")
            # A build-only draft is not a delivery. Preserve its APK and receipt before
            # completing the same development batch, without inventing another version.
            previous = self.load_artifact(edition, require_source=False)
            # A rejected concurrent-source build can leave a newer receipt than
            # the successful cache. Archive that verified file too; never reuse it.
            archive = self.root / "app/build/outputs/apk/archive" / f"uninstalled-{self.version}-{self.code}-{previous['sha256'][:12]}" / edition
            if not archive.exists():
                shutil.copytree(self.receipt_path(edition).parent, archive)
                receipt = read_json(archive / "latest.json")
                for item in receipt["files"]:
                    item["path"] = str(archive / Path(item["path"]).name)
                write_json(archive / "latest.json", receipt)
            archived = archive / Path(previous["path"]).name
            if not archived.is_file() or digest(archived) != previous["sha256"]:
                raise DeliveryError("Uninstalled build archive failed verification; nothing replaced.")
            print(f"[ARCHIVE] Preserved uninstalled draft: {archive}", flush=True)
        if not self.o.rebuild and cached.get("fingerprint") == before:
            try:
                result = self.load_artifact(edition)
                if result["sha256"] == cached.get("sha256"):
                    print(f"[REUSE] Verified same-source {edition} APK; no rebuild.", flush=True)
                    return result
            except (OSError, ValueError, DeliveryError):
                pass  # Rebuild; never silently select an arbitrary older APK.
        from storage_guard import check_storage
        check_storage(self.root)
        self.stage("BUILD-" + edition.upper())
        if os.name == "nt":
            import ctypes
            class MemoryStatus(ctypes.Structure):
                _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong)] + [
                    (x, ctypes.c_ulonglong) for x in ("total", "available", "totalPage", "availablePage", "totalVirtual", "availableVirtual", "extended")]
            memory = MemoryStatus()
            memory.length = ctypes.sizeof(memory)
            if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(memory)) and memory.available < 3 * 1024**3:
                raise DeliveryError("Less than 3 GiB free RAM. Build not started; existing Java/IDE processes were not killed.")
        main_before = self.fingerprint(False)
        args = GRADLE_ARGS + ["-I", str(self.root / "scripts/phone-release-abi.gradle"),
                              "-PpythonExecutable=" + sys.executable]
        parameters = "-BuildType Release -GradleArguments @(" + ",".join(ps_quote(x) for x in args) + ")"
        if edition == "full":
            parameters += " -BundleModels"
        self.script("build-apk.ps1", parameters)
        if self.git("rev-parse", "HEAD") != self.commit or self.fingerprint(False) != main_before:
            raise DeliveryError("Source changed while building. Refusing to install or publish an ambiguous artifact.")
        result = self.load_artifact(edition)
        fingerprint = self.fingerprint()
        receipt = read_json(self.receipt_path(edition))
        receipt["sourceFingerprint"] = fingerprint
        write_json(self.receipt_path(edition), receipt)
        self.state["builds"][key] = {"fingerprint": fingerprint, "fingerprintPolicy": POLICY, "sha256": result["sha256"]}
        write_json(self.state_path, self.state)
        return result

    def verify_apk(self, artifact):
        self.stage("VERIFY-" + artifact["edition"].upper())
        path = artifact["path"]
        if digest(Path(path)) != artifact["sha256"]:
            raise DeliveryError("APK changed after build.")
        command = f"& {ps_quote(self.signer)} verify --verbose --min-sdk-version 23 --print-certs {ps_quote(path)}; exit $LASTEXITCODE"
        check_signature_schemes(self.r.run([self.ps, "-NoProfile", "-NonInteractive", "-Command", command]))
        badging = self.r.run([self.aapt, "dump", "badging", path])
        match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
        if not match or match.groups() != (PACKAGE, str(self.code), self.version):
            raise DeliveryError("Actual APK package/version disagrees with source and receipt.")
        if "application-debuggable" in badging:
            raise DeliveryError("Refusing a debuggable APK as a Release delivery.")

    def installed_hash(self):
        paths = read_adb_device(self.r, self.adb, self.serial, "pm", "path", PACKAGE)
        candidates = [x.removeprefix("package:").strip() for x in paths.splitlines() if x.startswith("package:")]
        base = next((x for x in candidates if x.endswith("/base.apk")), None)
        if not base:
            return None
        out = self.r.run([self.adb, "-s", self.serial, "shell", "sha256sum", base], check=False)
        match = re.match(r"^([0-9a-fA-F]{64})\s", out)
        if match:
            return match[1].lower()
        # Toybox may be unavailable on unusual OEM firmware. Verify via a read-only pull.
        with tempfile.TemporaryDirectory(prefix="cyime-installed-") as directory:
            local = Path(directory) / "base.apk"
            self.r.run([self.adb, "-s", self.serial, "pull", base, str(local)], timeout=300)
            return digest(local)

    def install(self, artifact):
        self.stage("INSTALL")
        if self.installed_hash() == artifact["sha256"]:
            print("[REUSE] Device already has this exact APK.", flush=True)
        else:
            result = self.r.run([self.adb, "-s", self.serial, "install", "-r", artifact["path"]], timeout=600)
            if not re.search(r"\bSuccess\b", result):
                raise DeliveryError("ADB did not report Success. No uninstall/downgrade fallback.")
            if self.installed_hash() != artifact["sha256"]:
                raise DeliveryError("Installed APK hash could not be confirmed.")
        versions = self.delivered_versions()
        self.state["installed"] = {"serial": self.serial, **artifact}
        versions[str(self.code)] = self.version
        self.state["deliveredVersions"] = versions
        write_json(self.state_path, self.state)

    def release_info(self):
        # Listing distinguishes network/auth failure from 'tag absent'. Do not mask errors as absence.
        raw = self.r.run(["gh", "api", "--paginate", f"repos/{self.repo}/releases?per_page=100"], timeout=180)
        dec = json.JSONDecoder()
        while raw.strip():
            raw = raw.lstrip()
            page, used = dec.raw_decode(raw)
            for release in page:
                if release["tag_name"] == self.version:
                    return release
            raw = raw[used:]
        return None

    def verify_release_target(self, info):
        if info.get("draft"):
            ref = info["target_commitish"]
        else:
            ref = self.version
        sha = self.r.run(["gh", "api", f"repos/{self.repo}/commits/{ref}", "--jq", ".sha"])
        if sha != self.commit:
            raise DeliveryError("This release/tag targets a different commit. It will not be overwritten.")

    def remote_asset_matches(self, item, artifact):
        remote_digest = item.get("digest") or ""
        if remote_digest.startswith("sha256:"):
            return remote_digest[7:].lower() == artifact["sha256"]
        with tempfile.TemporaryDirectory(prefix="cyime-release-") as directory:
            self.r.run(["gh", "release", "download", self.version, "--repo", self.repo,
                        "--pattern", Path(artifact["path"]).name, "--dir", directory], timeout=600)
            return digest(Path(directory) / Path(artifact["path"]).name) == artifact["sha256"]

    def publication_gate(self, editions):
        self.require_committed_sources()
        for subdir, patch in (("app/src/main/jni/librime", "cyime-librime.patch"),
                              ("app/src/main/jni/librime-lua-deps", "cyime-lua-android.patch"),
                              ("app/src/main/assets/rime", "cyime-rime-schema.patch")):
            self.r.run(["git", "-C", str(self.root / subdir), "apply", "--reverse", "--check",
                        str(self.root / "patches" / patch)])
        for edition in editions:
            receipt = read_json(self.receipt_path(edition))
            status = receipt.get("layoutGate")
            if status == "passed":
                gate = read_json(self.receipt_path(edition).parent / "layout-gate.json")
                if gate.get("version") != self.version or gate.get("bundledModels") != (edition == "full"):
                    raise DeliveryError("Mismatched layout gate receipt.")
            elif status != "not-run":
                raise DeliveryError("Missing/invalid layout gate status; not claiming unexecuted tests passed.")

    def check_remote_tag(self):
        refs = self.git("ls-remote", "origin", f"refs/tags/{self.version}", f"refs/tags/{self.version}^{{}}")
        values = [line.split() for line in refs.splitlines() if line.strip()]
        if values:
            sha = next((v[0] for v in values if v[1].endswith("^{}")), values[0][0])
            if sha != self.commit:
                raise DeliveryError("Version tag already points to other source. No tag overwrite or automatic version guessing.")

    def publish(self, artifacts):
        self.stage("PUBLISH")
        self.publication_gate([a["edition"] for a in artifacts])
        self.check_remote_tag()
        info = self.release_info()
        if info is None:
            notes = self.root / f"docs/{self.version}.md"
            if not notes.is_file():
                notes = self.work / "release-notes.md"
                title = f"# CyIME {self.version}\n\nSource: `{self.commit}`\n\n"
                history = self.git("log", "-20", "--format=- %s")
                notes.write_text(title + "Recent commits (not a test/acceptance claim):\n\n" + history + "\n", encoding="utf-8")
            # Create a draft first. Publish only after all requested assets match.
            self.r.run(["gh", "release", "create", self.version, "--repo", self.repo,
                        "--target", self.commit, "--title", f"CyIME {self.version}",
                        "--notes-file", str(notes), "--draft"], timeout=180)
            info = self.release_info()
            if info is None:
                raise DeliveryError("Release command returned, but no matching GitHub release exists.")
        self.verify_release_target(info)
        for artifact in artifacts:
            name = Path(artifact["path"]).name
            match = next((a for a in info.get("assets", []) if a["name"] == name), None)
            if match:
                if not self.remote_asset_matches(match, artifact):
                    raise DeliveryError(f"Remote asset differs: {name}. No --clobber or published asset replacement.")
            elif info.get("draft"):
                self.r.run(["gh", "release", "upload", self.version, artifact["path"], "--repo", self.repo], timeout=1200)
            else:
                raise DeliveryError(f"Published release lacks {name}. Refusing to mutate a completed release.")
        # Re-query after uploads and verify before making a draft public.
        info = self.release_info()
        for artifact in artifacts:
            name = Path(artifact["path"]).name
            item = next((a for a in info["assets"] if a["name"] == name), None)
            if not item or not self.remote_asset_matches(item, artifact):
                raise DeliveryError(f"Remote verification failed for {name}.")
        if info.get("draft"):
            self.r.run(["gh", "release", "edit", self.version, "--repo", self.repo, "--draft=false", "--latest"])
        final = self.release_info()
        if not final or final.get("draft"):
            raise DeliveryError("Release is not public yet; re-run the same command to resume.")
        self.state["published"] = {"tag": self.version, "commit": self.commit, "url": final["html_url"]}
        write_json(self.state_path, self.state)
        print(final["html_url"], flush=True)

    def execute(self):
        print(f"[SOURCE] {self.root}\n[VERSION] {self.version} / {self.code}", flush=True)
        self.preflight()
        if getattr(self.o, "check_only", False):
            self.dependencies(prepare=False)
            print(f"[CHECKED] Java, SDK, signing configuration and native sources found. Device: {self.serial or 'none'}.", flush=True)
            print("No APK build, installation or publication was performed.", flush=True)
            return
        if getattr(self.o, "install_only", False):
            if not self.serial:
                raise DeliveryError("Installation pending: no authorized device. Re-run the same --install-only command after connecting.")
            edition = "full" if self.o.full else "standard"
            artifact = self.load_artifact(edition, require_source=False)
            print("[EXISTING APK] Installing the verified receipt for this version; this does not include unbuilt edits.", flush=True)
            self.verify_apk(artifact)
            self.install(artifact)
            self.stage("DONE")
            print(artifact["path"], flush=True)
            return
        self.check_delivered_version()
        self.dependencies()
        target = "full" if self.o.full else "standard"
        editions = ["standard", "full"] if getattr(self.o, "both", False) else [target]
        # A changed already-published version is not silently assigned another version/tag.
        if self.o.publish:
            self.check_remote_tag()
            existing = self.release_info()
            if existing:
                self.verify_release_target(existing)
        artifacts = []
        for edition in editions:
            artifact = self.build(edition)
            self.verify_apk(artifact)
            artifacts.append(artifact)
        # Recheck source identity before any device/network write.
        for artifact in artifacts:
            key = f"{self.version}/{self.code}/{artifact['edition']}"
            if self.state["builds"][key]["fingerprint"] != self.fingerprint():
                raise DeliveryError("Source changed after build. Installation/publication stopped.")
        pending_install = not getattr(self.o, "build_only", False) and not self.serial
        if not getattr(self.o, "build_only", False) and self.serial:
            self.install(next(a for a in artifacts if a["edition"] == target))
        if self.o.publish:
            self.publish(artifacts)
        self.stage("INSTALL-PENDING" if pending_install else "DONE")
        for artifact in artifacts:
            print(artifact["path"], flush=True)
        if self.serial:
            print(f"Installed {self.version}/{self.code} ({target}) on {self.serial}. Data retained.", flush=True)
        elif pending_install:
            print("APK ready; installation pending. Connect the device and re-run this command to reuse it.", flush=True)
        print("Manual functional acceptance: NOT performed by this delivery script.", flush=True)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--publish", action="store_true", help="Publish/resume only the requested edition(s); default is lightweight.")
    p.add_argument("--both", action="store_true", help="Explicitly build/publish both lightweight and bundled editions.")
    p.add_argument("--full", action="store_true", help="Install the model-bundled edition instead of standard.")
    p.add_argument("--serial", default="", help="Select a device when more than one is attached.")
    p.add_argument("--rebuild", action="store_true", help="Do not reuse the previous verified build.")
    p.add_argument("--build-only", action="store_true", help="Build without requiring an attached device or installing.")
    p.add_argument("--check", dest="check_only", action="store_true", help="Check the actual environment without building, installing or downloading dependencies.")
    p.add_argument("--install-only", action="store_true", help="Install the already-built, verified APK for this version; does not include unbuilt edits.")
    p.add_argument("--entry-root", type=Path, help=argparse.SUPPRESS)
    o = p.parse_args()
    if o.install_only and (o.publish or o.build_only or o.rebuild or o.both or o.check_only):
        p.error("--install-only cannot be combined with build, check or publication options")
    root = (o.entry_root or Path(__file__).resolve().parent.parent).resolve()
    try:
        root = integration_root(root)
    except (DeliveryError, OSError, subprocess.SubprocessError) as exc:
        print(f"STOP: {exc}", file=sys.stderr)
        return 1
    if not (root / "app/build.gradle.kts").exists():
        print("ERROR: Save this file as <CyIME>/scripts/auto-deliver.py", file=sys.stderr)
        return 1
    runner = Runner(root)
    try:
        common = Path(runner.run(["git", "rev-parse", "--git-common-dir"]))
        if not common.is_absolute():
            common = root / common
        with delivery_lock(common / "cyime-auto-delivery.lock"):
            delivery = Delivery(root, o, runner)
            try:
                delivery.execute()
            except Exception as exc:
                delivery.state["error"] = str(exc)
                write_json(delivery.state_path, delivery.state)
                raise
        return 0
    except (DeliveryError, OSError, ValueError, subprocess.SubprocessError) as exc:
        print(f"\nSTOP: {exc}\nNo clean, uninstall, force checkout, auto-commit or release overwrite was performed.", file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
