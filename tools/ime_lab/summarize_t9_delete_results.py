"""Verify the deletion fix against the previous APK and export reproducible evidence."""
import hashlib
import html
import json
import math
from pathlib import Path
import shutil

ROOT = Path('.gradle')
OUT = Path('docs/bug/t9-delete-fix-2026-10-02')


def read(name):
    return json.loads((ROOT / name).read_text(encoding='utf-8'))


def stats(values):
    values = sorted(values)
    return {name: values[math.ceil(len(values) * p) - 1]
            for name, p in [('p50_ms', .5), ('p95_ms', .95), ('max_ms', 1)]}


def main():
    before = read('t9-delete-before.json')
    after = read('t9-delete-final.json')
    assert len(before) == len(after) == 504
    assert read('t9-delete-before-candidates.json') == read('t9-delete-final-candidates.json')
    timings = {}
    for case in ('repeated7', 'random', 'sentence', 'separators'):
        timings[case] = {label: stats([r['ms'] for r in data if r['case'] == case and r['action'] == 'delete'])
                         for label, data in [('before', before), ('after', after)]}
        assert timings[case]['after']['p95_ms'] < 25
        assert timings[case]['after']['max_ms'] < 70
    queue = read('t9-delete-queue-final.json')
    assert len(queue['deleteToEngineCompleteMs']) == 63
    assert stats(queue['deleteToEngineCompleteMs'])['p95_ms'] < 35
    assert queue['heldDeleteTotalMs'] < 62 * 70 + 500
    log = (ROOT / 't9-delete-integration.log').read_text(encoding='utf-8')
    assert 'OK (35 tests)' in log
    native = (ROOT / 't9-delete-native.log').read_text(encoding='utf-8')
    assert '[  PASSED  ] 342 tests.' in native
    ranks = {}
    for label, count in [('default', 47), ('learned', 47), ('lock-first', 21), ('lock-all', 21)]:
        assert 'OK (1 test)' in (ROOT / f't9-delete-{label}.log').read_text(encoding='utf-8')
        rows = [json.loads(line) for line in (ROOT / f't9-delete-{label}.jsonl').read_text(encoding='utf-8').splitlines()]
        # The previous learned run could fall back to a shared user DB. Rerun
        # the old APK with the corrected fixture for a fair learned comparison.
        old_name = 't9-delete-before-learned.jsonl' if label == 'learned' else f't9-acceptance-{label}.jsonl'
        old = [json.loads(line) for line in (ROOT / old_name).read_text(encoding='utf-8').splitlines()]
        assert len(rows) == len(old) == count
        signature = lambda records: [(r['id'], r['variant'], r['target_rank'], r['candidates']) for r in records]
        assert signature(rows) == signature(old), label
        ranks[label] = {'count': count, 'all_native_candidates_unchanged': True}
    binaries = {}
    for abi in ('arm64-v8a', 'x86_64'):
        apk = Path(f'app/build/outputs/t9-fix/CyIME-T9-delete-fix-20261002-debug-{abi}.apk')
        with apk.open('rb') as stream:
            sha = hashlib.file_digest(stream, 'sha256').hexdigest()
        binaries[abi] = dict(path=apk.as_posix(), bytes=apk.stat().st_size, sha256=sha)
    report = dict(
        date='2026-10-02', physical_acceptance='未验收',
        device='isolated Android 16 x86_64 emulator-5556; 2 CPU cores / 2048 MiB; serial runs',
        algorithm='Joint decoder: reuse versioned detached decoded prefixes; average O(n) key lookup plus O(k*n) candidate copying on a hit. Syllable graph and initial phrase lookup still run; cold/evicted prefixes still use the full decoder.',
        limits=dict(decoded_cache_accounted_bytes=8*1024*1024, decoded_cache_entries=128,
                    java_heap_mib=2048, java_metaspace_mib=512, gradle_workers=2,
                    native_compile_jobs=2, native_link_jobs=1, abi_builds='serial'),
        timings=timings, queue=dict(held_delete_ms=queue['heldDeleteTotalMs'],
            delete=stats(queue['deleteToEngineCompleteMs']), key=stats(queue['keyToEngineCompleteMs']),
            max_main_gap_ms=queue['maxMainHeartbeatGapMs']),
        baseline_queue='Previous APK exceeded the unchanged 10 s held-delete timeout in the same emulator. No completion time claimed.',
        native_tests=342, android_integration_tests=35, additional_matrix_tests=4,
        identical_native_stress_snapshots=504, regressions=ranks, apks=binaries,
        limitations=['真机未验收。模拟器耗时不代表实体手机；主线程心跳不等于显示帧。',
                     '首次输入、粘贴形成的未缓存前缀及缓存失效后仍需完整解码；本次不声称消除首次输入排队。',
                     '8 MiB 是保守内存记账预算，约束解码缓存；不是整个输入法或 JVM 的内存上限。'])
    OUT.mkdir(parents=True, exist_ok=True)
    names = ['t9-delete-before.json', 't9-delete-final.json', 't9-delete-queue-final.json',
             't9-delete-before-benchmark.log', 't9-delete-integration.log', 't9-delete-native.log',
             't9-delete-default.jsonl', 't9-delete-default.log', 't9-delete-apk-signature.log',
             't9-delete-before-learned.jsonl', 't9-delete-before-learned.log', 't9-delete-installed-smoke.log']
    for label in ('learned', 'lock-first', 'lock-all'):
        names += [f't9-delete-{label}.log', f't9-delete-{label}.jsonl']
    for name in names:
        shutil.copyfile(ROOT / name, OUT / name)
    # Retain the exact identity comparison without duplicating two 3.8 MB files.
    identities = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
                  for name in ('t9-delete-before-candidates.json', 't9-delete-final-candidates.json')}
    report['candidate_evidence_sha256'] = identities
    shutil.copyfile(ROOT / 't9-delete-final-candidates.json', OUT / 't9-delete-final-candidates.json')
    (OUT / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    rows = ''.join(f'<tr><td>{case}</td><td>{t["before"]["p95_ms"]:.2f}</td>'
                   f'<td>{t["after"]["p95_ms"]:.2f}</td><td>{t["after"]["max_ms"]:.2f}</td></tr>'
                   for case, t in timings.items())
    page = f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>T9 退格性能修复</title>
<style>body{{font:16px/1.7 system-ui;max-width:1000px;margin:40px auto;padding:0 20px;color:#172638}}
table{{border-collapse:collapse;width:100%}}th,td{{text-align:left;padding:10px;border-bottom:1px solid #ddd}}</style>
<h1>T9 退格性能修复 · 2026-10-02</h1><p><strong>真机未验收。</strong>相同模拟器顺序运行旧包与修复包，504 个原生候选快照完全一致。</p>
<table><tr><th>63 键场景</th><th>修复前退格 P95 / ms</th><th>修复后退格 P95 / ms</th><th>修复后最大 / ms</th></tr>{rows}</table>
<p>长按连续退格 63 次：{queue['heldDeleteTotalMs']} ms（每 70 ms 触发一次）；旧包在同机对照中超过 10 秒门槛。
首次输入的队列完成延迟 P95 仍为 {report['queue']['key']['p95_ms']:.1f} ms，该项没有作为本次已修复指标。</p>
<p>342 项原生、35 项 Android 集成、4 组额外回放测试通过。默认 47、学习后 47、首音节锁定 21、全部拼音锁定 21 组的全部原生候选与修复前一致。</p>
<p>每个前缀保存可独立复制的解码结果，命中时联合解码阶段平均只需 O(n) 查找与 O(k·n) 恢复候选；音节图和初始词组查询仍执行。学习、删词、回滚、上下文、词典或配置变化使缓存失效。
缓存限制 128 项、8 MiB 记账预算；构建 Java 堆 2 GiB、两个工作线程，按 ABI 串行。</p>
<ul>{''.join('<li>'+html.escape(s)+'</li>' for s in report['limitations'])}</ul>
<p><a href="verification.json">完整证据、计时及 APK SHA-256</a> · <a href="t9-delete-integration.log">集成测试</a> · <a href="t9-delete-native.log">原生测试</a></p></html>'''
    (OUT / 'index.html').write_text(page, encoding='utf-8')
    print(json.dumps({'timings': timings, 'queue': report['queue'], 'regressions': ranks}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
