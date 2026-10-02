#include <gtest/gtest.h>
#include "t9_decode_cache.h"
#include <vector>

namespace rime {
TEST(T9DecodeCache, RetainsEntire63KeyHistoryAndDistinguishesLocksAndSpans) {
  T9DecodeCache<std::vector<int>> cache;
  for (int n = 1; n <= 63; ++n)
    cache.Put(std::string(n, '7') + "#0#" + std::to_string(n), {n}, sizeof(int));
  for (int n = 62; n > 0; --n) {
    auto* result = cache.Find(std::string(n, '7') + "#0#" + std::to_string(n));
    ASSERT_NE(nullptr, result);
    EXPECT_EQ(n, result->front());
  }
  EXPECT_EQ(nullptr, cache.Find("qi#0#2"));
  EXPECT_EQ(nullptr, cache.Find("77#2#4"));
}
TEST(T9DecodeCache, EvictsLeastRecentlyUsedAndBoundsAccountedMemory) {
  T9DecodeCache<int> cache(1024, 3);
  cache.Put("a", 1, 100); cache.Put("b", 2, 100); cache.Put("c", 3, 100);
  ASSERT_NE(nullptr, cache.Find("a"));
  cache.Put("d", 4, 100);
  EXPECT_EQ(nullptr, cache.Find("b"));
  EXPECT_LE(cache.bytes(), 1024u);
  EXPECT_LE(cache.size(), 3u);
  cache.Put("huge", 0, 1024);
  EXPECT_EQ(nullptr, cache.Find("huge"));
  EXPECT_NE(nullptr, cache.Find("a"));
}
TEST(T9DecodeCache, ReplacementAndInvalidationDoNotReturnStaleValues) {
  T9DecodeCache<std::vector<int>> cache;
  cache.Put("same", {1}, 4);
  cache.Put("same", {2, 3}, 8);
  EXPECT_EQ(1u, cache.size());
  ASSERT_NE(nullptr, cache.Find("same"));
  EXPECT_EQ((std::vector<int>{2, 3}), *cache.Find("same"));
  cache.Clear();
  EXPECT_EQ(nullptr, cache.Find("same"));
  EXPECT_EQ(0u, cache.bytes());
}
TEST(T9DecodeCache, DictionaryRevisionAdvancesIndependentlyOfLearningTick) {
  const auto before = T9UserDictionaryRevision().load();
  T9InvalidateUserDictionary();
  T9InvalidateUserDictionary();
  EXPECT_EQ(before + 2, T9UserDictionaryRevision().load());
}
}  // namespace rime
