// datetime_hints 单元测试：打“时间”“日期”“星期”时接在词后面的当前时间和日期（用户 2026-10-31）。
#include <gtest/gtest.h>

#include <ctime>
#include <string>
#include <vector>

#include "datetime_hints.h"

using rime::datetime_hints::ForWord;
using Texts = std::vector<std::string>;

namespace {
std::tm At(int year, int month, int day, int hour, int minute, int second, int weekday) {
  std::tm t = {};
  t.tm_year = year - 1900;
  t.tm_mon = month - 1;
  t.tm_mday = day;
  t.tm_hour = hour;
  t.tm_min = minute;
  t.tm_sec = second;
  t.tm_wday = weekday;
  return t;
}
}  // namespace

TEST(DatetimeHintsTest, TimeGivesHoursMinutesThenSeconds) {
  EXPECT_EQ(ForWord("时间", At(2026, 10, 31, 15, 35, 20, 6)), (Texts{"15:35", "15:35:20"}));
  // 零点、个位数补零：09:05，不是 9:5。
  EXPECT_EQ(ForWord("时间", At(2026, 1, 2, 0, 5, 9, 5)), (Texts{"00:05", "00:05:09"}));
  EXPECT_EQ(ForWord("时间", At(2026, 1, 2, 9, 5, 0, 5)), (Texts{"09:05", "09:05:00"}));
}

TEST(DatetimeHintsTest, DateGivesIsoChineseAndWeekday) {
  EXPECT_EQ(ForWord("日期", At(2026, 10, 31, 15, 35, 20, 6)),
            (Texts{"2026-10-31", "2026年10月31日", "10月31日 星期六"}));
  // ISO 补零，中文写法不补零（1月2日，不是 01月02日）。
  EXPECT_EQ(ForWord("日期", At(2027, 1, 2, 8, 0, 0, 6)),
            (Texts{"2027-01-02", "2027年1月2日", "1月2日 星期六"}));
}

TEST(DatetimeHintsTest, WeekdayUsesSundayAsRi) {
  EXPECT_EQ(ForWord("星期", At(2026, 11, 1, 10, 0, 0, 0)), (Texts{"星期日", "周日"}));
  EXPECT_EQ(ForWord("星期", At(2026, 11, 2, 10, 0, 0, 1)), (Texts{"星期一", "周一"}));
  EXPECT_EQ(ForWord("星期", At(2026, 10, 31, 10, 0, 0, 6)), (Texts{"星期六", "周六"}));
}

TEST(DatetimeHintsTest, OtherWordsGiveNothing) {
  const auto now = At(2026, 10, 31, 15, 35, 20, 6);
  // 同音词和带这些字的长词都不接：只认整个词。
  for (const char* word : {"事件", "实践", "时间表", "日起", "星期六", "现在", "", "shijian"}) {
    EXPECT_TRUE(ForWord(word, now).empty()) << word;
  }
}
