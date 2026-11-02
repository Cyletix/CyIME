# Regenerates app/src/test/resources/azookey_readings.tsv rows: the reading libCyAzooKey gives for romaji.
# Usage: python readings.py <libCyAzooKey.so built for the host> <dictionary dir> <empty memory dir> <romaji...>
import ctypes, json, sys

lib = ctypes.CDLL(sys.argv[1])
lib.cyakkc_query.restype = ctypes.c_void_p
assert lib.cyakkc_open(sys.argv[2].encode(), sys.argv[3].encode()) == 0
for romaji in sys.argv[4:]:
    query = "ー" if romaji == "-" else romaji  # the translator sends the long-vowel mark itself
    pointer = lib.cyakkc_query(query.encode(), 1, 0, 0)
    rows = json.loads(ctypes.string_at(pointer).decode())
    lib.cyakkc_free(ctypes.c_void_p(pointer))
    lib.cyakkc_end(0)
    print(f"{romaji}\t{rows[0][2] if rows else ''}")
