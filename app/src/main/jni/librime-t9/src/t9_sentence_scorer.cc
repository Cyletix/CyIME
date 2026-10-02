#ifndef T9_ALGO_ONLY_BUILD
#include "t9_sentence_scorer.h"
#include <rime/resource.h>
#include <rime/service.h>
#include <onnxruntime_cxx_api.h>
#include <utf8.h>
#include <array>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <chrono>
#include <cmath>
#include <fstream>
#include <memory>
#include <limits>
#include <unordered_map>
#include <vector>
#if defined(__ANDROID__)
#include <sys/resource.h>
#endif

namespace rime {
namespace {
// One engine-owned CPU session, one worker, bounded inputs and score cache.
// RimeEngine serializes access; finalize releases every allocation.
struct SentenceScorer {
  Ort::Env environment{ORT_LOGGING_LEVEL_ERROR, "CyIME-T9"};
  Ort::Session session{nullptr};
  Ort::MemoryInfo memory = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
  std::unordered_map<std::string, int64_t> vocabulary;
  std::unordered_map<std::string, double> scores;
  explicit SentenceScorer(const path& model, const path& vocab) {
    std::ifstream input(vocab);
    std::string token;
    int64_t id = 0;
    // Split on LF only: the vocabulary contains U+2028/U+2029 tokens.
    while (std::getline(input, token)) {
      if (!token.empty() && token.back() == '\r') token.pop_back();
      vocabulary.emplace(token, id++);
    }
    if (id != 21128 || vocabulary.at("[MASK]") != 103)
      throw std::runtime_error("invalid T9 sentence vocabulary");
    Ort::SessionOptions options;
    options.SetIntraOpNumThreads(1);
    options.SetInterOpNumThreads(1);
    options.SetExecutionMode(ExecutionMode::ORT_SEQUENTIAL);
    options.DisableCpuMemArena();
    options.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_EXTENDED);
    session = Ort::Session(environment, model.c_str(), options);
  }
  double Score(const std::string& text) {
    if (const auto found = scores.find(text); found != scores.end()) return found->second;
    std::vector<int64_t> ids{101};
    auto p = text.begin();
    while (p != text.end()) {
      const auto begin = p;
      utf8::next(p, text.end());
      auto token = vocabulary.find(std::string(begin, p));
      // Unknown text retains its dictionary score, without fabricated tokens.
      if (token == vocabulary.end()) return std::numeric_limits<double>::quiet_NaN();
      ids.push_back(token->second);
    }
    const size_t characters = ids.size() - 1;
    if (characters < 5 || characters > 24) return std::numeric_limits<double>::quiet_NaN();
    ids.push_back(102);
    // Mask the last four positions individually. The prefix supplies context.
    // A text always uses the same batch, so cache hits cannot change quantization.
    constexpr size_t batch = 4;
    const size_t width = ids.size();
    std::vector<int64_t> tokens, mask(batch * width, 1), positions, targets;
    for (size_t position = characters - batch + 1; position <= characters; ++position) {
      tokens.insert(tokens.end(), ids.begin(), ids.end());
      tokens[tokens.size() - width + position] = 103;
      positions.push_back(position);
      targets.push_back(ids[position]);
    }
    const std::array<int64_t, 2> shape{batch, static_cast<int64_t>(width)};
    const std::array<int64_t, 1> rows{batch};
    std::array<Ort::Value, 4> inputs{
        Ort::Value::CreateTensor<int64_t>(memory, tokens.data(), tokens.size(), shape.data(), 2),
        Ort::Value::CreateTensor<int64_t>(memory, mask.data(), mask.size(), shape.data(), 2),
        Ort::Value::CreateTensor<int64_t>(memory, positions.data(), batch, rows.data(), 1),
        Ort::Value::CreateTensor<int64_t>(memory, targets.data(), batch, rows.data(), 1)};
    const char* names[] = {"input_ids", "attention_mask", "positions", "targets"};
    const char* outputs[] = {"scores"};
    auto result = session.Run(Ort::RunOptions{nullptr}, names, inputs.data(), 4, outputs, 1);
    const auto* values = result.front().GetTensorData<float>();
    double total = 0;
    for (size_t i = 0; i < batch; ++i) total += values[i];
    if (!std::isfinite(total)) throw std::runtime_error("non-finite T9 sentence score");
    if (scores.size() >= 2048) scores.clear();
    scores.emplace(text, total);
    return total;
  }
};
// The single worker debounces typing and abandons superseded batches between
// inferences. Its mutex only protects metadata, never model loading or Run().
class AsyncScorer {
 public:
  AsyncScorer() : worker_([this] { Work(); }) {}
  ~AsyncScorer() {
    { std::lock_guard<std::mutex> lock(mutex_); stopping_ = true; }
    wake_.notify_one();
    worker_.join();
  }
  bool Request(const path& model, const std::vector<std::string>& texts, std::vector<double>* result) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (model_ != model || texts_ != texts) {
      model_ = model; texts_ = texts; ++generation_; ready_ = false;
      changed_ = std::chrono::steady_clock::now();
      wake_.notify_one();
    }
    if (ready_) *result = result_;
    return ready_;
  }
  bool Ready() { std::lock_guard<std::mutex> lock(mutex_); return ready_; }
  void Cancel() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (texts_.empty()) return;
    texts_.clear(); ++generation_; ready_ = false;
    wake_.notify_one();
  }
  bool Loaded() { std::lock_guard<std::mutex> lock(mutex_); return loaded_; }
  std::string Status() {
    std::lock_guard<std::mutex> lock(mutex_);
    return failed_ ? "unavailable" : loaded_ ? "ready" : "loading";
  }
 private:
  void Work() {
#if defined(__ANDROID__)
    // Sentence refinement must yield CPU time to the key queue and UI.
    setpriority(PRIO_PROCESS, 0, 10);
#endif
    std::unique_ptr<SentenceScorer> scorer;
    path loaded;
    uint64_t processed = 0;
    std::unique_lock<std::mutex> lock(mutex_);
    while (!stopping_) {
      wake_.wait(lock, [&] { return stopping_ || processed != generation_; });
      if (stopping_) break;
      const auto generation = generation_;
      if (texts_.empty()) { processed = generation; continue; }
      if (wake_.wait_until(lock, changed_ + std::chrono::milliseconds(90),
            [&] { return stopping_ || generation_ != generation; })) continue;
      const auto model = model_;
      const auto texts = texts_;
      lock.unlock();
      std::vector<double> result;
      bool failed = false;
      try {
        if (loaded != model) {
          scorer.reset(); loaded = model;
          scorer = std::make_unique<SentenceScorer>(model, model.parent_path() / (model.stem().string() + ".vocab"));
        }
        for (const auto& text : texts) {
          { std::lock_guard<std::mutex> guard(mutex_);
            if (stopping_ || generation != generation_) break; }
          result.push_back(scorer ? scorer->Score(text) : std::numeric_limits<double>::quiet_NaN());
        }
      } catch (const std::exception& error) {
        LOG(ERROR) << "T9 sentence scoring unavailable: " << error.what();
        scorer.reset(); failed = true;
        result.assign(texts.size(), std::numeric_limits<double>::quiet_NaN());
      }
      lock.lock();
      loaded_ = scorer != nullptr; failed_ = failed;
      processed = generation;
      if (generation == generation_) { result_ = std::move(result); ready_ = true; }
    }
  }
  std::mutex mutex_;
  std::condition_variable wake_;
  bool stopping_ = false, ready_ = false, loaded_ = false, failed_ = false;
  uint64_t generation_ = 0;
  path model_;
  std::vector<std::string> texts_;
  std::vector<double> result_;
  std::chrono::steady_clock::time_point changed_;
  std::thread worker_;
};
std::unique_ptr<AsyncScorer> scorer;
bool grammar_loaded = false;
// Only the serialized engine thread changes this, during an idle refinement.
bool refining = false;
bool requested = false;
}

bool T9RequestSentenceScores(const std::string& model, const std::vector<std::string>& texts,
                             std::vector<double>* scores) {
  requested = false;
  if (model.empty() || texts.empty()) { T9CancelSentenceScores(); return false; }
  for (const auto& text : texts) {
    const auto count = utf8::distance(text.begin(), text.end());
    if (count < 5 || count > 24) { T9CancelSentenceScores(); return false; }
  }
  try {
    const ResourceType type{"t9_sentence", "", ".onnx"};
    std::unique_ptr<ResourceResolver> resolver(Service::instance().CreateResourceResolver(type));
    if (!scorer) scorer = std::make_unique<AsyncScorer>();
    requested = true;
    return scorer->Request(resolver->ResolvePath(model), texts, scores);
  } catch (const std::exception& error) {
    T9CancelSentenceScores();
    LOG(ERROR) << "T9 sentence worker unavailable: " << error.what();
    return false;
  }
}
int T9SentenceRefinementState() { return !requested || !scorer ? 0 : scorer->Ready() ? 2 : 1; }
void T9CancelSentenceScores() { requested = false; if (scorer) scorer->Cancel(); }
bool T9RefiningSentences() { return refining; }
void T9SetRefiningSentences(bool active) { refining = active; }
void T9ReleaseSentenceScorer() { scorer.reset(); grammar_loaded = false; refining = false; requested = false; }
void T9SetGrammarStatus(bool loaded) { grammar_loaded = loaded; }
bool T9SentenceScorerReady() { return scorer && scorer->Loaded(); }
std::string T9ScoringStatus() {
  return std::string("grammar=") + (grammar_loaded ? "ready" : "unavailable") +
      ";sentence=" + (scorer ? scorer->Status() : "not_requested");
}
}  // namespace rime
#endif
