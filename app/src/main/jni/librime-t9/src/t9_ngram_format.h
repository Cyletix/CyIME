#ifndef CYIME_T9_NGRAM_FORMAT_H_
#define CYIME_T9_NGRAM_FORMAT_H_

// File formats of the T9 word trigram model (version 2 below, compact version 3 after it), shared by tools/ime_lab/ngram/ngram_build.cc
// and the joint decoder. Little endian, memory-mapped, sections 8-byte aligned.
// All log values are natural logs. For histories u, v and a word w:
//   ln P(w | v)    = stored bigram (v, w) if kept, else ln_backoff(v) + ln_lower(w);
//   ln P(w | u, v) = stored trigram (u, v, w) if kept,
//                  = bigram_backoff(u, v) + ln P(w | v) if the bigram (u, v) is kept,
//                  = ln P(w | v) otherwise;
// and a dictionary word missing from the model uses unseen_lower / unseen_ml.
#include <cstddef>
#include <cstdint>

namespace cyime_t9_ngram {

constexpr char kMagic[8] = {'C', 'Y', 'T', '9', 'N', 'G', 'R', '2'};
constexpr uint32_t kVersion = 2;

struct Header {
  char magic[8];
  uint32_t version;
  uint32_t words;          // model words, including </s> and <s>
  uint32_t eos;            // model id of </s>
  uint32_t bos;            // model id of <s> (history only)
  uint64_t bigrams;
  uint64_t trigrams;
  uint64_t hash_capacity;  // power of two
  float unseen_lower;      // ln P_lower of a dictionary word never seen in the corpus
  float unseen_ml;         // ln P_ml of the same
  uint64_t hash_offset;    // HashSlot[hash_capacity]
  uint64_t word_offset;    // WordEntry[words]
  uint64_t begin_offset;   // uint32_t[words + 1]; bigrams of history v are [begin[v], begin[v+1])
  uint64_t next_offset;    // uint32_t[bigrams], ascending within each history
  uint64_t logp_offset;    // float[bigrams]
  uint64_t bigram_backoff_offset;  // float[bigrams]: backoff of the bigram as a trigram history
  uint64_t tri_begin_offset;       // uint32_t[bigrams + 1]: trigrams after bigram b
  uint64_t tri_next_offset;        // uint32_t[trigrams], ascending within each bigram
  uint64_t tri_logp_offset;        // float[trigrams]
};

struct HashSlot {
  uint64_t key;  // Hash(text); 0 marks an empty slot
  uint32_t id;
  uint32_t reserved;
};

struct WordEntry {
  float ln_lower;    // Kneser-Ney lower order (continuation) probability
  float ln_ml;       // corpus frequency, for a word without history
  float ln_backoff;  // backoff weight when this word is the history
  float reserved;
};

// Compact format (version 3): the same model with every table packed.
//   hash:     uint64_t[hash_capacity]; (key & kKeyMask) | (id + 1), 0 marks an empty slot.
//             The slot position comes from the low bits of the key, the stored high 40 bits
//             tell the words of one probe run apart.
//   words:    int16_t[words][3] = ln_lower, ln_ml, ln_backoff scaled by kLogScale.
//   next:     3-byte little-endian ids; logp, bigram_backoff, tri_logp: int16_t scaled.
//   tri_begin(b) = tri_base[b >> tri_block_shift] + tri_lo[b], with uint32_t tri_base and
//             uint16_t tri_lo (shift 0 degenerates to one uint32_t per bigram).
constexpr char kMagic3[8] = {'C', 'Y', 'T', '9', 'N', 'G', 'R', '3'};
constexpr uint32_t kVersion3 = 3;
constexpr double kLogScale = 1024.0;  // int16 step of about 0.001 nats, range about [-32, 32)
constexpr uint64_t kKeyMask = ~uint64_t{0xFFFFFF};
constexpr uint32_t kMaxCompactWords = 0xFFFFFE;

struct Header3 {
  char magic[8];
  uint32_t version;
  uint32_t words;
  uint32_t eos;
  uint32_t bos;
  uint64_t bigrams;
  uint64_t trigrams;
  uint64_t hash_capacity;
  float unseen_lower;
  float unseen_ml;
  uint32_t tri_block_shift;
  uint32_t reserved;
  uint64_t hash_offset;            // uint64_t[hash_capacity]
  uint64_t word_offset;            // int16_t[words * 3]
  uint64_t begin_offset;           // uint32_t[words + 1]
  uint64_t next_offset;            // uint8_t[bigrams * 3]
  uint64_t logp_offset;            // int16_t[bigrams]
  uint64_t bigram_backoff_offset;  // int16_t[bigrams]
  uint64_t tri_base_offset;        // uint32_t[(bigrams >> tri_block_shift) + 1]
  uint64_t tri_lo_offset;          // uint16_t[bigrams + 1]
  uint64_t tri_next_offset;        // uint8_t[trigrams * 3]
  uint64_t tri_logp_offset;        // int16_t[trigrams]
};

inline int16_t QuantizeLog(double ln) {
  const double scaled = ln * kLogScale;
  if (!(scaled > -32768.0)) return -32768;  // also -inf and NaN
  if (scaled > 32767.0) return 32767;
  return static_cast<int16_t>(scaled < 0 ? scaled - 0.5 : scaled + 0.5);
}

inline double DequantizeLog(int16_t q) { return q / kLogScale; }

inline uint32_t ReadId24(const uint8_t* p) {
  return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) | (static_cast<uint32_t>(p[2]) << 16);
}

// FNV-1a over the UTF-8 bytes of the word text.
inline uint64_t Hash(const char* text, size_t size) {
  uint64_t h = 14695981039346656037ULL;
  for (size_t i = 0; i < size; ++i) {
    h ^= static_cast<unsigned char>(text[i]);
    h *= 1099511628211ULL;
  }
  return h ? h : 1;
}

}  // namespace cyime_t9_ngram

#endif  // CYIME_T9_NGRAM_FORMAT_H_
