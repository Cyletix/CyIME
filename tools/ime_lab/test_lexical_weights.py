import unittest
from collections import Counter
from lexical_weights import observations, partition


class LexicalWeightsTest(unittest.TestCase):
    def test_duplicate_documents_and_code_are_not_observations(self):
        text = "输入法 输入法 中文"
        counts, support, stats = observations([text, text, "中文输入法", "```\n错误错误\n```"])
        self.assertEqual(3, counts["输入法"])
        self.assertEqual(2, support["输入法"])
        self.assertNotIn("错误", counts)
        self.assertEqual(2, stats["duplicate_or_empty"])

    def test_frequency_is_not_multiplied_by_context_windows(self):
        counts, _, _ = observations(["中文输入法很好用"])
        self.assertEqual(1, counts["输入法"])

    def test_readings_require_unambiguous_dictionary_evidence(self):
        counts = Counter({"输入法": 9, "银行": 8, "重行": 7, "未知词": 6, "偶尔": 1})
        support = Counter({word: 2 for word in counts})
        accepted, review = partition(counts, support, {"输入法": {"shu ru fa"}, "银行": {"yin hang"}, "重行": {"chong xing", "zhong hang"}})
        self.assertEqual([("输入法", "shu ru fa", 9, 2), ("银行", "yin hang", 8, 2)], accepted)
        self.assertEqual({"ambiguous_reading", "missing_reading"}, {row[-1] for row in review})

    def test_one_document_repetition_cannot_pass_document_support(self):
        accepted, _ = partition(Counter({"输入法": 999}), Counter({"输入法": 1}), {"输入法": {"shu ru fa"}})
        self.assertEqual([], accepted)


if __name__ == "__main__": unittest.main()
