/* CyLM C API: shared by the Windows Core and the Android JNI bridge (docs/CYLM_PHONE_DESIGN.md).
 * Build the library by compiling cylm.cpp with -DCYLM_LIBRARY. Models are memory-mapped; one CylmModel can serve
 * many sessions (one per text field). A session keeps the incremental decoding state of its last input. */
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
CylmSession* cylm_session(CylmModel* model);
/* keys: pinyin letters (full, initials or mixed, "'" separates) or nine-key digits; mode 0 = pinyin, 1 = nine-key;
 * context: text before the caret (UTF-8, may be NULL). Candidates are written to out as UTF-8 strings each ended
 * by '\0', best first. Returns the number of candidates, or -1 on bad arguments. */
int cylm_decode(CylmSession* session, const char* keys, int mode, const char* context, char* out, int cap);
void cylm_close_session(CylmSession* session);
void cylm_close(CylmModel* model);

#ifdef __cplusplus
}
#endif
#endif
