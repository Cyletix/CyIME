#pragma once
#include <string>
#include <vector>

namespace cyime {
// Called under the engine lock, before any schema/session is created.
void InitializeCandidatePolicy(const std::string& user_dir);
bool HideCandidate(const std::string& text);
bool RestoreCandidate(const std::string& text);
// Texts hidden by the former long-press "delete"; kept only so they can be restored.
std::vector<std::string> HiddenCandidates();
}
