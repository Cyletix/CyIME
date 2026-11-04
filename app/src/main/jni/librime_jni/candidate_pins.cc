#include "candidate_pins.h"
#include <algorithm>
#include <ctime>
#include <filesystem>
#include <fstream>
#include <map>
#include <mutex>
#include <set>
#include <rime/candidate.h>
#include <rime/component.h>
#include <rime/composition.h>
#include <rime/context.h>
#include <rime/engine.h>
#include <rime/filter.h>
#include <rime/menu.h>
#include <rime/registry.h>
#include <rime/segmentation.h>
#include <rime/translation.h>

namespace cyime::pins {
namespace {
using namespace rime;

constexpr char kHeader[] = "# CyIME pinned candidates 1: code<TAB>text<TAB>pinned at (Unix seconds)";
// How far down the list a pinned word is looked for. The menu of a code rarely goes this deep.
constexpr size_t kScan = 200;
constexpr size_t kMaxPins = 5000, kMaxCode = 64, kMaxText = 256;

struct Pin { std::string text; long long time = 0; };

std::mutex mutex;
std::map<std::string, std::vector<Pin>> pins;  // code -> pins, first pinned first
std::vector<std::string> unreadable;           // lines kept as they are, never dropped by a save
std::filesystem::path file;
bool writable = false;
size_t count = 0;

bool Valid(const std::string& code, const std::string& text) {
  return !code.empty() && code.size() <= kMaxCode && !text.empty() && text.size() <= kMaxText &&
         code.find_first_of("\t\r\n") == std::string::npos && text.find_first_of("\t\r\n") == std::string::npos;
}

void Load() {
  pins.clear();
  unreadable.clear();
  count = 0;
  std::error_code error;
  const bool exists = std::filesystem::exists(file, error);
  std::ifstream in(file, std::ios::binary);
  // A file that exists but cannot be read is never overwritten.
  writable = !exists || static_cast<bool>(in);
  if (!in) return;
  std::string line;
  while (std::getline(in, line)) {
    if (!line.empty() && line.back() == '\r') line.pop_back();
    if (line.empty() || line == kHeader) continue;
    if (line.front() == '#') { unreadable.push_back(line); continue; }
    const auto first = line.find('\t');
    const auto second = first == std::string::npos ? first : line.find('\t', first + 1);
    const auto code = line.substr(0, first);
    const auto text = first == std::string::npos ? std::string() :
        line.substr(first + 1, second == std::string::npos ? std::string::npos : second - first - 1);
    long long time = 0;
    if (second != std::string::npos) {
      try { time = std::stoll(line.substr(second + 1)); } catch (...) { time = -1; }
    }
    auto& list = pins[NormalizeCode(code)];
    if (!Valid(code, text) || time < 0 || count >= kMaxPins ||
        std::any_of(list.begin(), list.end(), [&](const Pin& pin) { return pin.text == text; })) {
      if (list.empty()) pins.erase(NormalizeCode(code));
      unreadable.push_back(line);
      continue;
    }
    list.push_back({text, time});
    ++count;
  }
  for (auto& [code, list] : pins)
    std::stable_sort(list.begin(), list.end(), [](const Pin& a, const Pin& b) { return a.time < b.time; });
}

bool Save() {
  if (!writable || file.empty()) return false;
  auto pending = file;
  pending += ".tmp";
  {
    std::ofstream out(pending, std::ios::binary | std::ios::trunc);
    if (!out) return false;
    out << kHeader << '\n';
    for (const auto& line : unreadable) out << line << '\n';
    for (const auto& [code, list] : pins)
      for (const auto& pin : list) out << code << '\t' << pin.text << '\t' << pin.time << '\n';
    out.flush();
    if (!out) return false;
  }
  std::error_code error;
  std::filesystem::rename(pending, file, error);  // replaces the old file on Windows too
  return !error;
}

// The menu of a code with pins: the pinned words found near the top come first, in pin order; everything
// else keeps its order. Words not offered for this input are not added.
class PinTranslation : public Translation {
 public:
  PinTranslation(an<Translation> source, const std::vector<std::string>& pinned) : source_(source) {
    std::vector<an<Candidate>> found(pinned.size());
    size_t missing = pinned.size();
    for (size_t scanned = 0; missing && scanned < kScan && !source_->exhausted(); ++scanned) {
      auto candidate = source_->Peek();
      source_->Next();
      if (!candidate) continue;
      const auto pin = std::find(pinned.begin(), pinned.end(), candidate->text());
      if (pin != pinned.end() && !found[pin - pinned.begin()]) {
        found[pin - pinned.begin()] = candidate;
        --missing;
        continue;
      }
      buffered_.push_back(candidate);
    }
    CandidateList head;
    for (auto& candidate : found)
      if (candidate) { moved_.insert(candidate->text()); head.push_back(candidate); }
    buffered_.insert(buffered_.begin(), head.begin(), head.end());
    Advance();
  }
  bool Next() override {
    if (exhausted()) return false;
    Advance();
    return !exhausted();
  }
  an<Candidate> Peek() override { return current_; }

 private:
  void Advance() {
    current_.reset();
    while (position_ < buffered_.size()) {
      auto candidate = buffered_[position_++];
      // A pinned word moved to the front is not offered a second time further down.
      if (position_ > moved_.size() && moved_.count(candidate->text())) continue;
      current_ = candidate;
      return;
    }
    while (!source_->exhausted()) {
      auto candidate = source_->Peek();
      source_->Next();
      if (!candidate || moved_.count(candidate->text())) continue;
      current_ = candidate;
      return;
    }
    set_exhausted(true);
  }
  an<Translation> source_;
  CandidateList buffered_;
  size_t position_ = 0;
  std::set<std::string> moved_;
  an<Candidate> current_;
};

class PinFilter : public Filter {
 public:
  explicit PinFilter(const Ticket& ticket) : Filter(ticket) {}
  // The engine applies a filter right after asking whether it applies to the segment.
  bool AppliesToSegment(Segment* segment) override {
    pinned_.clear();
    if (!engine_ || !segment || !engine_->context()) return false;
    const auto& input = engine_->context()->composition().input();
    if (segment->end <= segment->start || segment->end > input.size()) return false;
    pinned_ = Pinned(NormalizeCode(input.substr(segment->start, segment->end - segment->start)));
    return !pinned_.empty();
  }
  an<Translation> Apply(an<Translation> translation, CandidateList*) override {
    if (pinned_.empty() || !translation) return translation;
    return New<PinTranslation>(translation, pinned_);
  }

 private:
  std::vector<std::string> pinned_;
};
}  // namespace

void Initialize(const std::string& user_dir) {
  {
    std::lock_guard<std::mutex> lock(mutex);
    file = std::filesystem::u8path(user_dir) / "cyime_pins.txt";
    Load();
  }
  Registry::instance().Register("cyime_pins", new Component<PinFilter>());
}

std::string NormalizeCode(const std::string& input) {
  std::string code;
  for (const char c : input)
    if (c != '\'' && c != ' ') code.push_back(c);
  return code;
}

std::string ActiveCode(Context* context) {
  if (!context || context->composition().empty()) return {};
  const auto& composition = context->composition();
  const auto& segment = composition.back();
  const auto& input = composition.input();
  if (segment.end <= segment.start || segment.end > input.size()) return {};
  return NormalizeCode(input.substr(segment.start, segment.end - segment.start));
}

std::vector<std::string> Pinned(const std::string& code) {
  std::vector<std::string> texts;
  std::lock_guard<std::mutex> lock(mutex);
  const auto found = pins.find(code);
  if (found == pins.end()) return texts;
  for (const auto& pin : found->second) texts.push_back(pin.text);
  return texts;
}

bool IsPinned(const std::string& code, const std::string& text) {
  std::lock_guard<std::mutex> lock(mutex);
  const auto found = pins.find(code);
  return found != pins.end() && std::any_of(found->second.begin(), found->second.end(),
      [&](const Pin& pin) { return pin.text == text; });
}

bool SetPinned(Context* context, const std::string& text, bool pinned) {
  const auto code = ActiveCode(context);
  if (!Valid(code, text)) return false;
  if (pinned) {
    const auto& menu = context->composition().back().menu;
    bool offered = false;
    for (size_t i = 0; menu && i < kScan && !offered; ++i) {
      const auto candidate = menu->GetCandidateAt(i);
      if (!candidate) break;
      offered = candidate->text() == text;
    }
    if (!offered) return false;
  }
  {
    std::lock_guard<std::mutex> lock(mutex);
    auto& list = pins[code];
    const auto existing = std::find_if(list.begin(), list.end(), [&](const Pin& pin) { return pin.text == text; });
    if ((existing != list.end()) == pinned) {
      if (list.empty()) pins.erase(code);
      return true;
    }
    const auto before = list;
    if (pinned) {
      if (count >= kMaxPins) { if (list.empty()) pins.erase(code); return false; }
      // Later than every pin of this code, so a clock set back still appends.
      long long time = static_cast<long long>(std::time(nullptr));
      if (!list.empty()) time = std::max(time, list.back().time);
      list.push_back({text, time});
      ++count;
    } else {
      list.erase(existing);
      --count;
    }
    if (list.empty()) pins.erase(code);
    if (!Save()) {
      if (before.empty()) pins.erase(code); else pins[code] = before;
      count = pinned ? count - 1 : count + 1;
      return false;
    }
  }
  context->RefreshNonConfirmedComposition();
  return true;
}
}  // namespace cyime::pins
