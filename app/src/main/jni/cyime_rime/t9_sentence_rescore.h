// 26-key sentence choice (t9/sentence_rescore): Rime makes translator/max_sentences sentences as usual
// and the word model only picks which one comes first (pinyin26-word-model-20261018.md).
#ifndef CYIME_T9_SENTENCE_RESCORE_H_
#define CYIME_T9_SENTENCE_RESCORE_H_

#include <rime/common.h>
#include <rime/gear/poet.h>

#include "t9_script_query.h"

class T9NgramModel;

namespace rime {

class Sentence;

// Leaves one sentence in `sentences`: the word model's choice among Rime's n-best made without
// corrected words, followed by Rime's best when that one is a correction. `sentence_graph` is the
// word graph the n-best came from; without it no second n-best is made.
void T9RescoreSentences(const T9ScriptQuery& q, const T9NgramModel* ngram, bool ngram_sentence_start, Poet* poet,
                        const WordGraph* sentence_graph, int max_sentences, double cutoff,
                        const an<Sentence>& exact_sentence, const an<Sentence>& rime_best,
                        deque<an<Sentence>>* sentences);

// 14-key: a whole-input word read through the initial of the final merged key (都是 for DOUA, the A key read
// as s) normally follows the sentence of the typed letters (都啊). With the word model it goes first when its
// text is more likely than that sentence's by more than `margin` (ln units; t9/merged_tail_word_margin), and
// the sentence follows it. `word` is the best such whole-input word.
void T9PreferMergedTailWord(const T9ScriptQuery& q, const T9NgramModel* ngram, double margin,
                            const an<DictEntry>& word, deque<an<Sentence>>* sentences);

}  // namespace rime

#endif  // CYIME_T9_SENTENCE_RESCORE_H_
