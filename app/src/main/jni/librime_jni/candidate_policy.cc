#include "candidate_policy.h"
#include <algorithm>
#include <cstdio>
#include <fstream>
#include <set>
#include <sstream>
#include <utf8.h>
#include <rime/candidate.h>
#include <rime/config.h>
#include <rime/context.h>
#include <rime/engine.h>
#include <rime/filter.h>
#include <rime/gear/translator_commons.h>
#include <rime/registry.h>
#include <rime/translation.h>

namespace cyime {
namespace {
using namespace rime;
// All access belongs to the serialized Rime engine, including initialization.
std::set<std::string> hidden;
std::string hidden_path;

std::vector<std::string> HanCharacters(const std::string& text) {
  std::vector<std::string> chars;
  auto it = text.begin();
  while (it != text.end()) {
    const auto begin = it;
    const auto cp = utf8::next(it, text.end());
    if (!((cp >= 0x3400 && cp <= 0x9fff) || (cp >= 0x20000 && cp <= 0x323af)))
      return {};
    chars.emplace_back(begin, it);
  }
  return chars;
}

std::vector<size_t> SyllableEnds(const an<Phrase>& phrase) {
  std::vector<size_t> ends;
  const auto spans = phrase->spans();
  for (size_t position = phrase->start(); position < phrase->end();) {
    const auto next = spans.NextStop(position);
    if (next <= position) return {};
    ends.push_back(next);
    position = next;
  }
  return ends;
}

double SpellingCoverage(const an<Phrase>& phrase) {
  double complete = phrase->entry().quality_len;
  if (const auto sentence = As<Sentence>(phrase)) {
    complete = 0;
    for (const auto& part : sentence->components()) complete += part.quality_len;
  }
  return complete / std::max<size_t>(1, phrase->end() - phrase->start());
}

class PolicyTranslation : public Translation {
 public:
  PolicyTranslation(an<Translation> source, const std::string& input, bool numeric) : source_(source), end_(input.size()) {
    // Bound the work independently of dictionary size. This is native candidate
    // identity-preserving ordering, not a separate UI list with stale indices.
    for (size_t i = 0; i < 64 && !source_->exhausted(); ++i) {
      auto cand = Prepare(source_->Peek());
      source_->Next();
      if (cand) head_.push_back(cand);
    }
    // Chinese candidates use native matching evidence and native weights only.
    // Preserve the slots of English, dates, commands and pinned non-Phrase items.
    struct Ranked { an<Candidate> candidate; int group; double coverage; };
    std::vector<size_t> slots;
    std::vector<Ranked> ranked;
    an<Phrase> best_sentence;
    for (const auto& c : head_) {
      auto p = As<Sentence>(Candidate::GetGenuineCandidate(c));
      if (p && (!best_sentence || p->weight() > best_sentence->weight())) best_sentence = p;
    }
    const auto best_ends = best_sentence ? SyllableEnds(best_sentence) : std::vector<size_t>();
    for (size_t i = 0; i < head_.size(); ++i) {
      const auto p = As<Phrase>(Candidate::GetGenuineCandidate(head_[i]));
      if (!p || HanCharacters(p->text()).empty()) continue;
      int group = p->end() == end_ ? 0 : 1;
      // Alternative T9 segmentations remain accessible, after dictionary prefixes.
      // Words on the best-scoring segmentation (including ambiguous final initials)
      // keep competing by existing weights; no text blacklist or external model.
      if (numeric && As<Sentence>(p) && !best_ends.empty() && SyllableEnds(p) != best_ends)
        group = 2;
      slots.push_back(i);
      ranked.push_back({head_[i], group, numeric ? SpellingCoverage(p) : 0.0});
    }
    std::stable_sort(ranked.begin(), ranked.end(), [](const auto& a, const auto& b) {
      if (a.group != b.group) return a.group < b.group;
      // Native translation already orders by word weights and explicit learned
      // preference. Keep that order instead of overriding user-history priority.
      return false;
    });
    // Do not reorder ordinary dictionary words by spelling coverage: a single
    // 3 must retain frequent d-initial words ahead of rare full-syllable e words.
    // Only the best full-spelling composition may precede an abbreviated whole
    // word. This repairs the sentence's missing native quality without promoting
    // every alternative sentence or assigning an arbitrary insertion position.
    if (numeric && best_sentence && SpellingCoverage(best_sentence) >= 1.0) {
      const auto sentence = std::find_if(ranked.begin(), ranked.end(), [&](const auto& r) {
        return Candidate::GetGenuineCandidate(r.candidate) == best_sentence;
      });
      const auto weaker = std::find_if(ranked.begin(), sentence, [](const auto& r) {
        return r.group == 0 && r.coverage < 1.0;
      });
      if (sentence != ranked.end() && weaker != sentence)
        std::rotate(weaker, sentence, sentence + 1);
    }
    for (size_t i = 0; i < slots.size(); ++i) head_[slots[i]] = ranked[i].candidate;
    // A whole English word should not lose to guessed Chinese abbreviations.
    // Fully spelled Chinese (e.g. you -> 有) and explicit non-dictionary entries
    // retain their native priority. No word list or fixed candidate is injected.
    if (!numeric && !input.empty() && input.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ") == std::string::npos) {
      const auto exact = std::find_if(head_.begin(), head_.end(), [&](const auto& c) {
        return c->start() == 0 && c->end() == end_ && c->text() == input && c->type() == "table";
      });
      if (exact != head_.end()) {
        auto insertion = exact;
        while (insertion != head_.begin()) {
          auto previous = insertion - 1;
          const auto phrase = As<Phrase>(Candidate::GetGenuineCandidate(*previous));
          if (!phrase || HanCharacters(phrase->text()).empty() || SpellingCoverage(phrase) >= 1.0) break;
          insertion = previous;
        }
        std::rotate(insertion, exact, exact + 1);
      }
    }
    Advance();
  }
  bool Next() override { current_.reset(); Advance(); return !exhausted(); }
  an<Candidate> Peek() override { return current_; }
 private:
  an<Candidate> Prepare(an<Candidate> cand) {
    if (!cand || hidden.count(cand->text())) return nullptr;
    // Keep the engine's complete predictive word and identity. Trimming to the
    // matched prefix discarded precisely the completion the user could select.
    if (hidden.count(cand->text()) || !seen_.insert(cand->text()).second) return nullptr;
    return cand;
  }
  void Advance() {
    if (position_ < head_.size()) { current_ = head_[position_++]; return; }
    while (!source_->exhausted()) {
      current_ = Prepare(source_->Peek()); source_->Next();
      if (current_) return;
    }
    set_exhausted(true);
  }
  an<Translation> source_;
  size_t end_, position_ = 0;
  CandidateList head_;
  std::set<std::string> seen_;
  an<Candidate> current_;
};

class CandidatePolicy : public Filter {
 public:
  explicit CandidatePolicy(const Ticket& ticket) : Filter(ticket) {}
  an<Translation> Apply(an<Translation> source, CandidateList*) override {
    const auto& input = engine_->context()->input();
    const bool numeric = !input.empty() && input.find_first_not_of("23456789' ") == std::string::npos;
    return New<PolicyTranslation>(source, input, numeric);
  }
};

bool StoreHidden(const std::set<std::string>& next) {
  if (hidden_path.empty()) return false;
  const auto pending = hidden_path + ".tmp";
  std::ofstream out(pending, std::ios::trunc | std::ios::binary);
  if (!out) return false;
  for (const auto& text : next) out << text << '\n';
  out.close();
  if (!out || std::rename(pending.c_str(), hidden_path.c_str()) != 0) return false;
  hidden = next;
  return true;
}
}

void InitializeCandidatePolicy(const std::string& user_dir) {
  hidden.clear();
  std::string line;
  hidden_path = user_dir + "/cyime-hidden-candidates.txt";
  std::ifstream preferences(hidden_path);
  while (std::getline(preferences, line)) if (!line.empty()) hidden.insert(line);
  Registry::instance().Register("cyime_candidate_policy", new Component<CandidatePolicy>());
}

bool HideCandidate(const std::string& text) {
  if (text.empty() || text.find_first_of("\r\n") != std::string::npos) return false;
  auto next = hidden; next.insert(text); return StoreHidden(next);
}
bool RestoreCandidate(const std::string& text) {
  auto next = hidden; next.erase(text); return StoreHidden(next);
}
}
