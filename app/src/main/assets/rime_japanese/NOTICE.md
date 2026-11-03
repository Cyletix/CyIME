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
its `Dictionary/` directory. `app/build-logic/tasks-japanese.gradle.kts` fetches it at build
time, checks every file against a recorded content hash and bundles it in each APK as
`azookey/dictionary.zip`; the phone unpacks it to `rime/azookey/dictionary`. No download is needed
on the phone. The Apache License 2.0 text is in LICENSE in this directory.

Changed by CyIME (2026-11-03): its `louds/` files also hold about 400,000 nouns from the Mozc open-source
dictionary that azooKey lacks (names such as 暁山, places, organisations), built by
`tools/azookey/build-dictionary.sh` from https://github.com/google/mozc/tree/00724c6d6395cd3cc6ca6e32ec0975f962d6c800/src/data/dictionary_oss
(Mozc: BSD 3-Clause License, Copyright 2010-2018 Google Inc.). As its README states, that vocabulary
is basically that of IPAdic (mecab-ipadic-2.7.0-20070801) enriched with the Okinawa dictionary, which is
in the public domain. IPAdic is licensed as follows:

> Copyright 2000, 2001, 2002, 2003 Nara Institute of Science and Technology. All Rights Reserved.
>
> Use, reproduction, and distribution of this software is permitted. Any copy of this software,
> whether in its original form or modified, must include both the above copyright notice and the
> following paragraphs.
>
> Nara Institute of Science and Technology (NAIST), the copyright holders, disclaims all warranties
> with regard to this software, including all implied warranties of merchantability and fitness, in
> no event shall NAIST be liable for any special, indirect or consequential damages or any damages
> whatsoever resulting from loss of use, data or profits, whether in an action of contract,
> negligence or other tortuous action, arising out of or in connection with the use or performance
> of this software.
>
> A large portion of the dictionary entries originate from ICOT Free Software. The following
> conditions for ICOT Free Software applies to the current dictionary as well.
>
> Each User may also freely distribute the Program, whether in its original form or modified, to any
> third party or parties, PROVIDED that the provisions of Section 3 ("NO WARRANTY") will ALWAYS
> appear on, or be attached to, the Program, which is distributed substantially in the same form as
> set out herein and that such intended distribution, if actually made, will neither violate or
> otherwise contravene any of the laws and regulations of the countries having jurisdiction over the
> User or the intended distribution itself.
>
> NO WARRANTY
>
> The program was produced on an experimental basis in the course of the research and development
> conducted during the project and is provided to users as so produced on an experimental basis.
> Accordingly, the program is provided without any warranty whatsoever, whether express, implied,
> statutory or otherwise. The term "warranty" used herein includes, but is not limited to, any
> warranty of the quality, performance, merchantability and fitness for a particular purpose of the
> program and the nonexistence of any infringement or violation of any right of any third party.
>
> Each user of the program will agree and understand, and be deemed to have agreed and understood,
> that there is no warranty whatsoever for the program and, accordingly, the entire risk arising
> from or otherwise connected with the program is assumed by the user.
>
> Therefore, neither ICOT, the copyright holder, or any other organization that participated in or
> was otherwise related to the development of the program and their respective officials,
> directors, officers and other employees shall be held liable for any and all damages, including,
> without limitation, general, special, incidental and consequential damages, arising out of or
> otherwise in connection with the use or inability to use the program or any product, material or
> result produced or otherwise obtained by using the program, regardless of whether they have been
> advised of, or otherwise had knowledge of, the possibility of such damages at any time during the
> project or thereafter. Each user will be deemed to have agreed to the foregoing by his or her
> commencement of use of the program. The term "use" as used herein includes, but is not limited
> to, the use, modification, copying and distribution of the program and the production of
> secondary products from the program.
>
> In the case where the program, whether in its original form or modified, was distributed or
> delivered to or received by a user from any person, organization or entity other than ICOT,
> unless it makes or grants independently of ICOT any specific warranty to the user in writing,
> such person, organization or entity, will also be exempted from and not be held liable to the
> user for any such damages as noted above as far as the program is concerned.

Mozc BSD 3-Clause License:

> Copyright 2010-2018, Google Inc. All rights reserved.
>
> Redistribution and use in source and binary forms, with or without modification, are permitted
> provided that the following conditions are met:
>
> * Redistributions of source code must retain the above copyright notice, this list of conditions
>   and the following disclaimer.
> * Redistributions in binary form must reproduce the above copyright notice, this list of
>   conditions and the following disclaimer in the documentation and/or other materials provided
>   with the distribution.
> * Neither the name of Google Inc. nor the names of its contributors may be used to endorse or
>   promote products derived from this software without specific prior written permission.
>
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
> IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
> FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
> CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
> DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
> DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
> IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
> OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

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
