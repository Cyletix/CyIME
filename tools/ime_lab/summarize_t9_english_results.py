"""Export T9 English regression evidence from the dedicated Android test device."""
import argparse
import hashlib
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/bug/t9-english-fix-2026-10-02"
parser = argparse.ArgumentParser()
parser.add_argument("--serial", default="emulator-5556")
parser.add_argument("--offline", action="store_true", help="Regenerate from already exported device evidence")
args = parser.parse_args()
adb = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe"
OUT.mkdir(parents=True, exist_ok=True)

def pull_private(name):
    data = (OUT / name).read_bytes() if args.offline else subprocess.check_output([str(adb), "-s", args.serial, "exec-out", "run-as", "com.cyletix.cyime", "cat", f"files/ime-lab/{name}"])
    (OUT / name).write_bytes(data)
    return [json.loads(line) for line in data.decode().splitlines()]

before = pull_private("t9-english-before.jsonl")
after = pull_private("t9-english-after.jsonl")
pull_private("t9-reported-results.jsonl")
for name in ("t9-long-english-final.json", "t9-long-english-final-candidates.json", "t9-long-queue.json", "t9-english-ime.png"):
    if not args.offline:
        subprocess.run([str(adb), "-s", args.serial, "pull", f"/sdcard/Android/data/com.cyletix.cyime/files/{name}", str(OUT / name)], check=True, capture_output=True)

logs = {"integration": "t9-english-final-integration.log", "native": "t9-english-native-tests.log"}
for name in logs.values():
    shutil.copyfile(ROOT / ".gradle" / name, OUT / name)
for name in ("t9-english-integration-timing-retry.log", "t9-english-held-delete-isolated.log", "t9-english-held-delete-isolated.json"):
    shutil.copyfile(ROOT / ".gradle" / name, OUT / name)
assert "OK (37 tests)" in (OUT / logs["integration"]).read_text(encoding="utf-8-sig")
assert "[  PASSED  ] 343 tests." in (OUT / logs["native"]).read_text(encoding="utf-8-sig")

def entries(text):
    return [row.split("\t") for row in text.split("...\n", 1)[1].splitlines() if "\t" in row]

old_apk = ROOT / "app/build/outputs/t9-fix/CyIME-T9-delete-fix-20261002-debug-x86_64.apk"
with zipfile.ZipFile(old_apk) as archive:
    old_entries = entries(archive.read("assets/rime_ice/cyime_t9_english.dict.yaml").decode())
new_entries = entries((ROOT / "app/build/generated/chinese-assets/rime_ice/cyime_t9_english.dict.yaml").read_text(encoding="utf-8"))
old_keys = {tuple(row[:2]) for row in old_entries}
new_keys = {tuple(row[:2]) for row in new_entries}
assert not old_keys - new_keys
assert len(new_entries) == len(new_keys)
assert all(re.fullmatch(r"[0-9]{2,64}", row[1]) for row in new_entries)
assert all(row["rank"] > 0 for row in after)

apks = []
for abi in ("arm64-v8a", "x86_64"):
    apk = ROOT / f"app/build/outputs/t9-fix/CyIME-T9-english-fix-20261002-debug-{abi}.apk"
    with apk.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    with zipfile.ZipFile(apk) as archive:
        assert {name.split("/")[1] for name in archive.namelist() if name.startswith("lib/")} == {abi}
        assert entries(archive.read("assets/rime_ice/cyime_t9_english.dict.yaml").decode()) == new_entries
    apks.append(dict(abi=abi, path=str(apk), bytes=apk.stat().st_size, sha256=digest))

samples = json.loads((OUT / "t9-long-english-final.json").read_text())
perf = []
for case in dict.fromkeys(row["case"] for row in samples):
    times = sorted(row["ms"] for row in samples if row["case"] == case and row["action"] == "delete")
    perf.append(dict(case=case, count=len(times), p95_ms=times[int(len(times) * .95)], max_ms=max(times)))
summary = dict(old_index_entries=len(old_entries), index_entries=len(new_entries), unique_texts=len({row[0] for row in new_entries}),
               lost_old_entries=len(old_keys-new_keys), recall_cases=sum(row["id"] == "recall" for row in after),
               native_tests=343, android_tests=37, physical_device_acceptance="未验收", deletion=perf, apks=apks)
isolated = json.loads((OUT / "t9-english-held-delete-isolated.json").read_text())
isolated_times = sorted(isolated["deleteToEngineCompleteMs"])
final_queue = json.loads((OUT / "t9-long-queue.json").read_text())
final_times = sorted(final_queue["deleteToEngineCompleteMs"])
summary["timing_retry"] = dict(first_batch_held_total_ms=4866, limit_ms=4840,
    isolated_total_ms=isolated["heldDeleteTotalMs"], isolated_delete_p95_ms=isolated_times[int(len(isolated_times) * .95)],
    final_total_ms=final_queue["heldDeleteTotalMs"], final_delete_p95_ms=final_times[int(len(final_times) * .95)],
    note="One whole-run timing threshold failure; original limits retained. Repeated in a fresh process and then ran engine tests before UI tests.")
(OUT / "verification.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

baseline = {row["target"]: row["rank"] for row in before if row["id"] == "recall"}
recall_rows = "".join(f"<tr><td>{html.escape(row['target'])}</td><td>{row['keys']}</td><td>{baseline.get(row['target'], '未测') or '缺失'}</td><td>{row['rank']}</td></tr>" for row in after if row["id"] == "recall")
learning_rows = "".join(f"<tr><td>{html.escape(row['target'])}</td><td>{html.escape(row['id'])}</td><td>{row['rank']}</td></tr>" for row in after if row["id"] != "recall")
perf_rows = "".join(f"<tr><td>{row['case']}</td><td>{row['count']}</td><td>{row['p95_ms']:.2f}</td><td>{row['max_ms']:.2f}</td></tr>" for row in perf)
apk_rows = "".join(f"<p>{a['abi']} · {a['bytes']:,} bytes<br><code>{a['sha256']}</code></p>" for a in apks)
document = f"""<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>T9 英文召回与学习回归 · 2026-10-02</title><style>
body{{font:16px/1.7 system-ui,sans-serif;max-width:1050px;margin:40px auto;padding:0 24px;color:#203047;background:#f4f7fc}}
h1,h2{{line-height:1.3}}section{{background:white;padding:24px;margin:20px 0;border:1px solid #dae2ed;border-radius:12px}}
table{{border-collapse:collapse;width:100%}}th,td{{text-align:left;padding:8px 12px;border-bottom:1px solid #e6edf4}}th{{background:#eef4fb}}code{{word-break:break-all;font-size:13px}}a{{color:#165ea6}}.status{{color:#176646;font-weight:650}}
</style><h1>T9 英文召回与学习回归</h1><p>2026-10-02 · Android 16 x86_64 专用模拟器 · 真机未验收</p>
<section><h2>结果</h2><p class="status">343 项原生测试、37 项 Android 集成测试通过。</p>
<p>旧包 steam 连续选用 5 次仍为第 22 位；修复后为 22 → 13 → 3 → 1，重启仍为第 1。Dota2 从缺失变为可召回，选用后升至第 1，重启保留。</p>
<p>词库由 {len(old_entries):,} 条扩展到 {len(new_entries):,} 条编码映射，覆盖 {summary['unique_texts']:,} 个不同文本，旧编码入口丢失数为 0。保留上游别名、大小写、空格及标点。没有按 steam、Dota2 等词名写特例。</p></section>
<section><h2>修复机制与边界</h2><p>空注释的英文候选经过原生选词，保存准确的英文词库编码；宿主接受上屏后才学习。中文连续片段仍按原拼音码学习，中英文编码分开写入。同步共享词库的 tick，并把已学习英文的整词分数与中文完整匹配基准对齐，保留原生频率与时间衰减。</p>
<p>字母词的数字后缀按需生成：最多 4 次精确前缀查询，每次最多 16 个基词，不扫描整份词库。支持 1–4 位后缀；不保证任意长度或词库外的任意生词。中文九键的 1 分词、0 直接上屏操作保持原语义；本次混输回归覆盖普通九键 2–9 输入路径。</p>
<p>上屏拒绝、文本不符、过期选择、清空后的旧记录均不会误学习；中英混合提交、重启持久化、候选完整消费、回删后重打均纳入测试。真实键盘测试覆盖 steam、Dota2、CS2 的点击、InputConnection 上屏和再次召回。</p></section>
<section><h2>召回检查（排名从 1 开始）</h2><table><tr><th>候选</th><th>输入键码</th><th>旧包</th><th>修复包首次输入</th></tr>{recall_rows}</table></section>
<section><h2>学习与重启</h2><table><tr><th>候选</th><th>阶段</th><th>排名</th></tr>{learning_rows}</table></section>
<section><h2>删除性能</h2><p>每组输入 63 键后逐次回删，校验原生候选文本、类型、质量、码及范围与相同输入前缀一致；另测 63 次、70 ms 间隔的长按删除。</p><table><tr><th>序列</th><th>删除次数</th><th>P95 / ms</th><th>最大 / ms</th></tr>{perf_rows}</table></section>
<section><h2>计时复验记录</h2><p>一轮先运行真实键盘 UI 的混合测试中，长按删除总耗时为 4866 ms，比 4840 ms 阈值多 26 ms；单次删除最大约 16.3 ms。保留原阈值，在新进程独立复验：总耗时 {isolated['heldDeleteTotalMs']} ms，删除 P95 {summary['timing_retry']['isolated_delete_p95_ms']:.2f} ms。随后按引擎性能在先、真实键盘 UI 在后的顺序完整重跑。未把首次超阈值记录删除或算作通过。</p><p><a href="t9-english-integration-timing-retry.log">首次计时失败日志</a> · <a href="t9-english-held-delete-isolated.json">独立复验数据</a></p><p>以上是模拟器引擎和命令队列测量，不能代替手机触感验收；长串首次输入的排队延迟仍属于此前记录的待优化项。</p></section>
<section><h2>最终长按测量的余量</h2><p>完整重跑的 63 次长按删除总耗时为 {final_queue['heldDeleteTotalMs']} ms，单次删除 P95 为 {summary['timing_retry']['final_delete_p95_ms']:.2f} ms。虽然通过原阈值，但总耗时距离 4840 ms 仅剩 {4840-final_queue['heldDeleteTotalMs']} ms，模拟器计时仍有波动，需要真机确认持续长按体验。</p></section>
<section><h2>构建与复验</h2><p>Gradle 堆上限 2 GiB，最多 2 个 worker，Kotlin 进程内编译；两种 ABI 顺序打包。x86_64 已通过 adb install -r 覆盖安装；没有连接实体手机。未提交 Git。</p><p>触屏 UI 测试要求 Android 配置为 NOKEYS。此模拟器虽然配置 hw.keyboard=no，仍暴露 AT 虚拟键盘，已在专用测试环境临时解绑以验证完整软键盘。测试退出时恢复先前默认输入法，避免污染后续引擎测试。</p>{apk_rows}</section>
<section><h2>原始证据</h2><p><a href="verification.json">验证摘要</a> · <a href="t9-english-before.jsonl">旧包候选</a> · <a href="t9-english-after.jsonl">修复包候选</a> · <a href="t9-reported-results.jsonl">原问题清单</a> · <a href="t9-long-queue.json">长按删除</a> · <a href="t9-long-english-final-candidates.json">逐前缀候选</a> · <a href="{logs['integration']}">Android 测试日志</a> · <a href="{logs['native']}">原生测试日志</a></p></section></html>"""
(OUT / "index.html").write_text(document, encoding="utf-8")
print(json.dumps(summary, ensure_ascii=False, indent=2))
