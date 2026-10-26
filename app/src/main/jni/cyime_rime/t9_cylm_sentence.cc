#include "t9_cylm_sentence.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <map>
#include <mutex>
#include <sstream>
#include <thread>
#include <utility>

#include <rime/dict/dictionary.h>
#include <rime/gear/translator_commons.h>
#include <utf8.h>

#include "t9_script_options.h"

#ifdef __ANDROID__
#include <android/log.h>
#endif

namespace rime {

namespace {
// Proof on a device that CyLM runs (adb logcat -s CyLM): the first keys and then every 50th log how many keys
// asked, how many got CyLM's sentence first, how many it missed the budget for, and the last decode time.
// Never the keys or the text.
void TraceCylm(bool replaced, bool late, double ms) {
  static std::atomic<long> calls{0}, replacements{0}, misses{0};
  const long n = ++calls;
  const long r = replaced ? ++replacements : replacements.load();
  const long m = late ? ++misses : misses.load();
#ifdef __ANDROID__
  if (n <= 3 || n % 50 == 0)
    __android_log_print(ANDROID_LOG_INFO, "CyLM", "decode calls=%ld replaced=%ld late=%ld last_ms=%.2f", n, r, m, ms);
#else
  (void)n; (void)r; (void)m; (void)ms;
#endif
}

std::atomic<int> budget_ms{12};

// CyLM decodes on its own thread so a key never waits longer than the budget (user 2026-10-26: typing lagged
// with CyLM in the key path). A key takes CyLM's sentence only when it is ready within the budget; otherwise
// Rime's sentence stays and that late result is dropped: the candidates shown for a key never change afterwards.
// One worker serves every session; it always decodes the newest request, so skipped keys cost nothing.
class CylmWorker {
 public:
  static CylmWorker& Instance() {
    static auto* worker = new CylmWorker();  // lives for the process, like the sessions
    return *worker;
  }
  // The record for (session, input, context), waiting at most `budget`; empty when not ready in time.
  string Decode(CylmSession* session, const string& in, bool digits, const string& context,
                std::chrono::milliseconds budget, double* ms, bool* late) {
    std::unique_lock<std::mutex> lock(mutex_);
    if (finished_ == wanted_ && done_session_ == session && done_in_ == in && done_context_ == context) {
      *ms = done_ms_;
      return done_record_;  // the same input asked again (another sentence pass, a page turn)
    }
    session_ = session; in_ = in; digits_ = digits; context_ = context;
    const uint64_t id = ++wanted_;
    wake_.notify_one();
    const bool ready = done_.wait_for(lock, budget, [&] { return finished_ >= id; });
    *late = !ready;
    if (!ready || done_session_ != session || done_in_ != in || done_context_ != context) return string();
    *ms = done_ms_;
    return done_record_;
  }

 private:
  CylmWorker() { std::thread([this] { Run(); }).detach(); }
  void Run() {
    vector<char> out(16384);
    std::unique_lock<std::mutex> lock(mutex_);
    for (;;) {
      wake_.wait(lock, [&] { return wanted_ > started_; });
      const uint64_t id = started_ = wanted_;
      CylmSession* session = session_;
      const string in = in_, context = context_;
      const bool digits = digits_;
      lock.unlock();
      const auto t0 = std::chrono::steady_clock::now();
      const int n = cylm_decode2(session, in.c_str(), digits ? 1 : 0, context.c_str(), out.data(),
                                 static_cast<int>(out.size()));
      const double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
      lock.lock();
      done_session_ = session; done_in_ = in; done_context_ = context; done_ms_ = ms;
      done_record_ = n > 0 && out[0] ? string(out.data()) : string();
      finished_ = id;
      done_.notify_all();
    }
  }
  std::mutex mutex_;
  std::condition_variable wake_, done_;
  uint64_t wanted_ = 0, started_ = 0, finished_ = 0;
  CylmSession* session_ = nullptr;
  string in_, context_;
  bool digits_ = false;
  CylmSession* done_session_ = nullptr;
  string done_in_, done_context_, done_record_;
  double done_ms_ = 0;
};
}  // namespace

void T9CylmSetBudget(int ms) { budget_ms = std::max(1, std::min(200, ms)); }

void T9CylmWarm(CylmModel* model) {
  // The first decode maps hundreds of MB of model pages (about 0.6 s on the tablet); do it on the worker while
  // the keyboard opens, with a session of its own, so the first key does not pay for it.
  CylmSession* session = model ? cylm_session(model) : nullptr;
  if (!session) return;
  double ms = 0;
  bool late = false;
  CylmWorker::Instance().Decode(session, "nihao", false, string(), std::chrono::milliseconds(0), &ms, &late);
}

void T9ApplyCylmSentence(CylmModel* model, const void* owner, const string& in, const string& left_context,
                         Dictionary* dict, const Language* translator_language, size_t start,
                         const an<PhraseSyllabifier>& syllabifier, deque<an<Sentence>>* sentences) {
  if (in.empty()) return;
  const bool digits = std::all_of(in.begin(), in.end(), [](char c) { return c >= '2' && c <= '9'; });
  const bool letters = std::all_of(in.begin(), in.end(), [](char c) { return (c >= 'a' && c <= 'z') || c == '\''; });
  if (!digits && !letters) return;
  // One incremental session per translator (one text field at a time), kept for the process.
  static std::mutex mutex;
  static std::map<std::pair<const void*, const CylmModel*>, CylmSession*> sessions;
  CylmSession* session;
  {
    std::lock_guard<std::mutex> lock(mutex);
    auto& slot = sessions[{owner, model}];
    if (!slot) slot = cylm_session(model);
    session = slot;
  }
  if (!session) return;
  const string context = !left_context.empty() && left_context[0] == '\x1e' ? left_context.substr(1) : string();
  double ms = 0;
  bool late = false;
  const string record = CylmWorker::Instance().Decode(session, in, digits, context,
                                                      std::chrono::milliseconds(budget_ms.load()), &ms, &late);
  if (record.empty()) return TraceCylm(false, late, ms);
  // record: text \x1f pinyin \x1f spans
  const auto unit = record.find('\x1f');
  const string text = record.substr(0, unit);
  const auto& rime = sentences->front();
  if (text.empty() || text == rime->text()) return TraceCylm(false, false, ms);
  DictEntry entry;
  entry.text = text;
  entry.weight = rime->weight();
  const Language* language = nullptr;
  if (unit != string::npos && dict) {
    const auto end = record.find('\x1f', unit + 1);
    std::istringstream pinyin(record.substr(unit + 1, end == string::npos ? string::npos : end - unit - 1));
    const auto& ids = T9ScriptOptions::T9SyllableIds(dict);
    string syllable;
    Code code;
    while (pinyin >> syllable) {
      const auto found = ids.find(syllable);
      if (found == ids.end()) { code.clear(); break; }
      code.push_back(found->second);
    }
    if (!code.empty() && code.size() == static_cast<size_t>(utf8::unchecked::distance(text.begin(), text.end()))) {
      entry.code = code;
      language = translator_language;
    }
  }
  auto sentence = New<Sentence>(language);
  sentence->Extend(entry, rime->end() - start, rime->weight());
  sentence->Offset(start);
  sentence->set_syllabifier(syllabifier);
  sentence->set_quality(rime->quality());
  sentences->front() = sentence;
  TraceCylm(true, false, ms);
}

}  // namespace rime
