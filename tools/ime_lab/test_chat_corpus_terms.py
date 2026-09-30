import unittest
from collections import Counter

from chat_corpus_terms import clean_learning_text, count_words


class ChatCorpusTermsTest(unittest.TestCase):
    def test_normalization_and_redaction_keep_word_boundaries(self):
        text = (
            "前Ｐｙｔｈｏｎ后 https://example.org/private?token=abc 中文 "
            "reader@example.org 文本 C:\\Users\\Example\\secret.txt 完成 "
            "/home/example/secret.txt 结束 "
            + "a" * 64
            + " 间隔 "
            + "QWxhZGRpbjpvcGVuIHNlc2FtZQ" * 3
            + " 收尾"
        )
        cleaned = clean_learning_text(text)
        self.assertIn("前Python后", cleaned)
        for hidden in (
            "example.org", "reader@", "C:\\Users", "/home/example",
            "a" * 64, "QWxhZGRpbjpvcGVuIHNlc2FtZQ",
        ):
            self.assertNotIn(hidden, cleaned)
        self.assertRegex(cleaned, r"中文\s+文本\s+完成")
        self.assertRegex(cleaned, r"结束\s+间隔\s+收尾")

    def test_quoted_paths_with_spaces_and_unc_paths_are_removed(self):
        text = (
            '前文 "C:\\Program Files\\Demo\\private.txt" 中段 '
            "\\\\server\\share\\private.txt 后文 '/home/demo/My File.txt' 末尾"
        )
        cleaned = clean_learning_text(text)
        for retained in ("前文", "中段", "后文", "末尾"):
            self.assertIn(retained, cleaned)
        for removed in ("Program Files", "server", "My File.txt"):
            self.assertNotIn(removed, cleaned)

    def test_non_http_url_schemes_are_removed(self):
        cleaned = clean_learning_text("开头 s3://bucket/private-object 结尾")
        self.assertRegex(cleaned, r"开头\s+结尾")

    def test_fenced_blocks_and_quoted_lines_are_removed(self):
        text = (
            "保留开头\n"
            "> 引用内容\n"
            "```python\nsecret_value = '隐私内容'\n```\n"
            "保留中间\n"
            "~~~\n更多代码\n~~~\n"
            "保留末尾"
        )
        cleaned = clean_learning_text(text)
        for retained in ("保留开头", "保留中间", "保留末尾"):
            self.assertIn(retained, cleaned)
        for removed in ("引用内容", "secret_value", "隐私内容", "更多代码"):
            self.assertNotIn(removed, cleaned)

    def test_unclosed_fence_drops_remainder(self):
        self.assertEqual(clean_learning_text("正文\n```\n代码\n尾部"), "正文\n   ")

    def test_count_words_uses_jieba_and_casefolds_english(self):
        counts = count_words("中国 中国 你 Python PYTHON Go a I 中 国")
        self.assertEqual(
            counts,
            Counter({("han", "中国"): 2, ("latin", "python"): 2, ("latin", "go"): 1}),
        )

    def test_clean_then_count_does_not_count_removed_content(self):
        text = "中国 https://example.org Python\n> 私人引用\n中国"
        counts = count_words(clean_learning_text(text))
        self.assertEqual(counts, Counter({("han", "中国"): 2, ("latin", "python"): 1}))


if __name__ == "__main__":
    unittest.main()
