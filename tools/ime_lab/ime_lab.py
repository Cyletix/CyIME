"""Offline tools: no QQ database access, networking, or IME modification."""
import argparse, collections, json, math, re, unicodedata
from pathlib import Path

def rows(path):
    with Path(path).open(encoding="utf-8-sig") as f:
        for i, line in enumerate(f, 1):
            if line.strip():
                obj = json.loads(line)
                if not isinstance(obj, dict):
                    raise ValueError(f"line {i}: expected object")
                yield obj

def write(path, value):
    # Exclusive create prevents accidental overwrite of earlier results.
    with Path(path).open("x", encoding="utf-8") as f:
        json.dump(value, f, ensure_ascii=False, indent=2)
        f.write("\n")

def evaluate(cases, samples):
    index = {c["id"]: c for c in cases}
    result, times = [], []
    groups = collections.defaultdict(list)
    observed = set()
    for s in samples:
        cid = s["case_id"]
        if cid not in index:
            raise ValueError("unknown case: " + cid)
        c = index[cid]
        if c["input"] is None or s.get("input") != c["input"]:
            raise ValueError("input mismatch: " + cid)
        candidates = s["candidates"]
        if not isinstance(candidates, list) or any(
            not isinstance(x, dict) or not isinstance(x.get("text"), str)
            for x in candidates
        ):
            raise ValueError("candidates must contain text objects")
        observed.add(cid)
        texts = [x["text"] for x in candidates]
        rank = next((i + 1 for i, t in enumerate(texts) if t in c["expected"]), None)
        if c["check"] in ("date", "no_date"):
            # Never infer date type from text: tags must come from the engine.
            if any(not isinstance(x.get("type"), str) for x in candidates):
                passed = None
            else:
                has_date = any(x["type"] in {"date", "date_hint", "datetime", "lunar", "time", "week", "timestamp"} for x in candidates)
                passed = has_date if c["check"] == "date" else not has_date
        else:
            passed = rank is not None and rank <= c["top_k"]
        result.append({"case_id":cid, "rank":rank, "pass":passed,
                       "observed_candidates":len(texts)})
        latency = s.get("latency_ms")
        if latency is not None:
            if isinstance(latency, bool) or not isinstance(latency, (int, float)) or not math.isfinite(latency) or latency < 0:
                raise ValueError("invalid latency")
            times.append(latency)
            metadata = {k:v for k,v in s.get("metadata", {}).items() if k != "iteration"}
            key = json.dumps({"case_id":cid, "metadata":metadata}, sort_keys=True, ensure_ascii=False)
            groups[key].append(latency)
    return {"scope":"Only supplied snapshots; not device acceptance. Do not mix devices/builds/scenarios.",
            "results":result, "missing":[c["id"] for c in cases if c["id"] not in observed],
            "latency_samples":len(times),
            "latency_groups":[dict(json.loads(key), samples=len(values),
                p50_ms=sorted(values)[math.ceil(.5*len(values))-1],
                p95_ms=sorted(values)[math.ceil(.95*len(values))-1]) for key,values in groups.items()]}

def prior(messages, minimum=2):
    if minimum < 2:
        raise ValueError("minimum count must be >= 2")
    counts = {n:collections.Counter() for n in (1,2,3)}
    seen = set()
    stats = collections.Counter()
    for m in messages:
        stats["rows"] += 1
        # Missing/ambiguous authorship fails closed.
        if m.get("is_self") is not True or m.get("kind") != "text" or m.get("is_forwarded") or m.get("is_quoted"):
            stats["excluded_not_self_text"] += 1
            continue
        if not all(isinstance(m.get(k),str) and m[k] for k in ("conversation_id","message_id","text")):
            raise ValueError("self text needs conversation_id, message_id, text")
        key = (m["conversation_id"],m["message_id"])
        if key in seen:
            stats["duplicates"] += 1
            continue
        seen.add(key)
        t = unicodedata.normalize("NFKC",m["text"])
        # Replace with boundaries; never join across removed content.
        t = re.sub(r"https?://\S+|[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}", " ", t)
        t = re.sub(r"[0-9]+", " ", t)
        # Conservative first version: Chinese character n-grams only.
        segments = re.findall(r"[\u4e00-\u9fff]+",t)
        stats["accepted"] += 1
        for seg in segments:
            for n in counts:
                counts[n].update(seg[i:i+n] for i in range(len(seg)-n+1))
    return {"unit":"Chinese character n-gram, not word frequency or Rime dictionary",
            "warning":"Frequency filtering is not anonymization; keep private and review before use.",
            "stats":dict(stats),"min_count":minimum,
            "ngrams":{str(n):[{"text":t,"count":v} for t,v in sorted(c.items(), key=lambda x:(-x[1],x[0])) if v >= minimum] for n,c in counts.items()}}

def selftest():
    cases=[{"id":"x","input":"3","expected":["的"],"check":"rank","top_k":3},
           {"id":"d","input":"77","expected":[],"check":"no_date","top_k":20}]
    r=evaluate(cases,[{"case_id":"x","input":"3","candidates":[{"text":"饿"},{"text":"的"}],"latency_ms":2},
                      {"case_id":"d","input":"77","candidates":[{"text":"QQ"}],"latency_ms":10}])
    assert r["results"][0]["rank"] == 2 and r["results"][0]["pass"]
    assert r["results"][1]["pass"] is None and len(r["latency_groups"]) == 2
    try:
        evaluate(cases,[{"case_id":"x","input":"4","candidates":[]}])
        raise AssertionError("mismatch accepted")
    except ValueError:
        pass
    m={"is_self":True,"kind":"text","conversation_id":"c","message_id":"1","text":"你好123世界 https://example.org 你好"}
    p=prior([m,m,dict(m,message_id="2",is_self=False),dict(m,message_id="3",is_self="true")])
    assert p["stats"]["accepted"] == 1 and p["stats"]["duplicates"] == 1
    assert p["ngrams"]["2"] == [{"text":"你好","count":2}]
    assert prior([dict(m,text="你"),dict(m,message_id="2",text="好")])["ngrams"]["2"] == []
    print("PASS: rank, missing type, percentile, input validation, self-only, deduplication, redaction boundary, message boundary")

def main():
    p=argparse.ArgumentParser(description=__doc__)
    sub=p.add_subparsers(dest="cmd",required=True)
    sub.add_parser("selftest")
    e=sub.add_parser("evaluate")
    e.add_argument("cases"); e.add_argument("samples"); e.add_argument("output")
    b=sub.add_parser("prior")
    b.add_argument("messages"); b.add_argument("output")
    b.add_argument("--min-count",type=int,default=2)
    a=p.parse_args()
    if a.cmd == "selftest": selftest()
    elif a.cmd == "evaluate": write(a.output,evaluate(json.loads(Path(a.cases).read_text(encoding="utf-8-sig")),rows(a.samples)))
    else: write(a.output,prior(rows(a.messages),a.min_count))
if __name__ == "__main__":
    main()
