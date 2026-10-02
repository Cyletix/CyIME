// Included inside namespace rime in the generated script translator.
// System graphs and versioned, detached decoded results have separate caches.
namespace {
struct T9Line {
  an<T9Line> previous;
  an<DictEntry> entry;
  size_t end = 0;
  double score = 0;
  double prior = -1;
  double matched_length = 0;
  string text;
  Code code;
};

static double T9WholeTextPrior(GramDb* db, const string& text) {
  if (!db || utf8::distance(text.begin(), text.end()) > grammar::kMaxEncodedUnicode) return -1;
  const auto encoded = grammar::encode(text);
  GramDb::Match matches[GramDb::kMaxResults];
  const auto size = db->Lookup("", encoded, matches);
  for (int i = 0; i < size; ++i)
    if (matches[i].length == encoded.size()) return matches[i].value;
  return -1;
}


template <class QueryResult>
static void T9Collect(map<int, DictEntryList>& destination,
                      const an<QueryResult>& result, size_t budget) {
  if (!result) return;
  for (auto& end : *result) {
    auto& entries = destination[end.first];
    // Bound each source independently, before the source-neutral merge.
    for (size_t n = 0; n < budget && !end.second.exhausted(); ++n) {
      entries.push_back(end.second.Peek());
      end.second.Next();
    }
  }
}
}  // namespace

struct T9Path { Sentence sentence; double prior; double coverage; };
struct T9JointFrame {
  EdgeMap edges;
  size_t total = 0, bytes = 0;
  WordGraph merged;
  map<int, size_t> system_reach, user_reach, omitted_end;
  map<size_t, vector<an<T9Line>>> states;
};
struct T9JointCache {
  T9DecodeCache<vector<T9Path>> results;
  deque<T9JointFrame> frames;
  size_t frame_bytes = 0;
  uint32_t dict_checksum = 0, schema_checksum = 0;
  uint64_t user_revision = 0, user_tick = 0;
  const UserDictionary* user_dict = nullptr;
  string preceding;
  hash_set<string> blacklist;
  using Options = std::tuple<size_t, size_t, size_t, size_t, size_t,
                            double, double, double, string, bool, bool, bool>;
  Options options;
};

namespace {
size_t T9EntryBytes(const DictEntry& entry) {
  return sizeof(DictEntry) + entry.text.capacity() + entry.comment.capacity() +
         entry.preedit.capacity() + entry.custom_code.capacity() +
         entry.code.capacity() * sizeof(SyllableId);
}
size_t T9SentenceBytes(const Sentence& sentence) {
  // Outer Sentence storage is counted by the vector. Count each component
  // object once; double-counting its slab evicts short rollback prefixes early.
  size_t bytes = T9EntryBytes(sentence.entry()) + 32 +
                 sentence.word_lengths().capacity() * sizeof(size_t) +
                 (sentence.components().capacity() - sentence.components().size()) * sizeof(DictEntry);
  for (const auto& entry : sentence.components()) bytes += T9EntryBytes(entry);
  return bytes;
}
}  // namespace

bool ScriptTranslation::PrepareJointCandidates(Dictionary* dict,
                                                UserDictionary* user_dict) {
  const auto& syllables = syllabifier_->syllable_graph();
  const size_t total = syllables.interpreted_length;
  if (!total) return false;
  const string preceding = translator_->GetPrecedingText(start_);
  T9JointCache* cache = nullptr;
  // Rebuild native objects with this query's syllabifier/spans. Neither Menu
  // indices nor mutable Phrase entries from a previous query are reused.
  auto restore = [&](const vector<T9Path>& paths) {
    vector<string> texts;
    for (const auto& path : paths) texts.push_back(preceding + path.sentence.text());
    vector<double> neural;
    const bool ready = T9RequestSentenceScores(t9_sentence_model_, texts, &neural);
    const bool apply = T9RefiningSentences() && ready && neural.size() == paths.size() &&
        std::all_of(neural.begin(), neural.end(), [](double n) { return std::isfinite(n); });
    vector<std::pair<size_t, double>> order;
    for (size_t i = 0; i < paths.size(); ++i)
      order.emplace_back(i, paths[i].sentence.entry().weight + (apply ? t9_sentence_weight_ * neural[i] : 0));
    std::sort(order.begin(), order.end(), [&](const auto& a, const auto& b) {
      if (a.second != b.second) return a.second > b.second;
      const auto& x = paths[a.first]; const auto& y = paths[b.first];
      if (x.prior != y.prior) return x.prior > y.prior;
      if (x.sentence.text() != y.sentence.text()) return x.sentence.text() < y.sentence.text();
      return x.sentence.code() < y.sentence.code();
    });
    set<string> displayed;
    for (const auto& item : order) {
      const auto& path = paths[item.first];
      if (!displayed.insert(path.sentence.text()).second) continue;
      auto sentence = New<Sentence>(path.sentence);
      sentence->Offset(start_);
      sentence->set_syllabifier(syllabifier_);
      sentence->set_weight(item.second);
      sentence->set_quality(path.coverage + 0.1 * (0.5 + std::atan(item.second / 10.0) / 3.141592653589793) + translator_->initial_quality());
      sentences_.push_back(std::move(sentence));
    }
  };
  const string cache_key = syllabifier_->input() + "#" + std::to_string(start_) +
                           "#" + std::to_string(end_of_input_);
  if (t9_decoded_cache_ && !enable_correction_ && !translator_->strict_spelling()) {
    auto& owned = translator_->t9_joint_cache;
    if (!owned) owned = New<T9JointCache>();
    cache = owned.get();
    const auto revision = T9UserDictionaryRevision().load(std::memory_order_relaxed);
    const auto tick = user_dict ? user_dict->tick() : 0;
    auto* prism = dict->prism().get();
    T9JointCache::Options options{t9_source_budget_, t9_pool_budget_, t9_dictionary_beam_,
        t9_sentence_beam_, t9_result_budget_, t9_spelling_penalty_, t9_word_penalty_,
        t9_sentence_weight_, t9_sentence_model_, translator_->enable_completion(),
        translator_->enable_word_completion(), translator_->complete_short_prefix()};
    if (cache->dict_checksum != prism->dict_file_checksum() ||
        cache->schema_checksum != prism->schema_file_checksum() ||
        cache->user_revision != revision || cache->user_tick != tick ||
        cache->user_dict != user_dict || cache->preceding != preceding ||
        cache->blacklist != translator_->blacklist() || cache->options != options) {
      cache->results.Clear();
      cache->frames.clear(); cache->frame_bytes = 0;
      cache->dict_checksum = prism->dict_file_checksum();
      cache->schema_checksum = prism->schema_file_checksum();
      cache->user_revision = revision;
      cache->user_tick = tick;
      cache->user_dict = user_dict;
      cache->preceding = preceding;
      cache->blacklist = translator_->blacklist();
      cache->options = std::move(options);
    }
    if (const auto* paths = cache->results.Find(cache_key)) {
      restore(*paths);
      return !sentences_.empty();
    }
  }
  // Compare actual post-pruning graph rows, not just an assumed unchanged
  // digit prefix. A new key can change abbreviation pruning much earlier.
  auto sameRow = [](const EndVertexMap& a, const EndVertexMap& b) {
    if (a.size() != b.size()) return false;
    auto x = a.begin(); auto y = b.begin();
    for (; x != a.end(); ++x, ++y) {
      if (x->first != y->first || x->second.size() != y->second.size()) return false;
      auto u = x->second.begin(); auto v = y->second.begin();
      for (; u != x->second.end(); ++u, ++v) {
        const auto& p = u->second; const auto& q = v->second;
        if (u->first != v->first || p.type != q.type || p.end_pos != q.end_pos ||
            p.credibility != q.credibility || p.is_correction != q.is_correction ||
            p.tips != q.tips || p.ambiguous_source_positions != q.ambiguous_source_positions) return false;
      }
    }
    return true;
  };
  // Pruning may alternate between two valid graph shapes as a syllable
  // completes. Keep bounded recent shapes instead of losing the stable prefix
  // every other key. Selection uses exact graph rows, never a digit heuristic.
  const T9JointFrame* previous = nullptr;
  size_t best_prefix = 0;
  if (cache) for (const auto& frame : cache->frames) {
    if (frame.total >= total) continue;
    size_t prefix = frame.total;
    for (const auto& row : syllables.edges) {
      auto old = frame.edges.find(row.first);
      if (old == frame.edges.end() || !sameRow(row.second, old->second)) { prefix = std::min(prefix, row.first); break; }
    }
    for (const auto& old : frame.edges) if (!syllables.edges.count(old.first)) { prefix = std::min(prefix, old.first); break; }
    if (!previous || prefix > best_prefix) { previous = &frame; best_prefix = prefix; }
  }
  set<size_t> dirty;
  if (previous) {
    for (const auto& row : syllables.edges) {
      auto old = previous->edges.find(row.first);
      if (old == previous->edges.end() || !sameRow(row.second, old->second)) dirty.insert(row.first);
    }
    for (const auto& old : previous->edges)
      if (!syllables.edges.count(old.first)) dirty.insert(old.first);
    if (previous->total != total) dirty.insert(std::min(previous->total, total));
  }
  auto reusable = [&](int origin, const map<int, size_t>& reach) {
    const auto old = reach.find(origin);
    const auto changed = dirty.lower_bound(origin);
    return old != reach.end() && (changed == dirty.end() || *changed > old->second);
  };
  const size_t stable = previous && previous->total > 0
      ? std::min(previous->total - 1, dirty.empty() ? previous->total - 1 : *dirty.begin()) : 0;
  WordGraph graph, system;
  map<int, size_t> system_reach, user_reach, omitted_end;
  vector<size_t> starts;
  for (const auto& row : syllables.edges) {
    const bool needs_evicted_edges = previous && previous->omitted_end.count(row.first) &&
        previous->omitted_end.at(row.first) > stable;
    if (previous && !needs_evicted_edges && reusable(row.first, previous->system_reach) && reusable(row.first, previous->user_reach)) {
      if (previous->omitted_end.count(row.first)) omitted_end[row.first] = previous->omitted_end.at(row.first);
      const auto found = previous->merged.find(row.first);
      if (found != previous->merged.end()) graph[row.first] = found->second;
      system_reach[row.first] = previous->system_reach.at(row.first);
      user_reach[row.first] = previous->user_reach.at(row.first);
    } else { starts.push_back(row.first); system_reach[row.first] = row.first; }
  }
  {
    T9LookupScope scope(&system_reach);
    auto buckets = dict->LookupAll(syllables, starts, &translator_->blacklist(), false, 0, t9_dictionary_beam_);
    for (auto& start : buckets) T9Collect(system[start.first], start.second, t9_source_budget_);
  }
  for (const auto start : starts) {
    map<int, DictEntryList> user;
    user_reach[start] = start;
    if (user_dict) {
      T9LookupScope scope(&user_reach); T9OriginScope origin(start);
      T9Collect(user, user_dict->Lookup(syllables, start, 0), t9_source_budget_);
    }
    auto merged = std::move(system[start]);
    for (const auto& end : user) {
      auto& entries = merged[end.first];
      entries.insert(entries.end(), end.second.begin(), end.second.end());
    }
    for (auto& end : merged) {
      T9MergeEntries(end.second, t9_pool_budget_);
      graph[start][end.first] = std::move(end.second);
    }
  }
  map<size_t, vector<an<T9Line>>> states;
  if (previous) for (const auto& state : previous->states) if (state.first <= stable) states.insert(state);
  if (states.empty()) states[0].push_back(New<T9Line>());
  auto better = [](const an<T9Line>& a, const an<T9Line>& b) {
    if (a->score != b->score) return a->score > b->score;
    if (a->prior != b->prior) return a->prior > b->prior;
    if (a->text != b->text) return a->text < b->text;
    return a->code < b->code;
  };
  for (const auto& start : graph) {
    const auto found = states.find(start.first);
    if (found == states.end()) continue;
    for (const auto& end : start.second) {
      if (end.first <= start.first || static_cast<size_t>(end.first) > total || static_cast<size_t>(end.first) <= stable) continue;
      auto& target = states[end.first];
      for (const auto& previous : found->second) {
        for (const auto& entry : end.second) {
          if (entry->IsPredictiveMatch()) continue;
          double score = previous->score + Grammar::Evaluate(
              previous->text.empty() ? preceding : previous->text,
              entry->text, entry->weight, static_cast<size_t>(end.first) == total, t9_grammar_.get());
          // Apply the same score before allocating a path. Strictly worse
          // paths cannot enter a full beam; equal scores still reach tie-breaks.
          // Discourage over-segmentation and penalize abbreviated/fuzzy spans.
          score -= t9_word_penalty_;
          score -= t9_spelling_penalty_ * std::max(0.0,
              static_cast<double>(end.first - start.first) - entry->quality_len);
          if (target.size() >= t9_sentence_beam_ && score < target.back()->score) continue;
          auto line = New<T9Line>();
          line->previous = previous;
          line->entry = entry;
          line->matched_length = previous->matched_length + entry->quality_len;
          line->end = end.first;
          line->text = previous->text + entry->text;
          if (line->end == total) line->prior = T9WholeTextPrior(t9_gram_db_, line->text);
          line->code = previous->code;
          line->code.insert(line->code.end(), entry->code.begin(), entry->code.end());
          line->score = score;
          auto duplicate = std::find_if(target.begin(), target.end(), [&](const auto& other) {
            return line->text == other->text && line->code == other->code;
          });
          if (duplicate != target.end()) {
            if (!better(line, *duplicate)) continue;
            target.erase(duplicate);
          }
          const auto pos = std::lower_bound(target.begin(), target.end(), line, better);
          target.insert(pos, line);
          if (target.size() > t9_sentence_beam_) target.pop_back();
        }
      }
    }
  }
  if (cache) {
    // Keep the search frontier, not every dictionary bucket behind it. The
    // lattice already owns the winning prefixes. If future pruning moves the
    // stable boundary back past any evicted bucket, that origin is re-queried.
    WordGraph frontier = graph;
    const size_t cutoff = total > 12 ? total - 12 : 0;
    for (auto& start : frontier) for (auto it = start.second.begin(); it != start.second.end();) {
      if (static_cast<size_t>(it->first) <= cutoff) {
        omitted_end[start.first] = std::max(omitted_end[start.first], static_cast<size_t>(it->first));
        it = start.second.erase(it);
      } else ++it;
    }
    // Conservative accounting includes containers and copied line text/codes.
    size_t bytes = 0;
    for (const auto& start : frontier) {
      bytes += 128;
      for (const auto& end : start.second) {
        bytes += 128 + end.second.size() * 32;
        for (const auto& entry : end.second) bytes += T9EntryBytes(*entry);
      }
    }
    for (const auto& state : states) for (const auto& line : state.second)
      bytes += sizeof(T9Line) + 128 + line->text.capacity() + line->code.capacity() * sizeof(SyllableId);
    for (const auto& row : syllables.edges) for (const auto& end : row.second) bytes += 128 + end.second.size() * 256;
    if (total <= 128 && bytes <= 16 * 1024 * 1024) {
      // Retain distinct prefix shapes, rather than filling the budget with
      // near-identical consecutive suffixes and evicting a recurring shape.
      // Discarding a frame only affects reuse: every surviving frame still
      // goes through the exact dependency checks above.
      for (auto it = cache->frames.begin(); it != cache->frames.end();) {
        const size_t prefix = it->total > 12 ? it->total - 12 : 0;
        bool same_prefix = prefix > 0;
        for (const auto& row : it->edges) {
          if (row.first >= prefix) break;
          const auto current = syllables.edges.find(row.first);
          if (current == syllables.edges.end() || !sameRow(row.second, current->second)) { same_prefix = false; break; }
        }
        if (same_prefix) for (const auto& row : syllables.edges) {
          if (row.first >= prefix) break;
          if (!it->edges.count(row.first)) { same_prefix = false; break; }
        }
        if (same_prefix) { cache->frame_bytes -= it->bytes; it = cache->frames.erase(it); }
        else ++it;
      }
      while (!cache->frames.empty() && (cache->frames.size() >= 16 || cache->frame_bytes + bytes > 32 * 1024 * 1024)) {
        cache->frame_bytes -= cache->frames.front().bytes; cache->frames.pop_front();
      }
      cache->frames.push_back({syllables.edges, total, bytes, std::move(frontier),
          std::move(system_reach), std::move(user_reach), std::move(omitted_end), states});
      cache->frame_bytes += bytes;
    }
  }
  // Explicit final beam: all survivors receive the same bounded second pass.
  // Reorder the actual native Sentence objects before filters and Menu indexing.
  auto& final = states[total];
  if (final.size() > t9_result_budget_) final.resize(t9_result_budget_);
  vector<T9Path> paths;
  double coverage = 0;
  for (const auto& line : final)
    coverage = std::max(coverage, std::min(1.0, line->matched_length / total));
  for (const auto& line : final) {
    auto sentence = New<Sentence>(translator_->language());
    vector<an<T9Line>> components;
    for (auto cursor = line; cursor && cursor->entry; cursor = cursor->previous)
      components.push_back(cursor);
    for (auto it = components.rbegin(); it != components.rend(); ++it)
      sentence->Extend(*(*it)->entry, (*it)->end, (*it)->score);
    // All hypotheses in this translation share a match-coverage baseline,
    // preserving monotonic scores. Exact English can outrank an all-abbreviated
    // Chinese stream (e.g. two keys), without a forced language/source slot.
    sentence->set_quality(coverage + 0.1 *
                           (0.5 + std::atan(line->score / 10.0) / 3.141592653589793));
    paths.push_back({*sentence, line->prior, coverage});
    if (paths.size() >= t9_result_budget_) break;
  }
  restore(paths);
  if (cache) {
    size_t bytes = paths.capacity() * sizeof(T9Path);
    for (const auto& path : paths) bytes += T9SentenceBytes(path.sentence);
    cache->results.Put(cache_key, std::move(paths), bytes);
  }
  return !sentences_.empty();
}
