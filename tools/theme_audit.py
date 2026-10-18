"""Theme legibility audit: replays CyIME's key color math on white and black backdrops.

Usage: python tools/theme_audit.py [--csv out.csv]

For each built-in theme it reports, for light and dark appearance:
  text   contrast of key labels on letter keys (opaque keyboard)
  spec   contrast of labels on function keys
  sep    contrast between a letter key and the keyboard surface
  glass  worst label contrast with glass on, over white and black backdrops,
         sweeping the background opacity from the default down to 0.2
The numbers drive which themes stay; the Kotlin guard in FrostedKeyColors enforces the same floor.
"""
import sys
import yaml

XIME = 'app/src/main/assets/xime.yaml'


def rgb(v):
    v = int(v)
    a = (v >> 24) & 0xFF if v > 0xFFFFFF else 0xFF
    return ((v >> 16) & 0xFF) / 255, ((v >> 8) & 0xFF) / 255, (v & 0xFF) / 255, a / 255


def lin(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def lum(c):
    return 0.2126 * lin(c[0]) + 0.7152 * lin(c[1]) + 0.0722 * lin(c[2])


def contrast(a, b):
    la, lb = lum(a), lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def over(top, bottom, alpha=None):
    a = top[3] if alpha is None else alpha
    return tuple(top[i] * a + bottom[i] * (1 - a) for i in range(3)) + (1.0,)


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3)) + (1.0,)


def lighten(c, f=0.45):
    return lerp(c, (1, 1, 1, 1), f)


def darken(c, f=0.7):
    return tuple(x * (1 - f) for x in c[:3]) + (1.0,)


def soft_dark_container(accent):
    return lerp((0x30 / 255, 0x34 / 255, 0x3C / 255, 1), accent, 0.30)


def glass_tint(bg, fg, opacity):
    if opacity <= 0 or opacity >= 1:
        return bg
    dark = lum(fg) > lum(bg)
    ch = [bg[i] if dark else 1 - bg[i] for i in range(3)]
    peak = max(ch)
    if peak <= 0:
        return bg
    scale = min(1 / opacity, 1 / peak)
    return tuple((c * scale) if dark else 1 - c * scale for c in ch) + (1.0,)


def scheme_from_config(entry, glob):
    primary = rgb(entry.get('primary_color', 0x8F73E2))
    bgcfg = entry.get('keyboard_background') or {}

    def bg(dark):
        t = bgcfg.get('type')
        if t == 'solid':
            v = bgcfg.get('color_dark' if dark else 'color', bgcfg.get('color'))
            if v is not None:
                return rgb(v)
        if t == 'gradient':
            v = bgcfg.get('colors_dark' if dark else 'colors') or bgcfg.get('colors')
            if v:
                return rgb(v[0])
        if t == 'image':
            return None
        legacy = entry.get('keyboard_bg_color')
        if legacy is not None:
            c = rgb(legacy)
            return darken(c) if dark else c
        return None

    kb_light = bg(False) or (1, 1, 1, 1)
    kb_dark = bg(True) or (0x1C / 255, 0x1B / 255, 0x1F / 255, 1)
    keycfg = entry.get('key_background') or {}

    def key(dark):
        if keycfg.get('type') == 'solid':
            v = keycfg.get('color_dark' if dark else 'color', keycfg.get('color'))
            if v is not None:
                return rgb(v)
        v = entry.get('key_bg_color_dark' if dark else 'key_bg_color')
        return rgb(v) if v is not None else None

    lightened = lighten(primary)
    return dict(
        image=bgcfg.get('type') == 'image',
        kb=(kb_light, kb_dark),
        key=(key(False) or rgb(glob['key_bg_color']), key(True) or rgb(glob['key_bg_color_dark'])),
        text=(rgb(entry.get('key_text_color', glob['key_text_color'])),
              rgb(entry.get('key_text_color_dark', glob['key_text_color_dark']))),
        special=(rgb(entry['special_key_bg_color']) if 'special_key_bg_color' in entry else lighten(primary, 0.8),
                 rgb(entry['special_key_bg_color_dark']) if 'special_key_bg_color_dark' in entry
                 else soft_dark_container(lightened)),
    )


def builtins():
    c = lambda v: rgb(v)
    return {
        'pure_black': dict(image=False, kb=(c(0xF2F2F2), c(0x000000)), key=(c(0xFFFFFF), c(0x1C1C1C)),
                           text=(c(0x202020), c(0xFFFFFF)), special=(c(0xDEDEDE), c(0x353535))),
        'soft_blue': dict(image=False, kb=(c(0xF4F6FA), c(0x191C22)), key=(c(0xFFFFFF), c(0x2E323A)),
                          text=(c(0x20242C), c(0xE9EDF5)), special=(c(0xDCE6FA), c(0x495A7D))),
    }


def audit(name, s):
    rows = []
    for dark in (False, True):
        i = 1 if dark else 0
        kb, key, text, special = s['kb'][i], s['key'][i], s['text'][i], s['special'][i]
        key_opaque = over(key, kb)
        t = contrast(text, key_opaque)
        sp = contrast(text, over(special, kb))
        sep = contrast(key_opaque, kb)
        # Glass: defaults swap with appearance (light bg .2 / key .8, dark bg .8 / key .2).
        bg_default, key_alpha = (0.8, 0.2) if dark else (0.2, 0.8)
        worst = 99
        for backdrop in ((1, 1, 1, 1), (0, 0, 0, 1)):
            for bg_alpha in sorted({bg_default, 0.5, 0.3, 0.2}):
                surface = over(kb, backdrop, bg_alpha)
                tint = glass_tint(key, text, key_alpha)
                cap = over(tint, surface, key_alpha)
                worst = min(worst, contrast(text, cap))
        rows.append((name, 'dark' if dark else 'light', t, sp, sep, worst, s['image']))
    return rows


def main():
    data = yaml.safe_load(open(XIME, encoding='utf-8'))
    glob = data['keyboard']['colors']
    schemes = builtins()
    for sid, entry in data['color_schemes'].items():
        if entry.get('dynamic_color'):
            continue
        schemes[sid] = scheme_from_config(entry, glob)
    print(f"{'theme':22} {'mode':5} {'text':>5} {'spec':>5} {'sep':>5} {'glass':>6}")
    out = []
    for sid, s in schemes.items():
        for r in audit(sid, s):
            out.append(r)
            flag = ' image' if r[6] else ''
            print(f"{r[0]:22} {r[1]:5} {r[2]:5.2f} {r[3]:5.2f} {r[4]:5.2f} {r[5]:6.2f}{flag}")
    if '--csv' in sys.argv:
        with open(sys.argv[sys.argv.index('--csv') + 1], 'w', encoding='utf-8') as f:
            f.write('theme,mode,text,special,separation,glass_worst,image\n')
            for r in out:
                f.write(','.join(str(x) for x in r) + '\n')


if __name__ == '__main__':
    main()
