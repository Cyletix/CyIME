"""Read local sources, archives and APK bytes. Archived helpers are never executed."""
import hashlib
import json
import re
import sys
import tomllib
from zipfile import ZipFile


def require(condition, message):
    if not condition:
        raise ValueError(message)


class ContractMismatch(ValueError):
    pass


def read(path):
    return path.read_text(encoding="utf-8-sig")


def api(root):
    versions = tomllib.loads(read(root / "gradle/libs.versions.toml"))["versions"]
    build = read(root / "app/build.gradle.kts")
    settings = []
    for field in ("compileSdk", "minSdk", "targetSdk"):
        key = "android" + field[0].upper() + field[1:]
        require(re.search(rf"^\s*{field}\s*=\s*libs\.versions\.{key}\.get\(\)\.toInt\(\)", build, re.M),
                f"{field} 未绑定版本目录")
        settings.append(f"{field}={versions[key]}")
    return "当前构建配置：" + "，".join(settings) + "。这些值不能确定某台设备的 SDK_INT 或宿主能力。"


def wubi(root):
    import yaml
    result = []
    for path in sorted((root / "app/src/main/assets/rime").glob("wubi*.schema.yaml")):
        schema = yaml.safe_load(read(path))
        active = "reverse_lookup_translator" in schema["engine"]["translators"]
        reverse = schema.get("reverse_lookup", {})
        result.append(f"{schema['schema']['schema_id']}（" +
                      (f"{reverse.get('dictionary')}, 前缀 {reverse.get('prefix', '无独立前缀')}" if active
                       else "无反查翻译器声明") + "）")
    require(result, "五笔资源缺失")
    return "当前随包方案：" + "、".join(result) + "。用户补丁可以另行改变最终方案。"


def accents(root):
    import yaml
    config = yaml.safe_load(read(root / "app/src/main/assets/xime.yaml"))
    keys = config["keyboard"]["qwerty"]["keys"]
    missing = []
    for letter, tones in {"a": "āáǎà", "e": "ēéěè", "i": "īíǐì", "o": "ōóǒò", "u": "ūúǔù"}.items():
        options = keys[letter]["long_press"]["values"]
        absent = "".join(t for t in tones if t not in options)
        if absent:
            missing.append(f"{letter} 缺 {absent}")
    if missing:
        raise ContractMismatch("当前默认中文 26 键长按表：" + "，".join(missing))
    return "五个元音的四个拼音声调均列入默认长按表。"


def model(archive):
    # Validate the original manifests, raw events, protocols and user dictionary before comparing.
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ime_lab"))
    import t9_replay_compare as replay
    folder = archive / "t9-rank-20261105"
    details = []
    for state in ("empty", "seeded"):
        summary = json.loads(read(folder / ("compare-model-" + state) / "comparison.json"))
        before, after = (replay._load_run(folder / Path(summary[k].replace("\\", "/")).name)
                         for k in ("baseline", "candidate"))
        replay.require_compatible(before, after)
        require(not before["execution_issues"] and not after["execution_issues"], "回放存在未完成事件")
        comp = replay.compare_snapshots(before["snapshots"], after["snapshots"])
        details.append(f"{state} {comp['event_count']} 个匹配事件、目标退步 {len(comp['regressions'])}、"
                       f"进步 {len(comp['improvements'])}、首选变化 {len(comp['top_changes'])}；归档速度门禁 {summary['speed_gate']}")
    return "复算 11 月 5 日 v3 与 clean1105 的同源回放：" + "。".join(details) + "。仅适用于这两份历史模型。"


def stroke(archive):
    folder = archive / "pr-input-20261107"
    old = json.loads(read(folder / "stroke-android-before.json"))
    new = json.loads(read(folder / "stroke-fixed-results.json"))["candidates"]
    full = "phnphnphhzhszhshzs"
    wild = "ph*phnphhz'hszhshzs"
    for key, word in [(full, "笔画"), ("phnphnphhz'hszhshzs", "笔画"), ("phnphnphhz", "笔"), ("hszhshzs", "画")]:
        require(old[key][0] == new[key][0] == word, f"{key}: 新旧单字或全笔顺顺序不同")
    require(not old[wild] and new[wild][0] == "笔画" and new[wild.replace("'", "")][0] == "笔画",
            "历史通配组词对照不成立")
    require(not new["****"], "超限通配应无结果")
    abbreviated = new["phn'hsz"]
    return f"历史候选文件：同一通配输入旧版空，新版首选「笔画」；全笔顺新旧版均首选「笔画」。前三笔分词简打返回 {abbreviated}。"


def glass(archive):
    from PIL import Image, ImageChops
    folder = archive / "key-glass-20261107"
    def pixels(name):
        with Image.open(folder / name) as image:
            require(image.size == (270, 224), f"玻璃参考图尺寸变化：{image.size}")
            return image.convert("RGB")
    diffs = {}
    for version in ("before", "after"):
        ref = pixels(f"{version}-scene-0.png")
        aligned = pixels(f"{version}-scene-1.png")
        moved = pixels(f"{version}-scene-2.png")
        interiors = set()
        different = 0
        aligned_different = 0
        for row in range(3):
            for column in range(3):
                x, y = 9 + 80 * column, 21 + 60 * row
                bounds = (x+12, y+10, x+68, y+50)
                original = ref.crop(bounds)
                interiors.add(original.tobytes())
                for a, b, c in zip(original.getdata(), aligned.crop(bounds).getdata(), moved.crop(bounds).getdata()):
                    different += a != c
                    aligned_different += a != b
        require(len(interiors) == 9 and aligned_different == 0, "原图或对齐状态无效")
        require(ImageChops.difference(ref, moved).getbbox() and ImageChops.difference(aligned, moved).getbbox(),
                "整张图完全相同，无法证明折射仍存在")
        diffs[version] = different
    require(diffs["before"] > 0 and diffs["after"] == 0, f"键心对照不成立：{diffs}")
    return f"重新读取六张归档图片。九个键心共 20,160 个像素，取样错位后旧版 {diffs['before']} 个偏离原图，修后 {diffs['after']} 个；键心以外仍有变化。"


def voice(archive):
    folder = archive / "voice-duck-20261107"
    def volumes(name):
        return {step: int(value) for step, value in re.findall(r"^(.*?) volume=(\d+) expected=\d+",
                                                               read(folder / name), re.M)}
    old, new = volumes("tablet-old.log"), volumes("tablet.log")
    cold = "speaking during cold load"
    require(old[cold] == old["before"] == new["before"], "冷启动的基线不一致")
    require(new[cold] == new["speaking with warm model"] < new["before"], "冷/热启动没有压低")
    for label in ("released before model ready", "after decoding", "cancelled"):
        require(new[label] == new["before"], f"{label} 媒体音量未还原")
    return f"归档实测档位：冷启动前 {new['before']}，旧版开麦 {old[cold]}，修后开麦 {new[cold]}；提前松手、解码结束、取消均恢复 {new['before']}。"


def shift(archive):
    folder = archive / "rotated-shift-20261107"
    old, new = (read(folder / f"shift-{v}.log") for v in ("before", "after"))
    require("commits=[]" in old and "commits=[L, candidate:1]" in new, "旧新拖选提交结果不符")
    target = r"shift=\[.*?\] letter=\[.*?\] cand=\[.*?\]"
    require(re.search(target, old)[0] == re.search(target, new)[0], "对照手势目标不同")
    return "归档拖选：相同坐标，旧版两次均未上屏，修后依次为 L、第二候选；本轮用 JVM 像素测试检查单线。"


def lock(archive):
    folder = archive / "floating-lock-controls-20261107"
    results = []
    for mode in ("normal", "edge", "rotated-edge"):
        log = read(folder / f"shift-{mode}-after.log")
        states = [(int(t), state) for t, state in re.findall(r"continuous hold at (\d+) ms is locked=(true|false)", log)]
        require(len(states) == 2 and states[0][0] < 500 <= states[1][0] and [s[1] for s in states] == ["true", "false"],
                f"{mode}: {states}")
        require("candidate:1" in log and "move]" in log, f"{mode} 锁定后的输入/解锁后的拖动记录不完整")
        results.append(f"{mode} {states[0][0]}/{states[1][0]} ms")
    return "归档短于/长于半秒的锁态各一对：" + "，".join(results) + "。"


def clipboard(archive):
    folder = archive / "clipboard-group-order-20261107"
    require("old group remained below text" in read(folder / "before.log"), "缺少旧版跨分类失败对照")
    order = "group:g1 TEXT LINK ADDRESS PHONE EMAIL IMAGE"
    for name in ("after.log", "reopen.log", "board.log"):
        require(order in read(folder / name), f"{name} 未保留跨分类顺序")
    require("group:g16 TEXT" in read(folder / "edge.log"), "拖到长列表底端未保存")
    require("delete confirmation passed" in read(folder / "actions.log"), "改名/删除检查不完整")
    return "归档的设置和键盘各自重启后次序一致：收藏、文本、链接。旧版收藏仍在文本下；长列表中已拖过第 16 个自建组。"


def apk(path):
    if path is None:
        raise FileNotFoundError("未指定已有 APK（--apk）")
    with path.open("rb") as f:
        digest = hashlib.file_digest(f, "sha256").hexdigest()
    receipt = json.loads(read(path.parent / "latest.json"))
    require(any(f["sha256"] == digest and f["bytes"] == path.stat().st_size for f in receipt["files"]),
            "APK 未匹配同目录的构建回执")
    with ZipFile(path) as bundle:
        largest = sorted(bundle.infolist(), key=lambda entry: entry.compress_size, reverse=True)[:3]
    return (f"离线构建回执 {receipt['version']} / {receipt['versionCode']}，SHA-256 {digest}，"
            f"APK {path.stat().st_size:,} 字节。压缩后最大的三项：" +
            "，".join(f"{e.filename} {e.compress_size:,} 字节" for e in largest) + "。")


def collect(root, archives, package, selected):
    probes = {"api": (api, root), "wubi": (wubi, root), "accents": (accents, root), "model": (model, archives), "apk": (apk, package),
              **{f.__name__: (f, archives) for f in (stroke, glass, voice, shift, lock, clipboard)}}
    result = {}
    for name, (probe, arg) in probes.items():
        if name not in selected:
            continue
        try:
            result[name] = dict(status="ok", detail=probe(arg))
        except ContractMismatch as error:
            result[name] = dict(status="mismatch", detail=str(error))
        except (FileNotFoundError, ModuleNotFoundError) as error:
            result[name] = dict(status="missing", detail=str(error))
        except Exception as error:
            result[name] = dict(status="error", detail=f"{type(error).__name__}: {error}")
    return result
