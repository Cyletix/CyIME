/* CyLM C API: shared by the Windows Core and the Android JNI bridge (docs/CYLM_PHONE_DESIGN.md).
 * Build the library by compiling cylm.cpp with -DCYLM_LIBRARY. Models are memory-mapped; one CylmModel can serve
 * many sessions (one per text field). A session keeps the incremental decoding state of its last input: keep one
 * session per text field for its whole life and pass the whole key string on every keystroke with the same context;
 * a new session, a changed context or a changed mode makes the next call decode from scratch (several times slower). */
#ifndef CYLM_H
#define CYLM_H
#ifdef __cplusplus
extern "C" {
#endif

typedef struct CylmModel CylmModel;
typedef struct CylmSession CylmSession;

/* lexicon: compiled lexicon (cylm LEXICON.tsv --compile OUT.bin); model2 may be NULL;
 * params: "mix=0.8;mu=0.04;eos=0.6;nbest=16;abbrev_full=-4". Returns NULL on failure. */
CylmModel* cylm_open(const char* lexicon, const char* model, const char* model2, const char* params);
/* As cylm_open, plus a reranker: reranker = ONNX file from tools/cylm/nn/export_onnx.py (int8 recommended),
 * vocab = its character table (vocab.txt: one token per line, ids in line order). params may add
 * "rr_gamma=0.8;rr_short=0.1;rr_threads=4". The reranker runs inside cylm_decode (same keystroke). Needs a build
 * with -DCYLM_ORT linked against onnxruntime; without it cylm_open2 with a reranker returns NULL. */
CylmModel* cylm_open2(const char* lexicon, const char* model, const char* model2, const char* reranker, const char* vocab,
                      const char* params);
/* The user's own preferences, applied after ranking: a TSV of "P<TAB>D" rows (the user types P rather than its
 * homophone D, or reported it); a P ranked below its D moves to just above D. Call before cylm_session. Returns the
 * number of D entries, or -1. */
int cylm_prefer(CylmModel* model, const char* path);
CylmSession* cylm_session(CylmModel* model);
/* keys: pinyin letters (full, initials or mixed, "'" separates) or nine-key digits; mode 0 = pinyin, 1 = nine-key;
 * context: text before the caret (UTF-8, may be NULL). Candidates are written to out as UTF-8 strings each ended
 * by '\0', best first. Returns the number of candidates, or -1 on bad arguments. */
int cylm_decode(CylmSession* session, const char* keys, int mode, const char* context, char* out, int cap);
/* As cylm_decode, but each record is "text\x1fpinyin\x1fspans\0": pinyin = the candidate's syllables separated by
 * spaces ("bei xian qi le"; for initials typed as abbreviations, the full syllable of the chosen reading), spans = the
 * key range [start-end) of each word in the input, comma separated ("0-3,3-7,7-9,9-11"), which maps nine-key digits
 * and pinyin letters alike back to the keys the user pressed. Use the pinyin as the Rime code when committing. */
int cylm_decode2(CylmSession* session, const char* keys, int mode, const char* context, char* out, int cap);
void cylm_close_session(CylmSession* session);
void cylm_close(CylmModel* model);

#ifdef __cplusplus
}
#endif
#endif
