"""One board for every candidate-quality result: rank-weighted score first, top-1 beside it.

Each finished run is registered as a directory under the results root (default D:/CyIME-Data/eval/results):

  <root>/<run-id>/run.json      what was measured (fields below)
  <root>/<run-id>/<files>       rank_score.py inputs, copied in by `add`

run.json: id, section (row group, e.g. "电脑 · 26 键引擎"), variant (row name), where (电脑 / 平板 / 手机),
date, engine, dict_md5 (first 8 hex of cn_dicts/base.dict.yaml), model (file + md5), source (doc or thread),
notes, order (sort key inside a section), sets: [{column, file, group?}], perf: {p95_ms|pss_mb|model_mb: {value, where, note}}.
`column` is one of COLUMNS below; `group` filters the file to one rank_score group ("26 public jianpin/sms").

  eval_board.py add RUN_ID --section S --variant V --where W [--date D --engine E --dict-md5 M --model F
                  --source DOC --notes N] --set COLUMN=FILE[@GROUP] ... [--perf p95_ms=22@电脑:note ...]
                  [--score COLUMN=SCORE[/TOP1][@N] ...]   summary numbers only (marked † on the board)
  eval_board.py build [--out board.md]        regenerate the board from every run.json (+ <root>/../notes.md)
"""

import argparse
import json
import shutil
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import rank_score  # noqa: E402

ROOT = Path("D:/CyIME-Data/eval/results")

# column id: (table, header, expected n, use occurrence weights)
COLUMNS = {
    "dev.full26": ("实机对比题（每项 150，短信组，键中心点按）", "26 键全拼", 150, False),
    "dev.jp26": ("实机对比题（每项 150，短信组，键中心点按）", "26 键简拼", 150, False),
    "dev.jp26.occ": ("实机对比题（每项 150，短信组，键中心点按）", "简拼·按次数", 150, True),
    "dev.t9": ("实机对比题（每项 150，短信组，键中心点按）", "九键整句", 150, False),
    "dev2.full26": ("实机对比题第二批（2026-10-23）", "26 键全拼 693", 693, False),
    "dev2.jp26": ("实机对比题第二批（2026-10-23）", "26 键简拼 754", 754, False),
    "dev2.t9": ("实机对比题第二批（2026-10-23）", "九键整句 540", 540, False),
    "hard.full26": ("实机难题（2026-10-23）", "全拼长句 400", 400, False),
    "hard.jp26": ("实机难题（2026-10-23）", "简拼 3～6 字 400", 400, False),
    "hard.t9": ("实机难题（2026-10-23）", "九键长句 300", 300, False),
    "hard.mixed": ("实机难题（2026-10-23）", "全拼简拼混打 300", 300, False),
    "hard.word": ("实机难题（2026-10-23）", "长词 200", 200, False),
    "hard.typo": ("实机难题（2026-10-23）", "邻键打错 200", 200, False),
    "cmp.full26": ("同一批对比题，电脑引擎", "26 键全拼", 150, False),
    "cmp.jp26": ("同一批对比题，电脑引擎", "26 键简拼", 150, False),
    "cmp.t9": ("同一批对比题，电脑引擎", "九键整句", 150, False),
    "pub.sms.jp": ("公开测试集", "短信 简拼 600", 600, False),
    "pub.sms.jp.occ": ("公开测试集", "短信 简拼·按次数", 600, True),
    "pub.lccc.jp": ("公开测试集", "LCCC 简拼 600", 600, False),
    "pub.sms.sent": ("公开测试集", "短信 整句 500", 500, False),
    "pub.lccc.sent": ("公开测试集", "LCCC 整句 500*", 500, False),
    "pub.daily.sent": ("公开测试集", "日常 整句 178", 178, False),
    "pub.choice": ("公开测试集", "同音词选择 1100", 1100, False),
    "pub.all.jp": ("公开测试集", "简拼 全部 1234", 1234, False),
    "pub.all.jp.occ": ("公开测试集", "简拼 全部·按次数", 1234, True),
    "pub.all.sent": ("公开测试集", "整句 全部 1180", 1180, False),
    "own.corpus": ("自建集", "语料常用语首字母 450", 450, False),
    "own.base": ("自建集", "base 书面词首字母 300", 300, False),
    "own.daily.jp": ("自建集", "日常短句首字母 74", 74, False),
    "own.daily.full": ("自建集", "日常短句全拼 120", 120, False),
    "own.chat.full": ("自建集", "自建对话全拼 88", 88, False),
    "own.chat.jp": ("自建集", "自建对话首字母 51", 51, False),
    "ho.jp": ("LCCC-large 留出终评（训练未见）", "短语 388", 388, False),
    "ho.jp.occ": ("LCCC-large 留出终评（训练未见）", "短语·按次数", 388, True),
    "ho.sent": ("LCCC-large 留出终评（训练未见）", "整句 600*", 600, False),
}
PERF = {"p95_ms": "逐键 p95（ms）", "pss_mb": "常驻内存 PSS（MB）", "model_mb": "模型体积（MB）"}
NOTES = {
    "pub.lccc.sent": "v3 的训练文本含 LCCC-base，这一列对 v3 偏高。",
    "ho.sent": "600 句里 433 句在 v3 训练文本中出现过，对 v3 偏高；公平子集见词组模型文档。",
}


def score_cell(run_dir, entry, column):
    if "score" in entry:  # registered from another machine's summary, no per-item file here
        return dict(score=entry["score"], top1=entry.get("top1"), n=entry.get("n", COLUMNS[column][2]), summary_only=True)
    items = rank_score.load(run_dir / entry["file"], run_dir / entry["targets"] if entry.get("targets") else None)
    if entry.get("group"):
        items = {k: v for k, v in items.items() if v["group"] == entry["group"]}
    if not items:
        return None
    s = rank_score.stats(items, 0.5, 4)["*all"]
    occ = COLUMNS[column][3]
    return dict(score=s["w_score"] if occ else s["score"], top1=s["w_top1"] if occ else s["top1"], n=s["n"])


def build(root, out):
    runs = []
    for manifest in sorted(root.glob("*/run.json")):
        run = json.loads(manifest.read_text(encoding="utf-8"))
        run["cells"] = {}
        for entry in run.get("sets", []):
            cell = score_cell(manifest.parent, entry, entry["column"])
            if cell:
                run["cells"][entry["column"]] = cell
        runs.append(run)
    runs.sort(key=lambda r: (r.get("order", 999), r["id"]))
    tables = defaultdict(list)
    for col, (table, *_rest) in COLUMNS.items():
        tables[table].append(col)
    lines = ["# CyIME 选词评估总表", "",
             "每格是 **加权分** / 首选率（%）。加权分：正确答案排第 1 位 1 分，第 2 位 0.5，第 3 位 0.25，第 4 位 0.125，"
             "之后 0 分。每列最高的加权分加粗。格子里的 ⚠ 表示题数与该列标准不同，不能直接比；空格 = 没测。"
             "† = 只登记了汇总数字，逐题文件不在本机。「环境」写明在哪测、模型和词库（base 词频 md5）：只有环境一致的两行之间的差别才全是该行改动带来的。", "",
             f"由 `tools/ime_lab/eval_board.py build` 从 `{root.as_posix()}` 生成，共 {len(runs)} 个结果。", ""]
    for table, cols in tables.items():
        rows = [r for r in runs if any(c in r["cells"] for c in cols)]
        if not rows:
            continue
        cols = [c for c in cols if any(c in r["cells"] for r in rows)]
        best = {c: max(r["cells"][c]["score"] for r in rows if c in r["cells"]) for c in cols}
        lines += [f"## {table}", "", "| 变体 | 环境 | " + " | ".join(COLUMNS[c][1] for c in cols) + " |",
                  "|---|---|" + "---|" * len(cols)]
        for section in dict.fromkeys(r["section"] for r in rows):
            lines.append(f"| **{section}** | |" + " |" * len(cols))
            for r in (r for r in rows if r["section"] == section):
                env = " · ".join(x for x in (r.get("where"), r.get("model"), f"词库 {r['dict_md5']}" if r.get("dict_md5") else "") if x)
                cells = []
                for c in cols:
                    cell = r["cells"].get(c)
                    if not cell:
                        cells.append("")
                        continue
                    sc = f"{cell['score']:.1f}"
                    sc = f"**{sc}**" if abs(cell["score"] - best[c]) < 0.05 else sc
                    warn = f" ⚠n={cell['n']}" if cell["n"] != COLUMNS[c][2] else ""
                    top1 = f"{cell['top1']:.1f}" if cell.get("top1") is not None else "—"
                    mark = "†" if cell.get("summary_only") else ""
                    cells.append(f"{sc}{mark} / {top1}{warn}")
                lines.append(f"| {r['variant']} | {env} | " + " | ".join(cells) + " |")
        notes = [NOTES[c] for c in cols if c in NOTES]
        lines += [""] + ([f"\\* {' '.join(notes)}", ""] if notes else [])
    perf_rows = [r for r in runs if r.get("perf")]
    if perf_rows:
        lines += ["## 速度、内存、体积", "", "| 变体 | " + " | ".join(PERF.values()) + " |", "|---|" + "---|" * len(PERF)]
        for r in perf_rows:
            cells = []
            for k in PERF:
                p = r["perf"].get(k)
                cells.append(f"{p['value']}（{p.get('where', '')}{'，' + p['note'] if p.get('note') else ''}）" if p else "")
            lines.append(f"| {r['variant']} | " + " | ".join(cells) + " |")
        lines.append("")
    notes_file = root.parent / "notes.md"  # hand-kept gaps and how to fill them
    if notes_file.exists():
        lines += [notes_file.read_text(encoding="utf-8").rstrip(), ""]
    lines += ["## 各结果的来源", "", "| 结果 | 日期 | 引擎 | 来源 | 说明 |", "|---|---|---|---|---|"]
    for r in runs:
        lines.append(f"| {r['variant']} | {r.get('date', '')} | {r.get('engine', '')} | {r.get('source', '')} | {r.get('notes', '')} |")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{out}: {len(runs)} runs")


def add(a):
    d = a.root / a.run_id
    d.mkdir(parents=True, exist_ok=True)
    sets = []
    for spec in a.set:
        column, rest = spec.split("=", 1)
        if column not in COLUMNS:
            sys.exit(f"unknown column {column}; known: {', '.join(COLUMNS)}")
        path, _, group = rest.partition("@")
        targets = None
        if "#" in path:  # run_eval.py results: FILE#METHOD needs the set file via --targets
            path, method = path.rsplit("#", 1)
            targets = Path(a.targets)
            shutil.copy(targets, d / f"targets-{targets.name}")
        src = Path(path)
        if not (d / src.name).exists():
            shutil.copy(src, d / src.name)
        entry = {"column": column, "file": src.name + (f"#{method}" if targets else "")}
        if targets:
            entry["targets"] = f"targets-{targets.name}"
        if group:
            entry["group"] = group
        sets.append(entry)
    for spec in a.score or []:  # COLUMN=SCORE[/TOP1][@N]
        column, rest = spec.split("=", 1)
        rest, _, n = rest.partition("@")
        score, _, top1 = rest.partition("/")
        entry = {"column": column, "score": float(score)}
        if top1:
            entry["top1"] = float(top1)
        if n:
            entry["n"] = int(n)
        sets.append(entry)
    perf = {}
    for spec in a.perf or []:
        key, rest = spec.split("=", 1)
        value, _, where_note = rest.partition("@")
        where, _, note = where_note.partition(":")
        perf[key] = {"value": value, "where": where, "note": note}
    manifest = d / "run.json"
    old = json.loads(manifest.read_text(encoding="utf-8")) if manifest.exists() else {}
    keep = [s for s in old.get("sets", []) if s["column"] not in {e["column"] for e in sets}]
    run = {**old, "id": a.run_id, "section": a.section or old.get("section", ""),
           "variant": a.variant or old.get("variant", a.run_id), "sets": keep + sets,
           "perf": {**old.get("perf", {}), **perf}}
    for key in ("where", "date", "engine", "dict_md5", "model", "source", "notes", "order"):
        value = getattr(a, key)
        if value is not None:
            run[key] = int(value) if key == "order" else value
    manifest.write_text(json.dumps(run, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{manifest}: {len(run['sets'])} sets")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--root", type=Path, default=ROOT)
    sub = p.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build")
    b.add_argument("--out", type=Path)
    s = sub.add_parser("add")
    s.add_argument("run_id")
    for key in ("section", "variant", "where", "date", "engine", "dict_md5", "model", "source", "notes", "targets", "order"):
        s.add_argument(f"--{key.replace('_', '-')}")
    s.add_argument("--set", action="append", default=[])
    s.add_argument("--perf", action="append")
    s.add_argument("--score", action="append", help="COLUMN=SCORE[/TOP1][@N] when only a summary is available")
    a = p.parse_args()
    if a.cmd == "build":
        build(a.root, a.out or a.root.parent / "board.md")
    else:
        add(a)


if __name__ == "__main__":
    main()
