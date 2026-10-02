import unittest

from analyze_t9_weights import components, evaluate, features, pick


class WeightStudyTests(unittest.TestCase):
    def sample(self):
        return {"id": "test", "target": "目标", "first": "原选", "first_type": "sentence",
                "candidates": [
                    {"text": "原选", "word_count": 2, "character_count": 2,
                     "dictionary_log_weight": -6, "settled_weight": -20,
                     "neural_weighted_delta": -2},
                    {"text": "目标", "word_count": 1, "character_count": 2,
                     "dictionary_log_weight": -8, "settled_weight": -21,
                     "neural_weighted_delta": -4}]}

    def test_zero_delta_preserves_native_scores_and_ties(self):
        sample = self.sample()
        self.assertEqual(pick(sample, (8, 0, 1, 1)), "原选")
        sample["candidates"][1]["settled_weight"] = -20
        self.assertEqual(pick(sample, (8, 0, 1, 1)), "原选")

    def test_penalty_scales_per_component_not_character(self):
        self.assertEqual(pick(self.sample(), (10, 0, 1, 1)), "目标")

    def test_removed_neural_score_recovers_immediate_order(self):
        self.assertEqual(pick(self.sample(), (8, 0, 0, 1)), "目标")

    def test_unmatched_path_never_invents_neural_contribution(self):
        sample = self.sample()
        sample["candidates"][0]["neural_weighted_delta"] = None
        self.assertEqual(pick(sample, (8, 0, 1, 1)), "原选")
        with self.assertRaises(ValueError):
            pick(sample, (8, 0, 0, 1))

    def test_non_sentence_slot_is_not_replaced(self):
        sample = self.sample()
        sample["first_type"] = "phrase"
        self.assertEqual(pick(sample, (10, 0, 1, 1)), "原选")

    def test_dictionary_log_scaling_and_regression_are_separate(self):
        sample = self.sample()
        result = evaluate([sample], (8, 0, 1, 0))
        self.assertEqual((result["correct"], result["fixed"], result["regressed"]), (1, 1, 0))
        sample["target"] = "原选"
        result = evaluate([sample], (8, 0, 1, 0))
        self.assertEqual((result["correct"], result["fixed"], result["regressed"]), (0, 0, 1))

    def test_component_weights_are_not_confused_with_sentence_score(self):
        row = {"text": "甲乙", "code": "1,2", "weight": "-25",
               "components": "甲:-1.5:2;乙:-2.5:3;"}
        self.assertEqual(components(row), [("甲", -1.5, 2), ("乙", -2.5, 3)])
        result = features(row, {})
        self.assertEqual(result["dictionary_log_weight"], -4)
        self.assertEqual(result["settled_weight"], -25)
        self.assertIsNone(result["neural_weighted_delta"])


if __name__ == "__main__":
    unittest.main()
