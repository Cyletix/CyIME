// What CyIME's sentence code reads from one script translation. ScriptSyllabifier is declared only
// inside Rime's script_translator.cc, so its correction test arrives as a function pointer.
#ifndef CYIME_T9_SCRIPT_QUERY_H_
#define CYIME_T9_SCRIPT_QUERY_H_

#include <cstddef>

#include <rime/algo/syllabifier.h>
#include <rime/common.h>
#include <rime/dict/vocabulary.h>

namespace rime {

class PhraseSyllabifier;
class ScriptTranslator;

struct T9ScriptQuery {
  ScriptTranslator* translator;
  const an<PhraseSyllabifier>& syllabifier;  // given to every sentence made
  const string& input;
  const SyllableGraph& syllables;
  size_t start;
  size_t end_of_input;
  bool enable_correction;
  const void* script_syllabifier;
  bool (*is_correction)(const void* script_syllabifier, const Code& code, size_t code_length, size_t start);

  // ScriptSyllabifier::IsCorrection.
  bool IsCorrection(const Code& code, size_t code_length, size_t from = 0) const {
    return is_correction(script_syllabifier, code, code_length, from);
  }
};

}  // namespace rime

#endif  // CYIME_T9_SCRIPT_QUERY_H_
