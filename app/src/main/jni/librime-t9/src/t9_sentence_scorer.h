#ifndef CYIME_T9_SENTENCE_SCORER_H_
#define CYIME_T9_SENTENCE_SCORER_H_
#include <string>
#include <vector>
namespace rime {
// Inference owns no Rime objects. Requests coalesce to the latest text batch.
bool T9RequestSentenceScores(const std::string& model, const std::vector<std::string>& texts,
                             std::vector<double>* scores);
int T9SentenceRefinementState();
void T9CancelSentenceScores();
bool T9RefiningSentences();
void T9SetRefiningSentences(bool active);
void T9ReleaseSentenceScorer();
void T9SetGrammarStatus(bool loaded);
std::string T9ScoringStatus();
bool T9SentenceScorerReady();
}
#endif
