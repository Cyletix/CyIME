// CyLM's best sentence in place of Rime's first sentence (cylm/* in the schema; off by default).
#ifndef CYIME_T9_CYLM_SENTENCE_H_
#define CYIME_T9_CYLM_SENTENCE_H_

#include <cstddef>

#include <cylm.h>
#include <rime/common.h>

namespace rime {

class Dictionary;
class Language;
class PhraseSyllabifier;
class Sentence;

// Words keep their places. With its pinyin (cylm_decode2) the sentence is one dictionary entry of
// `language`, learned like a typed phrase; a syllable the dictionary does not know leaves it as plain
// text that is not learned. `owner` keys one incremental CyLM session (one per translator).
void T9ApplyCylmSentence(CylmModel* model, const void* owner, const string& input, const string& left_context,
                         Dictionary* dict, const Language* language, size_t start,
                         const an<PhraseSyllabifier>& syllabifier, deque<an<Sentence>>* sentences);

// How long a key waits for CyLM (schema cylm/budget_ms); a slower result is not shown for that key.
void T9CylmSetBudget(int ms);
// Loads a newly opened model's pages on the CyLM thread, off the key path.
void T9CylmWarm(CylmModel* model);

}  // namespace rime

#endif  // CYIME_T9_CYLM_SENTENCE_H_
