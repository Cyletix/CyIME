// Writers for the T9 word trigram model files (formats in t9_ngram_format.h), shared by
// ngram_build.cc (corpus -> model) and ngram_pack.cc (version 2 file -> compact version 3).
#ifndef CYIME_TOOLS_NGRAM_WRITE_H_
#define CYIME_TOOLS_NGRAM_WRITE_H_

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

#include "t9_ngram_format.h"

namespace cyime_t9_ngram {

// The model as the version 2 layout holds it, with every id and offset as a plain integer.
struct ModelTables {
  uint32_t eos = 0, bos = 0;
  float unseen_lower = 0, unseen_ml = 0;
  std::vector<uint64_t> keys;        // Hash(text) per word; 0 for </s> and <s>
  std::vector<WordEntry> entries;    // per word
  std::vector<uint32_t> begin;       // words + 1
  std::vector<uint32_t> next;        // bigrams, ascending within a history
  std::vector<float> logp;           // bigrams
  std::vector<float> bigram_backoff; // bigrams
  std::vector<uint32_t> tri_begin;   // bigrams + 1
  std::vector<uint32_t> tri_next;    // trigrams
  std::vector<float> tri_logp;       // trigrams
  uint32_t words() const { return static_cast<uint32_t>(entries.size()); }
  uint64_t bigrams() const { return next.size(); }
  uint64_t trigrams() const { return tri_next.size(); }
};

struct WriteStats {
  uint64_t bytes = 0;
  uint64_t hash_collisions = 0;
  uint32_t tri_block_shift = 0;
  uint64_t clamped = 0;  // values outside the int16 range (compact only)
};

inline uint64_t Align8(uint64_t x) { return (x + 7) & ~uint64_t{7}; }

inline void Pad(std::ofstream& out, uint64_t offset) {
  while (static_cast<uint64_t>(out.tellp()) < offset) out.put(0);
}

template <class T>
void WriteRaw(std::ofstream& out, const std::vector<T>& v) {
  if (!v.empty()) out.write(reinterpret_cast<const char*>(v.data()), v.size() * sizeof(T));
}

inline uint64_t HashCapacity(uint32_t words) {
  uint64_t capacity = 1;
  while (capacity < static_cast<uint64_t>(words) * 2) capacity <<= 1;
  return capacity;
}

// Version 2: the original layout.
inline bool WriteV2(const std::string& path, const ModelTables& m, WriteStats* stats) {
  const uint32_t M = m.words();
  const uint64_t kept = m.bigrams(), kept3 = m.trigrams();
  const uint64_t capacity = HashCapacity(M);
  std::vector<HashSlot> slots(capacity, HashSlot{0, 0, 0});
  for (uint32_t w = 0; w < M; ++w) {
    const uint64_t key = m.keys[w];
    if (!key) continue;
    for (uint64_t i = key & (capacity - 1);; i = (i + 1) & (capacity - 1)) {
      if (slots[i].key == key) { ++stats->hash_collisions; break; }
      if (!slots[i].key) { slots[i] = {key, w, 0}; break; }
    }
  }
  Header h{};
  std::memcpy(h.magic, kMagic, sizeof(h.magic));
  h.version = kVersion;
  h.words = M; h.eos = m.eos; h.bos = m.bos;
  h.bigrams = kept; h.trigrams = kept3; h.hash_capacity = capacity;
  h.unseen_lower = m.unseen_lower; h.unseen_ml = m.unseen_ml;
  h.hash_offset = Align8(sizeof(h));
  h.word_offset = Align8(h.hash_offset + capacity * sizeof(HashSlot));
  h.begin_offset = Align8(h.word_offset + static_cast<uint64_t>(M) * sizeof(WordEntry));
  h.next_offset = Align8(h.begin_offset + (static_cast<uint64_t>(M) + 1) * sizeof(uint32_t));
  h.logp_offset = Align8(h.next_offset + kept * sizeof(uint32_t));
  h.bigram_backoff_offset = Align8(h.logp_offset + kept * sizeof(float));
  h.tri_begin_offset = Align8(h.bigram_backoff_offset + kept * sizeof(float));
  h.tri_next_offset = Align8(h.tri_begin_offset + (kept + 1) * sizeof(uint32_t));
  h.tri_logp_offset = Align8(h.tri_next_offset + kept3 * sizeof(uint32_t));
  stats->bytes = h.tri_logp_offset + kept3 * sizeof(float);
  std::ofstream out(path, std::ios::binary);
  if (!out) return false;
  out.write(reinterpret_cast<const char*>(&h), sizeof(h));
  Pad(out, h.hash_offset); WriteRaw(out, slots);
  Pad(out, h.word_offset); WriteRaw(out, m.entries);
  Pad(out, h.begin_offset); WriteRaw(out, m.begin);
  Pad(out, h.next_offset); WriteRaw(out, m.next);
  Pad(out, h.logp_offset); WriteRaw(out, m.logp);
  Pad(out, h.bigram_backoff_offset); WriteRaw(out, m.bigram_backoff);
  Pad(out, h.tri_begin_offset); WriteRaw(out, m.tri_begin);
  Pad(out, h.tri_next_offset); WriteRaw(out, m.tri_next);
  Pad(out, h.tri_logp_offset); WriteRaw(out, m.tri_logp);
  return static_cast<bool>(out);
}

// Version 3: the compact layout. Returns false when the model does not fit it
// (too many words) or the file cannot be written.
inline bool WriteV3(const std::string& path, const ModelTables& m, WriteStats* stats) {
  const uint32_t M = m.words();
  const uint64_t kept = m.bigrams(), kept3 = m.trigrams();
  if (M > kMaxCompactWords) return false;
  auto quantize = [&](double ln) {
    const int16_t q = QuantizeLog(ln);
    if (q == -32768 || q == 32767) ++stats->clamped;
    return q;
  };
  // Hash: high 40 bits of the key plus id + 1.
  const uint64_t capacity = HashCapacity(M);
  std::vector<uint64_t> slots(capacity, 0);
  for (uint32_t w = 0; w < M; ++w) {
    const uint64_t key = m.keys[w];
    if (!key) continue;
    const uint64_t high = key & kKeyMask;
    for (uint64_t i = key & (capacity - 1);; i = (i + 1) & (capacity - 1)) {
      if (slots[i] && (slots[i] & kKeyMask) == high) { ++stats->hash_collisions; break; }
      if (!slots[i]) { slots[i] = high | (static_cast<uint64_t>(w) + 1); break; }
    }
  }
  std::vector<int16_t> words(static_cast<size_t>(M) * 3);
  for (uint32_t w = 0; w < M; ++w) {
    words[w * 3] = quantize(m.entries[w].ln_lower);
    words[w * 3 + 1] = quantize(m.entries[w].ln_ml);
    words[w * 3 + 2] = quantize(m.entries[w].ln_backoff);
  }
  std::vector<uint8_t> next(kept * 3), tri_next(kept3 * 3);
  for (uint64_t i = 0; i < kept; ++i) {
    next[i * 3] = m.next[i] & 0xFF; next[i * 3 + 1] = (m.next[i] >> 8) & 0xFF; next[i * 3 + 2] = (m.next[i] >> 16) & 0xFF;
  }
  for (uint64_t i = 0; i < kept3; ++i) {
    tri_next[i * 3] = m.tri_next[i] & 0xFF; tri_next[i * 3 + 1] = (m.tri_next[i] >> 8) & 0xFF;
    tri_next[i * 3 + 2] = (m.tri_next[i] >> 16) & 0xFF;
  }
  std::vector<int16_t> logp(kept), backoff(kept), tri_logp(kept3);
  for (uint64_t i = 0; i < kept; ++i) { logp[i] = quantize(m.logp[i]); backoff[i] = quantize(m.bigram_backoff[i]); }
  for (uint64_t i = 0; i < kept3; ++i) tri_logp[i] = quantize(m.tri_logp[i]);
  // Trigram begin: the largest block shift (fewest uint32 bases) whose in-block offsets fit uint16.
  uint32_t shift = 6;
  for (;; --shift) {
    bool fits = true;
    for (uint64_t b = 0; b <= kept && fits; ++b)
      fits = m.tri_begin[b] - m.tri_begin[(b >> shift) << shift] <= 0xFFFF;
    if (fits || shift == 0) break;
  }
  const uint64_t blocks = (kept >> shift) + 1;
  std::vector<uint32_t> tri_base(blocks);
  std::vector<uint16_t> tri_lo(kept + 1);
  for (uint64_t blk = 0; blk < blocks; ++blk) tri_base[blk] = m.tri_begin[std::min<uint64_t>(blk << shift, kept)];
  for (uint64_t b = 0; b <= kept; ++b) tri_lo[b] = static_cast<uint16_t>(m.tri_begin[b] - tri_base[b >> shift]);
  stats->tri_block_shift = shift;

  Header3 h{};
  std::memcpy(h.magic, kMagic3, sizeof(h.magic));
  h.version = kVersion3;
  h.words = M; h.eos = m.eos; h.bos = m.bos;
  h.bigrams = kept; h.trigrams = kept3; h.hash_capacity = capacity;
  h.unseen_lower = m.unseen_lower; h.unseen_ml = m.unseen_ml;
  h.tri_block_shift = shift;
  h.hash_offset = Align8(sizeof(h));
  h.word_offset = Align8(h.hash_offset + capacity * sizeof(uint64_t));
  h.begin_offset = Align8(h.word_offset + words.size() * sizeof(int16_t));
  h.next_offset = Align8(h.begin_offset + (static_cast<uint64_t>(M) + 1) * sizeof(uint32_t));
  h.logp_offset = Align8(h.next_offset + next.size());
  h.bigram_backoff_offset = Align8(h.logp_offset + kept * sizeof(int16_t));
  h.tri_base_offset = Align8(h.bigram_backoff_offset + kept * sizeof(int16_t));
  h.tri_lo_offset = Align8(h.tri_base_offset + blocks * sizeof(uint32_t));
  h.tri_next_offset = Align8(h.tri_lo_offset + (kept + 1) * sizeof(uint16_t));
  h.tri_logp_offset = Align8(h.tri_next_offset + tri_next.size());
  stats->bytes = h.tri_logp_offset + kept3 * sizeof(int16_t);
  std::ofstream out(path, std::ios::binary);
  if (!out) return false;
  out.write(reinterpret_cast<const char*>(&h), sizeof(h));
  Pad(out, h.hash_offset); WriteRaw(out, slots);
  Pad(out, h.word_offset); WriteRaw(out, words);
  Pad(out, h.begin_offset); WriteRaw(out, m.begin);
  Pad(out, h.next_offset); WriteRaw(out, next);
  Pad(out, h.logp_offset); WriteRaw(out, logp);
  Pad(out, h.bigram_backoff_offset); WriteRaw(out, backoff);
  Pad(out, h.tri_base_offset); WriteRaw(out, tri_base);
  Pad(out, h.tri_lo_offset); WriteRaw(out, tri_lo);
  Pad(out, h.tri_next_offset); WriteRaw(out, tri_next);
  Pad(out, h.tri_logp_offset); WriteRaw(out, tri_logp);
  return static_cast<bool>(out);
}

inline bool WriteModel(const std::string& path, const ModelTables& m, uint32_t version, WriteStats* stats) {
  return version == kVersion3 ? WriteV3(path, m, stats) : WriteV2(path, m, stats);
}

}  // namespace cyime_t9_ngram

#endif  // CYIME_TOOLS_NGRAM_WRITE_H_
