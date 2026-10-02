"""Run the project's T9 engine on this computer (Linux directly, Windows via WSL)."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys

import summarize_daily_chat as summary
from t9_fixture import validate_fixture
from t9_replay import DEFAULT_FIXTURE, HERE, REPO, PASSES, compact_report, save
from t9_resources import snapshot_resources, file_digest
from t9_replay_compare import evaluate_known_bad, compare_runs, write_comparison, load_run


def linux_path(path):
    """Only translate an actual Windows drive path, without invoking a shell."""
    value = str(Path(path).resolve())
    if len(value) < 3 or value[1:3] != ":\\" or not value[0].isalpha():
        raise ValueError("WSL replay requires a local Windows drive path: " + value)
    return "/mnt/" + value[0].lower() + "/" + value[3:].replace("\\", "/")


def find_json_jar(explicit=None):
    if explicit:
        result = Path(explicit).resolve()
        if not result.is_file():
            raise ValueError("JSON jar not found: " + str(result))
        return result
    matches = sorted((Path.home() / ".gradle/caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar"))
    if not matches:
        raise ValueError("org.json JAR not cached; supply --json-jar with an existing org.json library.")
    return matches[-1]


class HostCommands:
    def __init__(self, path):
        self.log = Path(path)

    def run(self, args, timeout=3600):
        args = [str(arg) for arg in args]
        with self.log.open("a", encoding="utf-8") as log:
            log.write(json.dumps(args, ensure_ascii=False) + "\n")
            log.flush()
            # Direct file output retains native progress and failures, even after interruption.
            result = subprocess.run(args, stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
        if result.returncode:
            raise RuntimeError(f"Host command failed ({result.returncode}); see {self.log}")


def run_host(args):
    output = (args.output or REPO / ".codex-artifacts" /
              ("t9-host-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f"))).resolve()
    if output.exists():
        raise ValueError("Output already exists; choose a new directory: " + str(output))
    fixture_bytes = args.fixture.read_bytes()
    original = json.loads(fixture_bytes.decode("utf-8-sig"))
    fixture = validate_fixture(original)
    jar = find_json_jar(args.json_jar)
    if os.name == "nt":
        command = ["wsl.exe", "-d", args.wsl_distro, "--", "python3", linux_path(HERE / "t9_host.py"),
                   "--fixture", linux_path(args.fixture), "--output", linux_path(output),
                   "--json-jar", linux_path(jar), "--key-delay-ms", str(args.key_delay_ms)]
        for key in ("baseline", "assertions"):
            if getattr(args, key):
                command.extend(["--" + key, linux_path(getattr(args, key))])
        if args.host_cache:
            command.extend(["--host-cache", args.host_cache])
        return subprocess.run(command).returncode
    if platform.system() != "Linux":
        raise ValueError("Host replay currently supports Linux, or Windows with WSL; no device fallback is allowed.")
    output.mkdir(parents=True)
    if fixture == original:
        (output / "fixture.json").write_bytes(fixture_bytes)
    else:
        (output / "input-fixture.json").write_bytes(fixture_bytes)
        save(output / "fixture.json", fixture)
    protocol = dict(key_delay_ms=args.key_delay_ms, row_limit=20, boundary_limit=100,
                    settle_timeout_ms=5000, modes=["continuous", "paused"], variant="digits", runtime="host")
    manifest = dict(format_version=1, status="running", fixture_sha256=file_digest(output / "fixture.json"),
                    input_fixture_sha256=hashlib.sha256(fixture_bytes).hexdigest(), passes=PASSES,
                    protocol_settings=protocol, user_state="fresh_empty", personalization=False,
                    started_utc=datetime.now(timezone.utc).isoformat(),
                    host=dict(system=platform.system(), architecture=platform.machine(), release=platform.release()),
                    reading_provenance=fixture.get("pronunciation_review", fixture.get("reading_provenance", {})))
    save(output / "run.json", manifest)
    commands = HostCommands(output / "commands.log")
    try:
        cache = Path(args.host_cache).expanduser() if args.host_cache else Path.home() / ".cache/cyime-t9-host"
        print("Building/reusing the project T9 engine on this computer; no Android device is used.", flush=True)
        build_command = [sys.executable, str(HERE / "host_replay/build_host.py"), "--repo", str(REPO), "--cache", str(cache)]
        with (output / "build.log").open("w", encoding="utf-8") as log:
            built = subprocess.run(build_command, stdout=subprocess.PIPE, stderr=log, timeout=3600)
        (output / "build-result.txt").write_bytes(built.stdout)
        if built.returncode:
            raise RuntimeError("Host engine build failed; see build.log and build-result.txt")
        build_lines = built.stdout.decode("utf-8").strip().splitlines()
        if not build_lines:
            raise ValueError("Host engine build returned no artifact description")
        build = json.loads(build_lines[-1])
        if not isinstance(build, dict) or any(not isinstance(build.get(key), str) for key in
                                              ("library_dir", "manifest", "java", "javac")):
            raise ValueError("Host engine build returned an invalid artifact description")
        library_dir = Path(build["library_dir"])
        libraries = {p.name: file_digest(p) for p in sorted(library_dir.glob("*.so"))}
        if "librime_jni.so" not in libraries:
            raise ValueError("Host build returned no librime_jni.so")
        shutil.copyfile(build["manifest"], output / "engine-build.json")
        build_manifest = json.loads((output / "engine-build.json").read_text(encoding="utf-8"))
        if (not isinstance(build_manifest, dict) or build_manifest.get("status") != "complete" or
                build_manifest.get("library_sha256") != libraries["librime_jni.so"]):
            raise ValueError("Host library differs from its completed build manifest")
        source_files = Path(build["manifest"]).parent / "source-files.json"
        if file_digest(source_files) != build_manifest.get("source_files_sha256"):
            raise ValueError("Host build source inventory differs from its fingerprint")
        shutil.copyfile(source_files, output / "source-files.json")
        resources = output / "resources.zip"
        print("Snapshotting the existing dictionaries, configuration and scoring model.", flush=True)
        resource_info = snapshot_resources(REPO, resources)
        manifest["engine"] = dict(source="host-source-build", resource_type="resource_zip",
                                  resources_sha256=resource_info["sha256"], apk_sha256=resource_info["sha256"],
                                  libraries=libraries, build_manifest_sha256=file_digest(output / "engine-build.json"))
        save(output / "run.json", manifest)
        source_dir, classes = output / "runner/src", output / "runner/classes"
        source_dir.mkdir(parents=True)
        classes.mkdir()
        sources = []
        for source in (HERE / "android_replay/ChatReplay.java", HERE / "android_replay/RimeEngine.java"):
            target = source_dir / source.name
            target.write_bytes(source.read_bytes())
            sources.append(target)
        manifest["compiled_runner_sources"] = {p.name: file_digest(p) for p in sources}
        manifest["json_jar_sha256"] = file_digest(jar)
        commands.run([build.get("javac", "javac"), "-encoding", "UTF-8", "--release", "8", "-cp", jar,
                      "-d", classes, *sources])
        root = output / "native-run"
        print(f"Replaying {len(fixture['cases'])} cases locally; immediate and settled candidates.", flush=True)
        commands.run([build.get("java", "java"), "-cp", str(classes) + os.pathsep + str(jar),
                      "com.cyime.audit.ChatReplay", "--host", "--resources", resources,
                      "--lib-dir", library_dir, "--root", root, "--corpus", output / "fixture.json",
                      "--output", output / "replay.jsonl", "--modes", "continuous,paused",
                      "--key-delay-ms", args.key_delay_ms])
        if {p.name: file_digest(p) for p in sorted(library_dir.glob("*.so"))} != libraries:
            raise ValueError("Host engine libraries changed during replay")
        shutil.copyfile(root / "user/build/t9_pinyin.schema.yaml", output / "deployed-schema.yaml")
        records, sources = summary.read_evidence([output / "replay.jsonl"])
        report, snapshots = summary.summarize(fixture, records, PASSES)
        report.update(fixture_sha256=manifest["fixture_sha256"], sources=sources)
        summary.export_report(output / "report", report, snapshots)
        issues = sum(len(p["execution_issues"]) for p in report["passes"].values())
        manifest.update(status="complete", replay_sha256=file_digest(output / "replay.jsonl"),
                        deployed_schema_sha256=file_digest(output / "deployed-schema.yaml"), execution_issues=issues,
                        finished_utc=datetime.now(timezone.utc).isoformat())
        save(output / "run.json", manifest)
        load_run(output)  # Cross-check executed engine/resource/corpus hashes before reporting success.
        quality = evaluate_known_bad(output, args.assertions)
        if args.baseline:
            try:
                comparison = compare_runs(args.baseline.resolve(), output, args.assertions)
            except (ValueError, OSError, KeyError, TypeError) as error:
                comparison = dict(status="invalid", exit_code=2, error=str(error))
            write_comparison(output / "comparison", comparison)
            quality["comparison"] = dict(path="comparison/comparison.json", exit_code=comparison["exit_code"])
            quality["exit_code"] = max(quality["exit_code"], comparison["exit_code"])
        quality["exit_code"] = max(quality["exit_code"], 1 if issues else 0)
        save(output / "quality.json", quality)
        compact_report(output, fixture, report, records, quality)
        print(json.dumps(dict(output=str(output), execution="complete", quality_exit=quality["exit_code"],
                              known_bad=len(quality.get("violations", []))), ensure_ascii=False), flush=True)
        return quality["exit_code"]
    except Exception as error:
        manifest.update(status="failed", error=str(error), finished_utc=datetime.now(timezone.utc).isoformat())
        save(output / "run.json", manifest)
        raise


def add_arguments(parser):
    parser.add_argument("--fixture", type=Path, default=DEFAULT_FIXTURE)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--assertions", type=Path)
    parser.add_argument("--key-delay-ms", type=int, choices=range(0, 1001), default=35, metavar="0..1000")
    parser.add_argument("--wsl-distro", default="Ubuntu-22.04-D")
    parser.add_argument("--host-cache", help="Linux cache path for host source snapshots, dependencies and engine builds")
    parser.add_argument("--json-jar", type=Path, help="Existing org.json JAR (normally found in Gradle cache)")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    add_arguments(parser)
    args = parser.parse_args(argv)
    try:
        return run_host(args)
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as error:
        print("T9 host replay failed: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
