#ifndef CYIME_T9_LOOKUP_TRACE_H_
#define CYIME_T9_LOOKUP_TRACE_H_
#include <map>
#include <cstddef>
#include <algorithm>
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
}
#endif
