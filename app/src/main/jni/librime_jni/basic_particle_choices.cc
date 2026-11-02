#include "basic_particle_choices.h"
#include <rime/engine.h>
#include <rime/language.h>
#include <rime/schema.h>
#include <sstream>

namespace cyime {
using namespace rime;

namespace {
class ShortcodeSpans : public PhraseSyllabifier {
 public:
  explicit ShortcodeSpans(Spans spans) : spans_(std::move(spans)) {}
  Spans Syllabify(const Phrase*) override { return spans_; }
 private:
  Spans spans_;
};
}  // namespace

BasicParticleChoices::BasicParticleChoices(const Ticket& ticket)
    : translator_(ticket.engine, "translator") {
  auto* config = ticket.schema->config();
  // Some schemes have their own syllable encoding. They may promote an existing
  // whole dictionary candidate for an explicit raw code without using full-pinyin shortcuts.
  if (auto exact = config->GetList("cyime_particle_shortcuts/exact")) {
    for (size_t i = 0; i < exact->size(); ++i) {
      if (auto value = exact->GetValueAt(i)) {
        std::istringstream line(value->str());
        Choice word;
        string raw;
        if (line >> raw >> word.text) choices_[raw].push_back(word);
      }
    }
  }
  string alphabet;
  if (!config->GetString("translator/user_dict", &language_)) {
    config->GetString("translator/dictionary", &language_);
    language_ = Language::get_language_component(language_);
  }
  if (!config->GetString("cyime_particle_shortcuts/alphabet", &alphabet) || alphabet.size() != 26)
    return;
  const auto phrases = config->GetList("cyime_particle_shortcuts/phrases");
  if (!phrases) return;
  for (size_t i = 0; i < phrases->size(); ++i) {
    const auto value = phrases->GetValueAt(i);
    if (!value) continue;
    std::istringstream line(value->str());
    Choice word;
    line >> word.text;
    for (string syllable; line >> syllable;) {
      if (syllable.find_first_not_of("abcdefghijklmnopqrstuvwxyz") != string::npos) {
        word.syllables.clear();
        break;
      }
      if (!word.reading.empty()) word.reading += ' ';
      word.reading += syllable;
      word.syllables.push_back(syllable);
    }
    if (word.syllables.empty()) continue;
    string previous;
    for (bool initials : {false, true}) {
      string raw;
      word.spans = {};
      for (size_t j = 0; j < word.syllables.size(); ++j) {
        const auto& syllable = word.syllables[j];
        const size_t start = raw.size();
        const size_t length = initials || j + 1 == word.syllables.size() ? 1 : syllable.size();
        for (size_t k = 0; k < length; ++k) raw += alphabet[syllable[k] - 'a'];
        word.spans.AddSpan(start, raw.size());
      }
      if (raw != previous) choices_[raw].push_back(word);
      previous = std::move(raw);
    }
  }
}

an<Phrase> BasicParticleChoices::MakePhrase(const Choice& word, size_t end, const Language* language) {
  // Resolve IDs against this installed dictionary, never persist build-specific syllable IDs.
  if (!loaded_) {
    loaded_ = true;
    auto factory = Dictionary::Require("dictionary");
    the<Dictionary> dict(factory ? factory->Create(translator_) : nullptr);
    if (!dict || !dict->Load()) return nullptr;
    const auto& table = dict->primary_table();
    for (SyllableId id = 0;; ++id) {
      const auto spelling = table->GetSyllableById(id);
      if (spelling.empty()) break;
      syllable_ids_[spelling] = id;
    }
  }
  auto entry = New<DictEntry>();
  for (const auto& syllable : word.syllables) {
    auto id = syllable_ids_.find(syllable);
    if (id == syllable_ids_.end()) return nullptr;
    entry->code.push_back(id->second);
  }
  entry->text = word.text;
  entry->comment = entry->preedit = word.reading;
  auto phrase = New<Phrase>(language, "phrase", 0, end, entry);
  phrase->set_syllabifier(New<ShortcodeSpans>(word.spans));
  return phrase;
}

void BasicParticleChoices::Promote(const string& input, size_t start, CandidateList& head,
                                   const std::set<string>& hidden, std::set<string>& seen) {
  const auto found = start ? choices_.find(input.substr(start)) : choices_.find(input);
  if (found == choices_.end() || head.empty()) return;
  const Language* language = nullptr;
  for (const auto& candidate : head) {
    auto phrase = As<Phrase>(Candidate::GetGenuineCandidate(candidate));
    if (phrase && phrase->language() && phrase->language()->name() == language_) {
      language = phrase->language();
      break;
    }
  }
  if (!language) return;
  size_t slot = std::min<size_t>(2, head.size());
  for (const auto& word : found->second) {
    if (hidden.count(word.text)) continue;
    auto same = std::find_if(head.begin(), head.end(),
                             [&](const auto& c) { return c->text() == word.text; });
    const bool whole = same != head.end() && (*same)->start() == start && (*same)->end() == input.size();
    // After an earlier selection only move an existing candidate on the remaining span.
    // Its original offsets and syllabifier must survive selection and learning.
    if (start && !whole) continue;
    if (!whole && word.syllables.empty()) continue;
    if (whole && size_t(same - head.begin()) < slot) continue;
    auto candidate = whole ? *same : MakePhrase(word, input.size(), language);
    if (!candidate) continue;
    if (same != head.end()) {
      if (size_t(same - head.begin()) < slot) continue;
      head.erase(same);
    }
    head.insert(head.begin() + slot++, candidate);
    seen.insert(word.text);
  }
}
}  // namespace cyime
