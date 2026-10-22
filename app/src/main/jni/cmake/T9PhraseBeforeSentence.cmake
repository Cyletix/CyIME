# Compile a generated translation unit, leaving the pinned dependency intact.
set(T9_SCRIPT_TRANSLATOR_SOURCE "${CMAKE_SOURCE_DIR}/librime/src/rime/gear/script_translator.cc")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${T9_SCRIPT_TRANSLATOR_SOURCE}")
file(READ "${T9_SCRIPT_TRANSLATOR_SOURCE}" T9_SCRIPT_TRANSLATOR_CODE)

function(t9_replace old new)
  string(FIND "${T9_SCRIPT_TRANSLATOR_CODE}" "${old}" pos)
  if(pos EQUAL -1)
    message(FATAL_ERROR "librime changed: review T9 decoder anchor: ${old}")
  endif()
  string(REPLACE "${old}" "${new}" code "${T9_SCRIPT_TRANSLATOR_CODE}")
  set(T9_SCRIPT_TRANSLATOR_CODE "${code}" PARENT_SCOPE)
endfunction()

t9_replace("#include <rime/gear/poet.h>" "#include <rime/gear/poet.h>\n#include <t9_script_options.h>\n#include <t9_cylm_sentence.h>\n#include <t9_sentence_rescore.h>\n#include <t9_joint_decoder.h>\n#include <rime/gear/grammar.h>\n#include <t9_candidate_pool.h>\n#include <t9_decode_cache.h>\n#include <t9_lookup_trace.h>\n#include <tuple>\n#include <t9_sentence_scorer.h>\n#include <octagram.h>\n#include <gram_db.h>\n#include <gram_encoding.h>\n#include <utf8.h>\n#include <rime/resource.h>\n#include <rime/service.h>\n#include <t9_ngram_model.h>\n#include <t9_suppression.h>\n#include <unordered_map>\n#include <fstream>\n#include <mutex>\n#include <limits>\n#include <cstring>\n#include <cylm.h>\n#include <sstream>")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime-t9/src")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime-octagram/src")
# CyLM decoder (cylm/cylm.cpp, also built alone as libcylm.so): used only when a schema names its files.
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/cylm")
target_sources(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/cylm/cylm.cpp")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/cyime_rime")
target_sources(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/cyime_rime/t9_script_options.cc"
               "${CMAKE_SOURCE_DIR}/cyime_rime/t9_cylm_sentence.cc"
               "${CMAKE_SOURCE_DIR}/cyime_rime/t9_sentence_rescore.cc"
               "${CMAKE_SOURCE_DIR}/cyime_rime/t9_joint_decoder.cc")
# Source properties are per directory: set it where rime-static is defined, or main() gets linked in.
set_source_files_properties("${CMAKE_SOURCE_DIR}/cylm/cylm.cpp" TARGET_DIRECTORY rime-static PROPERTIES COMPILE_DEFINITIONS "CYLM_LIBRARY;CYLM_ORT"
  # OrtStatus results of option setters are ignored on purpose in cylm.cpp; the NDK build treats that as an error.
  COMPILE_OPTIONS "-Wno-unused-result")
# The reranker uses the onnxruntime C API; rime-static links onnxruntime already (jni CMakeLists).
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/onnxruntime/include")
t9_replace("double sentence_cutoff_threshold)" "double sentence_cutoff_threshold, Config* config)")
t9_replace("this, corrector_.get(), poet_.get(), input, segment.start, end_of_input,\n      max_sentences_, sentence_cutoff_threshold_);"
           "this, corrector_.get(), poet_.get(), input, segment.start, end_of_input,\n      max_sentences_, sentence_cutoff_threshold_, engine_->schema()->config());")
t9_replace("    set_exhausted(true);\n  }\n  bool Evaluate"
           "    LoadT9Options(config, context);\n    set_exhausted(true);\n  }\n  bool Evaluate")
t9_replace("  bool CheckEmpty();" [=[
  // The word graph the last MakeSentences used (the incremental cache or a kept copy), so the
  // sentence rescoring can make a second n-best without corrected words.
  const WordGraph* t9_sentence_graph_ = nullptr;
  WordGraph t9_own_sentence_graph_;
  static bool T9IsCorrection(const void* syllabifier, const Code& code, size_t length, size_t start) {
    return static_cast<const ScriptSyllabifier*>(syllabifier)->IsCorrection(code, length, start);
  }
  T9ScriptQuery T9Query(const an<PhraseSyllabifier>& phrase_syllabifier) const {
    return {translator_, phrase_syllabifier, syllabifier_->input(), syllabifier_->syllable_graph(), start_,
            end_of_input_, enable_correction_, syllabifier_.get(), &T9IsCorrection};
  }
  // CyLM's best sentence takes the place of Rime's sentence (cyime_rime/t9_cylm_sentence.cc).
  void ApplyCylmSentence(Dictionary* dict) {
    if (!t9_cylm_ || sentences_.empty()) return;
    T9ApplyCylmSentence(t9_cylm_.get(), translator_, syllabifier_->input(), t9_left_context_, dict,
                        translator_->language(), start_, syllabifier_, &sentences_);
  }
  bool CheckEmpty();]=])
t9_replace("  auto is_correction_match =" [=[
  if (t9_joint_) {
    {
      // cyime_rime/t9_joint_decoder.cc
      const an<PhraseSyllabifier> phrase_syllabifier = syllabifier_;
      T9DecodeJoint(T9Query(phrase_syllabifier), *this, &IsT9NumericInput, dict, user_dict, &sentences_);
    }
    ApplyCylmSentence(dict);
    return !CheckEmpty();
  }

  auto is_correction_match =]=])
t9_replace("  // A corrected learned phrase can win the general sentence beam even when" [=[
  const an<Sentence> rime_best = sentences_.empty() ? nullptr : sentences_.front();
  // A corrected learned phrase can win the general sentence beam even when]=])
t9_replace("    sentences_.push_front(exact_sentence_);\n  }\n  return !CheckEmpty();" [=[
    sentences_.push_front(exact_sentence_);
  }
  // With doubled-letter correction on, a lone corrected sentence is checked too: the typed reading keeps
  // the first place (aniu: 阿牛, not the corrected 按钮).
  const bool lone_correction = translator_->doubled_letter_correction() && sentences_.size() == 1 &&
      enable_correction_ && syllabifier_->IsCorrection(sentences_.front()->code(), sentences_.front()->end() - start_);
  if (t9_sentence_rescore_ && (sentences_.size() > 1 || lone_correction)) {
    // cyime_rime/t9_sentence_rescore.cc
    const an<PhraseSyllabifier> phrase_syllabifier = syllabifier_;
    T9RescoreSentences(T9Query(phrase_syllabifier), t9_ngram_.get(), t9_ngram_sentence_start_, poet_,
                       t9_sentence_graph_, max_sentences_, sentence_cutoff_threshold_, exact_sentence_, rime_best,
                       &sentences_);
  }
  ApplyCylmSentence(dict);
  return !CheckEmpty();]=])
t9_replace("set_exhausted((!phrase_" "set_exhausted(sentences_.empty() && (!phrase_")
# Keep the graph the n-best came from: the 26-key rescoring makes a second n-best from it without corrections.
t9_replace("        max_sentences_, sentence_cutoff_threshold_);\n    for (auto& sentence : sentences) {\n      sentence->Offset(start_);\n      sentence->set_syllabifier(syllabifier_);\n    }\n    translator_->CommitIncremental("
           "        max_sentences_, sentence_cutoff_threshold_);\n    t9_sentence_graph_ = &inc->graph;\n    for (auto& sentence : sentences) {\n      sentence->Offset(start_);\n      sentence->set_syllabifier(syllabifier_);\n    }\n    translator_->CommitIncremental(")
t9_replace("      max_sentences_, sentence_cutoff_threshold_);\n  for (auto& sentence : sentences) {\n    sentence->Offset(start_);\n    sentence->set_syllabifier(syllabifier_);\n  }\n  return sentences;\n}"
           "      max_sentences_, sentence_cutoff_threshold_);\n  if (inc && cacheable) {\n    t9_sentence_graph_ = &inc->graph;\n  } else {\n    t9_own_sentence_graph_ = std::move(graph);\n    t9_sentence_graph_ = &t9_own_sentence_graph_;\n  }\n  for (auto& sentence : sentences) {\n    sentence->Offset(start_);\n    sentence->set_syllabifier(syllabifier_);\n  }\n  return sentences;\n}")
t9_replace("#if defined(__ANDROID__)\n#include <android/log.h>\n#include <chrono>"
           "#if defined(__ANDROID__) && defined(CYIME_ENABLE_DIAGNOSTICS)\n#include <android/log.h>")
# Settings and shared resources live in cyime_rime/t9_script_options.cc; the translation derives from them.
t9_replace("class ScriptTranslation : public Translation {"
           "class ScriptTranslation : public Translation, public T9ScriptOptions {")

include("${CMAKE_CURRENT_LIST_DIR}/PinyinSyllableSelection.cmake")
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
