#!/usr/bin/env python3
"""CyIME one-command delivery. Python 3.10+, standard library only.
Default: prepare -> build/reuse Release -> verify -> adb install -r -> verify.
--publish: also build both editions and publish/resume the SAME GitHub release.
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
POLICY = "cyime-auto-delivery-v1"
# Match the existing scripts, including native-only changes. Never use file mtimes.
SOURCE_PATHS = ["app/src/main", "app/build.gradle.kts", "app/build-logic",
                "app/proguard-rules.pro", "plugin-core", "plugins", "patches",
                "gradle", "gradle.properties", "build.gradle.kts", "settings.gradle.kts",
                "gradlew", "gradlew.bat", ".gitmodules", ".gitattributes", "scripts", "tools"]
HELPERS = {"scripts/auto-deliver.py", "scripts/test_auto_deliver.py"}
GRADLE_ARGS = ["--max-workers=2", "--no-parallel", "--console=plain",
               "-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8",
               "-Pkotlin.compiler.execution.strategy=in-process"]

class DeliveryError(RuntimeError):
    pass

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
        java = next((p for p in java_candidates if (p/"bin/java.exe").is_file()
                     and (p/"release").is_file()
                     and re.search(r'JAVA_VERSION="(?:21|22|23|24|25)\.', (p/"release").read_text())), None)
        if not java:
            raise DeliveryError("Java 21+ not found in JAVA_HOME, Gradle toolchains or Android Studio.")
        self.r.env["JAVA_HOME"] = str(java)
        self.r.env["PATH"] = str(java/"bin") + os.pathsep + self.r.env.get("PATH", "")
        self.r.env["JAVA_TOOL_OPTIONS"] = self.r.env.get("JAVA_TOOL_OPTIONS", "") + " -Dfile.encoding=UTF-8"
        if os.name == "nt" and "-Djdk.net.unixdomain.tmpdir=" not in self.r.env["JAVA_TOOL_OPTIONS"]:
            # Java's Unix-domain IPC fails on this Windows Gradle setup; use its TCP fallback.
            ipc = Path.home()/".gradle/cyime-unixdomain-disabled"
            self.r.env["JAVA_TOOL_OPTIONS"] += " -Djdk.net.unixdomain.tmpdir=" + str(ipc).replace('\\', '/')
        if not getattr(self.o, "build_only", False):
            self.serial = choose_device(self.r.run([self.adb, "devices"]), self.serial)
        else:
            self.serial = ""
        if self.serial:
            self.check_device()
        self.check_publication()

    def check_device(self):
        abi = self.r.run([self.adb, "-s", self.serial, "shell", "getprop", "ro.product.cpu.abilist"])
        if "arm64-v8a" not in abi.split(","):
            raise DeliveryError("This project's Release delivery script targets ARM64; connected device is not ARM64.")
        dump = self.r.run([self.adb, "-s", self.serial, "shell", "dumpsys", "package", PACKAGE])
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
        paths = [p for p in (changes + "\n" + new).splitlines() if p and p not in HELPERS]
        if paths:
            raise DeliveryError("Publish needs committed source changes (local build/install does not):\n" + "\n".join(paths[:15]))

    def dependencies(self):
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
        h = hashlib.sha256((POLICY + self.commit + str(self.root)).encode())
        listing = self.git("ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", *SOURCE_PATHS)
        for name in sorted(set(listing.split("\0"))):
            if not name:
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

    def load_artifact(self, edition):
        receipt = read_json(self.receipt_path(edition))
        expected = {"version": self.version, "versionCode": self.code, "buildType": "release",
                    "bundledModels": edition == "full", "sourceCommit": self.commit}
        if any(receipt.get(k) != v for k, v in expected.items()):
            raise DeliveryError(f"Stale/wrong {edition} build receipt; refusing to install.")
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
        if not self.o.rebuild and cached.get("fingerprint") == before:
            try:
                result = self.load_artifact(edition)
                if result["sha256"] == cached.get("sha256"):
                    print(f"[REUSE] Verified same-source {edition} APK; no rebuild.", flush=True)
                    return result
            except (OSError, ValueError, DeliveryError):
                pass  # Rebuild; never silently select an arbitrary older APK.
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
        self.state["builds"][key] = {"fingerprint": self.fingerprint(), "sha256": result["sha256"]}
        write_json(self.state_path, self.state)
        return result

    def verify_apk(self, artifact):
        self.stage("VERIFY-" + artifact["edition"].upper())
        path = artifact["path"]
        if digest(Path(path)) != artifact["sha256"]:
            raise DeliveryError("APK changed after build.")
        command = f"& {ps_quote(self.signer)} verify --print-certs {ps_quote(path)}; exit $LASTEXITCODE"
        self.r.run([self.ps, "-NoProfile", "-NonInteractive", "-Command", command])
        badging = self.r.run([self.aapt, "dump", "badging", path])
        match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
        if not match or match.groups() != (PACKAGE, str(self.code), self.version):
            raise DeliveryError("Actual APK package/version disagrees with source and receipt.")
        if "application-debuggable" in badging:
            raise DeliveryError("Refusing a debuggable APK as a Release delivery.")

    def installed_hash(self):
        paths = self.r.run([self.adb, "-s", self.serial, "shell", "pm", "path", PACKAGE])
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
        self.state["installed"] = {"serial": self.serial, **artifact}
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

    def publication_gate(self):
        self.require_committed_sources()
        for subdir, patch in (("app/src/main/jni/librime", "cyime-librime.patch"),
                              ("app/src/main/jni/librime-lua-deps", "cyime-lua-android.patch"),
                              ("app/src/main/assets/rime", "cyime-rime-schema.patch")):
            self.r.run(["git", "-C", str(self.root / subdir), "apply", "--reverse", "--check",
                        str(self.root / "patches" / patch)])
        for edition in ("standard", "full"):
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
        self.publication_gate()
        self.check_remote_tag()
        info = self.release_info()
        if info is None:
            notes = self.root / f"docs/{self.version}.md"
            if not notes.is_file():
                notes = self.work / "release-notes.md"
                title = f"# CyIME {self.version}\n\nSource: `{self.commit}`\n\n"
                history = self.git("log", "-20", "--format=- %s")
                notes.write_text(title + "Recent commits (not a test/acceptance claim):\n\n" + history + "\n", encoding="utf-8")
            # Preserve the project's existing source-patch and delivery gates.
            self.script("publish-release.ps1", "-NotesFile " + ps_quote(str(notes)))
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
        self.preflight()
        self.dependencies()
        target = "full" if self.o.full else "standard"
        editions = ["standard", "full"] if self.o.publish else [target]
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
        if not getattr(self.o, "build_only", False):
            self.install(next(a for a in artifacts if a["edition"] == target))
        if self.o.publish:
            self.publish(artifacts)
        self.stage("DONE")
        for artifact in artifacts:
            print(artifact["path"], flush=True)
        if self.serial:
            print(f"Installed {self.version}/{self.code} ({target}) on {self.serial}. Data retained.", flush=True)
        print("Manual functional acceptance: NOT performed by this delivery script.", flush=True)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--publish", action="store_true", help="Build both editions, install, then publish/resume GitHub Release.")
    p.add_argument("--full", action="store_true", help="Install the model-bundled edition instead of standard.")
    p.add_argument("--serial", default="", help="Select a device when more than one is attached.")
    p.add_argument("--rebuild", action="store_true", help="Do not reuse the previous verified build.")
    p.add_argument("--build-only", action="store_true", help="Build without requiring an attached device or installing.")
    o = p.parse_args()
    root = Path(__file__).resolve().parent.parent
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
