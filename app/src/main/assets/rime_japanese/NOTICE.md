# Japanese input data

This directory vendors rime-jaroomaji by lazy fox chan, from:
https://github.com/lazyfoxchan/rime-jaroomaji/tree/3e14573828668721ec1eabdc56546ddcca8277e2

The `jaroomaji.*` assets, README.md and LICENSE are unmodified upstream files.
The four large dictionaries are fetched at build time from the exact revision
above by `app/build-logic/tasks-japanese.gradle.kts`, verified against recorded
SHA-256 checksums, and bundled in full in each APK. The remaining files live
in this directory. No dictionary download is needed on the phone.
The schema and non-dictionary code use Apache License 2.0 (see LICENSE).
The dictionaries retain their individual original copyright and license headers:

- jaroomaji.mozc and jaroomaji.mozcemoji: Google Mozc and its dictionary data,
  including BSD-style and IPAdic notices preserved in each file.
- jaroomaji.jmdict and jaroomaji.kanjidic2: Electronic Dictionary Research and
  Development Group data, CC BY-SA 4.0; https://www.edrdg.org/edrdg/licence.html
  and https://creativecommons.org/licenses/by-sa/4.0/.

`japanese.schema.yaml` and `japanese_kana.schema.yaml` are Xime-CyletixFork adaptations (modified 2026-09-23) of
the upstream schema, with different names/IDs and Japanese mode enabled by
default, plus full-width parentheses for the phone flick keys. They use the original jaroomaji dictionary and conversion rules.
The kana touch layout emits the same romanized readings as the QWERTY layout.
