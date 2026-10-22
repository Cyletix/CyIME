#include "t9_cylm_sentence.h"

#include <algorithm>
#include <map>
#include <mutex>
#include <sstream>
#include <utility>

#include <rime/dict/dictionary.h>
#include <rime/gear/translator_commons.h>
#include <utf8.h>

#include "t9_script_options.h"

namespace rime {

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
  vector<char> out(16384);
  const int n = cylm_decode2(session, in.c_str(), digits ? 1 : 0, context.c_str(), out.data(), static_cast<int>(out.size()));
  if (n <= 0 || !out[0]) return;
  const string record(out.data());  // text \x1f pinyin \x1f spans
  const auto unit = record.find('\x1f');
  const string text = record.substr(0, unit);
  const auto& rime = sentences->front();
  if (text.empty() || text == rime->text()) return;
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
}

}  // namespace rime
