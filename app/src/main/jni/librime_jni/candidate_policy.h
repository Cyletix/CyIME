#pragma once
#include <string>

namespace cyime {
// Called under the engine lock, before any schema/session is created.
void InitializeCandidatePolicy(const std::string& user_dir);
bool HideCandidate(const std::string& text);
bool RestoreCandidate(const std::string& text);
}
