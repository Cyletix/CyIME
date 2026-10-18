// The joint decoder's word trigram model must read the original (version 2) and the compact
// (version 3) file layouts alike. A tiny model is written in both layouts with the shared
// writer the build tools use, then every probability path is compared against hand values.
#include <gtest/gtest.h>

#include <chrono>
#include <cmath>
#include <filesystem>
#include <string>
#include <vector>

#include "t9_ngram_model.h"
// The writer lives with the model tools, outside the plugin sources.
#include "../../../../../../tools/ime_lab/ngram/ngram_write.h"

namespace {
using cyime_t9_ngram::ModelTables;
using cyime_t9_ngram::WordEntry;

// Words: 0 我, 1 们, 2 的, 3 </s>, 4 <s>.  Bigrams: 我→们 -0.5, 我→的 -2.0, 们→的 -0.25,
// <s>→我 -1.0.  Trigram under (我,们): 的 -0.125; bigram (我,们) backoff -0.75.
ModelTables TinyModel() {
  ModelTables m;
  m.eos = 3; m.bos = 4;
  m.unseen_lower = -12.0f; m.unseen_ml = -13.0f;
  const char* texts[] = {"我", "们", "的"};
  for (const char* t : texts) m.keys.push_back(cyime_t9_ngram::Hash(t, std::char_traits<char>::length(t)));
  m.keys.push_back(0); m.keys.push_back(0);
  m.entries = {WordEntry{-1.5f, -2.5f, -0.3f, 0}, WordEntry{-3.0f, -4.0f, -0.6f, 0},
               WordEntry{-1.0f, -1.25f, -0.9f, 0}, WordEntry{-5.0f, -6.0f, 0.0f, 0},
               WordEntry{-7.0f, -8.0f, -0.1f, 0}};
  m.begin = {0, 2, 3, 3, 3, 4};
  m.next = {1, 2, 2, 0};
  m.logp = {-0.5f, -2.0f, -0.25f, -1.0f};
  m.bigram_backoff = {-0.75f, 0.0f, 0.0f, 0.0f};
  m.tri_begin = {0, 1, 1, 1, 1};
  m.tri_next = {2};
  m.tri_logp = {-0.125f};
  return m;
}

class NgramModelTest : public ::testing::Test {
 protected:
  std::filesystem::path directory;
  void SetUp() override {
    directory = std::filesystem::temp_directory_path() /
        ("cyime-ngram-" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    std::filesystem::create_directories(directory);
  }
  void TearDown() override { std::filesystem::remove_all(directory); }

  void CheckModel(const std::string& path, uint32_t version) {
    auto model = T9NgramModel::Open(path);
    ASSERT_TRUE(model) << path;
    EXPECT_EQ(version, model->Version());
    EXPECT_EQ(5u, model->Words());
    EXPECT_EQ(3, model->Eos());
    EXPECT_EQ(4, model->Bos());
    const int32_t wo = model->Id("我"), men = model->Id("们"), de = model->Id("的");
    EXPECT_EQ(0, wo); EXPECT_EQ(1, men); EXPECT_EQ(2, de);
    EXPECT_EQ(T9NgramModel::kUnseen, model->Id("你"));
    const double tol = 1.5 / cyime_t9_ngram::kLogScale;
    EXPECT_NEAR(-2.5, model->Start(wo, false), tol);          // ln_ml
    EXPECT_NEAR(-1.0, model->Start(wo, true), tol);           // <s>→我 stored
    EXPECT_NEAR(-0.1 + -3.0, model->Start(men, true), tol);   // <s> backoff + lower(们)
    EXPECT_NEAR(-13.0, model->Start(T9NgramModel::kUnseen, false), tol);
    EXPECT_NEAR(-0.5, model->LogProb(wo, men), tol);          // stored bigram
    EXPECT_NEAR(-0.3 + -1.5, model->LogProb(wo, wo), tol);    // backoff(我) + lower(我)
    EXPECT_NEAR(-0.3 + -12.0, model->LogProb(wo, T9NgramModel::kUnseen), tol);
    EXPECT_NEAR(-1.0, model->LogProb(T9NgramModel::kUnseen, de), tol);  // no history: lower
    EXPECT_NEAR(-0.125, model->LogProb3(wo, men, de), tol);   // stored trigram
    EXPECT_NEAR(-0.75 + (-0.6 + -1.5), model->LogProb3(wo, men, wo), tol);  // tri backoff + bigram backoff
    EXPECT_NEAR(-0.25, model->LogProb3(de, men, de), tol);    // (的,们) not a bigram: plain bigram
    std::vector<int32_t> parts;
    model->Decompose("我们的", &parts);
    ASSERT_EQ(3u, parts.size());
    EXPECT_EQ(wo, parts[0]); EXPECT_EQ(men, parts[1]); EXPECT_EQ(de, parts[2]);
    int32_t last2 = 0, last = 0;
    const double chain = model->Chain(T9NgramModel::kUnseen, T9NgramModel::kUnseen, true, false, parts, &last2, &last);
    EXPECT_NEAR(-2.5 + -0.5 + -0.125, chain, 3 * tol);
    EXPECT_EQ(men, last2); EXPECT_EQ(de, last);
  }
};

TEST_F(NgramModelTest, OriginalAndCompactLayoutsGiveTheSameProbabilities) {
  const ModelTables m = TinyModel();
  cyime_t9_ngram::WriteStats v2, v3;
  const std::string p2 = (directory / "v2.lm").string(), p3 = (directory / "v3.lm").string();
  ASSERT_TRUE(cyime_t9_ngram::WriteV2(p2, m, &v2));
  ASSERT_TRUE(cyime_t9_ngram::WriteV3(p3, m, &v3));
  EXPECT_EQ(0u, v2.hash_collisions);
  EXPECT_EQ(0u, v3.hash_collisions);
  EXPECT_EQ(0u, v3.clamped);
  EXPECT_LT(v3.bytes, v2.bytes);
  CheckModel(p2, cyime_t9_ngram::kVersion);
  CheckModel(p3, cyime_t9_ngram::kVersion3);
}

TEST_F(NgramModelTest, CompactTrigramIndexSurvivesBlockBoundaries) {
  // 70 histories with one bigram each, trigrams only under bigrams 0 and 69: with block shift 6
  // the second lies in another block, so its begin must come from that block's base.
  ModelTables m;
  m.eos = 70; m.bos = 71; m.unseen_lower = -10; m.unseen_ml = -11;
  for (uint32_t w = 0; w < 70; ++w) {
    const std::string text = "w" + std::to_string(w);
    m.keys.push_back(cyime_t9_ngram::Hash(text.data(), text.size()));
    m.entries.push_back(WordEntry{-3.0f, -4.0f, -0.5f, 0});
    m.begin.push_back(w);
    m.next.push_back((w + 1) % 70);
    m.logp.push_back(-0.01f * static_cast<float>(w + 1));
    m.bigram_backoff.push_back(-0.2f);
  }
  m.keys.push_back(0); m.keys.push_back(0);
  m.entries.push_back(WordEntry{-5, -6, 0, 0}); m.entries.push_back(WordEntry{-7, -8, 0, 0});
  m.begin.push_back(70); m.begin.push_back(70); m.begin.push_back(70);
  m.tri_begin.assign(71, 0);
  for (uint32_t b = 1; b <= 70; ++b) m.tri_begin[b] = b <= 69 ? 1 : 2;
  m.tri_next = {5, 7};
  m.tri_logp = {-0.03f, -0.07f};
  cyime_t9_ngram::WriteStats stats;
  const std::string p3 = (directory / "blocks.lm").string();
  ASSERT_TRUE(cyime_t9_ngram::WriteV3(p3, m, &stats));
  EXPECT_EQ(6u, stats.tri_block_shift);
  auto model = T9NgramModel::Open(p3);
  ASSERT_TRUE(model);
  const double tol = 1.5 / cyime_t9_ngram::kLogScale;
  EXPECT_NEAR(-0.03, model->LogProb3(0, 1, 5), tol);                // trigram under bigram 0
  EXPECT_NEAR(-0.07, model->LogProb3(69, 0, 7), tol);               // trigram under bigram 69
  EXPECT_NEAR(-0.2 + (-0.5 + -3.0), model->LogProb3(69, 0, 8), tol);  // backoff under bigram 69
  EXPECT_NEAR(-0.2 + -0.32, model->LogProb3(30, 31, 32), tol);      // bigram 31→32 stored, no trigram
}
}  // namespace
