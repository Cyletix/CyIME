#ifndef CYIME_DATETIME_FILTER_H_
#define CYIME_DATETIME_FILTER_H_
#ifndef T9_ALGO_ONLY_BUILD

#include <rime/filter.h>

namespace rime {

// cyime_datetime: when 时间, 日期 or 星期 is among the first three candidates and spells the
// whole input, the current time or date (datetime_hints.h) follows it directly. The word itself
// keeps its place. Works on whatever produced the word, so full pinyin, double pinyin, nine keys
// and 14 keys follow one rule. Off while the Rime option _cyime_datetime_off is set (the app's
// 时间日期候选 setting, default on). Place it before simplifier@traditionalize so 周六 converts too.
class DatetimeFilter : public Filter {
 public:
  explicit DatetimeFilter(const Ticket& ticket) : Filter(ticket) {}
  an<Translation> Apply(an<Translation> translation,
                        CandidateList* candidates) override;
};

}  // namespace rime

#endif  // T9_ALGO_ONLY_BUILD
#endif  // CYIME_DATETIME_FILTER_H_
