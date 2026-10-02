#include "t9_suppression.h"
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <map>
#include <utility>
#ifndef T9_ALGO_ONLY_BUILD
#include <rime/service.h>
#endif

namespace rime {
T9SuppressionStore::T9SuppressionStore(std::string file) : file_(std::move(file)) {
  std::ifstream input(file_);
  std::string schema, text;
  while (input >> std::quoted(schema) >> std::quoted(text))
    entries_.emplace(schema, text);
}

bool T9SuppressionStore::Contains(const std::string& schema, const std::string& text) const {
  return entries_.count({schema, text}) != 0;
}

std::vector<std::string> T9SuppressionStore::List(const std::string& schema) const {
  std::vector<std::string> result;
  for (const auto& entry : entries_)
    if (entry.first == schema) result.push_back(entry.second);
  return result;
}

bool T9SuppressionStore::Set(const std::string& schema, const std::string& text, bool suppressed) {
  if (schema.empty() || text.empty()) return false;
  auto updated = entries_;
  if (suppressed) updated.emplace(schema, text);
  else updated.erase({schema, text});
  const auto temporary = file_ + ".tmp";
  {
    std::ofstream output(temporary, std::ios::trunc);
    if (!output) return false;
    for (const auto& entry : updated)
      output << std::quoted(entry.first) << '\t' << std::quoted(entry.second) << '\n';
    output.flush();
    if (!output) return false;
  }
  std::error_code error;
  std::filesystem::rename(temporary, file_, error);
  if (error) return false;
  entries_.swap(updated);
  return true;
}

#ifndef T9_ALGO_ONLY_BUILD
std::shared_ptr<T9SuppressionStore> GetT9SuppressionStore() {
  // JNI and filters execute under the existing RimeEngine lock. The directory
  // key keeps isolated tests and different Rime profiles independent.
  static std::map<std::string, std::weak_ptr<T9SuppressionStore>> stores;
  const auto file = (Service::instance().deployer().user_data_dir / "t9-suppressed-v1.txt").string();
  auto current = stores[file].lock();
  if (!current) {
    current = std::make_shared<T9SuppressionStore>(file);
    stores[file] = current;
  }
  return current;
}
#endif
}  // namespace rime
