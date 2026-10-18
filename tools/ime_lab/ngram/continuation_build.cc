// Build the post-commit continuation table from the SAME segmented corpus as the T9 word
// n-gram model, so suggestions after committed text share one probability scale with it.
//
// For every segmented Han run (tokens separated by spaces) two kinds of events are counted,
// both keyed by the trailing 1..3 characters of the text committed so far:
//   1. word boundary: context = tail of the text up to token i, continuation = token i+1;
//   2. inside a word:  context = tail of the text up to a prefix of token i (1..len-1 chars),
//      continuation = the rest of that token (what the user still has to type).
// A user who committed "这" alone is in either situation, so 个/些/样 (rest of 这个/这些/这样)
// and 是/就 (next word after 这) compete on the same count scale.
//
// Two sequential passes over the corpus, each bounded and resumable:
//   pass 1 counts how often each context occurs (one map of contexts), writes the selected
//          top contexts to OUT.contexts and stops;
//   pass 2 counts continuations only for those contexts (map bounded by them), writes OUT.
// Progress is printed every few seconds as a percentage of corpus bytes. Stopping loses at
// most the pass in progress; with OUT.contexts present, pass 1 is skipped (--restart redoes it).
//
// Usage: continuation_build out.tsv seg1.txt[,seg2.txt...] [--contexts 40000] [--top 10]
//        [--min-total 80] [--min-count 3] [--max-context 3] [--restart]
// Output rows: context \t continuation \t count \t total (header "# cyime-base-associations-v1"),
// the format BaseAssociationModel.read expects. Only Han words take part: the segmenter writes
// out-of-vocabulary runs as "<unk>", and anything else ends the context like punctuation.
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <string>
#include <unordered_map>
#include <vector>

namespace {
struct PairHash {
  size_t operator()(const std::pair<uint32_t, std::string>& p) const {
    return std::hash<std::string>{}(p.second) * 1000003u ^ p.first;
  }
};

bool DecodeUtf8(const std::string& s, std::vector<std::string>* chars) {
  chars->clear();
  for (size_t i = 0; i < s.size();) {
    unsigned char c = s[i];
    size_t n = c < 0x80 ? 1 : (c >> 5) == 6 ? 2 : (c >> 4) == 14 ? 3 : (c >> 3) == 30 ? 4 : 0;
    if (!n || i + n > s.size()) return false;
    chars->emplace_back(s, i, n);
    i += n;
  }
  return true;
}

bool IsHanWord(const std::vector<std::string>& chars) {
  if (chars.empty()) return false;
  for (const auto& c : chars) {
    if (c.size() != 3) return false;
    const uint32_t cp = ((static_cast<unsigned char>(c[0]) & 0x0F) << 12) |
                        ((static_cast<unsigned char>(c[1]) & 0x3F) << 6) |
                        (static_cast<unsigned char>(c[2]) & 0x3F);
    if (!((cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF))) return false;
  }
  return true;
}

std::string Join(const std::vector<std::string>& chars, size_t begin, size_t end) {
  std::string out;
  for (size_t i = begin; i < end; ++i) out += chars[i];
  return out;
}

struct Options {
  size_t max_contexts = 40000, top = 10, min_total = 80, min_count = 3, max_context = 3;
  bool restart = false;
};

// Streams every (committed-text tail, continuation) event of the corpora to `visit`.
// Returns false when a corpus cannot be read.
template <class Visit>
bool Scan(const std::vector<std::string>& corpora, const Options& o, const char* label, Visit visit) {
  uint64_t total_bytes = 0;
  for (const auto& corpus : corpora) {
    std::ifstream in(corpus, std::ios::binary | std::ios::ate);
    if (!in) { std::cerr << "cannot read " << corpus << "\n"; return false; }
    total_bytes += static_cast<uint64_t>(in.tellg());
  }
  uint64_t read_bytes = 0, runs = 0;
  auto started = std::chrono::steady_clock::now(), reported = started;
  std::vector<std::string> tail, token_chars, next_chars;
  for (const auto& corpus : corpora) {
    std::ifstream in(corpus);
    std::string line;
    while (std::getline(in, line)) {
      read_bytes += line.size() + 1;
      std::vector<std::string> tokens;
      for (size_t i = 0; i < line.size();) {
        const auto space = line.find(' ', i);
        const std::string token = line.substr(i, space == std::string::npos ? std::string::npos : space - i);
        if (!token.empty()) tokens.push_back(token);
        if (space == std::string::npos) break;
        i = space + 1;
      }
      if (tokens.empty()) continue;
      ++runs;
      std::string text;  // committed text so far; only its last characters matter
      auto emit = [&](const std::string& text_so_far, const std::string& continuation) {
        DecodeUtf8(text_so_far, &tail);
        for (size_t size = 1; size <= tail.size() && size <= o.max_context; ++size)
          visit(Join(tail, tail.size() - size, tail.size()), continuation);
      };
      for (size_t t = 0; t < tokens.size(); ++t) {
        if (!DecodeUtf8(tokens[t], &token_chars) || !IsHanWord(token_chars)) { text.clear(); continue; }
        for (size_t p = 1; p < token_chars.size() && p <= 6; ++p) {
          const std::string rest = Join(token_chars, p, token_chars.size());
          if (rest.size() <= 18) emit(text + Join(token_chars, 0, p), rest);
        }
        text += tokens[t];
        if (text.size() > 48) {
          size_t cut = text.size() - 48;
          while (cut < text.size() && (static_cast<unsigned char>(text[cut]) & 0xC0) == 0x80) ++cut;
          text = text.substr(cut);
        }
        if (t + 1 < tokens.size() && tokens[t + 1].size() <= 18 &&
            DecodeUtf8(tokens[t + 1], &next_chars) && IsHanWord(next_chars)) emit(text, tokens[t + 1]);
      }
      const auto now = std::chrono::steady_clock::now();
      if (now - reported >= std::chrono::seconds(10)) {
        reported = now;
        const double elapsed = std::chrono::duration<double>(now - started).count();
        std::cerr << label << " " << (100.0 * read_bytes / std::max<uint64_t>(1, total_bytes)) << "% "
                  << runs << " runs, " << elapsed << " s elapsed\n";
      }
    }
  }
  std::cerr << label << " done: " << runs << " runs\n";
  return true;
}
}  // namespace

int main(int argc, char** argv) {
  if (argc < 3) {
    std::cerr << "usage: continuation_build out.tsv seg.txt[,seg2.txt] [options]\n";
    return 2;
  }
  Options o;
  for (int i = 3; i < argc; ++i) {
    std::string a = argv[i];
    if (a == "--contexts") o.max_contexts = std::stoul(argv[++i]);
    else if (a == "--max-context") o.max_context = std::stoul(argv[++i]);
    else if (a == "--top") o.top = std::stoul(argv[++i]);
    else if (a == "--min-total") o.min_total = std::stoul(argv[++i]);
    else if (a == "--min-count") o.min_count = std::stoul(argv[++i]);
    else if (a == "--restart") o.restart = true;
  }
  std::vector<std::string> corpora;
  for (std::string list = argv[2]; !list.empty();) {
    const auto comma = list.find(',');
    corpora.push_back(list.substr(0, comma));
    list = comma == std::string::npos ? "" : list.substr(comma + 1);
  }
  const std::string out_path = argv[1];
  const std::string contexts_path = out_path + ".contexts";

  // Pass 1: context totals -> selected contexts (checkpoint file).
  std::vector<std::pair<std::string, uint64_t>> ranked;
  {
    std::ifstream saved(contexts_path);
    std::string line;
    if (saved && !o.restart) {
      while (std::getline(saved, line)) {
        const auto tab = line.find('\t');
        if (tab != std::string::npos) ranked.emplace_back(line.substr(0, tab), std::stoull(line.substr(tab + 1)));
      }
      std::cerr << "pass 1 skipped: " << ranked.size() << " contexts read from " << contexts_path << "\n";
    }
  }
  if (ranked.empty()) {
    std::unordered_map<std::string, uint64_t> totals;
    if (!Scan(corpora, o, "pass 1 (contexts)", [&](const std::string& ctx, const std::string&) { ++totals[ctx]; })) return 2;
    std::cerr << "distinct contexts=" << totals.size() << "\n";
    ranked.assign(totals.begin(), totals.end());
    ranked.erase(std::remove_if(ranked.begin(), ranked.end(), [&](const auto& e) { return e.second < o.min_total; }),
                 ranked.end());
    std::sort(ranked.begin(), ranked.end(), [](const auto& a, const auto& b) { return a.second > b.second; });
    if (ranked.size() > o.max_contexts) ranked.resize(o.max_contexts);
    std::ofstream saved(contexts_path + ".tmp");
    for (const auto& e : ranked) saved << e.first << '\t' << e.second << '\n';
    saved.close();
    std::rename((contexts_path + ".tmp").c_str(), contexts_path.c_str());
    std::cerr << "pass 1 saved " << ranked.size() << " contexts to " << contexts_path << "\n";
  }

  // Pass 2: continuations of the selected contexts only.
  std::unordered_map<std::string, uint32_t> index;
  for (size_t i = 0; i < ranked.size(); ++i) index[ranked[i].first] = static_cast<uint32_t>(i);
  std::unordered_map<std::pair<uint32_t, std::string>, uint32_t, PairHash> pairs;
  if (!Scan(corpora, o, "pass 2 (continuations)", [&](const std::string& ctx, const std::string& cont) {
        const auto it = index.find(ctx);
        if (it != index.end()) ++pairs[{it->second, cont}];
      })) return 2;
  std::cerr << "pairs=" << pairs.size() << "\n";
  std::vector<std::vector<std::pair<std::string, uint32_t>>> by_context(ranked.size());
  for (const auto& [key, c] : pairs)
    if (c >= o.min_count) by_context[key.first].emplace_back(key.second, c);
  std::ofstream out(out_path + ".tmp");
  out << "# cyime-base-associations-v1\n";
  uint64_t rows = 0;
  for (size_t i = 0; i < ranked.size(); ++i) {
    auto& list = by_context[i];
    std::sort(list.begin(), list.end(), [](const auto& a, const auto& b) {
      return a.second != b.second ? a.second > b.second : a.first < b.first;
    });
    if (list.size() > o.top) list.resize(o.top);
    for (const auto& [cont, c] : list) {
      out << ranked[i].first << '\t' << cont << '\t' << c << '\t' << ranked[i].second << '\n';
      ++rows;
    }
  }
  out.close();
  std::rename((out_path + ".tmp").c_str(), out_path.c_str());
  std::cerr << "contexts_written=" << ranked.size() << " rows=" << rows << " -> " << out_path << "\n";
  return 0;
}
