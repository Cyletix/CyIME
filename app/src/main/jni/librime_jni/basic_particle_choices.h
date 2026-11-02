#pragma once

#include <rime/candidate.h>
#include <rime/dict/dictionary.h>
#include <rime/gear/translator_commons.h>
#include <rime/ticket.h>
#include <map>
#include <set>

namespace cyime {

// First-page alternatives for a small explicit set of abbreviated everyday words.
// The schema supplies its actual key alphabet; imported schemas are opt-in.
class BasicParticleChoices {
 public:
  explicit BasicParticleChoices(const rime::Ticket& ticket);
  void Promote(const std::string& input, size_t start, rime::CandidateList& head,
               const std::set<std::string>& hidden, std::set<std::string>& seen);

 private:
  struct Choice {
    std::string text, reading;
    std::vector<std::string> syllables;
    rime::Spans spans;
  };
  rime::an<rime::Phrase> MakePhrase(const Choice& choice, size_t end,
                                    const rime::Language* language);
  rime::Ticket translator_;
  std::map<std::string, std::vector<Choice>> choices_;
  std::map<std::string, rime::SyllableId> syllable_ids_;
  std::string language_;
  bool loaded_ = false;
};
}  // namespace cyime
