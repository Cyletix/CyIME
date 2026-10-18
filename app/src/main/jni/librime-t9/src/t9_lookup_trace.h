#ifndef CYIME_T9_LOOKUP_TRACE_H_
#define CYIME_T9_LOOKUP_TRACE_H_
#include <map>
#include <cstddef>
#include <algorithm>
#if defined(CYIME_T9_WORK_COUNTERS)
#include <cstdint>
#endif
namespace rime {
// Query dependencies, not input logs. Thread-local and active only during T9
// lookup. Reusing an origin requires every observed graph row to be unchanged.
inline thread_local std::map<int, size_t>* t9_lookup_reach = nullptr;
inline thread_local int t9_lookup_origin = -1;
inline void T9ObservePosition(int origin, size_t position) {
  if (t9_lookup_reach && origin >= 0) {
    auto& reached = (*t9_lookup_reach)[origin];
    reached = std::max(reached, position);
  }
}
inline void T9ObserveExtra(size_t position) { T9ObservePosition(t9_lookup_origin, position); }
struct T9LookupScope {
  std::map<int, size_t>* previous = t9_lookup_reach;
  explicit T9LookupScope(std::map<int, size_t>* reach) { t9_lookup_reach = reach; }
  ~T9LookupScope() { t9_lookup_reach = previous; }
};
struct T9OriginScope {
  int previous = t9_lookup_origin;
  explicit T9OriginScope(int origin) { t9_lookup_origin = origin; }
  ~T9OriginScope() { t9_lookup_origin = previous; }
};
#if defined(CYIME_T9_WORK_COUNTERS)
// Per-key work counters for the host replay speed gate (tools/ime_lab). Only
// the host build defines CYIME_T9_WORK_COUNTERS; Android builds compile every
// T9_WORK_* use to nothing. Counting never reads back into any decision.
#define CYIME_T9_WORK_COUNTER_LIST(X)                                   \
  X(joint_decodes) X(joint_cache_hits) X(joint_frame_reuse)             \
  X(joint_origins_queried) X(joint_origins_reused)                      \
  X(dict_lookup_calls) X(dict_lookup_all_calls) X(dict_lookup_all_starts) \
  X(dict_table_queries) X(dict_buckets) X(table_nodes) X(table_edges)   \
  X(dict_extra_code_steps) X(dict_chunks) X(dict_chunk_entries)         \
  X(system_entries) X(user_dict_lookups) X(user_dfs_nodes)              \
  X(user_entries) X(user_entries_collected) X(merge_calls)              \
  X(merge_entries_in) X(merge_entries_out) X(beam_evaluations)          \
  X(beam_rejected) X(beam_insertions) X(grammar_queries)                \
  X(gram_lookups) X(whole_text_priors) X(ngram_lookups)
enum T9WorkCounter {
#define CYIME_T9_WORK_ENUM(name) kT9Work_##name,
  CYIME_T9_WORK_COUNTER_LIST(CYIME_T9_WORK_ENUM)
#undef CYIME_T9_WORK_ENUM
  kT9WorkCounterCount
};
inline thread_local uint64_t t9_work_counters[kT9WorkCounterCount] = {};
#endif
}
#if defined(CYIME_T9_WORK_COUNTERS)
#define T9_WORK_ADD(name, n) \
  (::rime::t9_work_counters[::rime::kT9Work_##name] += static_cast<uint64_t>(n))
#else
// The amount is not evaluated: pass only side-effect-free expressions.
#define T9_WORK_ADD(name, n) ((void)0)
#endif
#define T9_WORK_COUNT(name) T9_WORK_ADD(name, 1)
#endif
