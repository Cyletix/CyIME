#ifndef CYIME_DATETIME_HINTS_H_
#define CYIME_DATETIME_HINTS_H_

#include <ctime>
#include <string>
#include <vector>

namespace rime {
namespace datetime_hints {

// The current time or date offered after the plain word, the way other phone input methods do
// (user 2026-10-31): 时间 -> 15:35, 15:35:20; 日期 -> 2026-10-31, 2026年10月31日, 10月31日 星期六;
// 星期 -> 星期六, 周六. Any other word gives nothing. `now` is the device's local time.
std::vector<std::string> ForWord(const std::string& word, const std::tm& now);

}  // namespace datetime_hints
}  // namespace rime

#endif  // CYIME_DATETIME_HINTS_H_
