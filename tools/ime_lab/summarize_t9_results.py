"""Validate saved Android replay evidence and export a self-contained review report."""
import argparse
import hashlib
import html
import json
import math
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results-dir", type=Path, default=Path(".gradle"))
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    sources = Path("app/src/androidTest/assets/ime_lab")
    original = json.loads((sources / "t9_reported_cases.json").read_text(encoding="utf-8"))
    holdout = json.loads((sources / "t9_holdout_cases.json").read_text(encoding="utf-8"))
    runs = {}
    for label in ("default", "lock-first", "lock-all", "learned", "holdout"):
        log = args.results_dir / f"t9-acceptance-{label}.log"
        assert "OK (1 test)" in log.read_text(encoding="utf-8"), log
        data = args.results_dir / f"t9-acceptance-{label}.jsonl"
        rows = [json.loads(line) for line in data.read_text(encoding="utf-8").splitlines()]
        cases = (holdout if label == "holdout" else original)["constructed_and_historical_replays"]
        expected = {}
        for case in cases:
            keys = case.get("full_digit_keys", case.get("raw_keys"))
            if label.startswith("lock-"):
                if not case.get("pinyin") or len(case["pinyin"].replace(" ", "")) != len(keys):
                    continue
                variants = ["locked_1" if label == "lock-first" else "locked_99"]
            else:
                variants = ["digits"] + (["separated"] if "with_explicit_separator_key_1" in case else [])
            for variant in variants:
                expected[case["id"], variant] = case
        assert len(rows) == len(expected), (label, len(rows), len(expected))
        assert {(row["id"], row["variant"]) for row in rows} == set(expected)
        summary = []
        for row in rows:
            case = expected[row["id"], row["variant"]]
            targets = case.get("target_any", [case.get("target")])
            texts = [entry[0] for entry in row["candidates"]]
            rank = next((i + 1 for i, text in enumerate(texts) if text in targets), 0)
            assert rank == row["target_rank"]
            if label != "holdout":
                assert row["ranking_enforced"] and rank > 0, (label, row["id"])
                if case.get("target_rank_requirement") is not None:
                    assert rank == case["target_rank_requirement"], (label, row["id"], rank)
                for lower in case.get("pairwise_lower_priority_in_this_fixture", []):
                    assert lower not in texts or rank < texts.index(lower) + 1
            summary.append(dict(id=row["id"], variant=row["variant"], targets=targets,
                                rank=rank, first=texts[0] if texts else "", keys=row["keys"],
                                fixture_context=case.get("context", ""), external_context_applied=False,
                                latency_ms=row["latency_ms"], scoring_status=row["scoring_status"]))
        runs[label] = dict(count=len(rows), top1=sum(row["rank"] == 1 for row in summary),
                           recall100=sum(row["rank"] > 0 for row in summary),
                           ranking_gate=label != "holdout", rows=summary)
        for source in (log, data):
            shutil.copyfile(source, args.output_dir / source.name)

    for name, success in (("t9-integration-final.log", "OK (35 tests)"),
                          ("t9-native-final.log", "[  PASSED  ] 338 tests.")):
        source = args.results_dir / name
        assert success in source.read_text(encoding="utf-8"), name
        shutil.copyfile(source, args.output_dir / name)
    unit = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    for module in ("app", "plugin-core"):
        for path in Path(f"{module}/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
            root = ET.parse(path).getroot()
            for key in unit:
                unit[key] += int(root.get(key, "0"))
    assert unit["tests"] > 0 and not any(unit[k] for k in ("failures", "errors", "skipped"))
    queue = json.loads((args.results_dir / "t9-queue-final.json").read_text())
    latencies = sorted(queue.pop("keyToEngineCompleteMs"))
    queue["key_to_complete_p95_ms"] = latencies[math.ceil(len(latencies) * .95) - 1]
    queue["key_to_complete_max_ms"] = max(latencies)
    timings = json.loads((args.results_dir / "t9-long-final.json").read_text())
    for name in ("t9-queue-final.json", "t9-long-final.json", "t9-memory-final.txt", "t9-model-reproduction.log", "t9-apk-signature.log"):
        shutil.copyfile(args.results_dir / name, args.output_dir / name)
    binaries = {}
    for abi in ("arm64-v8a", "x86_64"):
        path = Path(f"app/build/outputs/t9-fix/CyIME-T9-fix-20261001-debug-{abi}.apk")
        binaries[abi] = dict(path=path.as_posix(), bytes=path.stat().st_size, sha256=digest(path))
    report = dict(date="2026-10-01", base_commit=original["reviewed_app_commit"],
                  device="isolated emulator-5556 / Android 16 / x86_64", physical_acceptance="未验收",
                  build=dict(java_heap_mib=2048, java_metaspace_mib=512, gradle_workers=2,
                             native_compile_jobs=2, native_link_jobs=1, abi_builds="serial"),
                  evidence_sha256=digest(sources / "t9_reported_cases.json"),
                  unit_tests=unit, native_tests=338, android_integration_tests=35,
                  runs=runs, stress_samples=len(timings), queue=queue, apks=binaries,
                  limitations=["原截图未记录完整按键；此处验证的是附件中明确标注的构造回放。",
                               "游什么泳反例验证完整词面仍可召回；未将其注释中的宿主前文注入引擎，未测试跨上屏文本排序。",
                               "口语留出集只测量排名，未用其结果调参；结构与编辑断言仍执行。",
                               "模拟器时间不等于手机延迟，主线程心跳不等于实际显示帧。",
                               "Debug APK：ARM64 约 540 MiB、x86_64 约 555 MiB，新增离线搭配库及量化句子模型。",
                               "内存文件为单点采样，不是峰值或硬性运行内存上限。",
                               "未连接实体手机；ARM64 包已构建，真机未验收。"])
    (args.output_dir / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    esc = html.escape
    sections = []
    for name, result in runs.items():
        rows = "".join(f"<tr><td>{esc(r['id'])}</td><td>{esc(r['variant'])}</td><td>{esc('/'.join(r['targets']))}</td>"
                       f"<td>{r['rank'] or '未召回'}</td><td>{esc(r['first'])}</td></tr>" for r in result["rows"])
        sections.append(f"<details {'open' if name == 'default' else ''}><summary>{name}：{result['count']} 组，"
                        f"首选 {result['top1']}，前 100 召回 {result['recall100']}</summary>"
                        f"<table><tr><th>用例</th><th>输入形式</th><th>目标</th><th>名次</th><th>实际首选</th></tr>{rows}</table></details>")
    page = f"""<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>CyIME T9 修复验证</title>
<style>body{{font:16px/1.6 system-ui,sans-serif;max-width:1100px;margin:40px auto;padding:0 20px;color:#182536}}
table{{border-collapse:collapse;width:100%;font-size:14px}}td,th{{text-align:left;border-bottom:1px solid #d7dfe7;padding:8px}}
th{{background:#edf3f7}}details{{margin:20px 0}}summary{{cursor:pointer;font-weight:600}}.status{{padding:16px;background:#edf6f0;border-left:4px solid #38865c}}</style>
<h1>CyIME T9 修复验证 · 2026-10-01</h1>
<p class="status">附件清单全部规定的召回、排名、成对比较通过。{unit['tests']} 项 JVM、338 项原生、35 项 Android 集成测试通过。<strong>真机未验收。</strong></p>
<p>原生候选先合并再限额，整词和组句使用同一评分；屏蔽保留存活候选顺序，可跨重启恢复。测试目标没有加入生产短语表或黑名单。</p>
<p>63 次连续退格 {queue['heldDeleteTotalMs']} ms，主线程最大心跳间隔 {queue['maxMainHeartbeatGapMs']} ms；
每 35 ms 输入一键的压力场景，队列完成延迟 P95 为 {queue['key_to_complete_p95_ms']:.1f} ms。共验证 {len(timings)} 个长串输入／退格状态。</p>
<p>构建限制：Java 堆 2 GiB、Metaspace 512 MiB、Gradle 工作线程 2、C++ 编译 2／链接 1；按 ABI 串行构建。</p>
<p><a href="verification.json">完整机器可读报告与 APK SHA-256</a> · <a href="t9-integration-final.log">Android 测试日志</a> · <a href="t9-native-final.log">原生测试日志</a></p>
{''.join(sections)}<h2>适用范围</h2><ul>{''.join('<li>'+esc(x)+'</li>' for x in report['limitations'])}</ul></html>"""
    (args.output_dir / "index.html").write_text(page, encoding="utf-8")
    print(json.dumps({name: {k: v for k, v in run.items() if k != "rows"} for name, run in runs.items()}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
