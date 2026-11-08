"""Run the November update feedback checks on the computer. No device or delivery commands."""
import argparse
import csv
import json
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from collections import Counter
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from storage_guard import check_storage


def load_cases():
    with (HERE / "cases.tsv").open(encoding="utf-8", newline="") as stream:
        cases = list(csv.DictReader(stream, delimiter="\t"))
    if len({c["id"] for c in cases}) != len(cases) or any(not all(c.values()) or None in c for c in cases):
        raise ValueError("Incomplete or duplicate feedback entry")
    return cases


def selectors(case):
    return [] if case["tests"] == "-" else case["tests"].split(",")


def test_classes():
    classes = {}
    for path in [*(ROOT / "app/src/test").rglob("*Test.kt"), *(HERE / "probes").glob("*Test.kt")]:
        package = re.search(r"^package ([\w.]+)", path.read_text(encoding="utf-8-sig"), re.M)
        if package:
            classes.setdefault(path.stem, []).append(f"{package[1]}.{path.stem}")
    return classes


def run_tests(cases, output):
    classes = test_classes()
    names = sorted({s for case in cases for s in selectors(case)})
    args = []
    for name in names:
        cls, dot, method = name.partition(".")
        if len(classes.get(cls, [])) != 1:
            raise ValueError(f"Missing or ambiguous JVM test: {cls}")
        args.extend(["--tests", classes[cls][0] + dot + method])
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [str(wrapper), "--offline", "--console=plain", "-I", str(HERE / "probes.gradle"),
               ":app:testDebugUnitTest", "--rerun", *args]
    env = os.environ.copy()
    if os.name == "nt" and Path("D:/CyIME-Data/uds").is_dir():
        env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") +
                                    " -Djdk.net.unixdomain.tmpdir=D:/CyIME-Data/uds").strip()
    with (output / "gradle.log").open("w", encoding="utf-8") as log:
        plan = subprocess.run(command + ["--dry-run"], cwd=ROOT, env=env, capture_output=True)
        plan_text = (plan.stdout + plan.stderr).decode("utf-8", "replace")
        (output / "task-plan.txt").write_text(plan_text, encoding="utf-8")
        if plan.returncode or re.search(r"(?im)^:(?:[^:\s]+:)*(?:assemble\w*|install\w*|uninstall\w*|connected\w*|publish\w*|package(?:Debug|Release))\s", plan_text):
            raise ValueError("Unexpected Gradle task plan; see task-plan.txt")
        started = time.time_ns()
        code = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT).returncode
    archive_xml(output, started, cases)
    return code


def archive_xml(output, started_ns, cases):
    # AGP chooses the XML directory after task configuration. Keep only the XML written
    # during this invocation, for exactly the classes requested. The caller forces a rerun.
    dest = output / "junit"
    dest.mkdir(exist_ok=False)
    names = {s.partition(".")[0] for case in cases for s in selectors(case)}
    for file in (ROOT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        cls = file.stem.rsplit(".", 1)[-1]
        if cls in names and file.stat().st_mtime_ns >= started_ns:
            shutil.copy2(file, dest / file.name)


def read_results(directory):
    records = []
    for file in sorted(directory.glob("TEST-*.xml")):
        for test in ET.parse(file).getroot().iter("testcase"):
            error = test.find("failure")
            if error is None:
                error = test.find("error")
            records.append(dict(cls=test.attrib["classname"].rsplit(".", 1)[-1], name=test.attrib["name"],
                                status=("failed" if error is not None else
                                        "skipped" if test.find("skipped") is not None else "passed"),
                                error=error.get("message", "") if error is not None else ""))
    return records


def match(selector, record):
    cls, _, method = selector.partition(".")
    return record["cls"] == cls and (not method or re.sub(r"\[.*?\]$", "", record["name"]) == method)


def write_report(cases, output, records, probes, code, sources=()):
    rows = []
    lines = ["# 更新反馈逐项排查", "", "本次在电脑运行。JVM 测试使用当前工作目录；标有归档的结果来自以前采集的原始文件。",
             "每行的验收条件描述用户操作。通过的测试只证明列出的场景。缺失、跳过与运行错误均单列。",
             "", f"测试进程退出码 {code}。JUnit 用例按类与名称去重后为 {dict(Counter(r['status'] for r in records))}。", ""]
    if sources:
        lines += ["采用下列明确指定的本轮 JUnit 原件（此汇总未重新执行测试）：", "",
                  *[f"- [{p.name} 的运行记录](<{(p/'gradle.log').as_posix()}>)" for p in sources], ""]
    for case in cases:
        tests = selectors(case)
        selected = [r for r in records if any(match(s, r) for s in tests)]
        missing = [s for s in tests if not any(match(s, r) for r in records)]
        problems = [r for r in selected if r["status"] != "passed"]
        probe = probes.get(case["probe"])
        if missing or any(r["status"] == "skipped" for r in problems):
            status = "检查不全"
        elif problems and all(r["cls"] in {"ReportedBehaviorTest", "ImportedEnglishAuditTest", "ThemeTrialAuditTest"}
                              and r["error"].startswith("java.lang.AssertionError") for r in problems):
            status = "本机已复现"
        elif problems:
            status = "断言或测试执行失败"
        elif not tests and not probe:
            status = "待验证"
        else:
            status = "已取得部分证据"
        if probe and probe["status"] != "ok":
            status = "材料缺失" if probe["status"] == "missing" else "证据检查失败"
            if probe["status"] == "mismatch":
                status = "当前配置已确认缺项"
        counts = dict(Counter(r["status"] for r in selected))
        rows.append(dict(**case, result=status, counts=counts, missing=missing, problems=problems, observation=probe))
        lines += [f"## {case['topic']} · {status}", "", case["expectation"], "",
                  f"- 本轮测试 {counts or '无对应 JVM 用例'}。", f"- 仍须核对：{case['remaining']}"]
        if tests:
            lines.append("- 测试选择器：" + "、".join(f"`{s}`" for s in tests))
        if missing:
            lines.append("- 未取得结果：" + "、".join(missing))
        for r in problems:
            lines.append(f"- {r['status']} · {r['cls']}.{r['name']} · {r['error']}")
        if probe:
            lines.append("- " + probe["detail"])
        lines.append("")
    (output / "report.md").write_text("\n".join(lines), encoding="utf-8")
    (output / "results.json").write_text(json.dumps(dict(gradle=code, cases=rows, tests=records),
                                                    indent=2, ensure_ascii=False), encoding="utf-8")
    return rows


def combine_runs(paths):
    records = {}
    codes = []
    for path in paths:
        saved = json.loads((path / "results.json").read_text(encoding="utf-8"))
        actual = read_results(path / "junit")
        if not actual or actual != saved["tests"]:
            raise ValueError(f"Archived raw JUnit results do not match the run: {path}")
        codes.append(saved["gradle"])
        for result in actual:
            key = (result["cls"], result["name"])
            if key in records and result != records[key]:
                raise ValueError(f"Conflicting results for {key}; report these runs separately")
            records[key] = result
    return list(records.values()), codes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="new report directory")
    parser.add_argument("--archives", type=Path, default=Path("D:/CyIME-Data/eval"))
    parser.add_argument("--apk", type=Path, help="existing APK for the size breakdown")
    parser.add_argument("--case", dest="ids", action="append", help="run one feedback ID (repeatable)")
    parser.add_argument("--from-run", type=Path, action="append", help="report existing raw JUnit instead of rerunning (repeatable)")
    args = parser.parse_args()
    cases = load_cases()
    if args.ids:
        unknown = set(args.ids) - {c["id"] for c in cases}
        if unknown:
            parser.error(f"unknown feedback ids: {sorted(unknown)}")
        cases = [c for c in cases if c["id"] in args.ids]
    check_storage(ROOT)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    info = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    (output / "run.txt").write_text(f"{datetime.now().astimezone().isoformat()}\n{ROOT}\nHEAD {info}\n",
                                    encoding="utf-8")
    print(f"Report directory: {output}", flush=True)
    sources = [p.resolve() for p in (args.from_run or [])]
    if sources:
        records, code = combine_runs(sources)
    else:
        code = -1
        try:
            code = run_tests(cases, output) if any(selectors(c) for c in cases) else 0
        except (OSError, ValueError) as error:
            (output / "run-error.txt").write_text(str(error), encoding="utf-8")
        records = read_results(output / "junit")
    from observations import collect
    probes = collect(ROOT, args.archives, args.apk, {c["probe"] for c in cases})
    rows = write_report(cases, output, records, probes, code, sources)
    print(f"{len(rows)} feedback entries; {len(records)} JVM cases; Gradle {code}.")
    if any(r["missing"] or r["problems"] for r in rows) or any(code if sources else [code]) or any(p["status"] != "ok" for p in probes.values()):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
