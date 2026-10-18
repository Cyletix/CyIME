// Convert a version 2 T9 n-gram model file into the compact version 3 layout, then verify
// that the decoder reads the same probabilities from both (within the int16 step).
//
// Usage: ngram_pack in-v2.lm out-v3.lm [--no-verify] [--samples 200000]
// Build (WSL):
//   g++ -O2 -std=c++17 -I app/src/main/jni/librime-t9/src tools/ime_lab/ngram/ngram_pack.cc \
//       app/src/main/jni/librime-t9/src/t9_ngram_model.cc -o ngram_pack (one command line)
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iostream>
#include <random>
#include <string>
#include <vector>

#include "ngram_write.h"
#include "t9_ngram_model.h"

using namespace cyime_t9_ngram;

namespace {
template <class T>
bool ReadAt(std::ifstream& in, uint64_t offset, std::vector<T>* v, uint64_t count) {
  v->resize(count);
  in.seekg(static_cast<std::streamoff>(offset));
  if (count) in.read(reinterpret_cast<char*>(v->data()), count * sizeof(T));
  return static_cast<bool>(in);
}
}  // namespace

int main(int argc, char** argv) {
  if (argc < 3) { std::cerr << "usage: ngram_pack in-v2.lm out-v3.lm [--no-verify] [--samples N]\n"; return 2; }
  bool verify = true;
  uint64_t samples = 200000;
  for (int i = 3; i < argc; ++i) {
    const std::string a = argv[i];
    if (a == "--no-verify") verify = false;
    else if (a == "--samples") samples = std::stoull(argv[++i]);
  }
  std::ifstream in(argv[1], std::ios::binary);
  Header h{};
  if (!in.read(reinterpret_cast<char*>(&h), sizeof(h)) || std::memcmp(h.magic, kMagic, 8) != 0 || h.version != kVersion) {
    std::cerr << "not a version 2 model: " << argv[1] << "\n";
    return 2;
  }
  ModelTables m;
  m.eos = h.eos; m.bos = h.bos; m.unseen_lower = h.unseen_lower; m.unseen_ml = h.unseen_ml;
  std::vector<HashSlot> slots;
  if (!ReadAt(in, h.hash_offset, &slots, h.hash_capacity) ||
      !ReadAt(in, h.word_offset, &m.entries, h.words) ||
      !ReadAt(in, h.begin_offset, &m.begin, h.words + 1) ||
      !ReadAt(in, h.next_offset, &m.next, h.bigrams) ||
      !ReadAt(in, h.logp_offset, &m.logp, h.bigrams) ||
      !ReadAt(in, h.bigram_backoff_offset, &m.bigram_backoff, h.bigrams) ||
      !ReadAt(in, h.tri_begin_offset, &m.tri_begin, h.bigrams + 1) ||
      !ReadAt(in, h.tri_next_offset, &m.tri_next, h.trigrams) ||
      !ReadAt(in, h.tri_logp_offset, &m.tri_logp, h.trigrams)) {
    std::cerr << "truncated model\n";
    return 2;
  }
  m.keys.assign(h.words, 0);
  for (const auto& s : slots) if (s.key && s.id < h.words) m.keys[s.id] = s.key;
  WriteStats stats;
  if (!WriteV3(argv[2], m, &stats)) { std::cerr << "cannot write " << argv[2] << "\n"; return 2; }
  std::printf("{\"words\":%u,\"bigrams\":%llu,\"trigrams\":%llu,\"tri_block_shift\":%u,\"hash_collisions\":%llu,"
              "\"clamped\":%llu,\"bytes\":%llu}\n",
              h.words, (unsigned long long)h.bigrams, (unsigned long long)h.trigrams, stats.tri_block_shift,
              (unsigned long long)stats.hash_collisions, (unsigned long long)stats.clamped,
              (unsigned long long)stats.bytes);
  if (!verify) return 0;

  // Compare the two readers on every word entry, every stored bigram and trigram index
  // reachable through random samples, and random (history2, history, word) triples.
  auto a = T9NgramModel::Open(argv[1]);
  auto b = T9NgramModel::Open(argv[2]);
  if (!a || !b) { std::cerr << "reader cannot open one of the files\n"; return 3; }
  // A backed-off value adds up to three quantized numbers, each off by half a step at most.
  const double tolerance = 1.5 / kLogScale + 1e-6;
  uint64_t checked = 0, mismatches = 0;
  double worst = 0;
  auto compare = [&](double x, double y, const char* what, int32_t u, int32_t v, int32_t w) {
    ++checked;
    const double d = std::fabs(x - y);
    // Values clamped at the int16 floor are allowed to differ (both are "impossible").
    const bool floor = y <= -32767.0 / kLogScale && x <= y;
    if (d > worst && !floor) worst = d;
    if (d > tolerance && !floor) {
      if (++mismatches <= 20)
        std::fprintf(stderr, "mismatch %s (%d,%d,%d): v2=%.6f v3=%.6f\n", what, u, v, w, x, y);
    }
  };
  std::mt19937_64 rng(20261012);
  const int32_t M = static_cast<int32_t>(h.words);
  for (int32_t w = 0; w < M; ++w) {
    compare(a->Start(w, false), b->Start(w, false), "ml", -1, -1, w);
    compare(a->LogProb(T9NgramModel::kUnseen, w), b->LogProb(T9NgramModel::kUnseen, w), "lower", -1, -1, w);
    compare(a->LogProb(w, T9NgramModel::kUnseen), b->LogProb(w, T9NgramModel::kUnseen), "backoff", -1, w, -1);
  }
  std::uniform_int_distribution<uint64_t> pick_bigram(0, h.bigrams ? h.bigrams - 1 : 0);
  std::uniform_int_distribution<int32_t> pick_word(0, M - 1);
  // Stored bigrams: find the history of a sampled bigram index by binary search on begin.
  for (uint64_t s = 0; s < samples && h.bigrams; ++s) {
    const uint64_t i = pick_bigram(rng);
    const int32_t v = static_cast<int32_t>(std::upper_bound(m.begin.begin(), m.begin.end(), static_cast<uint32_t>(i)) - m.begin.begin()) - 1;
    const int32_t w = static_cast<int32_t>(m.next[i]);
    compare(a->LogProb(v, w), b->LogProb(v, w), "bigram", -1, v, w);
    // A trigram under this bigram, if any, and a random third word (usually backoff).
    if (m.tri_begin[i] < m.tri_begin[i + 1]) {
      const uint64_t t = m.tri_begin[i] + rng() % (m.tri_begin[i + 1] - m.tri_begin[i]);
      const int32_t x = static_cast<int32_t>(m.tri_next[t]);
      compare(a->LogProb3(v, w, x), b->LogProb3(v, w, x), "trigram", v, w, x);
    }
    const int32_t r = pick_word(rng);
    compare(a->LogProb3(v, w, r), b->LogProb3(v, w, r), "tri-backoff", v, w, r);
  }
  for (uint64_t s = 0; s < samples; ++s) {
    const int32_t u = pick_word(rng), v = pick_word(rng), w = pick_word(rng);
    compare(a->LogProb(v, w), b->LogProb(v, w), "random-bigram", -1, v, w);
    compare(a->LogProb3(u, v, w), b->LogProb3(u, v, w), "random-trigram", u, v, w);
  }
  // Ids through the hash: every word of the old file must resolve to the same id in the
  // compact one when looked up by a text with that hash. Texts are not stored, so probe the
  // compact slots with the stored keys directly (the same walk the reader does).
  uint64_t id_mismatches = 0;
  {
    std::ifstream packed(argv[2], std::ios::binary);
    Header3 h3{};
    packed.read(reinterpret_cast<char*>(&h3), sizeof(h3));
    std::vector<uint64_t> slots3;
    ReadAt(packed, h3.hash_offset, &slots3, h3.hash_capacity);
    const uint64_t mask = h3.hash_capacity - 1;
    for (const auto& s : slots) {
      if (!s.key) continue;
      int64_t id = -1;
      for (uint64_t i = s.key & mask;; i = (i + 1) & mask) {
        if (!slots3[i]) break;
        if ((slots3[i] & kKeyMask) == (s.key & kKeyMask)) { id = static_cast<int64_t>((slots3[i] & 0xFFFFFF) - 1); break; }
      }
      if (id != static_cast<int64_t>(s.id)) ++id_mismatches;
    }
  }
  std::printf("{\"checked\":%llu,\"mismatches\":%llu,\"worst_abs_diff\":%.6f,\"tolerance\":%.6f,\"id_mismatches\":%llu}\n",
              (unsigned long long)checked, (unsigned long long)mismatches, worst, tolerance,
              (unsigned long long)id_mismatches);
  return mismatches || id_mismatches ? 1 : 0;
}
