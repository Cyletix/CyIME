"""Build app/src/main/assets/spanish/words.txt, the Spanish completion word list.

Frequencies come from FrequencyWords (OpenSubtitles 2018, pinned commit). Subtitles carry names,
English and typos, so only words the LibreOffice Spanish spelling dictionary accepts are kept;
a word it only accepts capitalized (Madrid, David) is stored capitalized.

    pip install spylls
    npm pack dictionary-es@4.0.0 && tar xzf dictionary-es-4.0.0.tgz
    python tools/spanish/build_words.py package/index app/src/main/assets/spanish/words.txt
"""
import re
import sys
import urllib.request

from spylls.hunspell import Dictionary

SOURCE = ("https://raw.githubusercontent.com/hermitdave/FrequencyWords/"
          "525f9b560de45753a5ea01069454e72e9aa541c6/content/2018/es/es_50k.txt")
LIMIT = 40000
SPANISH = re.compile(r"^[a-záéíóúüñ]+$")
# Single letters that are words: y, o, e, u, a. The rest are subtitle noise.
SINGLE = {"a", "y", "o", "e", "u"}


def main(dictionary_base: str, output: str) -> None:
    speller = Dictionary.from_files(dictionary_base)
    lines = urllib.request.urlopen(SOURCE).read().decode("utf-8").splitlines()
    seen, words = set(), []
    for line in lines:
        word, _count = line.split()
        if not SPANISH.match(word) or (len(word) == 1 and word not in SINGLE):
            continue
        form = word if speller.lookup(word) else word.capitalize() if speller.lookup(word.capitalize()) else None
        if form is None or form in seen:
            continue
        seen.add(form)
        words.append(form)
        if len(words) >= LIMIT:
            break
    with open(output, "w", encoding="utf-8", newline="\n") as file:
        file.write("\n".join(words) + "\n")
    print(f"{len(words)} words -> {output}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
