#ifndef CYIME_T9_DECODE_CACHE_H_
#define CYIME_T9_DECODE_CACHE_H_

#include <atomic>
#include <cstdint>
#include <list>
#include <string>
#include <unordered_map>
#include <utility>

namespace rime {
// Shared across UserDictionary instances: several translators can share a DB.
// A learning tick alone misses zero-commit updates, deletion and rollback.
inline std::atomic<uint64_t>& T9UserDictionaryRevision() {
  static std::atomic<uint64_t> revision{0};
  return revision;
}
inline void T9InvalidateUserDictionary() {
  T9UserDictionaryRevision().fetch_add(1, std::memory_order_relaxed);
}

// Owns only compact, detached decoded results, never a dictionary graph/Menu.
// Lookup is O(key length) average; eviction is O(number of evicted entries).
template <class Value>
class T9DecodeCache {
 public:
  explicit T9DecodeCache(size_t max_bytes = 8 * 1024 * 1024,
                         size_t max_entries = 128)
      : max_bytes_(max_bytes), max_entries_(max_entries) {}
  const Value* Find(const std::string& key) {
    auto found = index_.find(key);
    if (found == index_.end()) return nullptr;
    entries_.splice(entries_.begin(), entries_, found->second);
    return &found->second->value;
  }
  void Put(std::string key, Value value, size_t payload_bytes) {
    auto old = index_.find(key);
    if (old != index_.end()) {
      bytes_ -= old->second->bytes;
      entries_.erase(old->second);
      index_.erase(old);
    }
    // Include both copies of the key and conservative container overhead.
    const size_t bytes = payload_bytes + key.capacity() * 2 + sizeof(Entry) + 128;
    if (bytes > max_bytes_ || !max_entries_) return;
    while (!entries_.empty() &&
           (bytes_ + bytes > max_bytes_ || entries_.size() >= max_entries_)) {
      bytes_ -= entries_.back().bytes;
      index_.erase(entries_.back().key);
      entries_.pop_back();
    }
    entries_.push_front({std::move(key), std::move(value), bytes});
    index_[entries_.front().key] = entries_.begin();
    bytes_ += bytes;
  }
  void Clear() { index_.clear(); entries_.clear(); bytes_ = 0; }
  size_t bytes() const { return bytes_; }
  size_t size() const { return entries_.size(); }

 private:
  struct Entry { std::string key; Value value; size_t bytes; };
  std::list<Entry> entries_;
  std::unordered_map<std::string, typename std::list<Entry>::iterator> index_;
  size_t bytes_ = 0;
  const size_t max_bytes_, max_entries_;
};
}  // namespace rime
#endif
