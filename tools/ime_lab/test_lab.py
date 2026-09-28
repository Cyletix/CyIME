import unittest
from ime_lab import evaluate, prior
from neighbors import alternatives

class LabTest(unittest.TestCase):
    def test_scenarios_are_not_pooled(self):
        case={"id":"c","input":"3","expected":["的"],"check":"rank","top_k":3}
        samples=[{"case_id":"c","input":"3","candidates":[{"text":"的"}],"latency_ms":v,"metadata":{"device":d}} for d,v in [("phone",1),("tablet",100)]]
        r=evaluate([case],samples)
        self.assertEqual([g["p95_ms"] for g in r["latency_groups"]],[1,100])
    def test_unknown_date_type_and_missing_case(self):
        cases=[{"id":"d","input":"77","expected":[],"check":"no_date","top_k":20},{"id":"missing","input":None}]
        r=evaluate(cases,[{"case_id":"d","input":"77","candidates":[{"text":"QQ"}]}])
        self.assertIsNone(r["results"][0]["pass"]);self.assertEqual(r["missing"],["missing"])
    def test_no_quote_forward_or_cross_message_ngrams(self):
        m={"is_self":True,"kind":"text","conversation_id":"c","message_id":"a","text":"你好"}
        r=prior([dict(m,is_quoted=True),dict(m,is_forwarded=True),dict(m,text="你"),dict(m,text="好",message_id="b")])
        self.assertEqual(r["ngrams"]["2"],[])
    def test_exact_first_one_substitution_and_layout_specific(self):
        keys=[dict(text=s,x=i*10,y=0,width=10,height=10) for i,s in enumerate("abc")]
        a=alternatives("ab",keys)
        self.assertTrue(a[0]["exact"])
        self.assertTrue(all(sum(x!=y for x,y in zip("ab",v["input"]))==1 for v in a[1:]))
        self.assertNotIn("cb",[v["input"] for v in a])
        keys[1]["x"],keys[2]["x"]=keys[2]["x"],keys[1]["x"]
        self.assertIn("cb",[v["input"] for v in alternatives("ab",keys)])
    def test_invalid_geometry_rejected(self):
        with self.assertRaises(ValueError): alternatives("a",[dict(text="a",x=0,y=0,width=0,height=1)])

if __name__=="__main__": unittest.main()
