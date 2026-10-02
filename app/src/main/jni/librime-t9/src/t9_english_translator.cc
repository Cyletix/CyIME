#include "t9_english_translator.h"
#ifndef T9_ALGO_ONLY_BUILD
#include <algorithm>
#include <cmath>
#include <rime/segmentation.h>

namespace rime {
namespace {
class EnglishLearningTranslation : public CacheTranslation {
 public:
  explicit EnglishLearningTranslation(an<Translation> source) : CacheTranslation(source) {}
  an<Candidate> Peek() override {
    auto candidate = CacheTranslation::Peek();
    auto phrase = As<Phrase>(candidate);
    if (phrase && phrase->type() == "user_table" && phrase->entry().commit_count > 0) {
      // Learned whole words have full coverage, just like exact Chinese phrases.
      // Keep Rime's frequency/recency probability: repeated choices progressively
      // overtake joint sentences, with no word-specific rule or reserved rank.
      phrase->set_quality(std::max(phrase->quality(), 1.0 + std::exp(phrase->weight())));
    }
    return candidate;
  }
};
}  // namespace
an<Translation> T9EnglishTranslator::Query(const string& input, const Segment& segment) {
  an<Translation> exact = New<EnglishLearningTranslation>(TableTranslator::Query(input, segment));
  // Bounded exact prefix lookups, never scan the word list or enumerate letter
  // combinations. Learned full codes naturally come back through the base query.
  if (input.size() < 4 || input.size() > 64 ||
      input.find_first_not_of("0123456789") != string::npos ||
      !segment.HasAnyTagIn(tags_)) return exact;
  auto suffixes = New<FifoTranslation>();
  for (size_t count = 1; count <= 4 && count + 3 <= input.size(); ++count) {
    const size_t length = input.size() - count;
    Segment prefix(segment.start, segment.start + length);
    prefix.tags = segment.tags;
    auto bases = TableTranslator::Query(input.substr(0, length), prefix);
    for (size_t n = 0; bases && !bases->exhausted() && n < 16; ++n, bases->Next()) {
      auto base = As<Phrase>(Candidate::GetGenuineCandidate(bases->Peek()));
      if (!base || base->text().size() < 3 ||
          !std::all_of(base->text().begin(), base->text().end(), [](unsigned char c) {
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
          })) continue;
      auto entry = New<DictEntry>();
      entry->text = base->text() + input.substr(length);
      entry->custom_code = input + " ";
      entry->preedit = input;
      auto phrase = New<Phrase>(language(), "table", segment.start,
                                segment.start + input.size(), entry);
      // Inferred forms follow exact English entries. Shorter digit suffixes win
      // ties; a selected form subsequently gets normal native user-word ranking.
      phrase->set_quality(initial_quality_ - 0.25 - count * 0.01);
      suffixes->Append(phrase);
    }
  }
  if (suffixes->exhausted()) return exact;
  return New<DistinctTranslation>(exact + suffixes);
}
}  // namespace rime
#endif
