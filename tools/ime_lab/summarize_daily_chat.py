"""Validate complete ChatReplay JSONL evidence and export an offline review report.

Example:
  python tools/ime_lab/summarize_daily_chat.py --input replay.jsonl \
      --passes digits:continuous digits:paused --output-dir report

Exact target disagreement is a measurable ranking result, not a language-quality
verdict. Unfinished syllables are retained for human review but are not scored
against the already completed Chinese prefix.
"""

import argparse
import csv
import hashlib
import html
import json
from pathlib import Path
import sys


DEFAULT_FIXTURE = Path(__file__).parent / "fixtures" / "daily_chat_20261003.json"
EVENT_STAGES = {"key_immediate", "syllable_settled", "clause_settled", "pinyin_locked"}
CONTROL_STAGES = {"metadata", "deployment", "ready", "complete"}
MODES = {"continuous", "paused", "endpoints"}
VARIANTS = {"digits", "separated", "locked"}
KEYMAP = {letter: str(number) for number, letters in
          enumerate(("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"), 2)
          for letter in letters}


class EvidenceError(ValueError):
    """Evidence does not satisfy the declared replay contract."""


def require(condition, message):
    if not condition:
        raise EvidenceError(message)


def sentence_scoring_unavailable(status):
    # The production JNI returns e.g. grammar=ready;sentence=unavailable.
    # Grammar is lazily loaded, so unavailable grammar before a request is normal.
    return status == "unavailable" or (isinstance(status, str) and "sentence=unavailable" in status.split(";"))


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def load_fixture(path):
    fixture = read_json(path)
    cases = fixture["cases"]
    require(bool(cases), "Fixture has no cases")
    require(len({case["id"] for case in cases}) == len(cases), "Duplicate fixture case ID")
    for case in cases:
        require(len(case["text"]) == len(case["pinyin"]), f"{case['id']}: pinyin length mismatch")
        syllables = ["".join(KEYMAP[letter] for letter in syllable) for syllable in case["pinyin"]]
        require(all(syllables), f"{case['id']}: empty syllable")
        require("".join(syllables) == case["keys"], f"{case['id']}: incorrect T9 keys")
        if "syllable_keys" in case:
            require(syllables == case["syllable_keys"], f"{case['id']}: incorrect syllable keys")
        case["syllable_keys"] = syllables
    if "source_text" in fixture:
        for case in cases:
            require(fixture["source_text"][case["source_start"]:case["source_end"]] == case["text"],
                    f"{case['id']}: source offset mismatch")
    return fixture


def read_evidence(paths):
    records, sources = [], []
    for path in paths:
        lines = path.read_text(encoding="utf-8-sig").splitlines()
        require(bool(lines), f"Empty evidence file: {path}")
        data = []
        for line_number, line in enumerate(lines, 1):
            require(bool(line.strip()), f"{path}:{line_number}: blank evidence line")
            try:
                row = json.loads(line)
            except json.JSONDecodeError as error:
                raise EvidenceError(f"{path}:{line_number}: invalid JSON: {error}") from error
            require(isinstance(row, dict), f"{path}:{line_number}: record must be an object")
            require(row.get("stage") in EVENT_STAGES | CONTROL_STAGES,
                    f"{path}:{line_number}: unknown stage {row.get('stage')!r}")
            data.append(row)
        metadata = data[0]
        require(metadata.get("stage") == "metadata" and metadata.get("protocol") == 1,
                f"{path}: missing protocol-1 metadata")
        require(sum(row["stage"] == "metadata" for row in data) == 1,
                f"{path}: multiple metadata records")
        require(sum(row["stage"] == "ready" for row in data) == 1, f"{path}: missing/duplicate ready record")
        require(sum(row["stage"] == "complete" for row in data) == 1 and data[-1]["stage"] == "complete",
                f"{path}: replay has no unique final completion record")
        require(data[-1].get("records_before_complete") == len(data) - 1,
                f"{path}: completion record count mismatch")
        require(not sentence_scoring_unavailable(data[-1].get("scoring_status")),
                f"{path}: sentence scoring model unavailable; replay cannot validate the configured algorithm")
        require(metadata.get("boundary_limit", 0) >= 100,
                f"{path}: boundary_limit must be at least 100 for Top100 recall")
        require(metadata.get("row_limit", 0) > 0, f"{path}: missing candidate row limit")
        require(metadata.get("variant") in VARIANTS, f"{path}: unknown variant")
        require(bool(metadata.get("modes")) and set(metadata["modes"]) <= MODES,
                f"{path}: invalid declared modes")
        ready_index = next(index for index, row in enumerate(data) if row["stage"] == "ready")
        for index, row in enumerate(data):
            if row["stage"] not in EVENT_STAGES:
                continue
            require(index > ready_index, f"{path}:{index + 1}: candidate evidence before ready")
            require(row.get("variant") == metadata["variant"] and row.get("mode") in metadata["modes"],
                    f"{path}:{index + 1}: event differs from declared pass")
            limit = metadata["boundary_limit"] if row.get("syllable_boundary") else metadata["row_limit"]
            require(isinstance(row.get("rows"), list) and len(row["rows"]) <= limit,
                    f"{path}:{index + 1}: invalid candidate list/limit")
            records.append({**row, "source_file": str(path), "source_line": index + 1})
        sources.append({"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                        "records": len(data), "metadata": metadata,
                        "ready": data[ready_index], "complete": data[-1]})
    return records, sources


def parse_pass(value):
    pieces = value.split(":")
    require(len(pieces) == 2 and pieces[0] in VARIANTS and pieces[1] in MODES,
            f"Unknown pass {value!r}; use variant:mode, e.g. digits:paused")
    return pieces


def expected_events(cases, variant, mode):
    """Yield the exact ordered event identities emitted by ChatReplay protocol 1."""
    for case in cases:
        position = 0
        for char_index, syllable in enumerate(case["syllable_keys"], 1):
            for _ in syllable:
                position += 1
                if mode != "endpoints" or position == len(case["keys"]):
                    yield case["id"], position, "key_immediate"
            endpoint = char_index == len(case["text"])
            if variant == "locked":
                yield case["id"], position, "pinyin_locked"
            if mode == "paused" or endpoint:
                yield case["id"], position, "clause_settled" if endpoint else "syllable_settled"


def metrics(rows):
    result = {"count": len(rows)}
    for limit in (1, 5, 100):
        count = sum(0 < row["rank"] <= limit for row in rows)
        result[f"top{limit}"] = count
        result[f"top{limit}_percent"] = round(100 * count / len(rows), 2) if rows else None
    return result


def summarize(fixture, records, passes):
    require(len(set(passes)) == len(passes), "Duplicate requested pass")
    cases = fixture["cases"]
    by_id = {case["id"]: case for case in cases}
    summary, snapshots = {}, []
    expected_counts = {"clauses": len(cases), "characters": sum(len(case["text"]) for case in cases),
                       "digit_keys": sum(len(case["keys"]) for case in cases)}
    for pass_name in passes:
        variant, mode = parse_pass(pass_name)
        selected = [row for row in records if row["variant"] == variant and row["mode"] == mode]
        expected = list(expected_events(cases, variant, mode))
        actual = [(row.get("id"), len(row.get("keys", "")), row["stage"]) for row in selected]
        missing = sorted(set(expected) - set(actual))
        extra = sorted(set(actual) - set(expected), key=str)
        require(not missing and not extra and len(actual) == len(set(actual)),
                f"{pass_name}: incomplete/duplicate coverage: expected {len(expected)} snapshots, "
                f"observed {len(actual)}; missing={missing[:8]}, extra={extra[:8]}, "
                f"duplicates={len(actual) - len(set(actual))}")
        require(actual == expected, f"{pass_name}: replay snapshots are out of fixture/key order")
        checked, execution_issues = [], []
        for row in selected:
            case = by_id[row["id"]]
            position = len(row["keys"])
            prefix_lengths = []
            total = 0
            for syllable in case["syllable_keys"]:
                total += len(syllable)
                prefix_lengths.append(total)
            completed = sum(length <= position for length in prefix_lengths)
            boundary = position in prefix_lengths
            endpoint = position == len(case["keys"])
            expected_prefix = case["text"][:completed]
            identity = f"{pass_name}/{case['id']}/{position}/{row['stage']}"
            require(row["keys"] == case["keys"][:position], f"{identity}: incorrect key prefix")
            require(row.get("target") == case["text"] and row.get("expected_prefix") == expected_prefix,
                    f"{identity}: incorrect expected text")
            require(row.get("char_count") == completed and row.get("active_char") == completed + (not boundary),
                    f"{identity}: incorrect character counters")
            require(row.get("syllable_boundary") == boundary and row.get("clause_endpoint") == endpoint,
                    f"{identity}: incorrect boundary flags")
            sent = row.get("sent_keys")
            expected_sent = case["keys"][:position]
            if variant == "separated":
                expected_sent = ""
                consumed = 0
                for syllable in case["syllable_keys"]:
                    if consumed >= position:
                        break
                    if consumed:
                        expected_sent += "'"
                    expected_sent += syllable[:position - consumed]
                    consumed += len(syllable)
            require(sent == expected_sent, f"{identity}: unexpected sent keys")
            if "input" in row or "remaining_digits" in row:
                require(isinstance(row.get("input"), str) and isinstance(row.get("remaining_digits"), str),
                        f"{identity}: missing native input state")
                if variant == "digits" and (row["input"] != sent or row["remaining_digits"] != sent):
                    execution_issues.append({"snapshot": identity, "issue": "native_input_differs_from_sent_keys",
                                             "input": row["input"], "remaining_digits": row["remaining_digits"]})
            require(all(isinstance(candidate, dict) and isinstance(candidate.get("text"), str)
                        for candidate in row["rows"]), f"{identity}: invalid candidate text")
            texts = [candidate["text"] for candidate in row["rows"]]
            rank = next((index for index, text in enumerate(texts, 1) if text == expected_prefix), -1) if boundary else None
            require(row.get("target_prefix_rank") == rank, f"{identity}: reported rank differs from raw candidates")
            require(row.get("top_exact") is (boundary and rank == 1), f"{identity}: incorrect top_exact")
            require(isinstance(row.get("accepted"), bool), f"{identity}: missing accepted status")
            if not row["accepted"]:
                execution_issues.append({"snapshot": identity, "issue": "input_not_accepted"})
            if sentence_scoring_unavailable(row.get("scoring_status")):
                execution_issues.append({"snapshot": identity, "issue": "sentence_scoring_unavailable"})
            if row["stage"] in {"clause_settled", "syllable_settled"}:
                require(isinstance(row.get("settle"), dict) and isinstance(row["settle"].get("timeout"), bool),
                        f"{identity}: missing settlement evidence")
                if row["settle"]["timeout"]:
                    execution_issues.append({"snapshot": identity, "issue": "settlement_timeout"})
            checked.append({**row, "pass": pass_name, "role": case["role"], "is_body": case["is_body"],
                            "time": case.get("time"), "speaker": case.get("speaker"),
                            "key_index": position, "character": case["text"][row["active_char"] - 1],
                            "pinyin": case["pinyin"][row["active_char"] - 1],
                            "rank": rank, "first": texts[0] if texts else "",
                            "target_mismatch": boundary and rank != 1,
                            "language_quality": "not_assessed"})
        groups = {
            "immediate_characters": [row for row in checked if row["stage"] == "key_immediate" and row["syllable_boundary"]],
            "immediate_clauses": [row for row in checked if row["stage"] == "key_immediate" and row["clause_endpoint"]],
            "settled_characters": [row for row in checked if row["stage"] in {"syllable_settled", "clause_settled"}],
            "settled_clauses": [row for row in checked if row["stage"] == "clause_settled"],
        }
        if variant == "locked":
            groups["pinyin_locked_characters"] = [row for row in checked if row["stage"] == "pinyin_locked"]
        summary[pass_name] = {
            "coverage": {"status": "complete", "scope": "endpoints_only" if mode == "endpoints" else "all_keys_and_characters",
                         "expected_fixture": expected_counts,
                         "observed_key_snapshots": sum(row["stage"] == "key_immediate" for row in checked),
                         "observed_immediate_character_boundaries": len(groups["immediate_characters"]),
                         "observed_settled_character_boundaries": len(groups["settled_characters"]),
                         "observed_clause_endpoints": len(groups["settled_clauses"]),
                         "total_snapshots": len(checked)},
            "settled_characters_scope": "all_characters" if mode == "paused" else "clause_endpoints_only",
            "execution_issues": execution_issues,
            "metrics": {name: {"all": metrics(rows), "body": metrics([row for row in rows if row["is_body"]])}
                        for name, rows in groups.items()},
        }
        snapshots.extend(checked)
    return {"fixture_id": fixture.get("id"), "expected_counts": expected_counts, "passes": summary,
            "physical_acceptance": "未验收", "language_quality": "not_automatically_assessed",
            "metric_definition": "At a complete pinyin syllable boundary, exact full Chinese prefix equality; rank is 1-based, -1 means absent from captured candidates. Top100 counts only ranks 1–100.",
            "limitations": [
                "目标文字不一致不等于非正常语言；语言自然度须结合原始首选、输入进度和语境人工审阅。",
                "未完成音节的逐键首选保留供审阅，不对尚未输入完成的目标汉字评分。",
                "前缀指标是持续保留当前片段编码后的完整目标前缀命中率，不是逐字选词上屏命中率。",
                "片段边界清空组合，不提交正文；未覆盖用户词库学习、跨上屏语境、实际键盘显示和触控。",
                "连续模式只在片段末等待重排；暂停模式每完成一个字的拼音后等待重排。",
                "独立原生引擎回放不能替代当前源码及真机界面验收；真机未验收。",
            ]}, snapshots


def write_tsv(path, rows):
    fields = ["pass", "id", "role", "stage", "key_index", "keys", "sent_keys", "char_count", "active_char",
              "character", "pinyin", "syllable_boundary", "clause_endpoint", "target", "expected_prefix",
              "first", "rank", "target_mismatch", "language_quality", "process_ms", "inspect_ms", "wait_ms",
              "state_before_inspect", "state", "input", "remaining_digits", "source_file", "source_line", "raw_candidates"]
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fields, delimiter="\t", extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({**row, "raw_candidates": json.dumps(row["rows"], ensure_ascii=False, separators=(",", ":"))})


def render_html(report, snapshots):
    # Escape '<' before putting JSON in a script element, including candidate text.
    payload = json.dumps({"report": report, "snapshots": snapshots}, ensure_ascii=False, separators=(",", ":")).replace("<", "\\u003c")
    metrics_rows = []
    for pass_name, result in report["passes"].items():
        for phase, scopes in result["metrics"].items():
            for scope, value in scopes.items():
                cells = [pass_name, phase, scope, str(value["count"])]
                cells.extend(f"{value[f'top{limit}']} / {value['count']} ({value[f'top{limit}_percent']}%)" for limit in (1, 5, 100))
                metrics_rows.append("<tr>" + "".join(f"<td>{html.escape(cell)}</td>" for cell in cells) + "</tr>")
    issues = sum(len(result["execution_issues"]) for result in report["passes"].values())
    status = f"覆盖验证通过；执行异常 {issues} 项。" if issues else "请求的各轮回放均已通过完整性验证。"
    shell = r'''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>CyIME 九键聊天逐键审阅</title>
<style>body{font:15px/1.6 system-ui,sans-serif;margin:28px auto;padding:0 22px;max-width:1500px;color:#182536;background:#fafbfd}
h1{font-size:26px}h2{font-size:20px;margin-top:28px}.notice{background:#edf3fb;padding:14px;border-left:4px solid #4075a8}
table{border-collapse:collapse;width:100%;font-size:13px;background:white}th,td{text-align:left;vertical-align:top;padding:8px;border-bottom:1px solid #dbe1e8}
th{background:#edf1f6}td:nth-child(4){overflow-wrap:anywhere}.scroll{overflow:auto}label{display:inline-block;margin:5px 12px 5px 0}
select,input,button{font:inherit;padding:5px}input[type=search]{width:250px}.filters{position:sticky;top:0;background:#fafbfd;border-bottom:1px solid #dbe1e8;padding:10px 0;z-index:1}
pre{white-space:pre-wrap;word-break:break-word;font-size:12px;max-height:450px;overflow:auto;background:#f3f5f8;padding:10px}
.mismatch{color:#985316}.muted{color:#596879}summary,button{cursor:pointer}#count{padding:8px 0}a{color:#285f91}</style>
<h1>CyIME 九键聊天逐键审阅</h1><p class="notice">__STATUS__ <strong>真机未验收。</strong><br>
“目标不一致”只表示当前首选不等于预期原文，不能据此判为“非正常语言”。本页保留每个按键和每个字的原始候选供人工审查。</p>
<p>每个字的指标按该字完整拼音输入结束时的<strong>完整目标前缀</strong>精确匹配计算。中途未完成的拼音不评分；连续模式的等待后前缀只有片段末采样。</p>
<details><summary>测试适用范围与限制</summary><ul>__LIMITATIONS__</ul></details>
<h2>精确命中率</h2><div class="scroll"><table><thead><tr><th>轮次</th><th>阶段</th><th>范围</th><th>样本数</th><th>Top1</th><th>Top5</th><th>Top100</th></tr></thead><tbody>__METRICS__</tbody></table></div>
<h2>逐键与逐字审查</h2><div class="filters">
<label>轮次 <select id="pass"><option value="">全部</option></select></label>
<label>类别 <select id="role"><option value="">全部</option><option>body</option><option>speaker</option><option>heading</option><option>introduction</option></select></label>
<label>阶段 <select id="stage"><option value="">全部</option><option>key_immediate</option><option>syllable_settled</option><option>clause_settled</option><option>pinyin_locked</option></select></label>
<label>范围 <select id="scope"><option value="all">所有按键及等待后快照</option><option value="characters">完整字音节</option><option value="clauses">片段末</option><option value="partial">未完成音节</option></select></label>
<label><input id="mismatch" type="checkbox">仅目标不一致（完整音节）</label>
<label>搜索 <input id="search" type="search" placeholder="编号、原文、首选或输入数字"></label>
<div><button id="prev">上一页</button> <button id="next">下一页</button> <span id="count" aria-live="polite"></span></div></div>
<div class="scroll"><table><thead><tr><th>轮次／片段</th><th>输入进度</th><th>预期</th><th>实际首选</th><th>目标名次</th><th>原始候选和状态</th></tr></thead><tbody id="rows"></tbody></table></div>
<p class="muted">每页 100 条；所有数据均嵌入本文件，离线可筛选。逐键、逐字与逐片段 TSV 及完整 JSONL 位于同目录。未对语言自然度作自动裁决。</p>
<script id="evidence" type="application/json">__PAYLOAD__</script>
<script>
const data=JSON.parse(document.getElementById('evidence').textContent), all=data.snapshots;
const $=id=>document.getElementById(id); let page=0, filtered=[];
for(const name of Object.keys(data.report.passes)){let option=document.createElement('option');option.value=name;option.textContent=name;$('pass').append(option)}
function cell(tr,text,className){let td=document.createElement('td');td.textContent=text;if(className)td.className=className;tr.append(td);return td}
function render(){const query=$('search').value.trim().toLowerCase(),scope=$('scope').value;
filtered=all.filter(r=>(!$('pass').value||r.pass===$('pass').value)&&(!$('role').value||r.role===$('role').value)&&(!$('stage').value||r.stage===$('stage').value)&&
(scope==='all'||scope==='characters'&&r.syllable_boundary||scope==='clauses'&&r.clause_endpoint||scope==='partial'&&!r.syllable_boundary)&&
(!$('mismatch').checked||r.target_mismatch)&&(!query||[r.id,r.target,r.expected_prefix,r.first,r.keys].some(v=>String(v).toLowerCase().includes(query))));
page=Math.min(page,Math.max(0,Math.ceil(filtered.length/100)-1));$('rows').replaceChildren();
for(const r of filtered.slice(page*100,(page+1)*100)){let tr=document.createElement('tr');
cell(tr,r.pass+'\n'+r.id+' · '+r.role+'\n'+r.stage);cell(tr,'第 '+r.key_index+' 键 · 第 '+r.active_char+' 字 '+r.character+' ('+r.pinyin+')\n'+r.keys+(r.syllable_boundary?'\n音节完成':'\n音节未完成'));
cell(tr,r.target+'\n当前完整前缀：'+r.expected_prefix);cell(tr,r.first||'（无候选）',r.target_mismatch?'mismatch':'');cell(tr,r.rank===null?'不评分':r.rank<0?'未召回':String(r.rank));
let td=cell(tr,''),details=document.createElement('details'),summary=document.createElement('summary'),pre=document.createElement('pre');summary.textContent=r.rows.length+' 个候选 · process '+r.process_ms+' ms';
details.append(summary,pre);details.addEventListener('toggle',()=>{if(details.open&&!pre.textContent)pre.textContent=JSON.stringify(r,null,2)});td.append(details);$('rows').append(tr)}
$('count').textContent=' '+filtered.length+' 条， 第 '+(filtered.length?page+1:0)+' / '+Math.ceil(filtered.length/100)+' 页';$('prev').disabled=page===0;$('next').disabled=(page+1)*100>=filtered.length;}
for(const id of ['pass','role','stage','scope','mismatch','search'])$(id).addEventListener(id==='search'?'input':'change',()=>{page=0;render()});
$('prev').onclick=()=>{page--;render()};$('next').onclick=()=>{page++;render()};render();
</script></html>'''
    return shell.replace("__STATUS__", html.escape(status)).replace("__LIMITATIONS__", "".join(
        "<li>" + html.escape(item) + "</li>" for item in report["limitations"])).replace(
        "__METRICS__", "".join(metrics_rows)).replace("__PAYLOAD__", payload)


def export_report(output_dir, report, snapshots):
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "summary.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (output_dir / "snapshots.jsonl").open("w", encoding="utf-8") as stream:
        for row in snapshots:
            stream.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
    write_tsv(output_dir / "per-key.tsv", [row for row in snapshots if row["stage"] == "key_immediate"])
    write_tsv(output_dir / "per-character.tsv", [row for row in snapshots if row["syllable_boundary"]])
    write_tsv(output_dir / "per-clause.tsv", [row for row in snapshots if row["clause_endpoint"]])
    (output_dir / "index.html").write_text(render_html(report, snapshots), encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, default=DEFAULT_FIXTURE)
    parser.add_argument("--input", type=Path, action="append", required=True, help="Complete ChatReplay JSONL; repeat for additional files")
    parser.add_argument("--passes", nargs="+", required=True, help="Explicit required variant:mode passes; prevents silently missing entire runs")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    try:
        fixture = load_fixture(args.fixture)
        records, sources = read_evidence(args.input)
        report, snapshots = summarize(fixture, records, args.passes)
        report["fixture_path"] = str(args.fixture)
        report["fixture_sha256"] = hashlib.sha256(args.fixture.read_bytes()).hexdigest()
        report["sources"] = sources
        issues = sum(len(result["execution_issues"]) for result in report["passes"].values())
        report["validation"] = {"coverage": "complete", "execution": "errors" if issues else "complete", "execution_issue_count": issues}
        export_report(args.output_dir, report, snapshots)
        validation = {"status": "complete_with_execution_errors" if issues else "complete", "passes": report["passes"]}
        exit_code = 1 if issues else 0
    except (EvidenceError, OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        validation = {"status": "failed", "error": str(error), "requested_passes": args.passes}
        exit_code = 2
        print(f"Evidence validation failed: {error}", file=sys.stderr)
        # A failed rerun must not leave an older successful landing page visible.
        (args.output_dir / "summary.json").write_text(json.dumps(validation, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (args.output_dir / "index.html").write_text(
            '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>回放证据验证失败</title>'
            '<h1>回放证据验证失败</h1><p>当前证据不满足请求的完整覆盖要求，未生成命中率报告。</p><pre>'
            + html.escape(str(error)) + '</pre><p>同目录之前生成的明细文件不属于本次成功报告。</p></html>',
            encoding="utf-8")
    (args.output_dir / "validation.json").write_text(json.dumps(validation, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(validation, ensure_ascii=False, indent=2))
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
