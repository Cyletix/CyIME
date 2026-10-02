#include <gtest/gtest.h>
#include "t9_candidate_pool.h"
#include <memory>
#include <vector>
#include <string>

namespace {
struct Entry {
  std::string text;
  std::vector<int> code;
  double weight;
  double quality_len = 3;
  int matching_code_size = 0;
  bool IsPredictiveMatch() const { return matching_code_size > 0; }
};
using Pool = std::vector<std::shared_ptr<Entry>>;
auto Word(std::string text, int code, double weight) {
  return std::make_shared<Entry>(Entry{text, {code}, weight});
}
TEST(T9CandidatePool, LateSystemSourceCannotBeStarvedByUserEntries) {
  Pool pool;
  for (int i = 0; i < 64; ++i) pool.push_back(Word("user" + std::to_string(i), 1, -100-i));
  pool.push_back(Word("system", 2, -1));
  auto reversed = pool;
  std::reverse(reversed.begin(), reversed.end());
  rime::T9MergeEntries(pool, 8);
  rime::T9MergeEntries(reversed, 8);
  ASSERT_EQ(8u, pool.size());
  ASSERT_EQ("system", pool.front()->text);
  for (size_t i = 0; i < pool.size(); ++i) EXPECT_EQ(pool[i], reversed[i]);
}
TEST(T9CandidatePool, DifferentPronunciationsAndMatchTypesRetainIdentity) {
  Pool pool{Word("same", 1, -1), Word("same", 2, -2), Word("same", 1, -3)};
  auto completion = Word("same", 1, -1);
  completion->matching_code_size = 1;
  pool.push_back(completion);
  rime::T9MergeEntries(pool, 8);
  ASSERT_EQ(3u, pool.size());
  EXPECT_EQ(2, pool[1]->code[0]);
  EXPECT_TRUE(pool[2]->IsPredictiveMatch());
}
TEST(T9CandidatePool, LowerFrequencyReadingSurvivesHomophoneFlood) {
  Pool pool;
  for (int i=0; i<20; ++i) pool.push_back(Word(std::to_string(i),1,-i));
  pool.push_back(Word("different reading",2,-30));
  rime::T9MergeEntries(pool, 8);
  EXPECT_EQ(8u, pool.size());
  EXPECT_TRUE(std::any_of(pool.begin(),pool.end(),[](auto entry){ return entry->code[0]==2; }));
}
}  // namespace
