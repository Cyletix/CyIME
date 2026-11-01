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
#include <rime/menu.h>
#include <rime/schema.h>
#include <rime/translator.h>
#include <rime/gear/translator_commons.h>
#include <rime/language.h>
#include <rime/registry.h>
#include <rime/translation.h>

namespace cyime {
namespace {
using namespace rime;
// All access belongs to the serialized Rime engine, including initialization.
std::set<std::string> hidden;
std::string hidden_path;
thread_local Context* omission_context = nullptr;
thread_local bool omission_inserted = false;

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
  PolicyTranslation(an<Translation> source, const std::string& input, bool numeric, Translator* omission) : source_(source), end_(input.size()) {
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
    KeepFixedOrder();
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
    // Imported English words (CellDictionaryEnglish) come right after the first Chinese
    // candidate on nine keys, where short digit codes collide with common Chinese: they
    // never take the first place from it.
    if (!input.empty() && input.find_first_not_of("0123456789' ") == std::string::npos) {
      const auto chinese = std::find_if(head_.begin(), head_.end(), [](const auto& c) {
        const auto p = As<Phrase>(Candidate::GetGenuineCandidate(c));
        return !p || !p->language() || p->language()->name().rfind("user_cyime_cells_en", 0) != 0;
      });
      if (chinese != head_.begin() && chinese != head_.end()) std::rotate(head_.begin(), chinese, chinese + 1);
    }
    if (omission && head_.size() >= 2) {
      Segment segment(0, input.size());
      segment.tags.insert("abc");
      auto alternatives = omission->Query(input, segment);
      if (alternatives && !alternatives->exhausted() && alternatives->Peek() &&
          alternatives->Peek()->text() != head_.front()->text()) {
        const auto original = head_;
        size_t inserted = 0;
        for (size_t i = 0; i < 10 && inserted < 2 && !alternatives->exhausted(); ++i) {
          auto c = alternatives->Peek();
          alternatives->Next();
          if (!c || c->start() != 0 || c->end() != input.size() ||
              hidden.count(c->text()) || HanCharacters(c->text()).empty()) continue;
          auto same = [&](const auto& item) { return item->text() == c->text(); };
          auto first_five = original.begin() + std::min<size_t>(5, original.size());
          if (std::any_of(original.begin(), first_five, same) ||
              std::any_of(head_.begin() + 2, head_.begin() + 2 + inserted, same)) continue;
          auto old = std::find_if(head_.begin(), head_.end(), same);
          if (old != head_.end()) { c = *old; head_.erase(old); }
          seen_.insert(c->text());
          head_.insert(head_.begin() + 2 + inserted++,
                       New<ShadowCandidate>(c, "cyime_omission", c->text(), "纠错", false));
          omission_inserted = true;
        }
      }
    }
    Advance();
  }
  bool Next() override { current_.reset(); Advance(); return !exhausted(); }
  an<Candidate> Peek() override { return current_; }
 private:
  // Pairs the user ranks for himself, whatever was learned (2026-10-31): the plain 那个 comes
  // before the question 哪个 on the same span, even after picking 哪个 more often. Only the
  // first word moves, to just before the second; nothing else changes place.
  void KeepFixedOrder() {
    static const std::pair<const char*, const char*> kPairs[] = {{"那个", "哪个"}};
    for (const auto& pair : kPairs) {
      const auto find = [&](const char* text) {
        return std::find_if(head_.begin(), head_.end(), [&](const auto& c) { return c->text() == text; });
      };
      const auto first = find(pair.first), second = find(pair.second);
      if (first == head_.end() || second == head_.end() || first < second) continue;
      if ((*first)->start() != (*second)->start() || (*first)->end() != (*second)->end()) continue;
      std::rotate(second, first, first + 1);
    }
  }
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
    Translator* extra = nullptr;
    if (omission_context == engine_->context()) {
      std::string id;
      if (!omission_ && engine_->schema()->config()->GetString("cyime_omission_schema", &id)) {
        Schema schema(id);
        std::string prism;
        auto component = Translator::Require("script_translator");
        if (component && schema.config()->GetString("translator/prism", &prism) && prism == id) {
          Ticket ticket(engine_, "translator");
          ticket.schema = &schema;
          omission_.reset(component->Create(ticket));
        }
      }
      extra = omission_.get();
    }
    return New<PolicyTranslation>(source, input, numeric, extra);
  }
 private:
  the<Translator> omission_;
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

bool RefineOmission(rime::Context* context) {
  if (!context || context->input().size() < 4 || context->input().size() > 80 ||
      context->input().find_first_not_of("abcdefghijklmnopqrstuvwxyz") != std::string::npos ||
      context->caret_pos() != context->input().size() || context->composition().size() != 1 ||
      context->composition().back().status >= rime::Segment::kSelected ||
      context->composition().back().selected_index != 0) return false;
  const auto original = context->composition().back();
  if (!original.menu || original.menu->Prepare(2) < 2) return false;
  struct Scope {
    explicit Scope(rime::Context* ctx) { omission_context = ctx; omission_inserted = false; }
    ~Scope() { omission_context = nullptr; }
  } scope(context);
  if (!context->RefreshNonConfirmedComposition() || context->composition().empty()) return false;
  auto menu = context->composition().back().menu;
  if (!menu) return false;
  menu->Prepare(5);
  // Late filters (e.g. simplified/traditional conversion) run after CandidatePolicy.
  // Keep the menu the user saw if the refresh failed to add anything or displaced a protected choice.
  bool keep = omission_inserted;
  for (size_t i = 0; keep && i < 2; ++i) {
    auto before = original.menu->GetCandidateAt(i);
    auto after = menu->GetCandidateAt(i);
    keep = before && after && before->text() == after->text() && before->end() == after->end();
  }
  if (!keep) context->composition().back() = original;
  return keep;
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
std::vector<std::string> HiddenCandidates() {
  return std::vector<std::string>(hidden.begin(), hidden.end());
}
}
