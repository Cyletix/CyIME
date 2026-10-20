// CyLM batch decoder: the same algorithm as tools/cylm/decoder.py, fast enough to evaluate every board column
// in a minute and to serve as the reference for the IME's own implementation.
//
//   cylm.exe LEXICON.tsv MODEL.lm [--adapt COUNTS.tsv] [--set key=value ...] < items.tsv > out.tsv
//
// LEXICON.tsv: word \t syllables (space separated) \t weight   (exported by cylm/export.py, adjustments applied)
// COUNTS.tsv : a \t b \t w \t count                              (adaptive trigram counts, <s> and </s> included)
// items.tsv  : id \t mode (pinyin|t9|choice) \t keys \t context [\t candidate|candidate... for choice]
// out.tsv    : id \t ms \t cand1|cand2|...
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iostream>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#ifdef _WIN32
#define NOMINMAX
#include <windows.h>
#else
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#endif

using u16 = std::u16string;
static const int kMaxWord = 8, kMaxAbbrev = 6;

// ------------------------------------------------------------------ utf-8
static std::vector<uint32_t> Utf8Decode(const std::string& s) {
  std::vector<uint32_t> out;
  for (size_t i = 0; i < s.size();) {
    unsigned char c = s[i];
    uint32_t cp; int n;
    if (c < 0x80) { cp = c; n = 1; }
    else if ((c >> 5) == 6) { cp = c & 0x1f; n = 2; }
    else if ((c >> 4) == 14) { cp = c & 0x0f; n = 3; }
    else { cp = c & 0x07; n = 4; }
    for (int k = 1; k < n && i + k < s.size(); ++k) cp = (cp << 6) | (s[i + k] & 0x3f);
    out.push_back(cp); i += n;
  }
  return out;
}
static void Utf8Append(std::string& s, uint32_t cp) {
  if (cp < 0x80) s += char(cp);
  else if (cp < 0x800) { s += char(0xc0 | (cp >> 6)); s += char(0x80 | (cp & 0x3f)); }
  else if (cp < 0x10000) { s += char(0xe0 | (cp >> 12)); s += char(0x80 | ((cp >> 6) & 0x3f)); s += char(0x80 | (cp & 0x3f)); }
  else { s += char(0xf0 | (cp >> 18)); s += char(0x80 | ((cp >> 12) & 0x3f)); s += char(0x80 | ((cp >> 6) & 0x3f)); s += char(0x80 | (cp & 0x3f)); }
}
static std::string Utf8Encode(const std::vector<uint32_t>& v, size_t a, size_t b) {
  std::string s; for (size_t i = a; i < b; ++i) Utf8Append(s, v[i]); return s;
}
static bool IsHan(uint32_t c) { return c >= 0x4e00 && c <= 0x9fff; }

static std::vector<std::string> Split(const std::string& s, char d) {
  std::vector<std::string> out; size_t a = 0;
  for (;;) { size_t b = s.find(d, a); out.push_back(s.substr(a, b - a)); if (b == std::string::npos) break; a = b + 1; }
  return out;
}

static const uint8_t* MapFile(const char* path) {
#ifdef _WIN32
  HANDLE f = CreateFileA(path, GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, 0, nullptr);
  if (f == INVALID_HANDLE_VALUE) return nullptr;
  HANDLE map = CreateFileMappingA(f, nullptr, PAGE_READONLY, 0, 0, nullptr);
  return map ? static_cast<const uint8_t*>(MapViewOfFile(map, FILE_MAP_READ, 0, 0, 0)) : nullptr;
#else
  int fd = ::open(path, O_RDONLY);
  if (fd < 0) return nullptr;
  struct stat st; if (fstat(fd, &st) != 0) { ::close(fd); return nullptr; }
  void* addr = mmap(nullptr, size_t(st.st_size), PROT_READ, MAP_SHARED, fd, 0);
  ::close(fd);
  return addr == MAP_FAILED ? nullptr : static_cast<const uint8_t*>(addr);
#endif
}

// ------------------------------------------------------------------ c3 model
#pragma pack(push, 1)
struct Header3 {
  char magic[8]; uint32_t version, words, eos, bos; uint64_t bigrams, trigrams, capacity;
  float unseen_lower, unseen_ml; uint32_t shift, reserved;
  uint64_t hash_offset, word_offset, begin_offset, next_offset, logp_offset, backoff_offset, tri_base_offset,
      tri_lo_offset, tri_next_offset, tri_logp_offset;
};
#pragma pack(pop)

struct C3 {
  const uint8_t* m = nullptr; Header3 h{};
  static constexpr double kScale = 1024.0;
  int32_t none = -1;
  std::unordered_map<std::string, int32_t> id_cache;
  std::unordered_map<std::string, std::vector<int32_t>> dec_cache;
  bool Open(const char* path) {
#ifdef _WIN32
    HANDLE f = CreateFileA(path, GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, 0, nullptr);
    if (f == INVALID_HANDLE_VALUE) return false;
    HANDLE map = CreateFileMappingA(f, nullptr, PAGE_READONLY, 0, 0, nullptr);
    m = static_cast<const uint8_t*>(MapViewOfFile(map, FILE_MAP_READ, 0, 0, 0));
#else
    int fd = ::open(path, O_RDONLY);
    if (fd < 0) return false;
    struct stat st; if (fstat(fd, &st) != 0) return false;
    void* addr = mmap(nullptr, size_t(st.st_size), PROT_READ, MAP_SHARED, fd, 0);
    ::close(fd);
    if (addr == MAP_FAILED) return false;
    m = static_cast<const uint8_t*>(addr);
#endif
    std::memcpy(&h, m, sizeof h);
    return std::memcmp(h.magic, "CYT9NGR3", 8) == 0 && h.version == 3;
  }
  template <class T> T At(uint64_t off) const { T v; std::memcpy(&v, m + off, sizeof v); return v; }
  uint32_t U24(uint64_t off) const { return m[off] | (m[off + 1] << 8) | (m[off + 2] << 16); }
  int32_t Id(const std::string& text) {
    auto it = id_cache.find(text);
    if (it != id_cache.end()) return it->second;
    uint64_t hh = 14695981039346656037ULL;
    for (unsigned char b : text) { hh ^= b; hh *= 1099511628211ULL; }
    if (!hh) hh = 1;
    uint64_t mask = h.capacity - 1, i = hh & mask; int32_t r = -1;
    for (;;) {
      uint64_t slot = At<uint64_t>(h.hash_offset + 8 * i);
      if (!slot) break;
      if ((slot >> 24) == (hh >> 24)) { r = int32_t(slot & 0xFFFFFF) - 1; break; }
      i = (i + 1) & mask;
    }
    if (id_cache.size() > 4000000) id_cache.clear();
    id_cache[text] = r;
    return r;
  }
  double Lower(int32_t i) const { return i < 0 ? h.unseen_lower : At<int16_t>(h.word_offset + 6 * i) / kScale; }
  double Ml(int32_t i) const { return i < 0 ? h.unseen_ml : At<int16_t>(h.word_offset + 6 * i + 2) / kScale; }
  double Backoff(int32_t i) const { return At<int16_t>(h.word_offset + 6 * i + 4) / kScale; }
  int64_t Find(uint64_t off, uint32_t b, uint32_t e, uint32_t v) const {
    uint32_t lo = b, hi = e;
    while (lo < hi) { uint32_t mid = (lo + hi) / 2; if (U24(off + 3ull * mid) < v) lo = mid + 1; else hi = mid; }
    return (lo < e && U24(off + 3ull * lo) == v) ? int64_t(lo) : -1;
  }
  int64_t Bigram(int32_t a, int32_t w) const {
    uint32_t b = At<uint32_t>(h.begin_offset + 4ull * a), e = At<uint32_t>(h.begin_offset + 4ull * a + 4);
    return Find(h.next_offset, b, e, uint32_t(w));
  }
  uint32_t TriBegin(int64_t b) const {
    return At<uint32_t>(h.tri_base_offset + 4ull * (uint64_t(b) >> h.shift)) + At<uint16_t>(h.tri_lo_offset + 2ull * b);
  }
  double Logp(int32_t a, int32_t w) const {
    if (a < 0) return Lower(w);
    if (w >= 0) { int64_t k = Bigram(a, w); if (k >= 0) return At<int16_t>(h.logp_offset + 2ull * k) / kScale; }
    return Backoff(a) + Lower(w);
  }
  double Logp3(int32_t a2, int32_t a, int32_t w) const {
    double bi = Logp(a, w);
    if (a2 < 0 || a < 0) return bi;
    int64_t b = Bigram(a2, a);
    if (b < 0) return bi;
    if (w >= 0) {
      int64_t k = Find(h.tri_next_offset, TriBegin(b), TriBegin(b + 1), uint32_t(w));
      if (k >= 0) return At<int16_t>(h.tri_logp_offset + 2ull * k) / kScale;
    }
    return At<int16_t>(h.backoff_offset + 2ull * b) / kScale + bi;
  }
  const std::vector<int32_t>& Decompose(const std::string& text) {
    auto it = dec_cache.find(text);
    if (it != dec_cache.end()) return it->second;
    std::vector<int32_t> parts;
    int32_t i = Id(text);
    if (i >= 0) parts.push_back(i);
    else {
      auto cps = Utf8Decode(text); size_t n = cps.size();
      std::vector<double> best(n + 1, -1e18); std::vector<size_t> from(n + 1, 0); std::vector<int32_t> wid(n + 1, -1);
      best[0] = 0;
      for (size_t a = 0; a < n; ++a) {
        if (best[a] <= -1e17) continue;
        for (size_t b = a + 1; b <= std::min(n, a + 8); ++b) {
          int32_t id = Id(Utf8Encode(cps, a, b));
          if (id < 0 && b > a + 1) continue;
          double s = best[a] + Ml(id);
          if (s > best[b]) { best[b] = s; from[b] = a; wid[b] = id; }
        }
      }
      for (size_t j = n; j > 0; j = from[j]) parts.push_back(wid[j]);
      std::reverse(parts.begin(), parts.end());
    }
    if (dec_cache.size() > 2000000) dec_cache.clear();
    return dec_cache[text] = parts;
  }
};

// ------------------------------------------------------------------ adaptive model
struct Adapt {
  static constexpr double D = 0.75;
  std::unordered_map<std::string, int32_t> ids; std::vector<std::string> names;
  struct H2 { size_t operator()(uint64_t x) const { return std::hash<uint64_t>()(x * 0x9E3779B97F4A7C15ULL); } };
  std::unordered_map<uint64_t, double, H2> c3, h3; std::unordered_map<uint64_t, int, H2> t3, cont2;
  std::unordered_map<int32_t, double> h2; std::unordered_map<int32_t, int> t2, cont1;
  double n1 = 1, vocab = 1; bool loaded = false;
  int32_t Intern(const std::string& s) {
    auto it = ids.find(s); if (it != ids.end()) return it->second;
    int32_t id = int32_t(names.size()); names.push_back(s); ids[s] = id; return id;
  }
  int32_t Lookup(const std::string& s) const { auto it = ids.find(s); return it == ids.end() ? -1 : it->second; }
  static uint64_t K2(int32_t a, int32_t b) { return (uint64_t(uint32_t(a)) << 32) | uint32_t(b); }
  static uint64_t K3(int32_t a, int32_t b, int32_t w) { return (uint64_t(uint32_t(a)) << 42) ^ (uint64_t(uint32_t(b)) << 21) ^ uint32_t(w); }
  bool Load(const char* path) {
    std::ifstream in(path); if (!in) return false;
    std::string line;
    std::unordered_map<uint64_t, double, H2> trig; std::vector<std::tuple<int32_t, int32_t, int32_t, double>> rows;
    while (std::getline(in, line)) {
      auto f = Split(line, '\t'); if (f.size() < 4) continue;
      rows.emplace_back(Intern(f[0]), Intern(f[1]), Intern(f[2]), std::stod(f[3]));
    }
    for (auto& [a, b, w, c] : rows) {
      c3[K3(a, b, w)] += c; h3[K2(a, b)] += c; t3[K2(a, b)] += 1; cont2[K2(b, w)] += 1;
    }
    for (auto& [k, c] : cont2) { int32_t b = int32_t(k >> 32), w = int32_t(k & 0xffffffff); h2[b] += c; t2[b] += 1; cont1[w] += 1; }
    n1 = 0; for (auto& [w, c] : cont1) n1 += c; if (n1 == 0) n1 = 1;
    vocab = double(cont1.size()) + 1; loaded = true;
    return true;
  }
  double P(int32_t a, int32_t b, int32_t w) const {
    auto get = [](const auto& m, auto k) -> double { auto it = m.find(k); return it == m.end() ? 0.0 : double(it->second); };
    double p1 = ((w >= 0 ? get(cont1, w) : 0.0) + 0.5) / (n1 + 0.5 * vocab);
    double p2 = p1;
    if (b >= 0) { double hh = get(h2, b); if (hh > 0) p2 = (std::max(get(cont2, K2(b, w)) - D, 0.0) + D * get(t2, b) * p1) / hh; }
    if (a < 0 || b < 0) return p2;
    double hh3 = get(h3, K2(a, b));
    if (hh3 <= 0) return p2;
    return (std::max(get(c3, K3(a, b, w)) - D, 0.0) + D * get(t3, K2(a, b)) * p2) / hh3;
  }
};

// ------------------------------------------------------------------ lexicon and keys
struct Lexicon {
  std::vector<std::string> syls; std::unordered_map<std::string, char16_t> code; std::vector<char> first;
  std::unordered_map<std::string, int64_t> weight;
  std::unordered_map<u16, std::vector<std::pair<int64_t, std::string>>> by_key;
  std::unordered_map<std::string, std::vector<u16>> by_init;
  std::unordered_set<std::string> init_prefix;
  double total = 1;
  bool Load(const char* path) {
    std::ifstream in(path); if (!in) return false;
    std::string line; std::vector<std::tuple<std::string, std::vector<std::string>, int64_t>> rows;
    std::map<std::string, int> sylset;
    while (std::getline(in, line)) {
      auto f = Split(line, '\t'); if (f.size() < 3) continue;
      auto s = Split(f[1], ' ');
      for (auto& x : s) sylset[x] = 0;
      rows.emplace_back(f[0], s, std::stoll(f[2]));
    }
    for (auto& [s, i] : sylset) { i = int(syls.size()); syls.push_back(s); }
    first.resize(syls.size());
    for (size_t i = 0; i < syls.size(); ++i) { code[syls[i]] = char16_t(i + 1); first[i] = syls[i][0]; }
    for (auto& [w, s, wt] : rows) {
      u16 key; for (auto& x : s) key += code[x];
      by_key[key].emplace_back(wt, w);
      auto& cur = weight[w]; if (wt > cur || cur == 0) cur = std::max(cur, wt);
    }
    std::unordered_map<u16, int64_t> best;
    for (auto& [k, v] : by_key) {
      std::sort(v.begin(), v.end(), [](auto& x, auto& y) { return x.first != y.first ? x.first > y.first : x.second > y.second; });
      best[k] = v[0].first;
      if (k.size() > size_t(kMaxWord)) continue;
      std::string ini; for (char16_t c : k) ini += first[c - 1];
      if (k.size() <= size_t(kMaxAbbrev)) by_init[ini].push_back(k);
      for (size_t i = 1; i <= ini.size(); ++i) init_prefix.insert(ini.substr(0, i));
    }
    for (auto& [i, v] : by_init) std::stable_sort(v.begin(), v.end(), [&](const u16& a, const u16& b) { return best[a] > best[b]; });
    total = 0; for (auto& [w, wt] : weight) total += double(wt); if (total <= 0) total = 1;
    return true;
  }
  const std::string& Syl(char16_t c) const { return syls[c - 1]; }

  // ---------------------------------------------------------------- compiled form (mmap, no parsing)
  // Sorted arrays searched by binary search, so loading is a file mapping. Layout (little endian):
  //   header | syllables (8 bytes each) | words: offsets u32[nw+1], pool, weights u32[nw] (sorted by bytes)
  //   | keys: offsets u32[nk+1], pool u16 (sorted), entry offsets u32[nk+1], entries {u32 weight, u32 word}
  //   | initials: offsets u32[ni+1], pool (sorted), key-list offsets u32[ni+1], key indices u32
  //   | prefixes: offsets u32[np+1], pool (sorted)
  struct BinHeader { char magic[8]; uint32_t nsyl, nw, nk, ni, np, pad; double total;
                     uint64_t syl, woff, wpool, wwt, koff, kpool, eoff, ent, ioff, ipool, loff, list, poff, ppool; };
  struct Ent { uint32_t wt, word; };
  bool bin = false; const uint8_t* B = nullptr; BinHeader H{};
  template <class T> const T* At(uint64_t off) const { return reinterpret_cast<const T*>(B + off); }
  std::string_view Word(uint32_t i) const { auto o = At<uint32_t>(H.woff); return {reinterpret_cast<const char*>(B + H.wpool + o[i]), o[i + 1] - o[i]}; }
  std::u16string_view Key(uint32_t i) const { auto o = At<uint32_t>(H.koff); return {At<char16_t>(H.kpool) + o[i], o[i + 1] - o[i]}; }
  std::string_view Ini(uint32_t i) const { auto o = At<uint32_t>(H.ioff); return {reinterpret_cast<const char*>(B + H.ipool + o[i]), o[i + 1] - o[i]}; }
  std::string_view Pre(uint32_t i) const { auto o = At<uint32_t>(H.poff); return {reinterpret_cast<const char*>(B + H.ppool + o[i]), o[i + 1] - o[i]}; }
  template <class F, class V> static int64_t Find(uint32_t n, F get, const V& v) {
    uint32_t lo = 0, hi = n;
    while (lo < hi) { uint32_t mid = (lo + hi) / 2; if (get(mid) < v) lo = mid + 1; else hi = mid; }
    return lo < n && get(lo) == v ? int64_t(lo) : -1;
  }
  size_t WordCount() const { return bin ? H.nw : weight.size(); }
  int64_t WeightOf(const std::string& w) const {
    if (!bin) { auto it = weight.find(w); return it == weight.end() ? -1 : it->second; }
    auto k = Find(H.nw, [&](uint32_t i) { return Word(i); }, std::string_view(w));
    return k < 0 ? -1 : int64_t(At<uint32_t>(H.wwt)[k]);
  }
  std::vector<std::pair<int64_t, std::string>> KeyWords(const u16& key) const {
    if (!bin) { auto it = by_key.find(key); return it == by_key.end() ? std::vector<std::pair<int64_t, std::string>>{} : it->second; }
    std::vector<std::pair<int64_t, std::string>> out;
    auto k = Find(H.nk, [&](uint32_t i) { return Key(i); }, std::u16string_view(key));
    if (k < 0) return out;
    auto eo = At<uint32_t>(H.eoff); auto e = At<Ent>(H.ent);
    for (uint32_t j = eo[k]; j < eo[k + 1]; ++j) out.emplace_back(int64_t(e[j].wt), std::string(Word(e[j].word)));
    return out;
  }
  std::vector<u16> InitKeys(const std::string& ini) const {
    if (!bin) { auto it = by_init.find(ini); return it == by_init.end() ? std::vector<u16>{} : it->second; }
    std::vector<u16> out;
    auto k = Find(H.ni, [&](uint32_t i) { return Ini(i); }, std::string_view(ini));
    if (k < 0) return out;
    auto lo = At<uint32_t>(H.loff); auto l = At<uint32_t>(H.list);
    for (uint32_t j = lo[k]; j < lo[k + 1]; ++j) out.emplace_back(Key(l[j]));
    return out;
  }
  bool HasPrefix(const std::string& p) const {
    if (!bin) return init_prefix.count(p) > 0;
    return Find(H.np, [&](uint32_t i) { return Pre(i); }, std::string_view(p)) >= 0;
  }
  bool Compile(const char* out_path) const {
    std::vector<std::string> words; for (auto& [w, wt] : weight) words.push_back(w);
    std::sort(words.begin(), words.end());
    std::unordered_map<std::string, uint32_t> wid; for (uint32_t i = 0; i < words.size(); ++i) wid[words[i]] = i;
    std::vector<u16> keys; for (auto& [k, v] : by_key) keys.push_back(k);
    std::sort(keys.begin(), keys.end());
    std::unordered_map<u16, uint32_t> kid; for (uint32_t i = 0; i < keys.size(); ++i) kid[keys[i]] = i;
    std::vector<std::string> inis; for (auto& [i, v] : by_init) inis.push_back(i);
    std::sort(inis.begin(), inis.end());
    std::vector<std::string> pres(init_prefix.begin(), init_prefix.end()); std::sort(pres.begin(), pres.end());
    std::string data; BinHeader h{}; std::memcpy(h.magic, "CYLX0002", 8);
    h.nsyl = uint32_t(syls.size()); h.nw = uint32_t(words.size()); h.nk = uint32_t(keys.size()); h.ni = uint32_t(inis.size()); h.np = uint32_t(pres.size()); h.total = total;
    data.resize(sizeof h);
    auto align = [&]() { while (data.size() % 8) data.push_back('\0'); };
    auto put = [&](const void* p, size_t n) { data.append(static_cast<const char*>(p), n); };
    auto offsets = [&](auto& items, auto size_of) { std::vector<uint32_t> o{0}; for (auto& x : items) o.push_back(o.back() + uint32_t(size_of(x))); return o; };
    align(); h.syl = data.size(); for (auto& s : syls) { char b[8] = {0}; std::memcpy(b, s.data(), std::min<size_t>(7, s.size())); put(b, 8); }
    align(); h.woff = data.size(); { auto o = offsets(words, [](auto& w) { return w.size(); }); put(o.data(), o.size() * 4); }
    h.wpool = data.size(); for (auto& w : words) put(w.data(), w.size());
    align(); h.wwt = data.size(); for (auto& w : words) { uint32_t x = uint32_t(std::min<int64_t>(weight.at(w), 0xFFFFFFFF)); put(&x, 4); }
    align(); h.koff = data.size(); { auto o = offsets(keys, [](auto& k) { return k.size(); }); put(o.data(), o.size() * 4); }
    align(); h.kpool = data.size(); for (auto& k : keys) put(k.data(), k.size() * 2);
    align(); h.eoff = data.size(); { std::vector<uint32_t> o{0}; for (auto& k : keys) o.push_back(o.back() + uint32_t(by_key.at(k).size())); put(o.data(), o.size() * 4); }
    align(); h.ent = data.size(); for (auto& k : keys) for (auto& [wt, w] : by_key.at(k)) { Ent e{uint32_t(std::min<int64_t>(std::max<int64_t>(wt, 0), 0xFFFFFFFF)), wid.at(w)}; put(&e, sizeof e); }
    align(); h.ioff = data.size(); { auto o = offsets(inis, [](auto& s) { return s.size(); }); put(o.data(), o.size() * 4); }
    h.ipool = data.size(); for (auto& s : inis) put(s.data(), s.size());
    align(); h.loff = data.size(); { std::vector<uint32_t> o{0}; for (auto& s : inis) o.push_back(o.back() + uint32_t(by_init.at(s).size())); put(o.data(), o.size() * 4); }
    align(); h.list = data.size(); for (auto& s : inis) for (auto& k : by_init.at(s)) { uint32_t x = kid.at(k); put(&x, 4); }
    align(); h.poff = data.size(); { auto o = offsets(pres, [](auto& s) { return s.size(); }); put(o.data(), o.size() * 4); }
    h.ppool = data.size(); for (auto& s : pres) put(s.data(), s.size());
    std::memcpy(data.data(), &h, sizeof h);
    std::ofstream f(out_path, std::ios::binary); f.write(data.data(), std::streamsize(data.size()));
    return bool(f);
  }
  bool LoadBin(const uint8_t* mapped) {
    std::memcpy(&H, mapped, sizeof H);
    if (std::memcmp(H.magic, "CYLX0002", 8) != 0) return false;
    B = mapped; bin = true; total = H.total;
    for (uint32_t i = 0; i < H.nsyl; ++i) syls.emplace_back(reinterpret_cast<const char*>(B + H.syl + 8 * i));
    first.resize(syls.size());
    for (size_t i = 0; i < syls.size(); ++i) { code[syls[i]] = char16_t(i + 1); first[i] = syls[i][0]; }
    return true;
  }
};

struct Token { char kind; char16_t full; char letter; std::string prefix; };  // kind f / a / s(eparator)

struct Params {
  double abbrev_full = 0.0, eos2 = -1.0, mix = 0.0, mu = 0.02, abbrev = -0.7, word_pen = 0.0, eos = 1.0, adapt_lambda = 0.0, select_lm = 1.0;
  int incremental = 0, interleave = 1, per_span = 24, per_span_abbrev = 48, per_span1 = 80, scan = 4000, beam = 32, nbest = 10;
  bool Set(const std::string& k, const std::string& v) {
    if (k == "incremental") incremental = std::stoi(v); else if (k == "abbrev_full") abbrev_full = std::stod(v); else if (k == "eos2") eos2 = std::stod(v); else if (k == "interleave") interleave = std::stoi(v);
    else if (k == "mix") mix = std::stod(v); else if (k == "mu") mu = std::stod(v); else if (k == "abbrev") abbrev = std::stod(v);
    else if (k == "word_pen") word_pen = std::stod(v); else if (k == "eos") eos = std::stod(v);
    else if (k == "adapt_lambda") adapt_lambda = std::stod(v); else if (k == "select_lm") select_lm = std::stod(v);
    else if (k == "per_span") per_span = std::stoi(v); else if (k == "per_span_abbrev") per_span_abbrev = std::stoi(v);
    else if (k == "per_span1") per_span1 = std::stoi(v); else if (k == "scan") scan = std::stoi(v);
    else if (k == "beam") beam = std::stoi(v); else if (k == "nbest") nbest = std::stoi(v);
    else return false;
    return true;
  }
};

static const char* kT9[] = {"", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};
static char T9Of(char c) { for (int d = 2; d <= 9; ++d) if (std::strchr(kT9[d], c)) return char('0' + d); return '?'; }
static const std::string kInitials = "bpmfdtnlgkhjqxrzcsyw";

struct State {
  int32_t a, b, wa, wb, a2 = -1, b2 = -1;
  bool operator==(const State& o) const { return a == o.a && b == o.b && wa == o.wa && wb == o.wb && a2 == o.a2 && b2 == o.b2; }
};
struct StateHash { size_t operator()(const State& s) const {
  uint64_t x = uint64_t(uint32_t(s.a)) * 0x9E3779B97F4A7C15ULL ^ uint64_t(uint32_t(s.b)) * 0xC2B2AE3D27D4EB4FULL ^
               uint64_t(uint32_t(s.wa)) * 0x165667B19E3779F9ULL ^ uint64_t(uint32_t(s.wb)) ^
               uint64_t(uint32_t(s.a2)) * 0x27D4EB2F165667C5ULL ^ uint64_t(uint32_t(s.b2)) * 0x94D049BB133111EBULL; return size_t(x ^ (x >> 29)); } };

struct Edge { int j; std::string word; int abbrevs; };

struct Decoder {
  Lexicon& lex; C3& base; Adapt& adapt; Params p; C3* base2 = nullptr;
  std::unordered_map<std::string, char16_t> spell; size_t max_syl = 0;
  std::unordered_map<std::string, std::vector<char16_t>> by_t9; std::unordered_map<std::string, std::vector<std::string>> t9_prefix;
  std::unordered_map<std::string, double> prior_cache, lnw_cache;
  std::unordered_map<std::string, std::vector<std::pair<double, std::string>>> hit_cache;
  int32_t BOSW, EOSW;
  Decoder(Lexicon& l, C3& b, Adapt& a, Params pp) : lex(l), base(b), adapt(a), p(pp) {
    for (auto& s : lex.syls) {
      if (s == "m" || s == "n" || s == "ng" || s == "hm" || s == "hng") continue;
      spell[s] = lex.code[s]; max_syl = std::max(max_syl, s.size());
      std::string d; for (char c : s) d += T9Of(c);
      by_t9[d].push_back(lex.code[s]);
      for (size_t i = 1; i <= d.size(); ++i) {
        auto& v = t9_prefix[d.substr(0, i)]; std::string pre = s.substr(0, i);
        if (std::find(v.begin(), v.end(), pre) == v.end()) v.push_back(pre);
      }
    }
    BOSW = adapt.Lookup("<s>"); EOSW = adapt.Lookup("</s>");
  }
  std::vector<std::vector<std::pair<int, Token>>> Tokens(const std::string& text, bool t9) {
    size_t n = text.size(); std::vector<std::vector<std::pair<int, Token>>> out(n);
    for (size_t i = 0; i < n; ++i) {
      if (text[i] == '\'') { out[i].push_back({int(i + 1), Token{'s', 0, 0, ""}}); continue; }
      for (size_t j = i + 1; j <= std::min(n, i + max_syl); ++j) {
        std::string sub = text.substr(i, j - i);
        if (!t9) { auto it = spell.find(sub); if (it != spell.end()) out[i].push_back({int(j), Token{'f', it->second, 0, ""}}); }
        else {
          auto it = by_t9.find(sub);
          if (it != by_t9.end()) for (char16_t c : it->second) out[i].push_back({int(j), Token{'f', c, 0, ""}});
          if (j == n) {
            auto pt = t9_prefix.find(sub);
            if (pt != t9_prefix.end()) for (auto& pre : pt->second) {
              auto sp = spell.find(pre);
              bool dup = false;
              if (sp != spell.end()) for (auto& e : out[i]) if (e.second.kind == 'f' && e.second.full == sp->second && e.first == int(j)) dup = true;
              if (!dup) out[i].push_back({int(j), Token{'a', 0, pre[0], pre}});
            }
          }
        }
      }
      if (!t9) {
        char ch = text[i];
        if (kInitials.find(ch) != std::string::npos) {
          std::string two = text.substr(i, 2);
          if (two == "zh" || two == "ch" || two == "sh") out[i].push_back({int(i + 2), Token{'a', 0, ch, two}});
          out[i].push_back({int(i + 1), Token{'a', 0, ch, ""}});
        }
      }
    }
    return out;
  }
  double Lnw(const std::string& w) {
    auto it = lnw_cache.find(w); if (it != lnw_cache.end()) return it->second;
    double wt = double(std::max<int64_t>(0, lex.WeightOf(w)));
    return lnw_cache[w] = std::log((wt + 1) / lex.total);
  }
  double Prior(const std::string& w) {
    auto it = prior_cache.find(w); if (it != prior_cache.end()) return it->second;
    double lm = 0;
    if (p.select_lm != 0) for (int32_t x : base.Decompose(w)) lm += base.Ml(x);
    double r = p.select_lm * lm + std::max(p.mu, 0.02) * Lnw(w) * (p.select_lm != 0 ? 1 : 50);
    return prior_cache[w] = r;
  }
  bool Matches(const u16& key, const std::vector<Token>& path) {
    for (size_t i = 0; i < path.size(); ++i) {
      const Token& t = path[i];
      if (t.kind == 'f') { if (key[i] != t.full) return false; }
      else {
        const std::string& s = lex.Syl(key[i]);
        if (s[0] != t.letter) return false;
        if (!t.prefix.empty() && s.compare(0, t.prefix.size(), t.prefix) != 0) return false;
      }
    }
    return true;
  }
  void Collect(std::unordered_map<std::string, std::pair<int, double>>& bucket, const std::vector<Token>& path, const std::string& ini) {
    int ab = 0; for (auto& t : path) ab += t.kind == 'a';
    std::string ck;
    if (!ab) { ck = "F"; for (auto& t : path) { ck += char(t.full >> 8); ck += char(t.full & 0xff); } }
    else {
      if (path.size() > size_t(kMaxAbbrev)) return;
      ck = "A";
      for (auto& t : path) { if (t.kind == 'f') { ck += 'f'; ck += char(t.full >> 8); ck += char(t.full & 0xff); } else { ck += 'a'; ck += t.letter; ck += t.prefix; ck += '/'; } }
    }
    auto it = hit_cache.find(ck);
    if (it == hit_cache.end()) {
      std::vector<std::pair<double, std::string>> hits;
      if (!ab) {
        u16 key; for (auto& t : path) key += t.full;
        for (auto& [wt, w] : lex.KeyWords(key)) hits.emplace_back(Prior(w), w);
        std::sort(hits.begin(), hits.end(), [](auto& x, auto& y) { return x.first != y.first ? x.first > y.first : x.second > y.second; });
        if (hits.size() > size_t(p.per_span)) hits.resize(p.per_span);
      } else {
        size_t limit = path.size() == 1 ? p.per_span1 : p.per_span_abbrev;
        std::unordered_set<std::string> words;
        auto init_keys = lex.InitKeys(ini);
        {
          size_t k = 0;
          for (auto& key : init_keys) {
            if (k++ >= size_t(p.scan)) break;
            if (Matches(key, path)) for (auto& [wt, w] : lex.KeyWords(key)) words.insert(w);
          }
        }
        for (auto& w : words) hits.emplace_back(Prior(w), w);
        std::sort(hits.begin(), hits.end(), [](auto& x, auto& y) { return x.first != y.first ? x.first > y.first : x.second > y.second; });
        if (hits.size() > limit) hits.resize(limit);
      }
      it = hit_cache.emplace(ck, std::move(hits)).first;
    }
    for (auto& [pr, w] : it->second) {
      auto old = bucket.find(w);
      if (old == bucket.end() || std::make_pair(old->second.first, -old->second.second) > std::make_pair(ab, -pr)) bucket[w] = {ab, pr};
    }
  }
  std::vector<std::vector<Edge>> Lattice(const std::string& text, bool t9, int min_end = -1) {
    auto toks = Tokens(text, t9);
    size_t n = text.size();
    std::map<std::pair<int, int>, std::unordered_map<std::string, std::pair<int, double>>> spans;
    // A word covers at most kMaxWord syllables of at most 6 keys (plus separators), so spans that end after
    // min_end start no earlier than this.
    const size_t first = min_end < 0 ? 0 : size_t(std::max(0, min_end + 1 - 64));
    for (size_t i = first; i < n; ++i) {
      for (auto& [j, t] : toks[i]) if (t.kind == 's' && j > min_end) spans[{int(i), j}][""] = {0, 0.0};
      struct Item { int pos; std::vector<Token> path; std::string ini; };
      std::vector<Item> stack{{int(i), {}, ""}};
      while (!stack.empty()) {
        Item cur = std::move(stack.back()); stack.pop_back();
        if (cur.pos >= int(n)) continue;
        for (auto& [j, t] : toks[cur.pos]) {
          if (t.kind == 's') continue;
          char f = t.kind == 'f' ? lex.first[t.full - 1] : t.letter;
          std::string ini2 = cur.ini + f;
          if (!lex.HasPrefix(ini2)) continue;
          std::vector<Token> path2 = cur.path; path2.push_back(t);
          if (j > min_end) Collect(spans[{int(i), j}], path2, ini2);
          if (path2.size() < size_t(kMaxWord)) stack.push_back({j, std::move(path2), ini2});
        }
      }
    }
    std::vector<std::vector<Edge>> out(n);
    for (auto& [ij, words] : spans) {
      std::vector<std::pair<std::string, std::pair<int, double>>> items(words.begin(), words.end());
      std::sort(items.begin(), items.end(), [](auto& x, auto& y) {
        if (x.second.first != y.second.first) return x.second.first < y.second.first;
        if (x.second.second != y.second.second) return x.second.second > y.second.second;
        return x.first < y.first; });
      int kf = 0, ka = 0; bool single = ij.second - ij.first <= 2;
      int la = single ? p.per_span1 : p.per_span_abbrev;
      for (auto& [w, v] : items) {
        if (v.first == 0) { if (kf++ >= p.per_span) continue; }
        else { if (ka++ >= la) continue; }
        out[ij.first].push_back({ij.second, w, v.first});
      }
    }
    return out;
  }
  static double Chain(C3& m, int32_t& a, int32_t& b, const std::string& w) {
    double lp = 0;
    for (int32_t x : m.Decompose(w)) { lp += b >= 0 ? m.Logp3(a, b, x) : m.Lower(x); a = b; b = x; }
    return lp;
  }
  static double Mix(double lp1, double lp2, double w2) { return std::log((1 - w2) * std::exp(lp1) + w2 * std::exp(lp2)); }
  std::pair<double, State> EdgeScore(const State& st, const std::string& w) {
    int32_t a = st.a, b = st.b, a2 = st.a2, b2 = st.b2;
    double lp = Chain(base, a, b, w);
    if (base2 && p.mix > 0) lp = Mix(lp, Chain(*base2, a2, b2, w), p.mix);
    int32_t wid = adapt.loaded ? adapt.Lookup(w) : -1;
    if (p.adapt_lambda > 0 && adapt.loaded) lp = std::log((1 - p.adapt_lambda) * std::exp(lp) + p.adapt_lambda * adapt.P(st.wa, st.wb, wid));
    State out{a, b, st.wb, wid, a2, b2};
    return {lp, out};
  }
  double Eos(const State& st) {
    double lp = base.Logp3(st.a, st.b, int32_t(base.h.eos));
    if (base2 && p.mix > 0) lp = Mix(lp, base2->Logp3(st.a2, st.b2, int32_t(base2->h.eos)), p.mix);
    if (p.adapt_lambda > 0 && adapt.loaded) lp = std::log((1 - p.adapt_lambda) * std::exp(lp) + p.adapt_lambda * adapt.P(st.wa, st.wb, EOSW));
    return lp;
  }
  State Start(const std::string& context) {
    auto cps = Utf8Decode(context);
    size_t e = cps.size(), s = e;
    while (s > 0 && IsHan(cps[s - 1])) --s;
    State st{-1, int32_t(base.h.bos), BOSW, BOSW, -1, base2 ? int32_t(base2->h.bos) : -1};
    if (s == e) return st;
    if (e - s > 16) s = e - 16;
    std::string run = Utf8Encode(cps, s, e);
    auto parts = base.Decompose(run);
    st.b = parts.back(); st.a = parts.size() >= 2 ? parts[parts.size() - 2] : int32_t(base.h.bos);
    if (base2) {
      auto p2 = base2->Decompose(run);
      st.b2 = p2.back(); st.a2 = p2.size() >= 2 ? p2[p2.size() - 2] : int32_t(base2->h.bos);
    }
    return st;
  }
  struct Hyp { double score; State st; std::string out; int ab = 0; };
  struct KeyHash { size_t operator()(const std::pair<State, std::string>& k) const { return StateHash()(k.first) ^ std::hash<std::string>()(k.second); } };
  using Beam = std::unordered_map<std::pair<State, std::string>, Hyp, KeyHash>;
  // Incremental state: the previous input's edges and beams. A new key changes only the spans that end after
  // the common prefix, so beams up to it are reused as they are (the IME decodes once per keystroke).
  std::string inc_text_, inc_context_; bool inc_t9_ = false; std::vector<std::vector<Edge>> inc_edges_; std::vector<Beam> inc_beams_;
  std::vector<std::pair<std::string, double>> Decode(const std::string& text, bool t9, const std::string& context) {
    size_t n = text.size();
    // When the whole input spells complete syllables (all but possibly the last), initial-only readings inside it
    // are what the user did not type: they cost abbrev_full instead of abbrev (applied to finished paths below).
    double abbrev = p.abbrev;
    if (p.abbrev_full != 0 && n >= 6) {
      auto toks = Tokens(text, t9);
      std::vector<char> ok(n + 1, 0); ok[0] = 1;
      for (size_t i = 0; i < n; ++i) {
        if (!ok[i]) continue;
        for (auto& [j, t] : toks[i]) if (t.kind == 'f' || t.kind == 's' || (t.kind == 'a' && size_t(j) == n && i > 0)) ok[j] = 1;
      }
      if (ok[n]) abbrev = p.abbrev_full;
    }
    // Reuse boundary: edges and beams up to `keep` are unchanged. Nine-key inputs allow a partial syllable only at
    // the end, so the old last position is recomputed for them.
    size_t keep = 0;
    if (p.incremental && t9 == inc_t9_ && context == inc_context_ && !inc_beams_.empty()) {
      while (keep < n && keep < inc_text_.size() && text[keep] == inc_text_[keep]) ++keep;
      if (t9 && keep > 0 && (keep == inc_text_.size() || keep == n)) --keep;
      if (!t9 && keep == inc_text_.size() && keep > 0 && keep < n) {}  // pinyin: abbreviations are valid anywhere
    }
    std::vector<std::vector<Edge>> edges(n);
    std::vector<Beam> beams(n + 1);
    if (keep > 0) {
      for (size_t i = 0; i < std::min(n, inc_edges_.size()); ++i)
        for (auto& e : inc_edges_[i]) if (size_t(e.j) <= keep) edges[i].push_back(e);
      for (size_t i = 0; i <= keep; ++i) beams[i] = std::move(inc_beams_[i]);
      auto fresh = Lattice(text, t9, int(keep));
      for (size_t i = 0; i < n; ++i) for (auto& e : fresh[i]) edges[i].push_back(e);
    } else {
      edges = Lattice(text, t9);
      State init = Start(context);
      beams[0][{init, ""}] = Hyp{0.0, init, "", 0};
    }
    for (size_t i = 0; i < n; ++i) {
      if (beams[i].empty()) continue;
      std::vector<Hyp> items; items.reserve(beams[i].size());
      for (auto& kv : beams[i]) items.push_back(kv.second);
      std::sort(items.begin(), items.end(), [](const Hyp& x, const Hyp& y) { return x.score != y.score ? x.score > y.score : x.out < y.out; });
      if (items.size() > size_t(p.beam)) items.resize(p.beam);
      for (auto& hy : items) {
        for (auto& e : edges[i]) {
          if (size_t(e.j) <= keep) continue;  // already in the reused beams
          double s2; State st2;
          if (e.word.empty()) { s2 = hy.score; st2 = hy.st; }
          else {
            auto sc = EdgeScore(hy.st, e.word);
            s2 = hy.score + sc.first + p.mu * Lnw(e.word) + p.abbrev * e.abbrevs + p.word_pen; st2 = sc.second;
          }
          std::string out2 = hy.out + e.word;
          auto key = std::make_pair(st2, out2);
          auto old = beams[e.j].find(key);
          if (old == beams[e.j].end() || s2 > old->second.score) beams[e.j][key] = Hyp{s2, st2, out2, hy.ab + e.abbrevs};
        }
      }
    }
    auto ranked = [&](double eos_w) {
      std::unordered_map<std::string, double> best;
      for (auto& kv : beams[n]) {
        double s = kv.second.score + eos_w * Eos(kv.second.st) + (abbrev - p.abbrev) * kv.second.ab;
        auto it = best.find(kv.second.out);
        if (it == best.end() || s > it->second) best[kv.second.out] = s;
      }
      std::vector<std::pair<std::string, double>> out(best.begin(), best.end());
      std::sort(out.begin(), out.end(), [](auto& x, auto& y) { return x.second != y.second ? x.second > y.second : x.first < y.first; });
      return out;
    };
    auto out = ranked(p.eos);
    if (p.eos2 >= 0) {
      // Two readings of a short input: a whole message (with the end-of-message probability) and a word that the
      // message continues after (eos2). The list alternates them: `interleave` whole-message candidates, then one word.
      auto alt = ranked(p.eos2);
      std::vector<std::pair<std::string, double>> merged; std::unordered_set<std::string> seen;
      size_t ia = 0, ib = 0;
      while (merged.size() < size_t(p.nbest) && (ia < out.size() || ib < alt.size())) {
        for (int k = 0; k < p.interleave && ia < out.size(); ++k, ++ia)
          if (seen.insert(out[ia].first).second) merged.push_back(out[ia]);
        while (ib < alt.size() && !seen.insert(alt[ib].first).second) ++ib;
        if (ib < alt.size()) merged.push_back(alt[ib++]);
      }
      out = merged;
    }
    if (out.size() > size_t(p.nbest)) out.resize(p.nbest);
    if (p.incremental) { inc_text_ = text; inc_context_ = context; inc_t9_ = t9; inc_edges_ = std::move(edges); inc_beams_ = std::move(beams); }
    return out;
  }
  std::vector<std::pair<std::string, double>> Choice(const std::string& context, const std::vector<std::string>& cands) {
    State st = Start(context);
    std::vector<std::pair<std::string, double>> sc;
    for (auto& c : cands) sc.emplace_back(c, EdgeScore(st, c).first + p.mu * Lnw(c));
    std::stable_sort(sc.begin(), sc.end(), [](auto& x, auto& y) { return x.second > y.second; });
    return sc;
  }
};

// ------------------------------------------------------------------ C API (cylm.h): library for Windows Core / Android JNI
#include "cylm.h"
struct CylmModel { Lexicon lex; C3 base; C3 base2; bool has2 = false; Adapt adapt; Params p; };
struct CylmSession { std::unique_ptr<Decoder> d; };
extern "C" {
CylmModel* cylm_open(const char* lexicon, const char* model, const char* model2, const char* params) {
  auto m = std::make_unique<CylmModel>();
  const uint8_t* mapped = MapFile(lexicon);
  if (!mapped || !m->lex.LoadBin(mapped)) return nullptr;  // the library takes compiled lexicons only
  if (!m->base.Open(model)) return nullptr;
  if (model2 && *model2) { if (!m->base2.Open(model2)) return nullptr; m->has2 = true; }
  for (auto& kv : Split(params ? params : "", ';')) {
    auto eq = kv.find('=');
    if (eq != std::string::npos && !m->p.Set(kv.substr(0, eq), kv.substr(eq + 1))) return nullptr;
  }
  m->p.incremental = 1;
  return m.release();
}
CylmSession* cylm_session(CylmModel* m) {
  if (!m) return nullptr;
  auto s = new CylmSession();
  s->d = std::make_unique<Decoder>(m->lex, m->base, m->adapt, m->p);
  if (m->has2) s->d->base2 = &m->base2;
  return s;
}
int cylm_decode(CylmSession* s, const char* keys, int mode, const char* context, char* out, int cap) {
  if (!s || !keys || !out || cap <= 0) return -1;
  auto ranked = s->d->Decode(keys, mode == 1, context ? context : "");
  int n = 0, used = 0;
  for (auto& [w, sc] : ranked) {
    if (used + int(w.size()) + 1 > cap) break;
    std::memcpy(out + used, w.data(), w.size()); used += int(w.size()); out[used++] = '\0'; ++n;
  }
  return n;
}
void cylm_close_session(CylmSession* s) { delete s; }
void cylm_close(CylmModel* m) { delete m; }
}

#ifndef CYLM_LIBRARY
int main(int argc, char** argv) {
  if (argc < 3) { std::fprintf(stderr, "usage: cylm LEXICON.tsv MODEL.lm [--adapt COUNTS.tsv] [--set k=v]... < items > out\n"); return 2; }
  Params p; std::string adapt_path, model2;
  for (int i = 3; i < argc; ++i) {
    std::string a = argv[i];
    if (a == "--adapt" && i + 1 < argc) adapt_path = argv[++i];
    else if (a == "--model2" && i + 1 < argc) model2 = argv[++i];
    else if (a == "--set" && i + 1 < argc) {
      std::string kv = argv[++i]; auto eq = kv.find('=');
      if (eq == std::string::npos || !p.Set(kv.substr(0, eq), kv.substr(eq + 1))) { std::fprintf(stderr, "bad --set %s\n", kv.c_str()); return 2; }
    }
  }
  auto t0 = std::chrono::steady_clock::now();
  Lexicon lex;
  {
    const uint8_t* mapped = nullptr;
    { std::ifstream probe(argv[1], std::ios::binary); char magic[8] = {0}; probe.read(magic, 8); if (std::memcmp(magic, "CYLX0002", 8) == 0) mapped = MapFile(argv[1]); }
    if (mapped ? !lex.LoadBin(mapped) : !lex.Load(argv[1])) { std::fprintf(stderr, "cannot read lexicon\n"); return 1; }
  }
  if (std::string(argv[2]) == "--compile") {  // cylm LEXICON.tsv --compile OUT.bin
    if (argc < 4 || !lex.Compile(argv[3])) { std::fprintf(stderr, "compile failed\n"); return 1; }
    std::fprintf(stderr, "compiled %zu words\n", lex.WordCount()); return 0;
  }
  C3 base; if (!base.Open(argv[2])) { std::fprintf(stderr, "cannot open model\n"); return 1; }
  Adapt adapt; if (!adapt_path.empty() && !adapt.Load(adapt_path.c_str())) { std::fprintf(stderr, "cannot read adapt\n"); return 1; }
  C3 base2; if (!model2.empty() && !base2.Open(model2.c_str())) { std::fprintf(stderr, "cannot open model2\n"); return 1; }
  Decoder d(lex, base, adapt, p);
  if (!model2.empty()) d.base2 = &base2;
  std::fprintf(stderr, "loaded in %.1fs: %zu words\n", std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count(), lex.WordCount());
  std::ios::sync_with_stdio(false);
  std::string line;
  while (std::getline(std::cin, line)) {
    if (!line.empty() && line.back() == '\r') line.pop_back();
    auto f = Split(line, '\t'); if (f.size() < 4) continue;
    auto t = std::chrono::steady_clock::now();
    std::vector<std::string> cands; std::vector<double> scores;
    auto ranked = f[1] == "choice" ? d.Choice(f[3], f.size() > 4 ? Split(f[4], '|') : std::vector<std::string>{})
                                   : d.Decode(f[2], f[1] == "t9", f[3]);
    for (auto& [w, s] : ranked) { cands.push_back(w); scores.push_back(s); }
    double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t).count();
    std::string out = f[0] + "\t" + std::to_string(ms) + "\t";
    for (size_t k = 0; k < cands.size(); ++k) { if (k) out += '|'; out += cands[k]; }
    out += "\t";
    for (size_t k = 0; k < scores.size(); ++k) { if (k) out += '|'; char b[32]; std::snprintf(b, sizeof b, "%.3f", scores[k]); out += b; }
    std::cout << out << '\n';
    std::cout.flush();
    if (d.hit_cache.size() > 3000000) d.hit_cache.clear();
  }
  return 0;
}
#endif  // CYLM_LIBRARY
