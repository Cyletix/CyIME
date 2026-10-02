"""Regression tests for rejecting incomplete or internally inconsistent evidence."""

from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest

import summarize_daily_chat as audit


FIXTURE = {"id": "minimal", "cases": [{"id": "c001", "text": "你好", "pinyin": ["ni", "hao"],
           "keys": "64426", "syllable_keys": ["64", "426"], "is_body": True, "role": "body"}]}


def sample():
    rows = []
    # At the first completed character target ranks second; the final target is
    # absent initially, then becomes first after waiting. Mid-syllable outputs
    # must not inflate any exact-prefix metric.
    for position, completed, boundary, candidates in [
        (1, 0, False, ["你"]), (2, 1, True, ["泥", "你"]),
        (3, 1, False, ["你"]), (4, 1, False, ["你"]), (5, 2, True, ["拟好"]),
    ]:
        expected = "你好"[:completed]
        rank = next((i for i, text in enumerate(candidates, 1) if text == expected), -1) if boundary else None
        record = {"stage": "key_immediate", "id": "c001", "variant": "digits", "mode": "paused",
                  "target": "你好", "keys": "64426"[:position], "sent_keys": "64426"[:position],
                  "expected_prefix": expected, "char_count": completed, "active_char": completed + (not boundary),
                  "syllable_boundary": boundary, "clause_endpoint": position == 5, "accepted": True,
                  "target_prefix_rank": rank, "top_exact": boundary and rank == 1,
                  "rows": [{"text": value} for value in candidates], "process_ms": 1, "inspect_ms": 0,
                  "wait_ms": 0, "state": 0, "state_before_inspect": 0}
        rows.append(record)
        if boundary:
            settled = deepcopy(record)
            settled.update(stage="clause_settled" if position == 5 else "syllable_settled",
                           settle={"timeout": False}, wait_ms=10)
            if position == 5:
                settled.update(rows=[{"text": "你好"}], target_prefix_rank=1, top_exact=True)
            rows.append(settled)
    return rows


class DailyChatSummaryTest(unittest.TestCase):
    def test_prefix_and_endpoint_denominators_and_ranks_are_distinct(self):
        report, rows = audit.summarize(FIXTURE, sample(), ["digits:paused"])
        result = report["passes"]["digits:paused"]
        self.assertEqual(result["coverage"]["observed_key_snapshots"], 5)
        self.assertEqual(result["metrics"]["immediate_characters"]["body"]["count"], 2)
        self.assertEqual(result["metrics"]["immediate_characters"]["body"]["top1"], 0)
        self.assertEqual(result["metrics"]["immediate_characters"]["body"]["top5"], 1)
        self.assertEqual(result["metrics"]["settled_clauses"]["body"]["top1"], 1)
        self.assertTrue(all(row["language_quality"] == "not_assessed" for row in rows))

    def test_missing_key_duplicate_key_and_missing_whole_pass_fail(self):
        rows = sample()
        for damaged, passes in [(rows[1:], ["digits:paused"]), (rows + [rows[0]], ["digits:paused"]),
                                (rows, ["digits:paused", "digits:continuous"])]:
            with self.subTest(passes=passes, count=len(damaged)), self.assertRaises(audit.EvidenceError):
                audit.summarize(FIXTURE, damaged, passes)

    def test_correct_total_count_cannot_hide_wrong_key_or_order(self):
        rows = sample()
        rows[0]["keys"] = "7"
        with self.assertRaisesRegex(audit.EvidenceError, "incorrect key prefix"):
            audit.summarize(FIXTURE, rows, ["digits:paused"])
        rows = sample()
        rows[0], rows[1] = rows[1], rows[0]
        with self.assertRaisesRegex(audit.EvidenceError, "order"):
            audit.summarize(FIXTURE, rows, ["digits:paused"])

    def test_reported_rank_must_agree_with_raw_candidates(self):
        rows = sample()
        rows[1]["target_prefix_rank"] = 1
        with self.assertRaisesRegex(audit.EvidenceError, "rank differs"):
            audit.summarize(FIXTURE, rows, ["digits:paused"])

    def test_timeout_is_an_execution_issue_not_silently_passed(self):
        rows = sample()
        rows[-1]["settle"]["timeout"] = True
        report, _ = audit.summarize(FIXTURE, rows, ["digits:paused"])
        self.assertEqual(report["passes"]["digits:paused"]["execution_issues"][0]["issue"], "settlement_timeout")

    def test_accepted_key_with_missing_native_digit_is_still_a_failure(self):
        rows = sample()
        for row in rows:
            row.update(input=row["sent_keys"], remaining_digits=row["sent_keys"])
        rows[-1]["input"] = "6442"
        report, _ = audit.summarize(FIXTURE, rows, ["digits:paused"])
        self.assertEqual(report["passes"]["digits:paused"]["execution_issues"][0]["issue"],
                         "native_input_differs_from_sent_keys")
        del rows[-1]["remaining_digits"]
        with self.assertRaisesRegex(audit.EvidenceError, "missing native input state"):
            audit.summarize(FIXTURE, rows, ["digits:paused"])

    def test_composite_jni_scoring_status_is_checked_without_failing_lazy_initialization(self):
        self.assertFalse(audit.sentence_scoring_unavailable("grammar=unavailable;sentence=not_requested"))
        self.assertFalse(audit.sentence_scoring_unavailable("grammar=ready;sentence=loading"))
        self.assertTrue(audit.sentence_scoring_unavailable("grammar=ready;sentence=unavailable"))
        rows = sample()
        rows[-1]["scoring_status"] = "grammar=ready;sentence=unavailable"
        report, _ = audit.summarize(FIXTURE, rows, ["digits:paused"])
        self.assertEqual(report["passes"]["digits:paused"]["execution_issues"][0]["issue"], "sentence_scoring_unavailable")

    def test_truncated_file_without_complete_marker_is_rejected(self):
        metadata = {"stage": "metadata", "protocol": 1, "variant": "digits", "modes": ["paused"],
                    "row_limit": 20, "boundary_limit": 100}
        data = [metadata, {"stage": "ready"}, *sample()]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "replay.jsonl"
            path.write_text("\n".join(json.dumps(row) for row in data), encoding="utf-8")
            with self.assertRaisesRegex(audit.EvidenceError, "completion"):
                audit.read_evidence([path])
            data.append({"stage": "complete", "records_before_complete": len(data)})
            path.write_text("\n".join(json.dumps(row) for row in data), encoding="utf-8")
            records, sources = audit.read_evidence([path])
            self.assertEqual(len(records), 7)
            self.assertEqual(sources[0]["records"], 10)
            data[-1]["scoring_status"] = "unavailable"
            path.write_text("\n".join(json.dumps(row) for row in data), encoding="utf-8")
            with self.assertRaisesRegex(audit.EvidenceError, "scoring model unavailable"):
                audit.read_evidence([path])

    def test_html_payload_cannot_end_script_element(self):
        report, rows = audit.summarize(FIXTURE, sample(), ["digits:paused"])
        rows[0]["rows"][0]["text"] = '</script><img src=x onerror="alert(1)">'
        page = audit.render_html(report, rows)
        self.assertNotIn('<img src=x', page)
        self.assertIn('\\u003c/script>', page)


if __name__ == "__main__":
    unittest.main()
