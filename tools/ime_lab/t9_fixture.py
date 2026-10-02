"""Prepare and validate the fixed input contract for the native T9 replay.

Prepare with one whitespace-separated pinyin syllable per Han character in the
entire UTF-8 source (a JSON string array also works):
  python tools/ime_lab/t9_fixture.py prepare --text chat.txt --pinyin chat.pinyin \
      --output chat.json
  python tools/ime_lab/t9_fixture.py validate chat.json

Alternatively, --auto-pinyin uses an already installed pypinyin. It never
installs dependencies or calls an API. Automatic readings are unreviewed;
explicit readings are provided, not certified. --readings corrections.json
overrides complete case readings, e.g. {"c001": ["shui", "bu", "zhao"]}.
Case IDs enumerate every contiguous Han span, including headings and speakers.
Non-Han characters remain verbatim in source_text and are not sent as keys.
This validates input/coverage, not pronunciation in context or output quality.
"""

import argparse
from copy import deepcopy
import hashlib
import importlib.metadata
import json
from pathlib import Path
import re
import sys
import unicodedata


KEYMAP = {letter: str(number) for number, letters in
          enumerate(("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"), 2)
          for letter in letters}
# CJK unified/compatibility ideographs, including supplementary-plane characters
# and ideographic zero. Offsets use Python Unicode code points, never UTF-16.
HAN_SPANS = re.compile(
    "[\u3007\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff"
    "\U00020000-\U0002a6df\U0002a700-\U0002b73f\U0002b740-\U0002b81f"
    "\U0002b820-\U0002ceaf\U0002ceb0-\U0002ebef\U0002ebf0-\U0002ee5f"
    "\U0002f800-\U0002fa1f\U00030000-\U0003134f\U00031350-\U000323af]+")
# The production T9PinyinMap::PinyinList inventory. A focused test detects drift.
PINYIN_SYLLABLES = frozenset("""
a ai an ang ao
ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu
ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi chong
chou chu chua chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo
da dai dan dang dao de dei den deng di dia dian diao die ding diu dong dou du duan dui dun duo
e ei en eng er
fa fan fang fei fen feng fiao fo fou fu
ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo
ha hai han hang hao he hei hen heng hong hou hu hua huai huan huang hui hun huo
ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun
ka kai kan kang kao ke kei ken keng kong kou ku kua kuai kuan kuang kui kun kuo
la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu lo long lou
lu luan lve lun luo lv
ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu
na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou
nu nuan nve nun nuo nv
o ou
pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu
qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun
ran rang rao re ren reng ri rong rou ru rua ruan rui run ruo
sa sai san sang sao se sen seng sha shai shan shang shao she shei shen sheng shi
shou shu shua shuai shuan shuang shui shun shuo si song sou su suan sui sun suo
ta tai tan tang tao te tei teng ti tian tiao tie ting tong tou tu tuan tui tun tuo
wa wai wan wang wei wen weng wo wu
xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun
ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun
za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi
zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo
""".split())


class FixtureError(ValueError):
    """The declared source, readings, keys, or coverage are inconsistent."""


def require(condition, message):
    if not condition:
        raise FixtureError(message)


def normalize_pinyin(value):
    """Canonical lowercase engine spelling; accept ü/u:/v and optional tone 0–5.

    Conventional marked vowels (e.g. nǚ) are accepted too. A tone digit must
    appear once, at the end. Invalid or unsupported production syllables fail.
    """
    require(isinstance(value, str), f"Pinyin must be a string: {value!r}")
    original = value
    value = value.strip().lower().replace("u:", "ü")
    value = unicodedata.normalize("NFD", value)
    # Keep the diaeresis meaningful: u + diaeresis is the engine's v.
    value = value.replace("u\u0308", "v")
    value = re.sub("[\u0300\u0301\u0304\u030c]", "", value)
    require(bool(re.fullmatch(r"[a-z]+[0-5]?", value)), f"Invalid pinyin: {original!r}")
    value = re.sub(r"[0-5]$", "", value)
    require(value in PINYIN_SYLLABLES, f"Unsupported T9 pinyin syllable: {original!r}")
    return value


def fixture_counts(cases):
    by_role = {}
    for case in cases:
        role = by_role.setdefault(case.get("role", "body"), {"cases": 0, "han_characters": 0})
        role["cases"] += 1
        role["han_characters"] += len(case["text"])
    body = [case for case in cases if case.get("is_body", True)]
    result = {
        "cases": len(cases), "han_characters": sum(len(case["text"]) for case in cases),
        "digit_keypresses": sum(len(case["keys"]) for case in cases),
        "body_cases": len(body), "body_han_characters": sum(len(case["text"]) for case in body),
        "body_digit_keypresses": sum(len(case["keys"]) for case in body), "by_role": by_role,
    }
    if any("utterance_id" in case for case in cases):
        result["utterances"] = len({case.get("utterance_id") for case in cases if case.get("utterance_id")})
    return result


def validate_fixture(fixture, *, require_source=False):
    """Return a validated deep copy with canonical readings and derived keys.

    Existing keys/counts are checked, never silently repaired. If source_text
    is present, require its exact SHA256 and one case per contiguous Han span,
    in source order. Cases-only fixtures are allowed unless require_source=True.
    Original input objects and files are never changed.
    """
    require(isinstance(fixture, dict), "Fixture must be an object")
    result = deepcopy(fixture)
    require(result.get("fixture_version", 1) == 1, "Unsupported fixture_version")
    require(isinstance(result.get("id"), str) and bool(result["id"].strip()), "Fixture ID is required")
    if "input_contract" in result:
        contract = result["input_contract"]
        require(isinstance(contract, dict), "input_contract must be an object")
        for key, expected in (("language", "zh-Hans"), ("scheme", "full_pinyin"), ("layout", "t9")):
            require(contract.get(key) == expected, f"Unsupported input_contract {key}: {contract.get(key)!r}")
        if "keymap" in contract:
            expected_map = {str(number): letters for number, letters in
                            enumerate(("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"), 2)}
            require(contract["keymap"] == expected_map, "Incorrect T9 input_contract keymap")
    cases = result.get("cases")
    require(isinstance(cases, list) and bool(cases), "Fixture has no cases")
    seen = set()
    for index, case in enumerate(cases, 1):
        require(isinstance(case, dict), f"Case {index} must be an object")
        case_id = case.get("id")
        require(isinstance(case_id, str) and bool(case_id.strip()), f"Case {index}: ID is required")
        require(case_id not in seen, f"Duplicate fixture case ID: {case_id}")
        seen.add(case_id)
        target = case.get("text")
        require(isinstance(target, str) and bool(HAN_SPANS.fullmatch(target)),
                f"{case_id}: text must be a nonempty contiguous Han span")
        tokens = case.get("pinyin")
        require(isinstance(tokens, list) and len(tokens) == len(target),
                f"{case_id}: require one pinyin syllable per Han character")
        try:
            tokens = [normalize_pinyin(value) for value in tokens]
        except FixtureError as error:
            raise FixtureError(f"{case_id}: {error}") from error
        syllable_keys = ["".join(KEYMAP[letter] for letter in token) for token in tokens]
        keys = "".join(syllable_keys)
        require("keys" not in case or case["keys"] == keys, f"{case_id}: incorrect T9 keys")
        require("syllable_keys" not in case or case["syllable_keys"] == syllable_keys,
                f"{case_id}: incorrect syllable keys")
        case.update(pinyin=tokens, keys=keys, syllable_keys=syllable_keys)
        case.setdefault("role", "body")
        case.setdefault("is_body", case["role"] == "body")
        require(isinstance(case["role"], str) and bool(case["role"].strip()), f"{case_id}: invalid role")
        require(isinstance(case["is_body"], bool), f"{case_id}: is_body must be boolean")
    require(not require_source or "source_text" in result, "source_text is required")
    if "source_text" in result:
        source = result["source_text"]
        require(isinstance(source, str), "source_text must be a string")
        require(result.get("source_sha256_utf8") == hashlib.sha256(source.encode("utf-8")).hexdigest(),
                "Source SHA256 mismatch or missing source_sha256_utf8")
        spans = list(HAN_SPANS.finditer(source))
        require(len(spans) == len(cases), "Incomplete source Han coverage: case count differs from source spans")
        for case, span in zip(cases, spans):
            require(type(case.get("source_start")) is int and type(case.get("source_end")) is int,
                    f"{case['id']}: source offsets must be integer code point positions")
            require((case["source_start"], case["source_end"], case["text"]) ==
                    (span.start(), span.end(), span.group()), f"{case['id']}: source offset/order/coverage mismatch")
            if "source_line" in case:
                require(case["source_line"] == source.count("\n", 0, span.start()) + 1,
                        f"{case['id']}: source_line mismatch")
    if "counts" in result:
        require(isinstance(result["counts"], dict), "Fixture counts must be an object")
        calculated = fixture_counts(cases)
        for key, value in calculated.items():
            require(key not in result["counts"] or result["counts"][key] == value,
                    f"Fixture counts mismatch: {key}")
    return result


def prepare_fixture(text, pinyin_tokens=None, *, fixture_id="t9_text", source_label="Supplied text",
                    auto_pinyin=False, readings=None):
    """Create deterministic version-1 cases; readings maps case IDs to full arrays."""
    require(isinstance(text, str), "Source must be text")
    require(isinstance(fixture_id, str) and bool(fixture_id.strip()), "Fixture ID is required")
    spans = list(HAN_SPANS.finditer(text))
    require(bool(spans), "Source contains no Han characters")
    require((pinyin_tokens is not None) != bool(auto_pinyin), "Supply pinyin tokens or auto_pinyin, exclusively")
    require(readings is None or isinstance(readings, dict), "Readings corrections must map case IDs to arrays")
    overrides = readings or {}
    valid_ids = {f"c{index:03d}" for index in range(1, len(spans) + 1)}
    require(set(overrides) <= valid_ids, f"Unknown reading override case IDs: {sorted(set(overrides) - valid_ids, key=str)}")
    provenance = {"review_status": "provided", "generator": "explicit one-syllable-per-Han readings"}
    if auto_pinyin:
        try:
            import pypinyin
        except ImportError as error:
            raise FixtureError("--auto-pinyin requires an already installed pypinyin; provide --pinyin instead") from error
        provenance = {"review_status": "unreviewed",
                      "generator": f"pypinyin {importlib.metadata.version('pypinyin')} lazy_pinyin"}
        # Preserve phrase context inside each span. Never cross a punctuation boundary.
        pinyin_tokens = [token for span in spans for token in pypinyin.lazy_pinyin(span.group(), errors=lambda chars: list(chars))]
    require(isinstance(pinyin_tokens, (list, tuple)), "Pinyin tokens must be an array")
    require(len(pinyin_tokens) == sum(len(span.group()) for span in spans),
            f"Pinyin length mismatch: source has {sum(len(span.group()) for span in spans)} Han characters, got {len(pinyin_tokens)} syllables")
    cases, cursor, corrections = [], 0, []
    for index, span in enumerate(spans, 1):
        case_id = f"c{index:03d}"
        supplied = list(pinyin_tokens[cursor:cursor + len(span.group())])
        chosen = overrides.get(case_id, supplied)
        require(isinstance(chosen, list) and len(chosen) == len(span.group()),
                f"{case_id}: reading override must contain one syllable per Han character")
        if case_id in overrides:
            corrections.append({"case_id": case_id, "before": supplied, "after": chosen})
        cases.append({"id": case_id, "text": span.group(), "pinyin": chosen,
                      "role": "body", "is_body": True,
                      "source_start": span.start(), "source_end": span.end(),
                      "source_line": text.count("\n", 0, span.start()) + 1})
        cursor += len(span.group())
    provenance["overrides"] = corrections
    result = validate_fixture({
        "fixture_version": 1, "id": fixture_id, "source_label": source_label, "source_text": text,
        "source_sha256_utf8": hashlib.sha256(text.encode("utf-8")).hexdigest(),
        "input_contract": {
            "language": "zh-Hans", "scheme": "full_pinyin", "layout": "t9",
            "keymap": {str(number): letters for number, letters in
                       enumerate(("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"), 2)},
            "pinyin": "One full canonical toneless pinyin syllable per Han character; ü is v.",
            "case_boundary": "Every contiguous Han span is an independent composition; all other characters remain source literals.",
            "source_offsets": "Python Unicode code point offsets, start inclusive, end exclusive.",
            "body_filter": "All generated cases default to body; heading/speaker classification requires explicit editing.",
            "simulation_limit": "Input fixture only; pronunciation, candidate language quality and keyboard UI acceptance are not certified.",
        },
        "pronunciation_review": provenance, "cases": cases,
    }, require_source=True)
    result["counts"] = fixture_counts(result["cases"])
    return result


def read_tokens(path):
    text = path.read_text(encoding="utf-8-sig")
    if text.lstrip().startswith("["):
        result = json.loads(text)
        require(isinstance(result, list), "Pinyin JSON must be an array")
        return result
    return text.split()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    subparsers = parser.add_subparsers(dest="command", required=True)
    prepare = subparsers.add_parser("prepare", help="Create a fixture without running or learning from the engine")
    prepare.add_argument("--text", type=Path, required=True, help="UTF-8 source file, preserved verbatim")
    source = prepare.add_mutually_exclusive_group(required=True)
    source.add_argument("--pinyin", type=Path, help="Whitespace tokens or JSON string array; one per Han character")
    source.add_argument("--auto-pinyin", action="store_true", help="Use installed pypinyin; marks readings unreviewed")
    prepare.add_argument("--readings", type=Path, help="JSON object of case ID -> full corrected pinyin array")
    prepare.add_argument("--id", default="t9_text", help="Stable fixture ID")
    prepare.add_argument("--label", default="Supplied text", help="Source description")
    prepare.add_argument("--output", type=Path, required=True, help="New output file; existing files are rejected")
    validate = subparsers.add_parser("validate", help="Validate source coverage, readings, keys and counts")
    validate.add_argument("fixture", type=Path)
    validate.add_argument("--require-source", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.command == "prepare":
            # newline="" prevents Windows CRLF from being silently rewritten before hashing.
            with args.text.open(encoding="utf-8-sig", newline="") as stream:
                source_text = stream.read()
            readings = json.loads(args.readings.read_text(encoding="utf-8-sig")) if args.readings else None
            fixture = prepare_fixture(source_text, read_tokens(args.pinyin) if args.pinyin else None,
                                      fixture_id=args.id, source_label=args.label,
                                      auto_pinyin=args.auto_pinyin, readings=readings)
            with args.output.open("x", encoding="utf-8", newline="\n") as stream:
                stream.write(json.dumps(fixture, ensure_ascii=False, indent=2) + "\n")
        else:
            fixture = validate_fixture(json.loads(args.fixture.read_text(encoding="utf-8-sig")),
                                       require_source=args.require_source)
        print(json.dumps({"id": fixture["id"], "valid": True, "counts": fixture_counts(fixture["cases"]),
                          "reading_status": fixture.get("pronunciation_review", {}).get("review_status", "legacy_unspecified"),
                          "language_quality": "not_assessed"}, ensure_ascii=False, indent=2))
        return 0
    except (FixtureError, OSError, json.JSONDecodeError) as error:
        print(f"Fixture error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
