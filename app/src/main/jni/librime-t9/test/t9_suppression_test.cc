#include <gtest/gtest.h>
#include "t9_suppression.h"
#include <filesystem>
#include <chrono>

namespace {
class SuppressionTest : public ::testing::Test {
 protected:
  std::filesystem::path directory;
  void SetUp() override {
    directory = std::filesystem::temp_directory_path() /
        ("cyime-suppression-" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    std::filesystem::create_directories(directory);
  }
  void TearDown() override {
    std::filesystem::remove(directory / "feedback.txt");
    std::filesystem::remove(directory / "feedback.txt.tmp");
    std::filesystem::remove(directory);
  }
};
TEST_F(SuppressionTest, ExactTextAndSchemaSurviveRestartAndUndo) {
  const auto path = (directory / "feedback.txt").string();
  rime::T9SuppressionStore store(path);
  ASSERT_TRUE(store.Set("t9_pinyin", "日期", true));
  ASSERT_TRUE(store.Set("t9_pinyin", "包含\"引号\"和\n换行", true));
  EXPECT_FALSE(store.Contains("rime_ice", "日期"));
  EXPECT_FALSE(store.Contains("t9_pinyin", "日期安排"));
  rime::T9SuppressionStore restarted(path);
  EXPECT_TRUE(restarted.Contains("t9_pinyin", "日期"));
  EXPECT_EQ(2u, restarted.List("t9_pinyin").size());
  EXPECT_TRUE(restarted.List("rime_ice").empty());
  EXPECT_TRUE(restarted.Contains("t9_pinyin", "包含\"引号\"和\n换行"));
  ASSERT_TRUE(restarted.Set("t9_pinyin", "日期", false));
  rime::T9SuppressionStore restored(path);
  EXPECT_FALSE(restored.Contains("t9_pinyin", "日期"));
  EXPECT_TRUE(restored.Contains("t9_pinyin", "包含\"引号\"和\n换行"));
}
TEST_F(SuppressionTest, FailedPersistenceDoesNotAlterLiveCandidates) {
  rime::T9SuppressionStore store((directory / "absent" / "feedback.txt").string());
  EXPECT_FALSE(store.Set("t9_pinyin", "text", true));
  EXPECT_FALSE(store.Contains("t9_pinyin", "text"));
}
}  // namespace
