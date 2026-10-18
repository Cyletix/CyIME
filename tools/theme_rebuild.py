"""Tonal themes: the 20 themes retired in 1.9.15, rebuilt with a one-hue OKLCH lightness ladder.

Usage: python tools/theme_rebuild.py [--no-shot] [--out DIR]
  Writes app/src/main/java/com/kingzcheung/xime/ui/theme/TonalThemes.kt (the shipped colors),
  plus palettes.json and HTML/PNG mockups in DIR (default .codex-artifacts/theme-rebuild-20261016).
Rules: surfaces share the theme hue at low chroma, lightness alone separates background/keys/function keys,
only the enter key carries strong chroma; every text/surface pair is checked, glass included.
"""
import json, math, os, subprocess, sys
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HERE = sys.argv[sys.argv.index("--out") + 1] if "--out" in sys.argv else os.path.join(ROOT, ".codex-artifacts", "theme-rebuild-20261016")
SRC = os.path.join(ROOT, "app", "src", "main", "assets", "themes")
SRC_URI = "file:///" + SRC.replace(os.sep, "/")
KOTLIN = os.path.join(ROOT, "app", "src", "main", "java", "com", "kingzcheung", "xime", "ui", "theme", "TonalThemes.kt")
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

# ---------- color math ----------
def to_lin(c): return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
def to_srgb(c): c = max(0.0, min(1.0, c)); return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
def oklab(rgb):
    r, g, b = (to_lin(x) for x in rgb[:3])
    l = (0.4122214708*r + 0.5363325363*g + 0.0514459929*b) ** (1/3)
    m = (0.2119034982*r + 0.6806995451*g + 0.1073969566*b) ** (1/3)
    s = (0.0883024619*r + 0.2817188376*g + 0.6299787005*b) ** (1/3)
    return (0.2104542553*l + 0.7936177850*m - 0.0040720468*s,
            1.9779984951*l - 2.4285922050*m + 0.4505937099*s,
            0.0259040371*l + 0.7827717662*m - 0.8086757660*s)
def _lab2lin(L, a, b):
    l = (L + 0.3963377774*a + 0.2158037573*b) ** 3
    m = (L - 0.1055613458*a - 0.0638541728*b) ** 3
    s = (L - 0.0894841775*a - 1.2914855480*b) ** 3
    return (4.0767416621*l - 3.3077115913*m + 0.2309699292*s,
            -1.2684380046*l + 2.6097574011*m - 0.3413193965*s,
            -0.0041960863*l - 0.7034186147*m + 1.7076147010*s)
def lch(rgb):
    L, a, b = oklab(rgb); return L, math.hypot(a, b), math.degrees(math.atan2(b, a)) % 360
def from_lch(L, C, h):
    for _ in range(60):  # pull chroma in until the color is in sRGB gamut
        r, g, b = _lab2lin(L, C*math.cos(math.radians(h)), C*math.sin(math.radians(h)))
        if min(r, g, b) >= -1e-4 and max(r, g, b) <= 1 + 1e-4: break
        C *= 0.92
    return tuple(to_srgb(x) for x in (r, g, b))
def hx(v): v = int(v); return ((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255
def hexs(c): return '#%02X%02X%02X' % tuple(round(x * 255) for x in c[:3])
def lum(c): return 0.2126*to_lin(c[0]) + 0.7152*to_lin(c[1]) + 0.0722*to_lin(c[2])
def contrast(a, b):
    la, lb = lum(a), lum(b); return (max(la, lb) + .05) / (min(la, lb) + .05)
def mix(a, b, t): return tuple(a[i]*(1-t) + b[i]*t for i in range(3))

# ---------- themes (ids/names/sources from HEAD xime.yaml, removed in 1.9.15) ----------
THEMES = [
    # id, name, family, source color, original gradient stops (unused for color), image
    ("ocean_blue", "海洋蔚蓝", "solid", 0x1A73E8),
    ("sunset_orange", "落日橙光", "solid", 0xE65100),
    ("slate_gray", "沉稳石墨", "solid", 0x424242),
    ("rose_pink", "浪漫玫瑰", "solid", 0xAD1457),
    ("teal_cyan", "青碧如水", "solid", 0x00796B),
    ("sakura_pink", "樱花粉黛", "pastel", 0xE91E63),
    ("sky_blue", "晴空浅蓝", "pastel", 0x0288D1),
    ("mint_green", "薄荷浅绿", "pastel", 0x2E7D32),
    ("lilac_purple", "丁香浅紫", "pastel", 0x8E24AA),
    ("peach_orange", "蜜桃浅橙", "pastel", 0xEF6C00),
    ("cream_beige", "奶油米白", "pastel", 0x8D6E63),
    ("aurora_gradient", "极光青蓝", "gradient", 0x00B0A8),
    ("sunset_gradient", "落日余晖", "gradient", 0xFF7043),
    ("ocean_gradient", "深海渐变", "gradient", 0x1E88E5),
    ("purple_gradient", "紫梦渐变", "gradient", 0x7E57C2),
    ("rose_gradient", "玫瑰渐变", "gradient", 0xEC407A),
    ("forest_gradient", "森林迷雾", "gradient", 0x43A047),
    ("kitosonita_landscape", "夜幕之影", "image", "lll-sh-silhouette-bg.jpg"),
    ("mrfawdi_nature", "暮色之桥", "image", "nordseher-bridge-bg.jpg"),
    ("olivcelso_desert", "大漠流金", "image", "olivcelso-desert-bg.jpg"),
]

# 2026-10-18 用户：每个色系只用一个。蓝、紫、灰已有柔和蓝、薰衣草紫、黑白，其余色系各留一套。
SHIPPED = ["sakura_pink", "sunset_gradient", "aurora_gradient", "mint_green", "cream_beige", "olivcelso_desert"]

# Whole-image mean is pulled blue by the sky; the theme is named for the sand.
HUE_OVERRIDE = {"olivcelso_desert": (85.0, 0.11)}

# Surface ladder (L, C). One hue per theme; chroma only meaningful on the enter key.
LADDER = {
    "solid": {
        False: dict(bg=(0.945, 0.022), key=(0.995, 0.006), func=(0.875, 0.035), text=(0.24, 0.035)),
        True:  dict(bg=(0.165, 0.025), key=(0.315, 0.022), func=(0.245, 0.028), text=(0.955, 0.010)),
    },
    # Pastel family: same steps, about twice the surface chroma, so it reads as "tinted" not "gray".
    "pastel": {
        False: dict(bg=(0.925, 0.050), key=(0.990, 0.014), func=(0.855, 0.070), text=(0.27, 0.060)),
        True:  dict(bg=(0.195, 0.045), key=(0.335, 0.045), func=(0.265, 0.055), text=(0.955, 0.018)),
    },
}
LADDER["gradient"] = LADDER["solid"]
LADDER["image"] = LADDER["solid"]

RED = {False: hx(0xB3261E), True: hx(0xF2B8B5)}   # Material error, used for 清空 / 忘掉
WHITE, BLACK = (1, 1, 1), (0, 0, 0)

def image_hue(path):
    im = Image.open(path).convert('RGB').resize((64, 64))
    A = B = 0.0
    px = list(im.getdata())
    for p in px:
        L, a, b = oklab(tuple(x / 255 for x in p)); A += a; B += b
    A /= len(px); B /= len(px)
    return math.degrees(math.atan2(B, A)) % 360, math.hypot(A, B)

def build(theme_id, name, family, src):
    if family == "image":
        hue, sc = image_hue(os.path.join(SRC, src)); srcC = max(sc * 2.2, 0.06); srcL = 0.55
        if theme_id in HUE_OVERRIDE: hue, srcC = HUE_OVERRIDE[theme_id]
    else:
        srcL, srcC, hue = lch(hx(src))
    k = min(1.0, srcC / 0.08)          # near-gray source keeps surfaces neutral
    out = {"id": theme_id, "name": name, "family": family, "hue": round(hue, 1), "src_chroma": round(srcC, 3)}
    for dark in (False, True):
        lad = LADDER[family][dark]
        p = {r: from_lch(L, C * k, hue) for r, (L, C) in lad.items()}
        p["ftext"] = p["text"]
        # Enter: the only chromatic surface. The app draws the enter glyph in the function-key text color
        # (light: theme text, dark: white), so light uses a bright tint and dark a mid tone, each >= 4.5:1.
        aC = srcC if srcC < 0.08 else max(srcC, 0.12)
        if dark:
            L = 0.60; on = WHITE; acc = from_lch(L, aC, hue)
            while min(contrast(acc, on), glass_worst(p, True, [(acc, on)])) < 4.5 and L > 0.3:
                L -= 0.01; acc = from_lch(L, aC, hue)
        else:
            L = 0.74; on = p["text"]; acc = from_lch(L, aC, hue)
            # Glass darkens a light keycap a little, so the enter tint must also pass on glass.
            while min(contrast(acc, on), glass_worst(p, False, [(acc, on)])) < 4.5 and L < 0.95:
                L += 0.01; acc = from_lch(L, aC, hue)
        p["accent"], p["on_accent"] = acc, on
        # Selected candidate / active tab: accent hue as text, kept >= 4.5 on bg.
        sL = 0.86 if dark else 0.42
        sel = from_lch(sL, min(aC, 0.15), hue)
        while contrast(sel, p["bg"]) < 4.5: sL += 0.02 if dark else -0.02; sel = from_lch(sL, min(aC, 0.15), hue)
        p["selected"] = sel
        p["hint"] = from_lch(0.72 if dark else 0.50, 0.02 * k, hue)       # swipe hints on keys
        p["muted"] = from_lch(0.78 if dark else 0.42, 0.02 * k, hue)      # candidate text / icons
        p["divider"] = from_lch(0.30 if dark else 0.86, 0.02 * k, hue)
        p["chip"] = from_lch(0.26 if dark else 0.90, 0.05 * k, hue)       # selected tab pill
        p["red"] = RED[dark]
        # Gradient: two stops of the same hue around the bg step.
        if family == "gradient":
            bL, bC = lad["bg"]
            p["bg1"] = from_lch(bL - 0.005 if not dark else bL - 0.035, bC * k, hue)
            p["bg2"] = from_lch(bL - 0.065 if not dark else bL + 0.035, (bC + 0.045) * k, hue)
        if family == "image":
            p["overlay_alpha"] = fit_overlay(os.path.join(SRC, src), p, dark)
        out["dark" if dark else "light"] = p
    out["checks"] = checks(out)
    return out

def fit_overlay(path, p, dark):
    """Smallest bg-colored wash over the photo that keeps candidate text and toolbar icons >= 4.5:1."""
    im = Image.open(path).convert('RGB').resize((200, 120)).filter(ImageFilter.GaussianBlur(2))
    px = [tuple(x / 255 for x in q) for q in im.getdata()]
    for step in range(0, 21):
        a = 0.30 + step * 0.03
        worst = min(min(contrast(mix(q, p["bg"], a), p["muted"]), contrast(mix(q, p["bg"], a), p["selected"]))
                    for q in px[::7])
        if worst >= 4.5: return round(a, 2)
    return 0.9

def glass_worst(p, dark, pairs):
    """Worst label contrast with glass at default opacities over white and black hosts."""
    def tint(bg, fg, op):
        darks = lum(fg) > lum(bg)
        ch = [bg[i] if darks else 1 - bg[i] for i in range(3)]
        peak = max(ch)
        if peak <= 0: return bg
        sc = min(1 / op, 1 / peak)
        return tuple(c * sc if darks else 1 - c * sc for c in ch)
    bg_a, key_a = (0.8, 0.2) if dark else (0.2, 0.8)
    worst = 99
    for host in (WHITE, BLACK):
        panel = mix(host, p["bg"], bg_a)
        for key, text in pairs:
            worst = min(worst, contrast(text, mix(panel, tint(key, text, key_a), key_a)))
    return round(worst, 2)

def checks(t):
    r = {}
    for m in ("light", "dark"):
        p = t[m]
        bgs = [p["bg"]] + ([p["bg1"], p["bg2"]] if "bg1" in p else [])
        r[m] = {
            "字/键": round(contrast(p["text"], p["key"]), 2),
            "字/功能键": round(contrast(p["ftext"], p["func"]), 2),
            "键/底": round(min(contrast(p["key"], b) for b in bgs), 2),
            "功能键/字母键": round(contrast(p["func"], p["key"]), 2),
            "回车字": round(contrast(p["on_accent"], p["accent"]), 2),
            "候选/底": round(min(contrast(p["muted"], b) for b in bgs), 2),
            "选中候选/底": round(min(contrast(p["selected"], b) for b in bgs), 2),
            "提示字/键": round(contrast(p["hint"], p["key"]), 2),
            "红色/键": round(contrast(p["red"], p["key"]), 2),
            # Key/function labels are clamped to 4.5 at runtime (FrostedGlassConfig.legibleOver); enter is not.
            "玻璃字/键(未校正)": glass_worst(p, m == "dark", [(p["key"], p["text"]), (p["func"], WHITE if m == "dark" else p["ftext"])]),
            "玻璃回车": glass_worst(p, m == "dark", [(p["accent"], p["on_accent"])]),
        }
    return r

# ---------- HTML ----------
ICONS = {
 "logo": '<path d="M12 2l3 5-3 5-3-5zM12 12l3 5-3 5-3-5zM2 12l5-3 5 3-5 3zM12 12l5-3 5 3-5 3z"/>',
 "kbd": '<path d="M3 6h18v12H3z" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M6 9h2v2H6zm4 0h2v2h-2zm4 0h2v2h-2zM7 13h10v2H7z"/>',
 "emoji": '<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="9" cy="10" r="1.3"/><circle cx="15" cy="10" r="1.3"/><path d="M8 14q4 4 8 0" fill="none" stroke="currentColor" stroke-width="1.8"/>',
 "move": '<path d="M12 2l3 3h-2v6h6V9l3 3-3 3v-2h-6v6h2l-3 3-3-3h2v-6H5v2l-3-3 3-3v2h6V5H9z"/>',
 "clip": '<path d="M7 4h10v17H7z" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M9 3h6v3H9zM9 10h6v1.6H9zm0 4h6v1.6H9z"/>',
 "hand": '<path d="M4 16c3-9 6-9 4-2s3 4 5-1 3 2 7 1" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>',
 "mic": '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M6 11a6 6 0 0012 0M12 17v4" fill="none" stroke="currentColor" stroke-width="1.8"/>',
 "down": '<path d="M6 9l6 6 6-6" fill="none" stroke="currentColor" stroke-width="2.2"/>',
 "shift": '<path d="M12 4l8 9h-5v7H9v-7H4z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>',
 "del": '<path d="M8 6h12v12H8l-5-6z"/><path d="M11 9l6 6m0-6l-6 6" stroke="var(--key)" stroke-width="1.8"/>',
 "globe": '<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18" fill="none" stroke="currentColor" stroke-width="1.5"/>',
 "enter": '<path d="M19 6v6H7m0 0l4-4m-4 4l4 4" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>',
 "pin": '<path d="M8 11V8a4 4 0 018 0v3h1v9H7v-9z"/>',
}
def icon(n, size=18): return f'<svg viewBox="0 0 24 24" width="{size}" height="{size}" fill="currentColor">{ICONS[n]}</svg>'

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:Roboto,"Segoe UI","Microsoft YaHei UI","Microsoft YaHei",sans-serif;background:#F4F4F4;color:#222}
.kb{width:360px;border-radius:14px;overflow:hidden;background:var(--bgimg);color:var(--text);position:relative}
.tool{display:flex;justify-content:space-between;align-items:center;height:40px;padding:0 10px;color:var(--muted)}
.cand{display:flex;align-items:center;height:44px;padding:12px 10px 0;gap:16px;font-size:17px;color:var(--muted);white-space:nowrap;overflow:hidden}
.cand .pre{position:absolute;top:2px;left:10px;font-size:11px;color:var(--muted)}
.cand .sel{color:var(--selected);font-weight:600}
.rows{padding:2px 3px 6px}
.row{display:flex;gap:5px;margin:0 0 7px;justify-content:center}
.k{position:relative;height:44px;flex:1;background:var(--key);border-radius:7px;display:flex;align-items:center;justify-content:center;font-size:20px;box-shadow:0 1px 0 rgba(0,0,0,var(--sh))}
.k .h{position:absolute;top:2px;right:4px;font-size:9px;color:var(--hint)}
.k.f{background:var(--func);color:var(--ftext);font-size:15px}
.k.e{background:var(--accent);color:var(--onacc)}
.k.sp{flex:4.2;font-size:12px;color:var(--hint)}
.pop{position:absolute;right:6px;top:84px;background:var(--key);border-radius:10px;padding:6px 14px;box-shadow:0 3px 10px rgba(0,0,0,.28);font-size:15px;color:var(--red);font-weight:600;display:flex;gap:6px;align-items:center;z-index:3}
.sym{display:grid;grid-template-columns:repeat(5,1fr);gap:6px;padding:4px 6px}
.sym .k{height:42px}
.symbar{display:flex;gap:5px;padding:6px 6px 8px}
.tabs{flex:3.6;display:flex;justify-content:space-around;align-items:center;background:var(--func);border-radius:7px;font-size:14px;color:var(--muted)}
.tabs b{color:var(--selected);border-bottom:2px solid var(--selected);padding:4px 2px}
.clip{padding:4px 8px 10px}
.ctabs{display:flex;gap:4px;font-size:13px;color:var(--muted);margin-bottom:8px;align-items:center}
.ctabs span{padding:5px 9px;border-radius:8px}
.ctabs .on{background:var(--chip);color:var(--text);font-weight:600}
.cards{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.card{background:var(--key);border-radius:10px;padding:8px 22px 8px 10px;overflow:hidden;word-break:break-all;height:64px;font-size:13px;position:relative;line-height:1.35}
.card .pin{position:absolute;right:7px;top:6px;color:var(--muted)}
.card small{display:block;color:var(--muted);font-size:10px;margin-top:4px}
.more{display:flex;align-items:center;gap:8px;margin:8px 0;font-size:11px;color:var(--muted)}
.more i{flex:1;height:1px;background:var(--divider)}
.more span{border:1px solid var(--divider);border-radius:12px;padding:2px 9px}
.forget{color:var(--red);font-weight:600}
"""

def vars_css(p, family, img=None, dark=False):
    v = {k: hexs(p[k]) for k in ("bg", "key", "func", "text", "ftext", "accent", "selected", "hint", "muted", "divider", "chip", "red")}
    v["onacc"] = hexs(p["on_accent"])
    if family == "gradient":
        bgimg = f"linear-gradient(180deg,{hexs(p['bg1'])},{hexs(p['bg2'])})"
    elif family == "image":
        r, g, b = (round(x*255) for x in p["bg"])
        a = p["overlay_alpha"]
        bgimg = f"linear-gradient(rgba({r},{g},{b},{a}),rgba({r},{g},{b},{a})),url('{SRC_URI}/{img}') center/cover"
    else:
        bgimg = v["bg"]
    s = ";".join(f"--{k}:{val}" for k, val in v.items())
    return f"{s};--bgimg:{bgimg};--sh:{'.35' if dark else '.10'}"

def keyboard_main(compose=True, popup=False):
    hints = dict(q="1",w="2",e="3",r="4",t="5",y="6",u="7",i="8",o="9",p="0",a="~",s="/",d=":",f=";",g="“",h="”",j="-",k="(",l=")",z="*",x="@",c="、",v="?",b="!",n="%",m="#")
    def keys(s): return "".join(f'<div class="k">{c}<span class="h">{hints[c]}</span></div>' for c in s)
    top = ('<div class="cand" style="position:relative"><span class="pre">ni hao</span>'
           '<span class="sel">你好</span><span>拟好</span><span>你</span><span>妮</span><span>泥</span><span>尼</span>'
           f'<span style="margin-left:auto">{icon("down")}</span></div>') if compose else toolbar()
    pop = f'<div class="pop">清空 {icon("del",16)}</div>' if popup else ""
    return f"""{top}{pop}<div class="rows">
<div class="row">{keys("qwertyuiop")}</div>
<div class="row" style="padding:0 17px">{keys("asdfghjkl")}</div>
<div class="row"><div class="k f" style="flex:1.45">{icon("shift")}</div>{keys("zxcvbnm")}<div class="k f" style="flex:1.45">{icon("del",20)}</div></div>
<div class="row"><div class="k f" style="flex:1.2">!@#</div><div class="k f" style="flex:1.2">123</div><div class="k">，</div><div class="k sp">中文26键</div><div class="k f">{icon("globe")}</div><div class="k e" style="flex:1.7">{icon("enter",22)}</div></div>
</div>"""

def toolbar():
    return '<div class="tool">' + "".join(icon(n) for n in ("logo","kbd","emoji","move","clip","hand","mic","down")) + '</div>'

def symbol_panel():
    syms = "，。？！、：；…“”‘’（）《"
    cells = "".join(f'<div class="k">{c}</div>' for c in syms)
    return f"""{toolbar()}<div class="sym">{cells}</div>
<div class="symbar"><div class="k f" style="flex:1">中文</div><div class="k f" style="flex:1">123</div>
<div class="tabs"><b>常用</b><span>最近</span><span>中</span><span>英</span></div><div class="k f" style="flex:1.2">{icon("del",20)}</div></div>"""

def clip_panel():
    return f"""{toolbar()}<div class="clip">
<div class="ctabs"><span class="on">全部</span><span>文本</span><span>链接</span><span>地址</span><span>电话</span><span style="margin-left:auto">固定 3</span></div>
<div class="cards"><div class="card">明天上午十点开会，记得带资料<span class="pin">{icon("pin",13)}</span><small>固定</small></div>
<div class="card">https://github.com/Cyletix/CyIME<span class="pin">{icon("pin",13)}</span><small>链接 · 固定</small></div></div>
<div class="more"><i></i><span>还有 1 条固定 ⌄</span><i></i></div>
<div class="cards"><div class="card">收货地址：东京都新宿区…<small>2 分钟前</small></div>
<div class="card">长按菜单：复制 · 固定 · <span class="forget">删除</span><small>刚刚</small></div></div></div>"""

def theme_page(t):
    cols = []
    for mode in ("light", "dark"):
        p = t[mode]; dark = mode == "dark"
        st = vars_css(p, t["family"], t.get("img"), dark)
        c = t["checks"][mode]
        cols.append(f"""<div class="col"><h3>{'浅色' if not dark else '深色'}<em>字/键 {c['字/键']} · 键/底 {c['键/底']} · 回车 {c['回车字']} · 选中候选 {c['选中候选/底']}</em></h3>
<div class="kb" style="{st}">{keyboard_main(True, True)}</div>
<div class="kb" style="{st}">{symbol_panel()}</div>
<div class="kb" style="{st}">{clip_panel()}</div></div>""")
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{CSS}
.page{{padding:18px 20px;width:800px}} h1{{font-size:22px;margin-bottom:2px}} .sub{{font-size:13px;color:#555;margin-bottom:12px}}
.cols{{display:flex;gap:40px}} .col{{display:flex;flex-direction:column;gap:12px}} h3{{font-size:15px}} h3 em{{font-style:normal;font-weight:400;font-size:11px;color:#666;margin-left:8px}}
</style></head><body><div class="page"><h1>{t['name']} <span style="font-size:14px;color:#777">{t['id']}</span></h1>
<div class="sub">{FAMILY_LABEL[t['family']]} · 色相 {t['hue']}° · 回车 {hexs(t['light']['accent'])} / {hexs(t['dark']['accent'])}</div>
<div class="cols">{''.join(cols)}</div></div></body></html>"""

FAMILY_LABEL = {"solid": "纯色系（低彩度表面）", "pastel": "浅彩系（表面彩度约为纯色系两倍）",
                "gradient": "渐变系（同色相上下两档明度渐变）", "image": "图片系（同色相遮罩 + 不透明键帽）"}

def overview_page(themes):
    cells = []
    for t in themes:
        pair = "".join(f'<div class="kb mini" style="{vars_css(t[m], t["family"], t.get("img"), m=="dark")}">{keyboard_main(False)}</div>' for m in ("light", "dark"))
        cells.append(f'<div class="cell"><div class="nm">{t["name"]} <span>{FAMILY_LABEL[t["family"]][:3]}</span></div><div class="pair">{pair}</div></div>')
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{CSS}
.grid{{display:grid;grid-template-columns:repeat(4,1fr);gap:14px 18px;padding:16px;width:2960px}}
.cell .nm{{font-size:18px;font-weight:600;margin:0 0 6px 2px}} .cell .nm span{{font-size:13px;color:#777;font-weight:400}}
.pair{{display:flex;gap:8px}}
</style></head><body><div class="grid">{''.join(cells)}</div></body></html>"""

def shoot(html, png, w, h):
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1.5",
                    f"--window-size={w},{h}", f"--screenshot={png}", "file:///" + html.replace("\\", "/")],
                   check=True, capture_output=True)

def kt_color(c): return "0x%06X" % int(hexs(c)[1:], 16)

def kotlin(themes):
    roles = ("bg", "key", "func", "text", "accent", "selected", "muted", "divider", "chip")
    def arr(p): return "longArrayOf(" + ", ".join(kt_color(p[r]) for r in roles) + ")"
    lines = []
    for t in themes:
        L, D = t["light"], t["dark"]
        bg = ""
        if t["family"] == "gradient":
            bg = (",\n            BackgroundConfig(type = \"gradient\", angle = 90, "
                  f"colors = listOf({kt_color(L['bg1'])}, {kt_color(L['bg2'])}), "
                  f"colorsDark = listOf({kt_color(D['bg1'])}, {kt_color(D['bg2'])}))")
        elif t["family"] == "image":
            bg = (",\n            BackgroundConfig(type = \"image\", src = \"themes/" + t["img"] + "\", fit = \"cover\", "
                  f"overlayAlpha = {L['overlay_alpha']}f, overlayAlphaDark = {D['overlay_alpha']}f, "
                  f"overlayColor = {kt_color(L['bg'])}, overlayColorDark = {kt_color(D['bg'])})")
        name = t["name"]
        lines.append(f'        tonal("{t["id"]}", "{name}",\n            {arr(L)},\n            {arr(D)}{bg}),')
    body = "\n".join(lines)
    return f"""package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import com.kingzcheung.xime.settings.BackgroundConfig

/**
 * 1.9.15 移除的旧主题，按单色相 OKLCH 明度阶梯重建后恢复（2026-10-18 用户要求加回，每个色系只留一套）。
 * 表面与主题同色相、低彩度，只靠明度分层；只有回车带彩度。回车图标沿用功能键文字色，
 * 所以浅色回车取亮色调、深色回车取中明度，两者都与图标保持 4.5:1 以上。
 * 生成：python tools/theme_rebuild.py（色值与对比度检查都在脚本里），不要手改色值。
 */
object TonalThemes {{
    // 每组依次为：底、字母键、功能键、文字、回车、选中候选/强调、候选文字、分隔线、选中标签底。
    private fun tonal(id: String, name: String, light: LongArray, dark: LongArray, background: BackgroundConfig? = null): KeyboardColorScheme {{
        fun Long.c() = Color(0xFF000000 or this)
        return KeyboardColorScheme(
            id = id, name = name,
            specialKeyLight = light[2].c(), specialKeyDark = dark[2].c(),
            accentLight = light[5].c(), accentDark = dark[5].c(),
            primaryContainerLight = light[8].c(), primaryContainerDark = dark[8].c(),
            surfaceLight = light[0].c(), surfaceDark = dark[0].c(),
            keyBgLight = light[1].c(), keyBgDark = dark[1].c(),
            keyTextColorLight = light[3].c(), keyTextColorDark = dark[3].c(),
            candidateTextColorLight = light[6].c(), candidateTextColorDark = dark[6].c(),
            candidateSelectedTextColorLight = light[5].c(), candidateSelectedTextColorDark = dark[5].c(),
            dividerColorLight = light[7].c(), dividerColorDark = dark[7].c(),
            keyboardBackground = background,
            useThemeColors = true,
            enterKeyLight = light[4].c(), enterKeyDark = dark[4].c(),
            specialKeyTextColorLight = light[3].c(), specialKeyTextColorDark = dark[3].c(),
        )
    }}

    fun create(): List<KeyboardColorScheme> = listOf(
{body}
    )
}}
"""

def main():
    os.makedirs(os.path.join(HERE, "html"), exist_ok=True); os.makedirs(os.path.join(HERE, "png"), exist_ok=True)
    themes = []
    for tid, name, fam, src in THEMES:
        t = build(tid, name, fam, src)
        if fam == "image": t["img"] = src
        themes.append(t)
    ser = [{**{k: v for k, v in t.items() if k not in ("light", "dark")},
            **{m: {k: (hexs(v) if isinstance(v, tuple) else v) for k, v in t[m].items()} for m in ("light", "dark")}} for t in themes]
    json.dump(ser, open(os.path.join(HERE, "palettes.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    for i, t in enumerate(themes, 1):
        hp = os.path.join(HERE, "html", f"{i:02d}-{t['id']}.html")
        open(hp, "w", encoding="utf-8").write(theme_page(t))
        if "--no-shot" not in sys.argv: shoot(hp, os.path.join(HERE, "png", f"{i:02d}-{t['id']}.png"), 800, 905)
    open(KOTLIN, "w", encoding="utf-8", newline="\n").write(kotlin([t for i in SHIPPED for t in themes if t["id"] == i]))
    hp = os.path.join(HERE, "html", "00-overview.html")
    open(hp, "w", encoding="utf-8").write(overview_page(themes))
    if "--no-shot" not in sys.argv: shoot(hp, os.path.join(HERE, "png", "00-overview.png"), 2992, 1580)
    for t in themes:
        print(t["name"], json.dumps(t["checks"], ensure_ascii=False))

if __name__ == "__main__":
    main()
