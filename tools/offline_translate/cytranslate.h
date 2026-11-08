#pragma once

#include <string>

namespace cytranslate {

struct Engine;

// nullptr on failure, with the reason in error.
Engine *create(const std::string &model_path, const std::string &source_vocabulary,
               const std::string &target_vocabulary, const std::string &shortlist, std::string &error);
bool translate(Engine *engine, const std::string &text, std::string &out, std::string &error);
void destroy(Engine *engine);

}  // namespace cytranslate
