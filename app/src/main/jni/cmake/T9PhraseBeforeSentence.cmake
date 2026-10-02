# Compile a generated translation unit, leaving the pinned dependency intact.
set(T9_SCRIPT_TRANSLATOR_SOURCE "${CMAKE_SOURCE_DIR}/librime/src/rime/gear/script_translator.cc")
set(T9_JOINT_DECODER "${CMAKE_CURRENT_LIST_DIR}/T9JointDecoder.inc")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
             "${T9_SCRIPT_TRANSLATOR_SOURCE}" "${T9_JOINT_DECODER}")
file(READ "${T9_SCRIPT_TRANSLATOR_SOURCE}" T9_SCRIPT_TRANSLATOR_CODE)

function(t9_replace old new)
  string(FIND "${T9_SCRIPT_TRANSLATOR_CODE}" "${old}" pos)
  if(pos EQUAL -1)
    message(FATAL_ERROR "librime changed: review T9 decoder anchor: ${old}")
  endif()
  string(REPLACE "${old}" "${new}" code "${T9_SCRIPT_TRANSLATOR_CODE}")
  set(T9_SCRIPT_TRANSLATOR_CODE "${code}" PARENT_SCOPE)
endfunction()

t9_replace("#include <rime/gear/poet.h>" "#include <rime/gear/poet.h>\n#include <rime/gear/grammar.h>\n#include <t9_candidate_pool.h>\n#include <t9_decode_cache.h>\n#include <t9_lookup_trace.h>\n#include <tuple>\n#include <t9_sentence_scorer.h>\n#include <octagram.h>\n#include <gram_db.h>\n#include <gram_encoding.h>\n#include <utf8.h>")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime-t9/src")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime-octagram/src")
t9_replace("double sentence_cutoff_threshold)" "double sentence_cutoff_threshold, Config* config)")
t9_replace("this, corrector_.get(), poet_.get(), input, segment.start, end_of_input,\n      max_sentences_, sentence_cutoff_threshold_);"
           "this, corrector_.get(), poet_.get(), input, segment.start, end_of_input,\n      max_sentences_, sentence_cutoff_threshold_, engine_->schema()->config());")
t9_replace("    set_exhausted(true);\n  }\n  bool Evaluate" [=[
    if (config) {
      config->GetBool("t9/joint_decoder", &t9_joint_);
      config->GetBool("t9/decoded_cache", &t9_decoded_cache_);
      if (t9_joint_) {
        auto budget = [&](const char* key, size_t& value, int minimum, int maximum) {
          int configured = static_cast<int>(value);
          config->GetInt(key, &configured);
          value = static_cast<size_t>(std::max(minimum, std::min(maximum, configured)));
        };
        budget("t9/source_budget", t9_source_budget_, 8, 128);
        budget("t9/pool_budget", t9_pool_budget_, 8, 64);
        budget("t9/dictionary_beam", t9_dictionary_beam_, 8, 64);
        budget("t9/sentence_beam", t9_sentence_beam_, 8, 128);
        budget("t9/result_budget", t9_result_budget_, 3, 64);
        config->GetDouble("t9/spelling_penalty", &t9_spelling_penalty_);
        config->GetDouble("t9/word_penalty", &t9_word_penalty_);
        config->GetString("t9/sentence_model", &t9_sentence_model_);
        config->GetDouble("t9/sentence_weight", &t9_sentence_weight_);
        if (auto* component = Grammar::Require("grammar"))
          t9_grammar_.reset(component->Create(config));
        string language;
        if (config->GetString("grammar/language", &language)) {
          if (auto* component = dynamic_cast<OctagramComponent*>(Grammar::Require("grammar")))
            t9_gram_db_ = component->GetDb(language);
        }
        T9SetGrammarStatus(t9_gram_db_ != nullptr);
      }
    }
    set_exhausted(true);
  }
  bool Evaluate]=])
t9_replace("  bool CheckEmpty();" [=[
  bool t9_joint_ = false;
  bool t9_decoded_cache_ = true;
  size_t t9_source_budget_ = 128;
  size_t t9_pool_budget_ = 64;
  size_t t9_dictionary_beam_ = 16;
  size_t t9_sentence_beam_ = 24;
  size_t t9_result_budget_ = 12;
  double t9_spelling_penalty_ = 5.0;
  double t9_word_penalty_ = 8.0;
  double t9_sentence_weight_ = 0.25;
  string t9_sentence_model_;
  GramDb* t9_gram_db_ = nullptr;
  the<Grammar> t9_grammar_;
  bool PrepareJointCandidates(Dictionary* dict, UserDictionary* user_dict);
  bool CheckEmpty();]=])
t9_replace("  auto is_correction_match =" [=[
  if (t9_joint_) {
    PrepareJointCandidates(dict, user_dict);
    return !CheckEmpty();
  }

  auto is_correction_match =]=])
t9_replace("set_exhausted((!phrase_" "set_exhausted(sentences_.empty() && (!phrase_")
t9_replace("#if defined(__ANDROID__)\n#include <android/log.h>\n#include <chrono>"
           "#if defined(__ANDROID__) && defined(CYIME_ENABLE_DIAGNOSTICS)\n#include <android/log.h>")
file(READ "${T9_JOINT_DECODER}" T9_JOINT_CODE)
t9_replace("// ScriptTranslator implementation" "${T9_JOINT_CODE}\n\n// ScriptTranslator implementation")

set(T9_SCRIPT_TRANSLATOR_COPY "${CMAKE_CURRENT_BINARY_DIR}/cyime-script-translator.cc")
file(CONFIGURE OUTPUT "${T9_SCRIPT_TRANSLATOR_COPY}" CONTENT "${T9_SCRIPT_TRANSLATOR_CODE}" @ONLY)
get_target_property(T9_RIME_SOURCES rime-static SOURCES)
set(T9_SCRIPT_REPLACED FALSE)
foreach(T9_SOURCE IN LISTS T9_RIME_SOURCES)
  if(T9_SOURCE MATCHES "(^|/)rime/gear/script_translator\\.cc$")
    list(REMOVE_ITEM T9_RIME_SOURCES "${T9_SOURCE}")
    set(T9_SCRIPT_REPLACED TRUE)
  endif()
endforeach()
if(NOT T9_SCRIPT_REPLACED)
  message(FATAL_ERROR "Cannot locate librime script_translator compilation unit")
endif()
list(APPEND T9_RIME_SOURCES "${T9_SCRIPT_TRANSLATOR_COPY}")
set_property(TARGET rime-static PROPERTY SOURCES "${T9_RIME_SOURCES}")
