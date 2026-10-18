"""Crash and learning checks for 26-key input with the word model and a non-empty user dictionary.

1.14.10 crashed on the first letter once the user dictionary held words (the joint decoder's de-duplication of
learned entries read moved-from list elements). Fresh-dictionary regression runs cannot see that, so this probe:

1. types every single letter and some two-letter inputs with an empty user dictionary;
2. commits a few words (as a person picking candidates), then types the same short inputs again;
3. picks 这都可以 for zdky once and 自动化 for zdh twice and checks that each then comes first.

Usage: python joint_learning_probe.py --dll DIR --work DIR --t9-ngram MODEL [--rime-ice-extra YAML]
The data directory is a fresh copy (lexicon_regression.prepare_android); exit code 1 on any failure.
"""
import argparse
import ctypes as C
import os
import string
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import lexicon_regression as lr  # noqa: E402


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dll", required=True, type=Path)
    parser.add_argument("--work", required=True, type=Path)
    parser.add_argument("--t9-ngram", required=True, type=Path)
    parser.add_argument("--rime-ice-extra", type=Path)
    args = parser.parse_args()
    os.environ["CYIME_T9_NGRAM"] = str(args.t9_ngram)
    if args.rime_ice_extra:
        os.environ["CYIME_RIME_ICE_EXTRA"] = str(args.rime_ice_extra)
    args.work.mkdir(parents=True, exist_ok=True)
    data = lr.prepare_android(args.work)
    os.add_dll_directory(str(args.dll))
    rime = C.CDLL(str(args.dll / "rime.dll"))
    t = lr.Traits()
    t.data_size = C.sizeof(lr.Traits) - C.sizeof(C.c_int)
    t.shared_data_dir = t.user_data_dir = str(data).encode()
    t.distribution_name = t.distribution_code_name = b"joint-learning-probe"
    t.distribution_version, t.app_name, t.min_log_level = b"1", b"rime.joint_learning_probe", 2
    rime.RimeSetup(C.byref(t))
    rime.RimeInitialize(C.byref(t))
    if rime.RimeStartMaintenance(True):
        rime.RimeJoinMaintenanceThread()
    rime.RimeCreateSession.restype = C.c_size_t
    session = C.c_size_t(rime.RimeCreateSession())
    assert rime.RimeSelectSchema(session, b"rime_ice")
    rime.RimeSetOption(session, b"ascii_mode", False)
    rime.RimeSetProperty(session, b"cyime_neighbors", lr.qwerty_neighbors().encode())

    def top(text, n=5):
        rime.RimeClearComposition(session)
        for ch in text:
            rime.RimeProcessKey(session, ord(ch), 0)
        it, out = lr.Iter(), []
        if rime.RimeCandidateListBegin(session, C.byref(it)):
            while len(out) < n and rime.RimeCandidateListNext(C.byref(it)):
                out.append(it.candidate.text.decode())
            rime.RimeCandidateListEnd(C.byref(it))
        return out

    def pick(text, word):
        candidates = top(text, 10)
        rime.RimeSelectCandidate(session, candidates.index(word))

    short = list(string.ascii_lowercase) + ["nh", "zd", "wm", "ni", "zhe"]
    failures = []
    for text in short:  # an access violation here ends the process, which the caller sees as a failure
        if not top(text):
            failures.append(f"no candidate for {text} (empty user dictionary)")
    for text, word in (("nihao", "你好"), ("ni", "你"), ("women", "我们"), ("zhe", "这")):
        pick(text, word)
    for text in short:
        if not top(text):
            failures.append(f"no candidate for {text} (after learning)")
    for text, word, times in (("zdky", "这都可以", 1), ("zdh", "自动化", 2)):
        for _ in range(times):
            pick(text, word)
        first = top(text)[:1]
        print(text, "after", times, "pick(s):", top(text))
        if first != [word]:
            failures.append(f"{text}: picked {word} {times}x but first is {first}")
    rime.RimeDestroySession(session)
    rime.RimeFinalize()
    for failure in failures:
        print("FAIL", failure)
    print("PASS" if not failures else f"{len(failures)} failure(s)")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
