"""Read a T9 word n-gram model (app/src/main/jni/librime-t9/src/t9_ngram_format.h, version 2 or compact version 3).

Usage: python ngram_query.py MODEL "好一点 了" "好一点 可" ...
Each argument is a space-separated word sequence; prints ln P of every transition
(the first word uses the frequency unigram, the second the bigram, later words the trigram)
and the total.
"""
import mmap
import struct
import sys
from array import array
from bisect import bisect_left

HEADER = struct.Struct("<8sIIIIQQQffQQQQQQQQQ")
HEADER3 = struct.Struct("<8sIIIIQQQffIIQQQQQQQQQQ")
LOG_SCALE = 1024.0


class Model:
    def __init__(self, path):
        self.file = open(path, "rb")
        self.m = mmap.mmap(self.file.fileno(), 0, access=mmap.ACCESS_READ)
        magic = self.m[:8]
        self.compact = magic == b"CYT9NGR3"
        if self.compact:
            (magic, version, self.words, self.eos, self.bos, self.bigrams, self.trigrams, self.capacity,
             self.unseen_lower, self.unseen_ml, self.shift, _reserved, self.hash_offset, self.word_offset,
             self.begin_offset, self.next_offset, self.logp_offset, self.backoff_offset, self.tri_base_offset,
             self.tri_lo_offset, self.tri_next_offset, self.tri_logp_offset) = HEADER3.unpack_from(self.m, 0)
            assert version == 3, version
        else:
            (magic, version, self.words, self.eos, self.bos, self.bigrams, self.trigrams, self.capacity,
             self.unseen_lower, self.unseen_ml, self.hash_offset, self.word_offset, self.begin_offset,
             self.next_offset, self.logp_offset, self.backoff_offset, self.tri_begin_offset,
             self.tri_next_offset, self.tri_logp_offset) = HEADER.unpack_from(self.m, 0)
            assert magic == b"CYT9NGR2" and version == 2, (magic, version)

    def _value(self, offset, index):
        """A stored log value: float in version 2, int16 / LOG_SCALE in version 3."""
        if self.compact:
            return struct.unpack_from("<h", self.m, offset + 2 * index)[0] / LOG_SCALE
        return struct.unpack_from("<f", self.m, offset + 4 * index)[0]

    def _tri_begin(self, b):
        if self.compact:
            base = struct.unpack_from("<I", self.m, self.tri_base_offset + 4 * (b >> self.shift))[0]
            return base + struct.unpack_from("<H", self.m, self.tri_lo_offset + 2 * b)[0]
        return struct.unpack_from("<I", self.m, self.tri_begin_offset + 4 * b)[0]

    def id(self, text):
        h = 14695981039346656037
        for b in text.encode("utf-8"):
            h = ((h ^ b) * 1099511628211) & 0xFFFFFFFFFFFFFFFF
        h = h or 1
        mask = self.capacity - 1
        i = h & mask
        while True:
            if self.compact:
                slot = struct.unpack_from("<Q", self.m, self.hash_offset + 8 * i)[0]
                if slot == 0:
                    return None
                if (slot >> 24) == (h >> 24):
                    return (slot & 0xFFFFFF) - 1
            else:
                key, ident = struct.unpack_from("<QI", self.m, self.hash_offset + 16 * i)
                if key == h:
                    return ident
                if key == 0:
                    return None
            i = (i + 1) & mask

    def entry(self, ident):
        if self.compact:
            q = struct.unpack_from("<hhh", self.m, self.word_offset + 6 * ident)
            return tuple(v / LOG_SCALE for v in q)
        return struct.unpack_from("<fff", self.m, self.word_offset + 16 * ident)

    def lower(self, ident):
        return self.unseen_lower if ident is None else self.entry(ident)[0]

    def ml(self, ident):
        return self.unseen_ml if ident is None else self.entry(ident)[1]

    def _find(self, offset, begin, end, value):
        if end <= begin:
            return None
        if self.compact:
            raw = self.m[offset + 3 * begin:offset + 3 * end]
            ids = [int.from_bytes(raw[3 * j:3 * j + 3], "little") for j in range(end - begin)]
        else:
            ids = array("I")
            ids.frombytes(self.m[offset + 4 * begin:offset + 4 * end])
        k = bisect_left(ids, value)
        return begin + k if k < len(ids) and ids[k] == value else None

    def logp(self, history, word):
        """ln P(word | history); history/word are model ids or None (unseen word)."""
        if history is None:
            return self.lower(word)
        if word is not None:
            begin, end = struct.unpack_from("<II", self.m, self.begin_offset + 4 * history)
            k = self._find(self.next_offset, begin, end, word)
            if k is not None:
                return self._value(self.logp_offset, k)
        return self.entry(history)[2] + self.lower(word)

    def logp3(self, history2, history, word):
        bigram = self.logp(history, word)
        if history2 is None or history is None:
            return bigram
        begin, end = struct.unpack_from("<II", self.m, self.begin_offset + 4 * history2)
        b = self._find(self.next_offset, begin, end, history)
        if b is None:
            return bigram
        if word is not None:
            t0, t1 = self._tri_begin(b), self._tri_begin(b + 1)
            k = self._find(self.tri_next_offset, t0, t1, word)
            if k is not None:
                return self._value(self.tri_logp_offset, k)
        return self._value(self.backoff_offset, b) + bigram


def main():
    model = Model(sys.argv[1])
    print(f"words={model.words} bigrams={model.bigrams} trigrams={model.trigrams} "
          f"unseen_lower={model.unseen_lower:.2f} unseen_ml={model.unseen_ml:.2f}")
    for sequence in sys.argv[2:]:
        words = sequence.split()
        ids = [model.id(w) for w in words]
        total = model.ml(ids[0])
        parts = [f"{words[0]}:{total:.2f}"]
        for k in range(1, len(words)):
            value = model.logp(ids[k - 1], ids[k]) if k == 1 else model.logp3(ids[k - 2], ids[k - 1], ids[k])
            total += value
            parts.append(f"→{words[k]}:{value:.2f}{'' if ids[k] is not None else '(unseen)'}")
        print(f"{sequence:24} total={total:7.2f}  " + " ".join(parts))


if __name__ == "__main__":
    main()
