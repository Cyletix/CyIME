#include "datetime_filter.h"

#ifndef T9_ALGO_ONLY_BUILD

#include <ctime>
#include <set>
#include <string>

#include <rime/candidate.h>
#include <rime/context.h>
#include <rime/engine.h>
#include <rime/translation.h>

#include "datetime_hints.h"

namespace rime {
namespace {

// Only the first few candidates are looked at, so a rare reading deep in the list adds nothing.
constexpr size_t kLookahead = 3;

class DatetimeTranslation : public Translation {
 public:
  DatetimeTranslation(an<Translation> source, size_t input_end, const std::tm& now)
      : source_(source) {
    for (size_t i = 0; i < kLookahead && !source_->exhausted(); ++i) {
      if (auto cand = source_->Peek()) head_.push_back(cand);
      source_->Next();
    }
    for (size_t i = 0; i < head_.size(); ++i) {
      const auto word = head_[i];
      if (word->end() != input_end) continue;
      const auto texts = datetime_hints::ForWord(word->text(), now);
      if (texts.empty()) continue;
      CandidateList merged(head_.begin(), head_.begin() + i + 1);
      for (const auto& text : texts) {
        hints_.insert(text);
        merged.push_back(New<SimpleCandidate>("cyime_datetime", word->start(), word->end(),
                                              text, "", word->preedit()));
      }
      for (size_t j = i + 1; j < head_.size(); ++j) {
        if (!Duplicate(head_[j])) merged.push_back(head_[j]);
      }
      head_.swap(merged);
      break;
    }
    Advance();
  }

  bool Next() override {
    if (exhausted()) return false;
    Advance();
    return !exhausted();
  }

  an<Candidate> Peek() override { return current_; }

 private:
  // Once the hints are in, the nine-key t9_date_translator's own date/time/week candidates for
  // the same keys (and any other copy of a hint text) are left out: one list on every layout.
  bool Duplicate(const an<Candidate>& cand) const {
    if (hints_.empty()) return false;
    if (hints_.count(cand->text())) return true;
    const auto genuine = Candidate::GetGenuineCandidate(cand);
    const auto& type = genuine ? genuine->type() : cand->type();
    return type == "date" || type == "time" || type == "week";
  }

  void Advance() {
    if (position_ < head_.size()) {
      current_ = head_[position_++];
      return;
    }
    while (!source_->exhausted()) {
      auto cand = source_->Peek();
      source_->Next();
      if (cand && !Duplicate(cand)) {
        current_ = cand;
        return;
      }
    }
    current_.reset();
    set_exhausted(true);
  }

  an<Translation> source_;
  CandidateList head_;
  std::set<std::string> hints_;
  size_t position_ = 0;
  an<Candidate> current_;
};

}  // namespace

an<Translation> DatetimeFilter::Apply(an<Translation> translation,
                                      CandidateList* candidates) {
  if (!translation || !engine_) return translation;
  Context* context = engine_->context();
  if (!context || context->get_option("_cyime_datetime_off")) return translation;
  const time_t now = std::time(nullptr);
  std::tm local = {};
  localtime_r(&now, &local);
  return New<DatetimeTranslation>(translation, context->input().size(), local);
}

}  // namespace rime

#endif  // T9_ALGO_ONLY_BUILD
