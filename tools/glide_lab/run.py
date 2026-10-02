"""Offline Kotlin glide experiment. Never invokes Gradle, Android, ADB or network."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def field(value):
    if not isinstance(value, str) or not value or len(value) > 256 or any(ord(c) < 32 or c in '\x85\u2028\u2029' for c in value):
        raise ValueError("Expected nonempty text without control characters (at most 256 characters)")
    return value


def number(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("Expected a finite number")
    try:
        finite = math.isfinite(value)
    except OverflowError as exc:
        raise ValueError("Expected a finite number") from exc
    if not finite:
        raise ValueError("Expected a finite number")
    return value


def load_fixture(path):
    if path.stat().st_size > 4 * 1024 * 1024:
        raise ValueError("Fixture exceeds 4 MiB")
    data = json.loads(path.read_text(encoding="utf-8"))
    if data["schema"] != "cyime.glide.fixture.v1" or data["kind"] != "synthetic":
        raise ValueError("This preparation runner currently accepts explicitly synthetic fixtures only")
    if (data["language"], data["scheme"]) not in (("en", "direct"), ("zh", "pinyin")):
        raise ValueError("Unsupported language/scheme combination")
    field(data["id"])
    layout = data["layout"]
    field(layout["revision"])
    if number(layout["key_unit"]) <= 0 or not 2 <= len(layout["keys"]) <= 64:
        raise ValueError("Invalid layout size or key unit")
    symbols = set()
    for key in layout["keys"]:
        symbol = field(key["symbol"])
        if not re.fullmatch("[a-z]", symbol) or symbol in symbols:
            raise ValueError("Layout symbols must be unique lowercase Latin letters")
        symbols.add(symbol)
        number(key["x"])
        number(key["y"])
    entries = data["lexicon"]
    if not 1 <= len(entries) <= 10000:
        raise ValueError("Expected 1..10000 lexicon entries")
    ids = set()
    for entry in entries:
        identifier = field(entry["id"])
        if identifier in ids:
            raise ValueError("Duplicate lexicon id")
        ids.add(identifier)
        field(entry["text"])
        code = field(entry["code"])
        if len(code) > 64 or not set(code) <= symbols:
            raise ValueError("Code is too long or includes unsupported layout symbols")
        if not 0 <= number(entry["prior_cost"]) <= 1:
            raise ValueError("prior_cost must be in 0..1 (illustrative, not a probability)")
    cases = data["cases"]
    if not 1 <= len(cases) <= 1000:
        raise ValueError("Expected 1..1000 cases")
    case_ids = set()
    for case in cases:
        identifier = field(case["id"])
        if identifier in case_ids:
            raise ValueError("Duplicate case id")
        case_ids.add(identifier)
        field(case["description"])
        expected = case["expected_ids"]
        if not isinstance(expected, list) or not all(isinstance(x, str) and x in ids for x in expected):
            raise ValueError("Expected ids must reference the fixture lexicon")
        points = case["points"]
        if not 1 <= len(points) <= 2048:
            raise ValueError("Expected 1..2048 trace points")
        previous = -1
        for point in points:
            if len(point) != 3:
                raise ValueError("Point must be [x, y, milliseconds]")
            number(point[0])
            number(point[1])
            stamp = point[2]
            if isinstance(stamp, bool) or not isinstance(stamp, int) or not 0 <= stamp <= 60000 or stamp < previous:
                raise ValueError("Trace timestamps must be monotonic integers in 0..60000")
            previous = stamp
    return data


def cached(cache, group, artifact, version, suffix):
    paths = sorted((cache / group / artifact / version).glob(f"*/{artifact}-{version}.{suffix}"))
    if len(paths) != 1:
        raise FileNotFoundError(f"Need exactly one cached {group}:{artifact}:{version} .{suffix}; no download attempted")
    return paths[0]


def compiler_paths(cache):
    versions = (REPO / "gradle/libs.versions.toml").read_text(encoding="utf-8")
    version = re.search(r'^kotlin = "([^"]+)"', versions, re.M).group(1)
    compiler = cached(cache, "org.jetbrains.kotlin", "kotlin-compiler-embeddable", version, "jar")
    pom = cached(cache, "org.jetbrains.kotlin", "kotlin-compiler-embeddable", version, "pom")
    jars = [compiler]
    for dep in ET.parse(pom).findall("./m:dependencies/m:dependency", NS):
        coordinate = [dep.findtext("m:" + key, namespaces=NS) for key in ("groupId", "artifactId", "version")]
        jars.append(cached(cache, *coordinate, "jar"))
    # Kotlin's JVM backend uses JetBrains annotations; stdlib declares this version.
    jars.append(cached(cache, "org.jetbrains", "annotations", "13.0", "jar"))
    stdlib = cached(cache, "org.jetbrains.kotlin", "kotlin-stdlib", version, "jar")
    return version, jars, stdlib


def execute(arguments, log):
    result = subprocess.run(list(map(str, arguments)), text=True, encoding="utf-8", errors="replace",
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180)
    log.write_text(result.stdout, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}); see {log}\n{result.stdout[-3000:]}")
    return result.stdout


def write_inputs(data, directory):
    directory.mkdir()
    layout = data["layout"]
    (directory / "layout.tsv").write_text(
        f'{layout["revision"]}\t{layout["key_unit"]}\n' +
        "".join(f'{k["symbol"]}\t{k["x"]}\t{k["y"]}\n' for k in layout["keys"]), encoding="utf-8")
    (directory / "lexicon.tsv").write_text("".join(
        f'{e["id"]}\t{e["code"]}\t{e["text"]}\t{e["prior_cost"]}\n' for e in data["lexicon"]), encoding="utf-8")
    (directory / "traces.tsv").write_text("".join(
        f'{c["id"]}\t{p[0]}\t{p[1]}\t{p[2]}\n' for c in data["cases"] for p in c["points"]), encoding="utf-8")


def summarize(data, output):
    decoded = {}
    for line in output.splitlines():
        parts = line.split("\t")
        if parts[0] == "CASE":
            decoded[parts[1]] = {"rejection": None if parts[2] == "-" else parts[2],
                                  "median_ms": float(parts[3]), "candidates": []}
        elif parts[0] == "CANDIDATE":
            decoded[parts[1]]["candidates"].append({
                "id": parts[2], "shape": float(parts[3]), "endpoint": float(parts[4]),
                "prior": float(parts[5]), "total": float(parts[6])})
    cases = []
    for case in data["cases"]:
        result = decoded[case["id"]]
        candidate_ids = [c["id"] for c in result["candidates"]]
        expected = case["expected_ids"]
        rank = next((i + 1 for i, candidate in enumerate(candidate_ids) if candidate in expected), None)
        passed = rank == 1 if expected else not candidate_ids
        cases.append({"id": case["id"], "description": case["description"], "expected_ids": expected,
                      "rank": rank, "passed": passed, **result})
    positive = [c for c in cases if c["expected_ids"]]
    negative = [c for c in cases if not c["expected_ids"]]
    return {"fixture": data["id"], "kind": data["kind"], "language": data["language"],
            "scheme": data["scheme"], "lexicon_entries": len(data["lexicon"]),
            "summary": {"cases": len(cases), "passed": sum(c["passed"] for c in cases),
                        "top1": sum(c["rank"] == 1 for c in positive),
                        "top5": sum(c["rank"] is not None and c["rank"] <= 5 for c in positive), "positive_cases": len(positive),
                        "false_activations": sum(not c["passed"] for c in negative), "negative_cases": len(negative)},
            "cases": cases}


def compare(current, baseline):
    if current["fixture_sha256"] != baseline["fixture_sha256"] or baseline["schema"] != current["schema"]:
        raise ValueError("Comparison requires the same fixture bytes and report protocol")
    old = {(f["fixture"], c["id"]): c for f in baseline["fixtures"] for c in f["cases"]}
    changes = []
    for fixture in current["fixtures"]:
        for case in fixture["cases"]:
            before = old[(fixture["fixture"], case["id"])]
            if (before["rank"], before["passed"]) != (case["rank"], case["passed"]):
                changes.append({"fixture": fixture["fixture"], "case": case["id"],
                                "before_rank": before["rank"], "after_rank": case["rank"],
                                "before_passed": before["passed"], "after_passed": case["passed"]})
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="New result directory; never overwrites existing results")
    parser.add_argument("--fixture", type=Path, action="append", help="Repeat for multiple synthetic fixtures")
    parser.add_argument("--baseline", type=Path, help="Prior report.json using identical fixture bytes")
    parser.add_argument("--gradle-cache", type=Path, default=Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))) / "caches/modules-2/files-2.1")
    args = parser.parse_args()
    fixtures = args.fixture or sorted((HERE / "fixtures").glob("*.json"))
    data = [load_fixture(path) for path in fixtures]
    if not data or len({d["id"] for d in data}) != len(data):
        raise ValueError("Need at least one fixture and unique fixture ids")
    java = shutil.which("java")
    if not java:
        raise FileNotFoundError("A local JDK is required; no installation attempted")
    version, jars, stdlib = compiler_paths(args.gradle_cache)
    args.output.mkdir(parents=True, exist_ok=False)
    output = args.output.resolve()
    sources = sorted((HERE / "src").glob("*.kt"))
    artifact = output / "glide-lab.jar"
    print("Compiling isolated Kotlin experiment; no Gradle/Android tasks", flush=True)
    execute([java, "-Xmx512m", "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join(map(str, jars)),
             "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17",
             "-classpath", os.pathsep.join(map(str, [stdlib, jars[-1]])), "-d", artifact, *sources], output / "compile.log")
    command = [java, "-Xmx512m", "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join(map(str, [artifact, stdlib]))]
    checks = execute([*command, "com.kingzcheung.xime.glidelab.GlideCoreChecksKt"], output / "checks.log")
    print(checks.strip())
    report = {"schema": "cyime.glide.report.v1", "status": "experimental-not-device-accepted",
              "kotlin_version": version, "latency_protocol": "3 warmups, 7 repetitions per trace; median host JVM milliseconds; toy lexicon only",
              "sources_sha256": {str(p.relative_to(REPO)).replace('\\', '/'): sha(p) for p in [*sources, Path(__file__)]},
              "compiler_sha256": {p.name: sha(p) for p in jars},
              "fixture_sha256": {d["id"]: sha(p) for d, p in zip(data, fixtures)}, "fixtures": []}
    for i, fixture in enumerate(data):
        directory = output / f"fixture-{i}"
        write_inputs(fixture, directory)
        result = execute([*command, "com.kingzcheung.xime.glidelab.GlideReplayKt", directory], directory / "replay.tsv")
        evaluation = summarize(fixture, result)
        report["fixtures"].append(evaluation)
        print(f'{fixture["id"]}: {evaluation["summary"]}')
    if args.baseline:
        report["baseline_changes"] = compare(report, json.loads(args.baseline.read_text(encoding="utf-8")))
    (output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Report: {output / 'report.json'}")
    return 0 if all(c["passed"] for f in report["fixtures"] for c in f["cases"]) else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, KeyError, TypeError, OSError, RuntimeError, subprocess.TimeoutExpired) as exc:
        print(f"Glide lab: {exc}", file=sys.stderr)
        sys.exit(2)
