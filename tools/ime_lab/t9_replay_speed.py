"""Speed gate for host T9 replays: per-key engine work counters and warm-key latency.

Work counters come from the host engine (CYIME_T9_WORK_COUNTERS, see
tools/ime_lab/host_replay/host_replay_jni.cc). Each key row carries `work`
(processKey + flush) and `inspect_work` (candidate inspection); settled rows
carry the work done while waiting. Counts are deterministic for one engine,
input and user state, so the gate has no noise allowance: every repeat must
reproduce its primary run's work exactly (the baseline's own repeat variation is
zero), and per cost counter no key event's cost (work + inspect_work) may grow.
Each grown key fails the gate and is listed per case, never averaged; the total,
p50, p95, p99 and max of the per-key cost and the settle-stage work may not grow
either.

Counters are thread-local to the replay thread: work on the t9_sentence_scorer
worker thread is not counted (fine while sentence scoring is not requested).
Syllabifier graph building and candidate_policy have no counters; only latency
covers them. `dict_buckets` counts (table, start) pairs from QueryMulti.

Latency noise is mostly run-level (machine state, file cache), so it is judged
only on a timing session: at least three runs of the baseline engine (baseline
+ two --noise-baseline repeats) and one or more candidate runs, sequential,
interleaved so that baseline runs come first and last (B, C, B', C', B''), no
gap over 20 minutes, same host and runner, replay root on a native Linux
filesystem. For each of mean/p50/p95 the candidate's run average may not
exceed the baseline's run average by more than
    t99(df) * s * sqrt(1/n + 1/m) + max(1% of baseline, 0.005 ms),
a one-sided 1% two-sample t bound with s the run-to-run standard deviation
pooled over both engines' runs (df = n + m - 2). A slowdown inside that band
but beyond the baseline runs' own max-min spread is listed as a warning for
review. Anything else reports latency and leaves the gate incomplete.

The band is wide on purpose: on the measuring laptop (hybrid P/E cores, WSL2,
replay at nice 19 while in use) run averages vary by about 5% (CV) between runs
of one engine, with the same spread in thread CPU time, and a band equal to the
baseline spread failed a real same-engine session. Small cost changes are
caught by the exact work counters, not by latency.
"""

from datetime import datetime
import json
import math
import statistics

# Counters that measure work done per key. Growth here fails the speed gate.
COST_COUNTERS = (
    "dict_lookup_calls", "dict_lookup_all_calls", "dict_lookup_all_starts", "dict_table_queries",
    "dict_buckets", "table_nodes", "table_edges", "dict_extra_code_steps", "dict_chunks",
    "dict_chunk_entries", "system_entries", "user_dict_lookups", "user_dfs_nodes", "user_entries",
    "user_entries_collected", "merge_entries_in", "beam_evaluations", "beam_insertions",
    "grammar_queries", "gram_lookups", "whole_text_priors", "ngram_lookups",
)
WORK_GATED = ("total", "p50", "p95", "p99", "max")
LATENCY_GATED = ("mean", "p50", "p95")
LATENCY_RELATIVE_FLOOR = 0.01
LATENCY_EPSILON_MS = 0.005
MIN_BASELINE_RUNS = 3
SESSION_MAX_GAP_SECONDS = 20 * 60
# One-sided 99% quantiles of Student's t by degrees of freedom; beyond 20 the df-20 value (conservative).
T99 = {1: 31.821, 2: 6.965, 3: 4.541, 4: 3.747, 5: 3.365, 6: 3.143, 7: 2.998, 8: 2.896, 9: 2.821, 10: 2.764,
       11: 2.718, 12: 2.681, 13: 2.650, 14: 2.624, 15: 2.602, 16: 2.583, 17: 2.567, 18: 2.552, 19: 2.539, 20: 2.528}
# Mounted Windows drives (9p/drvfs) and network filesystems page files in slowly
# and unevenly; "warm" keys would measure file access, not decoding.
NON_NATIVE_FILESYSTEMS = {"9p", "drvfs", "v9fs", "cifs", "smb3", "smbfs", "nfs", "nfs4", "fuse", "fuseblk",
                          "virtiofs", "unknown"}


def percentile(values, fraction):
    ordered = sorted(values)
    if not ordered:
        return None
    position = (len(ordered) - 1) * fraction
    low = int(position)
    high = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[high] - ordered[low]) * (position - low)


def distribution(values):
    if not values:
        return {"count": 0}
    return {"count": len(values), "total": sum(values), "mean": round(statistics.fmean(values), 4),
            "p50": round(percentile(values, 0.5), 4), "p95": round(percentile(values, 0.95), 4),
            "p99": round(percentile(values, 0.99), 4), "max": max(values)}


def key_rows(run, pass_name):
    return [row for row in run["snapshots"] if row["pass"] == pass_name and row["stage"] == "key_immediate"]


def settle_rows(run, pass_name):
    return [row for row in run["snapshots"] if row["pass"] == pass_name and row["stage"] != "key_immediate"
            and isinstance(row.get("work"), dict)]


def cold_case(run):
    """The first case absorbs process start-up and model page-in; a one-case fixture keeps it."""
    cases = run["fixture"]["cases"]
    return cases[0]["id"] if len(cases) > 1 else None


def counter_names(run):
    names = run["ready"].get("work_counters")
    return list(names) if isinstance(names, list) and names else None


def has_work(run, pass_name):
    rows = key_rows(run, pass_name)
    return bool(rows) and all(isinstance(row.get("work"), dict) for row in rows)


def cost(row, name):
    return row["work"].get(name, 0) + (row.get("inspect_work") or {}).get(name, 0)


def exceeds(candidate, baseline, tolerance_percent):
    return candidate > baseline * (1 + tolerance_percent / 100.0)


def work_signature(run, pass_name):
    return [(row["id"], row["keys"], row["stage"], row.get("work"), row.get("inspect_work"))
            for row in run["snapshots"] if row["pass"] == pass_name]


def increased_keys(baseline, candidate, pass_name, names, tolerance_percent):
    """Every key whose cost counter grew, grouped by case; never averaged."""
    before = {(row["id"], row["keys"]): row for row in key_rows(baseline, pass_name)}
    cases = {}
    for row in key_rows(candidate, pass_name):
        old = before[(row["id"], row["keys"])]
        for name in names:
            if name in COST_COUNTERS and exceeds(cost(row, name), cost(old, name), tolerance_percent):
                cases.setdefault(row["id"], {}).setdefault(name, []).append(
                    {"keys": row["keys"], "baseline": cost(old, name), "candidate": cost(row, name)})
    grouped = {}
    for label, run in (("baseline", baseline), ("candidate", candidate)):
        for row in key_rows(run, pass_name):
            if row["id"] in cases:
                grouped.setdefault((label, row["id"]), []).append(row)
    result = []
    for case_id, counters in cases.items():
        result.append({"id": case_id, "keys": sorted({item["keys"] for items in counters.values() for item in items},
                                                    key=len),
                       "counters": {name: {"case_total": {label: sum(cost(row, name) for row in grouped[(label, case_id)])
                                                          for label in ("baseline", "candidate")},
                                           "keys": items}
                                    for name, items in counters.items()}})
    return result


def work_section(baselines, candidates, pass_name, tolerance_percent):
    baseline, candidate = baselines[0], candidates[0]
    names = counter_names(candidate) or counter_names(baseline) or []
    result = {"counters": {}, "tolerance_percent": tolerance_percent, "gated_statistics": list(WORK_GATED),
              "scope": "per key_immediate event: work (processKey + flush) + inspect_work (candidate reading), "
                       "replay thread; settle = waits after syllables/clauses"}
    increased = []
    base_rows, cand_rows = key_rows(baseline, pass_name), key_rows(candidate, pass_name)
    base_settle, cand_settle = settle_rows(baseline, pass_name), settle_rows(candidate, pass_name)
    for name in names:
        entry = {"baseline": distribution([cost(row, name) for row in base_rows]),
                 "candidate": distribution([cost(row, name) for row in cand_rows]),
                 "settle": {"baseline": sum(cost(row, name) for row in base_settle),
                            "candidate": sum(cost(row, name) for row in cand_settle)},
                 "cost": name in COST_COUNTERS}
        base_total, cand_total = entry["baseline"]["total"], entry["candidate"]["total"]
        entry["delta"] = cand_total - base_total
        entry["delta_percent"] = round(100.0 * entry["delta"] / base_total, 3) if base_total else (
            0.0 if not cand_total else None)
        grown = [stat for stat in WORK_GATED
                 if exceeds(entry["candidate"][stat], entry["baseline"][stat], tolerance_percent)]
        if exceeds(entry["settle"]["candidate"], entry["settle"]["baseline"], tolerance_percent):
            grown.append("settle")
        entry["grown_statistics"] = grown
        if entry["cost"] and grown:
            entry["verdict"] = "increased"
            increased.append(name)
        else:
            entry["verdict"] = "within_tolerance" if entry["cost"] else "informational"
        result["counters"][name] = entry
    result["increased"] = increased
    result["increased_keys"] = increased_keys(baseline, candidate, pass_name, names, tolerance_percent)
    result["increased_key_count"] = sum(len(case["keys"]) for case in result["increased_keys"])
    reference = {"baseline": work_signature(baseline, pass_name), "candidate": work_signature(candidate, pass_name)}
    result["not_reproducible"] = [run["directory"] for label, runs in (("baseline", baselines), ("candidate", candidates))
                                  for run in runs[1:] if work_signature(run, pass_name) != reference[label]]
    return result


def latency_values(rows, field):
    if field == "process_us":
        return [row["process_us"] / 1000.0 for row in rows]
    return [float(row["process_ms"]) for row in rows]


def warm_latency(run, pass_name, field):
    """Warm keys exclude the first fixture case of each pass (cold start, model page-in)."""
    cold, rows = cold_case(run), key_rows(run, pass_name)
    return {(row["id"], row["keys"]): value for row, value in zip(rows, latency_values(rows, field))
            if row["id"] != cold}


def decode_cache_only(run, pass_name):
    """A pass served entirely from the decode cache (small fixtures, paused pass) measures no decoding."""
    rows = key_rows(run, pass_name)
    if not rows or not all(isinstance(row.get("work"), dict) for row in rows):
        return False
    decodes = sum(row["work"].get("joint_decodes", 0) for row in rows)
    return decodes > 0 and sum(row["work"].get("joint_cache_hits", 0) for row in rows) >= decodes


def parse_time(value):
    result = datetime.fromisoformat(value)
    if result.tzinfo is None:
        raise ValueError("timestamp without time zone")
    return result


def timing_session(baselines, candidates):
    """Latency is comparable only between interleaved, sequential runs of one session on one machine."""
    reasons = []
    labelled = [("B", run) for run in baselines] + [("C", run) for run in candidates]
    if len(baselines) < MIN_BASELINE_RUNS:
        reasons.append(f"needs {MIN_BASELINE_RUNS} runs of the baseline engine (baseline + "
                       f"{MIN_BASELINE_RUNS - 1} --noise-baseline repeats); got {len(baselines)}")
    for field in ("host", "compiled_runner_sources", "json_jar_sha256"):
        if len({json.dumps(run["manifest"].get(field), sort_keys=True) for _, run in labelled}) > 1:
            reasons.append(f"{field} differs between timed runs")
    roots = [run["manifest"].get("replay_root") for _, run in labelled]
    if not all(isinstance(root, dict) and isinstance(root.get("filesystem"), str) for root in roots):
        reasons.append("replay root filesystem not recorded (run made before the native-root protocol)")
    else:
        filesystems = sorted({root["filesystem"] for root in roots})
        if len(filesystems) > 1:
            reasons.append("replay roots are on different filesystems: " + ", ".join(filesystems))
        elif filesystems[0] in NON_NATIVE_FILESYSTEMS:
            reasons.append(f"replay root on a non-native filesystem ({filesystems[0]}); timing measures file access")
    session = {"order": "", "span_minutes": None, "max_gap_minutes": None}
    try:
        times = sorted((parse_time(run["manifest"]["started_utc"]), parse_time(run["manifest"]["finished_utc"]),
                        label, run["directory"]) for label, run in labelled)
    except (KeyError, TypeError, ValueError):
        reasons.append("start/finish times missing or invalid")
        times = []
    if times:
        session["order"] = "".join(item[2] for item in times)
        gaps = []
        for previous, following in zip(times, times[1:]):
            gap = (following[0] - previous[1]).total_seconds()
            gaps.append(gap)
            if gap < 0:
                reasons.append(f"runs overlap in time: {previous[3]} and {following[3]}")
            elif gap > SESSION_MAX_GAP_SECONDS:
                reasons.append(f"{gap / 60:.1f} min gap between {previous[3]} and {following[3]} "
                               f"(limit {SESSION_MAX_GAP_SECONDS // 60} min)")
        if session["order"][0] != "B" or session["order"][-1] != "B":
            reasons.append("candidate runs must be bracketed by baseline-engine runs (B, C, ..., B')")
        session["span_minutes"] = round((times[-1][1] - times[0][0]).total_seconds() / 60, 2)
        session["max_gap_minutes"] = round(max(gaps) / 60, 2) if gaps else 0.0
    session.update(assessable=not reasons, reasons=reasons)
    return session


def latency_band(baseline_values, candidate_values):
    """One-sided 1% two-sample t bound on run averages, plus max(1% of baseline, 0.005 ms).

    The run-to-run spread is pooled over both engines' runs: on the measuring computer both are equally
    exposed to machine noise (about 5% run-level CV on the 10-10 sessions), and three baseline runs alone
    give too few degrees of freedom. Returns (band, degrees of freedom)."""
    n, m = len(baseline_values), len(candidate_values)
    squares = sum((value - statistics.fmean(baseline_values)) ** 2 for value in baseline_values)
    if m > 1:
        squares += sum((value - statistics.fmean(candidate_values)) ** 2 for value in candidate_values)
    freedom = n - 1 + (m - 1 if m > 1 else 0)
    spread = math.sqrt(squares / freedom)
    base = statistics.fmean(baseline_values)
    band = T99[min(freedom, 20)] * spread * math.sqrt(1 / n + 1 / m) + max(LATENCY_RELATIVE_FLOOR * base,
                                                                         LATENCY_EPSILON_MS)
    return band, freedom


def latency_section(baselines, candidates, pass_name, session):
    runs = baselines + candidates
    field = "process_us" if all(all("process_us" in row for row in key_rows(run, pass_name)) for run in runs) \
        else "process_ms"
    warm = {label: [warm_latency(run, pass_name, field) for run in group]
            for label, group in (("baseline", baselines), ("candidate", candidates))}
    per_run = {label: [distribution(list(values.values())) for values in group] for label, group in warm.items()}
    result = {"field": field, "resolution_ms": 0.001 if field == "process_us" else 1,
              "warm_definition": "key_immediate events except the first fixture case of the pass "
                                 "(kept when the fixture has a single case)",
              "per_run": {label: [{"directory": run["directory"], **values} for run, values in zip(group, per_run[label])]
                          for label, group in (("baseline", baselines), ("candidate", candidates))}}
    for label, values in per_run.items():
        result[label] = {metric: round(statistics.fmean(item[metric] for item in values), 4)
                         for metric in ("mean", "p50", "p95", "p99", "max") if all(metric in item for item in values)}
        result[label]["runs"] = len(values)
    # Pooled per-key means; reported as evidence, not gated (run-level noise is shared by all keys of a run).
    pooled = {label: {key: statistics.fmean(run[key] for run in group) for key in group[0]
                      if all(key in run for run in group)} for label, group in warm.items()}
    shared = sorted(pooled["baseline"].keys() & pooled["candidate"].keys())
    differences = [pooled["candidate"][key] - pooled["baseline"][key] for key in shared]
    if len(differences) > 1:
        result["paired_difference_ms"] = {"keys": len(differences), "mean": round(statistics.fmean(differences), 4),
                                          "median": round(statistics.median(differences), 4),
                                          "standard_error": round(statistics.stdev(differences) / math.sqrt(len(differences)), 4)}
    verdicts = {}
    cache_only = [run["directory"] for run in runs if decode_cache_only(run, pass_name)]
    for metric in LATENCY_GATED:
        base, cand = result["baseline"].get(metric), result["candidate"].get(metric)
        if base is None or cand is None:
            verdicts[metric] = {"verdict": "unavailable"}
            continue
        entry = {"baseline": base, "candidate": cand, "delta": round(cand - base, 4)}
        if cache_only:
            entry.update(verdict="not_applicable_decode_cache_only", runs=cache_only)
        elif not session["assessable"]:
            entry["verdict"] = "unassessed_no_timing_session"
        else:
            values = [item[metric] for item in per_run["baseline"]]
            candidate_values = [item[metric] for item in per_run["candidate"]]
            band, freedom = latency_band(values, candidate_values)
            spread = max(values) - min(values)
            entry.update(baseline_runs=values, candidate_runs=candidate_values, degrees_of_freedom=freedom,
                         baseline_spread=round(spread, 4), noise_band=round(band, 4),
                         noise_band_percent=round(100 * band / base, 2) if base else None,
                         verdict="slower_than_noise" if cand - base > band else "within_noise")
            # Not a failure: a slowdown beyond the baseline runs' own max-min spread is flagged for review.
            entry["above_baseline_spread"] = cand - base > spread + max(LATENCY_RELATIVE_FLOOR * base, LATENCY_EPSILON_MS)
        verdicts[metric] = entry
    result["gate"] = verdicts
    return result


def speed_report(baselines, candidates, passes, tolerance_percent=0.0):
    """Compare validated runs (t9_replay_compare.load_run results); the first of each list is the primary run."""
    report = {"format_version": 2, "passes": {},
              "runs": {"baseline": [run["directory"] for run in baselines],
                       "candidate": [run["directory"] for run in candidates]},
              "rule": "No key event's cost (work + inspect_work) of any cost counter may grow, nor its total, p50, "
                      "p95, p99 or max, nor settle work, and repeats must reproduce work exactly (default "
                      "tolerance 0: counts are deterministic). Warm latency mean/p50/p95 (run averages) may not exceed the "
                      "baseline's by more than a one-sided 1% t bound on the pooled run-to-run spread plus "
                      "max(1%, 0.005 ms), judged only in an interleaved timing session (>= 3 baseline-engine runs "
                      "bracketing the candidate runs); it detects only slowdowns larger than run-level noise. Quality gains never offset a speed regression."}
    session = timing_session(baselines, candidates)
    report["timing_session"] = session
    if not all(has_work(run, name) for run in baselines + candidates for name in passes):
        report.update(gate="unavailable", reason="Work counters are missing from at least one run "
                      "(older evidence or an engine built without CYIME_T9_WORK_COUNTERS).")
        for name in passes:
            report["passes"][name] = {"latency": latency_section(baselines, candidates, name, session)}
        return report
    failures, unassessed, warnings, assessed = [], [], [], 0
    for name in passes:
        work = work_section(baselines, candidates, name, tolerance_percent)
        latency = latency_section(baselines, candidates, name, session)
        report["passes"][name] = {"work": work, "latency": latency}
        failures.extend(f"{name}: work {counter} increased ({', '.join(work['counters'][counter]['grown_statistics'])})"
                        for counter in work["increased"])
        failures.extend(f"{name}: work not reproduced by {directory}" for directory in work["not_reproducible"])
        failures.extend(f"{name}: {case['id']} work grew on key(s) {', '.join(case['keys'])} "
                        f"({', '.join(case['counters'])})" for case in work["increased_keys"])
        for metric, entry in latency["gate"].items():
            if entry["verdict"] == "slower_than_noise":
                failures.append(f"{name}: latency {metric} slower than noise")
            if entry["verdict"] in ("slower_than_noise", "within_noise"):
                assessed += 1
            elif entry["verdict"] != "not_applicable_decode_cache_only":
                unassessed.append(f"{name}: latency {metric} {entry['verdict']}")
            if entry["verdict"] == "within_noise" and entry["above_baseline_spread"]:
                warnings.append(f"{name}: latency {metric} +{entry['delta']} ms exceeds the baseline runs' spread "
                                f"(within the {entry['noise_band']} ms noise band; review)")
    if not assessed and not unassessed:
        unassessed.append("no pass measured decoding latency")
    report["failures"] = failures
    report["unassessed"] = unassessed
    report["warnings"] = warnings
    report["gate"] = "failed" if failures else ("incomplete" if unassessed else "passed")
    return report


def markdown(report):
    lines = ["", "## 速度门禁", "", f"结果：{report['gate']}。" + (report.get("reason") or ""), ""]
    if report.get("failures"):
        lines += ["不合格项：" + "；".join(report["failures"]), ""]
    if report.get("warnings"):
        lines += ["需复核（未超出噪声带，但超出基线各次极差）：" + "；".join(report["warnings"]), ""]
    session = report.get("timing_session", {})
    runs = report.get("runs", {})
    lines += [f"基线引擎 {len(runs.get('baseline', []))} 次、候选 {len(runs.get('candidate', []))} 次；"
              f"时间顺序 {session.get('order') or '未知'}，跨度 {session.get('span_minutes')} 分钟。", ""]
    if report["gate"] == "incomplete":
        lines += ["耗时只报告、不判定通过：" + "；".join(session.get("reasons") or report.get("unassessed") or []), ""]
    for name, section in report["passes"].items():
        latency = section["latency"]
        lines += [f"### {name}", "", f"热键耗时（{latency['field']}，ms，各次运行的平均；不含每个过程的第一个用例）：", "",
                  "| 指标 | 基线 | 候选 | 差值 | 允许差（%） | 判定 |", "|---|---|---|---|---|---|"]
        for metric in ("mean", "p50", "p95", "p99", "max"):
            base, cand = latency["baseline"].get(metric), latency["candidate"].get(metric)
            gate = latency.get("gate", {}).get(metric, {})
            delta = "" if base is None or cand is None else round(cand - base, 3)
            band = f"{gate['noise_band']}（{gate['noise_band_percent']}%）" if "noise_band" in gate else ""
            lines.append(f"| {metric} | {base} | {cand} | {delta} | {band} | {gate.get('verdict', '仅报告')} |")
        if "paired_difference_ms" in latency:
            paired = latency["paired_difference_ms"]
            lines += ["", f"逐键配对差（候选−基线，各键多次平均）：均值 {paired['mean']} ms，中位 {paired['median']} ms，"
                          f"标准误 {paired['standard_error']} ms，{paired['keys']} 键（证据，不单独判定）。"]
        if "work" in section:
            work = section["work"]
            lines += ["", "逐键工作量（work + inspect_work；总计 / p50 / p95 / p99 / 最大；等待阶段另计）：", "",
                      "| 计数 | 基线 | 候选 | 差值% | 等待 基线/候选 | 判定 |", "|---|---|---|---|---|---|"]
            for counter, entry in work["counters"].items():
                b, c = entry["baseline"], entry["candidate"]
                lines.append(f"| {counter} | {b['total']} / {b['p50']} / {b['p95']} / {b['p99']} / {b['max']} | "
                             f"{c['total']} / {c['p50']} / {c['p95']} / {c['p99']} / {c['max']} | {entry['delta_percent']} | "
                             f"{entry['settle']['baseline']} / {entry['settle']['candidate']} | {entry['verdict']} |")
            if work["not_reproducible"]:
                lines += ["", "工作量未复现：" + "、".join(work["not_reproducible"])]
            lines += ["", f"代价计数增加的按键：{work['increased_key_count']} 个，分布在 {len(work['increased_keys'])} 个用例"
                          "（逐用例列出，不取平均；完整逐键数值见 comparison.json）。"]
            for case in work["increased_keys"]:
                parts = [f"{counter} {item['case_total']['baseline']}→{item['case_total']['candidate']}"
                         f"（{len(item['keys'])} 键）" for counter, item in case["counters"].items()]
                lines.append(f"- {case['id']}：" + "；".join(parts))
        lines.append("")
    return lines
