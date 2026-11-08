// Host check for libcytranslate: reads one sentence per line from stdin and prints
// "<milliseconds>\t<translation>" per line, after the load time on stderr.
//   cytranslate-cli <model.bin> <srcvocab.spm> <trgvocab.spm> <lex.s2t.bin> < sentences.txt

#include <chrono>
#include <cstdio>
#include <iostream>
#include <string>

#include "cytranslate.h"

int main(int argc, char **argv) {
  if (argc != 5) {
    std::cerr << "usage: " << argv[0] << " model srcvocab trgvocab shortlist < lines\n";
    return 2;
  }
  using Clock = std::chrono::steady_clock;
  auto start = Clock::now();
  std::string error;
  cytranslate::Engine *engine = cytranslate::create(argv[1], argv[2], argv[3], argv[4], error);
  if (engine == nullptr) {
    std::cerr << "load failed: " << error << "\n";
    return 1;
  }
  auto loaded = Clock::now();
  std::cerr << "load_ms\t" << std::chrono::duration<double, std::milli>(loaded - start).count() << "\n";
  std::string line;
  while (std::getline(std::cin, line)) {
    if (line.empty()) continue;
    std::string out;
    auto begin = Clock::now();
    if (!cytranslate::translate(engine, line, out, error)) {
      std::cerr << "translate failed: " << error << "\n";
      cytranslate::destroy(engine);
      return 1;
    }
    double ms = std::chrono::duration<double, std::milli>(Clock::now() - begin).count();
    std::cout << ms << "\t" << out << "\n";
  }
  cytranslate::destroy(engine);
  return 0;
}
