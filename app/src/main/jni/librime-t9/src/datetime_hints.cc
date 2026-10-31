#include "datetime_hints.h"

#include <cstdio>

namespace rime {
namespace datetime_hints {
namespace {

const char* Weekday(const std::tm& t) {
  static const char* kDays[] = {"日", "一", "二", "三", "四", "五", "六"};
  return kDays[(t.tm_wday % 7 + 7) % 7];
}

std::string Format(const char* pattern, int a, int b, int c = 0) {
  char buf[48];
  const int n = std::snprintf(buf, sizeof(buf), pattern, a, b, c);
  return n > 0 ? std::string(buf, static_cast<size_t>(n)) : std::string();
}

}  // namespace

std::vector<std::string> ForWord(const std::string& word, const std::tm& now) {
  const int year = now.tm_year + 1900, month = now.tm_mon + 1, day = now.tm_mday;
  if (word == "时间") {
    return {Format("%02d:%02d", now.tm_hour, now.tm_min),
            Format("%02d:%02d:%02d", now.tm_hour, now.tm_min, now.tm_sec)};
  }
  if (word == "日期") {
    return {Format("%04d-%02d-%02d", year, month, day),
            Format("%d年%d月%d日", year, month, day),
            Format("%d月%d日 星期", month, day) + Weekday(now)};
  }
  if (word == "星期") {
    return {std::string("星期") + Weekday(now), std::string("周") + Weekday(now)};
  }
  return {};
}

}  // namespace datetime_hints
}  // namespace rime
