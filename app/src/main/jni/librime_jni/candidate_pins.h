#pragma once
// 固定候选 (pinned candidates): words the user pinned for a code come first, in the order they were pinned.
// Shared by CyIME Android (app/src/main/jni/librime_jni) and CyIME Windows (third_party/cyime-engine/policy);
// keep both copies identical. Storage: <Rime user dir>/cyime_pins.txt, one pin per line,
// "code<TAB>text<TAB>pinned at (Unix seconds)"; lines starting with # are comments.
#include <string>
#include <vector>

namespace rime { class Context; }

namespace cyime::pins {
// Loads the pins and registers the "cyime_pins" filter. librime's engine appends that filter after every
// schema's own filters (engine.cc, InitializeComponents), so no schema lists it. Call once after Rime is
// initialized, before sessions are created.
void Initialize(const std::string& user_dir);
// The code a pin belongs to: a segment's input without the ' and space delimiters.
std::string NormalizeCode(const std::string& input);
// The code of the segment whose candidates are shown; empty when nothing is being composed.
std::string ActiveCode(rime::Context* context);
// Texts pinned for a code, first pinned first.
std::vector<std::string> Pinned(const std::string& code);
bool IsPinned(const std::string& code, const std::string& text);
// Pins (only a text the menu of the active code offers) or unpins `text` for the active code, saves the
// file and refreshes the menu. True when the pin state now is the requested one.
bool SetPinned(rime::Context* context, const std::string& text, bool pinned);
}
