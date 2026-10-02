#ifndef RIME_T9_SUPPRESSION_H_
#define RIME_T9_SUPPRESSION_H_
#include <memory>
#include <set>
#include <string>
#include <vector>

namespace rime {
// Exact schema + full text. No substring filtering and no user-dictionary edits.
class T9SuppressionStore {
 public:
  explicit T9SuppressionStore(std::string file);
  bool Contains(const std::string& schema, const std::string& text) const;
  std::vector<std::string> List(const std::string& schema) const;
  bool Set(const std::string& schema, const std::string& text, bool suppressed);
 private:
  std::string file_;
  std::set<std::pair<std::string, std::string>> entries_;
};

#ifndef T9_ALGO_ONLY_BUILD
std::shared_ptr<T9SuppressionStore> GetT9SuppressionStore();
#endif
}  // namespace rime
#endif
