// Nine-key joint decoder (t9/joint_decoder), used in place of Rime's phrase and sentence lookup.
#ifndef CYIME_T9_JOINT_DECODER_H_
#define CYIME_T9_JOINT_DECODER_H_

#include <rime/common.h>

#include "t9_script_query.h"

namespace rime {

class Dictionary;
class Sentence;
class UserDictionary;
struct T9ScriptOptions;

// Appends the decoded whole-input sentences to `sentences`, best first, and returns whether there are
// any. Results and search frames are cached on the translator (ScriptTranslator::t9_joint_cache).
// `is_numeric_input` is Rime's test for a digit-only (nine-key) input.
bool T9DecodeJoint(const T9ScriptQuery& q, const T9ScriptOptions& o, bool (*is_numeric_input)(const string&),
                   Dictionary* dict, UserDictionary* user_dict, deque<an<Sentence>>* sentences);

}  // namespace rime

#endif  // CYIME_T9_JOINT_DECODER_H_
