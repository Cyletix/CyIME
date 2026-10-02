// Diagnostic only: the shipped ONNX model with four masks at different positions.
// Does not modify the engine, dictionary or candidate set.
#include <onnxruntime_cxx_api.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <limits>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

int main(int argc, char** argv) {
  if (argc != 5) { std::cerr << "ScoreProbe MODEL VOCAB INPUT.tsv OUTPUT.tsv\n"; return 2; }
  try {
    std::unordered_map<std::string, int64_t> vocab;
    std::ifstream vocabulary(argv[2]);
    if (!vocabulary) throw std::runtime_error("Vocabulary unavailable");
    std::string token;
    int64_t index = 0;
    while (std::getline(vocabulary, token)) {
      if (!token.empty() && token.back() == '\r') token.pop_back();
      vocab.emplace(token, index++);
    }
    if (index != 21128 || vocab.at("[MASK]") != 103) throw std::runtime_error("Unexpected vocabulary");
    Ort::Env env(ORT_LOGGING_LEVEL_ERROR, "CyIME-T9-ScoreProbe");
    Ort::SessionOptions options;
    options.SetIntraOpNumThreads(1);
    options.SetInterOpNumThreads(1);
    options.SetExecutionMode(ExecutionMode::ORT_SEQUENTIAL);
    options.DisableCpuMemArena();
    options.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_EXTENDED);
    Ort::Session session(env, argv[1], options);
    auto memory = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    std::ifstream input(argv[3]);
    std::ofstream output(argv[4]);
    if (!input || !output) throw std::runtime_error("Input/output unavailable");
    output << std::setprecision(12);
    std::string line;
    size_t item = 0;
    while (std::getline(input, line)) {
      if (!line.empty() && line.back() == '\r') line.pop_back();
      const auto tab = line.find('\t');
      if (tab == std::string::npos) throw std::runtime_error("Malformed probe input");
      const auto key = line.substr(0, tab), text = line.substr(tab + 1);
      std::vector<int64_t> ids{101};
      bool supported = true;
      for (size_t at = 0; at < text.size();) {
        const auto lead = static_cast<unsigned char>(text[at]);
        const size_t count = lead < 128 ? 1 : lead < 224 ? 2 : lead < 240 ? 3 : 4;
        const auto found = vocab.find(text.substr(at, count));
        if (found == vocab.end()) { supported = false; break; }
        ids.push_back(found->second); at += count;
      }
      const size_t n = ids.size() - 1;
      if (!supported || n < 5 || n > 24) continue;
      ids.push_back(102);
      // Rotate measurement order to reduce one-sided cold-start timing bias.
      for (size_t order = 0; order < 3; ++order) {
        const size_t strategy = (order + item) % 3;
        std::vector<int64_t> positions;
        for (size_t k = 0; k < 4; ++k)
          positions.push_back(strategy == 0 ? n - 3 + k : strategy == 1 ? 1 + k * (n - 1) / 3 : k + 1);
        std::vector<int64_t> tokens, mask(4 * ids.size(), 1), targets;
        for (const auto pos : positions) {
          tokens.insert(tokens.end(), ids.begin(), ids.end());
          tokens[tokens.size() - ids.size() + pos] = 103;
          targets.push_back(ids[pos]);
        }
        const std::array<int64_t, 2> shape{4, static_cast<int64_t>(ids.size())};
        const std::array<int64_t, 1> rows{4};
        std::array<Ort::Value, 4> values{
          Ort::Value::CreateTensor<int64_t>(memory, tokens.data(), tokens.size(), shape.data(), 2),
          Ort::Value::CreateTensor<int64_t>(memory, mask.data(), mask.size(), shape.data(), 2),
          Ort::Value::CreateTensor<int64_t>(memory, positions.data(), 4, rows.data(), 1),
          Ort::Value::CreateTensor<int64_t>(memory, targets.data(), 4, rows.data(), 1)};
        const char* names[] = {"input_ids", "attention_mask", "positions", "targets"};
        const char* outputs[] = {"scores"};
        const auto begin = std::chrono::steady_clock::now();
        auto result = session.Run(Ort::RunOptions{nullptr}, names, values.data(), 4, outputs, 1);
        const auto micros = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now() - begin).count();
        const auto* scores = result.front().GetTensorData<float>();
        double total = 0;
        for (size_t k = 0; k < 4; ++k) total += scores[k];
        output << key << '\t' << (strategy == 0 ? "tail4" : strategy == 1 ? "spread4" : "head4")
               << '\t' << total << '\t' << micros << '\n';
      }
      ++item;
    }
  } catch (const std::exception& error) { std::cerr << error.what() << '\n'; return 2; }
}
