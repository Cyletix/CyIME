import unittest

from key_latency_report import check_smoke


def sample(at, length, total=40_000):
    return dict(at=at, len=length, total=total, layout="t9", trigger="up", dropped=0)


class KeyLatencySmokeTest(unittest.TestCase):
    def check(self, rows):
        return check_smoke(rows, ["63", "2"], 1, "t9", 200)[0]

    def test_one_slow_frame_fails_even_when_other_keys_are_fast(self):
        self.assertEqual(self.check([sample(1, 1), sample(2, 2, 200_000), sample(3, 1)]), 0)
        self.assertEqual(self.check([sample(1, 1), sample(2, 2, 200_001), sample(3, 1)]), 1)

    def test_missing_key_or_failed_word_reset_invalidates_capture(self):
        self.assertEqual(self.check([sample(1, 1), sample(2, 2)]), 2)
        self.assertEqual(self.check([sample(1, 1), sample(2, 2), sample(3, 3)]), 2)

    def test_repeated_event_or_wrong_layout_cannot_substitute_for_expected_key(self):
        self.assertEqual(self.check([sample(1, 1), sample(1, 2), sample(3, 1)]), 2)
        self.assertEqual(self.check([sample(1, 1), sample(2, 2), dict(sample(3, 1), layout="qwerty")]), 2)

