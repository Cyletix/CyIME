<h1 align="center">CyIME</h1>

<p align="center">
  <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">繁體中文</a>
</p>

**CyIME** is a Cyletix-maintained Android input method built on the [Rime](https://rime.im/) engine, covering Chinese (Wubi / Pinyin / 9-key / Shuangpin), Japanese kana, English, handwriting and voice input.

## Project identity

- Public identity: app name **CyIME**, maintainer **Cyletix**, install ID `com.cyletix.cyime`, APK name `CyIME-<version>-<abi>.apk`.
- The source is derived from upstream [Xime](https://github.com/ximeiorg/Xime) (by Kor1 / kingzcheung and contributors) and stays under [GNU GPL v3](LICENSE). Upstream and third-party notices are kept in [LICENSE](LICENSE), `app/src/main/assets/NOTICE.txt` and [TRADEMARKS.md](TRADEMARKS.md).
- To keep tracking upstream with few merge conflicts, the source namespace (`com.kingzcheung.xime`), plugin IDs, `xime.yaml` / `xime.custom.yaml`, the extension market and the index protocol are intentionally unchanged; only the install identity (applicationId) is independent. See [identity notes](docs/cyime-identity.md).

## Screenshots

<!-- Screenshots pending: add images under docs/Screenshot/ and insert a table here, e.g.
<table align="center">
  <tr>
    <td><img src="docs/Screenshot/xxx.jpg" width="180"><br><p align="center">Caption</p></td>
  </tr>
</table>
-->

## Features

- **Multiple input schemes** — bundled Pinyin dictionary plus Wubi / Pinyin / mixed schemes, custom schemes (Shuangpin, stroke, 9-key / 14-key), downloadable or importable over Wi-Fi
- **Rime engine** — mature and stable Rime (librime)
- **Rich keyboard layouts** — QWERTY, T9 Pinyin, stroke, handwriting, number (with calculator), symbols and Emoji
- **Floating keyboard** — draggable translucent card
- **Voice input** — local offline streaming recognition, plus online ASR plugins
- **AI suggestions** — Transformer-based next-word prediction (optional)
- **Material 3 UI** — light / dark themes and multiple color schemes
- **Keyboard and toolbar customization** — height / position, toolbar buttons, key sound and vibration
- **Swipe gestures** — cursor movement, delete, symbols
- **Clipboard manager** — history, quick send and pinning, with optional two-way sync via plugins
- **Physical keyboard support** — floating candidate bar for Bluetooth keyboards
- **Plugin ecosystem** — Lua plugins from the in-app extension store (emoji, clipboard sync, online ASR, WebDAV backup, ...)

## Requirements

- Android 9.0 (API 28) or newer

## Installation

### Download from Releases

1. Download `CyIME-<version>-<abi>.apk` for your ABI from [Releases](https://github.com/Cyletix/CyIME/releases):
   - `arm64-v8a` — most modern phones
   - `armeabi-v7a` — 32-bit devices
   - `x86_64` / `x86` — emulators
   - `universal` — all ABIs, larger package
2. Enable **CyIME** in system settings and select it as the current input method.

> **Upgrading from the old `com.kingzcheung.xime` build**: the install identity is now `com.cyletix.cyime`, so Android treats it as a separate app. The two can coexist, and data / settings from the old build are not migrated automatically. If GitHub downloads are slow, build the APK yourself (see below).

### Plugins (optional)

Lua plugins (`.xipk`) are installed from the in-app extension store: kaomoji, stickers, online speech recognition (FunAsr, Volcano, ...), WebDAV clipboard sync and backup. Plugin IDs and the market protocol stay upstream-compatible.

## Build

```bash
# Clone with submodules
git clone --recursive https://github.com/Cyletix/CyIME.git

# Or initialize submodules in an existing clone
git submodule update --init --recursive

# Debug / release builds
./gradlew assembleDebug
./gradlew assembleRelease
```

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Rime (librime) · JNI (Native C++)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md): open an Issue first, keep changes minimal, sign every commit with GPG.

## Acknowledgments

- Upstream [Xime](https://github.com/ximeiorg/Xime), the starting point of this branch
- [Rime](https://rime.im/) · [Trime](https://github.com/osfans/trime) · [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) · [onnxruntime](https://github.com/microsoft/onnxruntime)

## License

[GPLv3](LICENSE). Upstream code remains copyright of its authors; this branch is maintained by Cyletix. The "Xime" name, logo and other brand assets are **not** covered by GPLv3 — see [TRADEMARKS.md](TRADEMARKS.md).
