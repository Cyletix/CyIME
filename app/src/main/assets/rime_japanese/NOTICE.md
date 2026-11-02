# Japanese input data

## Conversion: azooKey

Kana-kanji conversion uses AzooKeyKanaKanjiConverter by Miwa / Ensan (MIT License,
https://github.com/azooKey/AzooKeyKanaKanjiConverter, commit 25a6346bf76931c1cf2214015b808875ea82f4bf),
built without Foundation/ICU and without the Zenzai neural model as `libCyAzooKey.so`
(source, build script and the exact changes: `tools/azookey` in the CyIME repository).
The library also contains swift-collections 1.7.5, swift-algorithms 1.2.1, swift-numerics 1.1.1,
swift-foundation (FoundationEssentials) and the Swift standard library from Swift 6.2
(Apache License 2.0 with Runtime Library Exception, https://swift.org/LICENSE.txt), and LLVM libc++
and libc++abi from the Android NDK (Apache License 2.0 with LLVM Exceptions, https://llvm.org/LICENSE.txt).

MIT License, Copyright (c) 2023 Miwa / Ensan:

> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
> associated documentation files (the "Software"), to deal in the Software without restriction,
> including without limitation the rights to use, copy, modify, merge, publish, distribute,
> sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or
> substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
> NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
> NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
> OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
> CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

The dictionary is azooKey_dictionary_storage (Apache License 2.0,
https://github.com/azooKey/azooKey_dictionary_storage, commit 4d418525b090cf49c219819d05a7e3cc2a4346eb),
the unmodified `Dictionary/` directory. `app/build-logic/tasks-japanese.gradle.kts` fetches it at build
time, checks every file against a recorded content hash and bundles it in each APK as
`azookey/dictionary.zip`; the phone unpacks it to `rime/azookey/dictionary`. No download is needed
on the phone. The Apache License 2.0 text is in LICENSE in this directory.

CyIME additions inside the library: the particles は/へ/を are also accepted as pronounced
(wa/e/o), by copying their dictionary entries under those readings at start-up.

## Schemas: rime-jaroomaji

The schemas come from rime-jaroomaji by lazy fox chan (Apache License 2.0, see LICENSE):
https://github.com/lazyfoxchan/rime-jaroomaji/tree/3e14573828668721ec1eabdc56546ddcca8277e2
README.md and LICENSE are the unmodified upstream files.

`japanese.schema.yaml` and `japanese_kana.schema.yaml` are Xime-CyletixFork adaptations (modified 2026-09-23) of
the upstream schema, with different names/IDs and Japanese mode enabled by default, plus full-width
parentheses for the phone flick keys. On 2026-11-02 CyIME replaced their dictionary translator, its
spelling rules and preedit rules with the azooKey translator; `jaroomaji.schema.yaml` got the same change.
The jaroomaji dictionaries are no longer bundled.
The kana touch layout emits the same romanized readings as the QWERTY layout.
