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

t9_replace("#include <rime/gear/poet.h>" "#include <rime/gear/poet.h>\n#include <rime/gear/grammar.h>\n#include <t9_candidate_pool.h>\n#include <t9_decode_cache.h>\n#include <t9_lookup_trace.h>\n#include <tuple>\n#include <t9_sentence_scorer.h>\n#include <octagram.h>\n#include <gram_db.h>\n#include <gram_encoding.h>\n#include <utf8.h>\n#include <rime/resource.h>\n#include <rime/service.h>\n#include <t9_ngram_model.h>\n#include <t9_suppression.h>\n#include <unordered_map>\n#include <fstream>\n#include <mutex>")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime-t9/src")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime-octagram/src")
t9_replace("double sentence_cutoff_threshold)" "double sentence_cutoff_threshold, Config* config)")
t9_replace("this, corrector_.get(), poet_.get(), input, segment.start, end_of_input,\n      max_sentences_, sentence_cutoff_threshold_);"
           "this, corrector_.get(), poet_.get(), input, segment.start, end_of_input,\n      max_sentences_, sentence_cutoff_threshold_, engine_->schema()->config());")
t9_replace("    set_exhausted(true);\n  }\n  bool Evaluate" [=[
    // Text committed before the composition, set by the app (RimeEngine.setT9LeftContext);
    // `context` is the engine context parameter added by PinyinSyllableSelection.cmake.
    if (context) t9_left_context_ = context->get_property("cyime_left_context");
    if (context) t9_neighbors_ = context->get_property("cyime_neighbors");
    if (config) {
      config->GetBool("t9/joint_decoder", &t9_joint_);
      config->GetBool("t9/decoded_cache", &t9_decoded_cache_);
      {
        // t9/reading_table: ln(count / count of the text's main reading) for minority readings
        // (的 di, 了 liao). The word model scores words, not readings, so without this 默默的 beat
        // 默默地 for momodi because 的 is also read di (目的).
        string readings;
        if (config->GetString("t9/reading_table", &readings) && !readings.empty()) {
          t9_readings_ = T9LoadReadings(T9ResolveOnce("t9_readings", ".txt", readings));
        }
      }
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
        int display = static_cast<int>(t9_display_sentences_);
        config->GetInt("t9/display_sentences", &display);
        { int keys = 0; config->GetInt("t9/more_sentences_min_keys", &keys);
          t9_more_sentences_min_keys_ = static_cast<size_t>(std::max(0, keys)); }
        config->GetBool("t9/cache_with_correction", &t9_cache_with_correction_);
        t9_display_sentences_ = static_cast<size_t>(std::max(0, display));
        config->GetDouble("t9/spelling_penalty", &t9_spelling_penalty_);
        config->GetDouble("t9/word_penalty", &t9_word_penalty_);
        config->GetString("t9/sentence_model", &t9_sentence_model_);
        config->GetDouble("t9/sentence_weight", &t9_sentence_weight_);
        string ngram_model;
        if (config->GetString("t9/ngram_model", &ngram_model) && !ngram_model.empty()) {
          // Not .bin: Rime cleanup moves *.bin in the user directory to trash.
          t9_ngram_ = T9NgramModel::Open(T9ResolveOnce("t9_ngram", ".lm", ngram_model));
        }
        config->GetDouble("t9/ngram_user_weight", &t9_ngram_user_weight_);
        config->GetDouble("t9/ngram_dictionary_prior", &t9_ngram_dictionary_prior_);
        config->GetBool("t9/ngram_sentence_start", &t9_ngram_sentence_start_);
        config->GetDouble("t9/ngram_eos_weight", &t9_ngram_eos_weight_);
        config->GetBool("t9/ngram_merge_segmentations", &t9_ngram_merge_segmentations_);
        config->GetDouble("t9/correction_penalty", &t9_correction_penalty_);
        config->GetBool("t9/prefer_user_entries", &t9_prefer_user_entries_);
        { int whole = 0; config->GetInt("t9/whole_input_phrases", &whole); t9_whole_input_phrases_ = static_cast<size_t>(std::max(0, whole)); }
        // 26-key uses the decoder only with the word model; without the model file it keeps
        // the ordinary Rime phrase/sentence path rather than dictionary weights + word penalty.
        bool requires_ngram = false;
        config->GetBool("t9/joint_requires_ngram", &requires_ngram);
        if (requires_ngram && !t9_ngram_) t9_joint_ = false;
        // Without the word n-gram model, keep dictionary weight + grammar + word penalty.
        if (!t9_ngram_) {
          if (auto* component = Grammar::Require("grammar"))
            t9_grammar_.reset(component->Create(config));
          string language;
          if (config->GetString("grammar/language", &language)) {
            if (auto* component = dynamic_cast<OctagramComponent*>(Grammar::Require("grammar")))
              t9_gram_db_ = component->GetDb(language);
          }
        }
        T9SetGrammarStatus(t9_gram_db_ != nullptr || t9_ngram_ != nullptr);
      }
    }
    set_exhausted(true);
  }
  bool Evaluate]=])
t9_replace("  bool CheckEmpty();" [=[
  bool t9_joint_ = false;
  using T9Readings = std::unordered_map<string, vector<std::pair<string, double>>>;
  std::shared_ptr<const T9Readings> t9_readings_;
  // Resource paths resolved once per type and name. Resolving stats files in the user and shared
  // data directories, and the translation reading this config is rebuilt on every key. A model
  // installed later already needs a restart (T9NgramModel::Open remembers missing files too).
  static string T9ResolveOnce(const char* type_name, const char* suffix, const string& name) {
    static std::mutex mutex;
    static std::map<string, string> resolved;
    std::lock_guard<std::mutex> lock(mutex);
    auto& path = resolved[Service::instance().deployer().user_data_dir.string() + '\n' + type_name + '\n' + name];
    if (path.empty()) {
      const ResourceType type{type_name, "", suffix};
      std::unique_ptr<ResourceResolver> resolver(Service::instance().CreateResourceResolver(type));
      path = resolver->ResolvePath(name).string();
    }
    return path;
  }
  // Loaded once per file and kept: a translation (and this member) is rebuilt on every key, so a
  // weak cache reread the file per key. Lines are text<TAB>spelling<TAB>ln share.
  static std::shared_ptr<const T9Readings> T9LoadReadings(const string& path) {
    static std::mutex mutex;
    static std::map<string, std::shared_ptr<const T9Readings>> loaded;
    std::lock_guard<std::mutex> lock(mutex);
    if (auto kept = loaded[path]) return kept;
    std::ifstream in(path);
    if (!in) return nullptr;
    auto table = std::make_shared<T9Readings>();
    string line;
    while (std::getline(in, line)) {
      const auto a = line.find('\t');
      const auto b = a == string::npos ? a : line.find('\t', a + 1);
      if (b == string::npos || line[0] == '#') continue;
      (*table)[line.substr(0, a)].emplace_back(line.substr(a + 1, b - a - 1), std::atof(line.c_str() + b + 1));
    }
    loaded[path] = table;
    return table;
  }
  bool t9_decoded_cache_ = true;
  size_t t9_source_budget_ = 128;
  size_t t9_pool_budget_ = 64;
  size_t t9_dictionary_beam_ = 16;
  size_t t9_sentence_beam_ = 24;
  size_t t9_result_budget_ = 12;
  size_t t9_display_sentences_ = 0;
  double t9_spelling_penalty_ = 5.0;
  double t9_word_penalty_ = 8.0;
  size_t t9_whole_input_phrases_ = 0;
  bool t9_prefer_user_entries_ = false;
  double t9_correction_penalty_ = -4.605170185988091;  // log(0.01): Rime's corrected-syllable credibility
  double t9_sentence_weight_ = 0.25;
  string t9_sentence_model_;
  GramDb* t9_gram_db_ = nullptr;
  the<Grammar> t9_grammar_;
  std::shared_ptr<const T9NgramModel> t9_ngram_;
  double t9_ngram_user_weight_ = 0.05;
  double t9_ngram_dictionary_prior_ = 0;
  bool t9_ngram_sentence_start_ = false;
  double t9_ngram_eos_weight_ = 0;
  bool t9_ngram_merge_segmentations_ = false;
  string t9_left_context_;
  string t9_neighbors_;
  size_t t9_more_sentences_min_keys_ = 0;
  bool t9_cache_with_correction_ = false;
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
