import json
from contextlib import closing
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import patch
from learning_workbench import clean_note, continuations, build, export_data


class LearningWorkbenchTest(unittest.TestCase):
    def test_notes_remove_metadata_code_quotes_and_embeds(self):
        text = '---\nauthor: 私密\n---\n你好世界\n```python\n秘密代码\n```\n> 引用文字\n![[附件]]\n%%隐藏%%\n'
        result = clean_note(text)
        self.assertIn('你好世界',result)
        for bad in ('私密','秘密代码','引用文字','附件','隐藏'): self.assertNotIn(bad,result)

    def test_boundaries_never_create_cross_sentence_pairs(self):
        with patch('learning_workbench.jieba.cut',side_effect=lambda text,**kw:list(text)):
            self.assertNotIn(('甲','乙'),continuations('甲。乙'))
            self.assertIn(('甲','乙'),continuations('甲乙'))

    def test_private_build_edits_and_export_keep_mobile_counts(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); notes=root/'notes'; notes.mkdir()
            (notes/'a.md').write_text('我想看看这个项目。我想看看这个项目。',encoding='utf-8')
            (notes/'.trash').mkdir(); (notes/'.trash'/'deleted.md').write_text('不应导入',encoding='utf-8')
            phone=root/'phone.json'; phone.write_text(json.dumps({'bigrams':[{'tokens':['你','好'],'count':3}],'trigrams':[]}),encoding='utf-8')
            chats=root/'chats.jsonl'; chats.write_text(json.dumps({'platform':'codex','text':'我想看看这个项目。'},ensure_ascii=False)+'\n',encoding='utf-8')
            result=build(root/'review.sqlite3',phone,chats,notes,minimum=1)
            self.assertEqual(3,result['phone_observations']); self.assertEqual(1,result['notebook_documents'])
            with closing(sqlite3.connect(root/'review.sqlite3')) as db, db:
                original=export_data(db)
                self.assertTrue(original['profile']['entries'])
                db.execute("UPDATE entries SET enabled=0 WHERE kind='profile'")
                edited=export_data(db)
                self.assertEqual([],edited['profile']['entries'])
                self.assertEqual(original['bigrams'],edited['bigrams'])
            with self.assertRaises(FileExistsError): build(root/'review.sqlite3',phone,chats,notes)


if __name__=='__main__': unittest.main()
