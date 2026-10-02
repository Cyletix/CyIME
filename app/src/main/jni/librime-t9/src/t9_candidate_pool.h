#ifndef CYIME_T9_CANDIDATE_POOL_H_
#define CYIME_T9_CANDIDATE_POOL_H_
#include <algorithm>
#include <functional>
#include <set>
#include <string>
#include <type_traits>
#include <unordered_set>
namespace rime {
template <class Entry>
bool T9EntryBefore(const Entry& a, const Entry& b) {
  if (a->IsPredictiveMatch() != b->IsPredictiveMatch())
    return !a->IsPredictiveMatch();
  if (a->quality_len != b->quality_len) return a->quality_len > b->quality_len;
  if (a->weight != b->weight) return a->weight > b->weight;
  if (a->code != b->code) return a->code < b->code;
  if (a->matching_code_size != b->matching_code_size)
    return a->matching_code_size < b->matching_code_size;
  return a->text < b->text;
}

template <class List>
void T9MergeEntries(List& pool, size_t budget) {
  using Entry = typename List::value_type;
  using Code = std::decay_t<decltype(pool.front()->code)>;
  std::sort(pool.begin(), pool.end(), T9EntryBefore<Entry>);
  struct IdentityHash {
    size_t operator()(const Entry& entry) const {
      size_t hash = std::hash<std::string>{}(entry->text);
      auto combine = [&](size_t value) { hash ^= value + 0x9e3779b9 + (hash << 6) + (hash >> 2); };
      for (const auto syllable : entry->code) combine(static_cast<size_t>(syllable));
      combine(static_cast<size_t>(entry->matching_code_size));
      return hash;
    }
  };
  struct SameIdentity {
    bool operator()(const Entry& a, const Entry& b) const {
      return a->text == b->text && a->code == b->code &&
             a->matching_code_size == b->matching_code_size;
    }
  };
  std::unordered_set<Entry, IdentityHash, SameIdentity> seen;
  seen.reserve(pool.size());
  List unique;
  for (const auto& entry : pool) {
    if (seen.insert(entry).second) unique.push_back(entry);
  }
  // Reserve half the pool for competing pronunciations, then fill by score.
  // Homophones of a locked pronunciation still share the remaining capacity.
  List selected;
  std::set<Code> codes;
  for (const auto& entry : unique) {
    if (selected.size() >= (budget + 1) / 2) break;
    if (codes.insert(entry->code).second) selected.push_back(entry);
  }
  for (const auto& entry : unique) {
    if (selected.size() >= budget) break;
    if (std::find(selected.begin(), selected.end(), entry) == selected.end())
      selected.push_back(entry);
  }
  std::sort(selected.begin(), selected.end(), T9EntryBefore<Entry>);
  pool = std::move(selected);
}

}  // namespace rime
#endif
