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

t9_replace("#include <rime/gear/poet.h>" "#include <rime/gear/poet.h>\n#include <rime/gear/grammar.h>\n#include <t9_candidate_pool.h>\n#include <t9_decode_cache.h>\n#include <t9_lookup_trace.h>\n#include <tuple>\n#include <t9_sentence_scorer.h>\n#include <octagram.h>\n#include <gram_db.h>\n#include <gram_encoding.h>\n#include <utf8.h>\n#include <rime/resource.h>\n#include <rime/service.h>\n#include <t9_ngram_model.h>\n#include <t9_suppression.h>\n#include <unordered_map>\n#include <fstream>\n#include <mutex>\n#include <limits>\n#include <cstring>\n#include <cylm.h>")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime-t9/src")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime-octagram/src")
# CyLM decoder (cylm/cylm.cpp, also built alone as libcylm.so): used only when a schema names its files.
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/cylm")
target_sources(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/cylm/cylm.cpp")
set_source_files_properties("${CMAKE_SOURCE_DIR}/cylm/cylm.cpp" PROPERTIES COMPILE_DEFINITIONS CYLM_LIBRARY)
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
      config->GetBool("t9/sentence_rescore", &t9_sentence_rescore_);
      {
        // CyLM fills the sentence slot when the schema names its files (cylm/lexicon, cylm/model, optional
        // cylm/model2 and cylm/params). Nothing names them by default, so nothing is loaded.
        string lexicon, model, model2, params;
        if (config->GetString("cylm/lexicon", &lexicon) && config->GetString("cylm/model", &model) &&
            !lexicon.empty() && !model.empty()) {
          config->GetString("cylm/model2", &model2);
          config->GetString("cylm/params", &params);
          t9_cylm_ = T9CylmOpen(T9ResolveOnce("cylm", "", lexicon), T9ResolveOnce("cylm", "", model),
                                model2.empty() ? string() : T9ResolveOnce("cylm", "", model2), params);
        }
      }
      if (t9_sentence_rescore_ && !t9_joint_) {
        // 26-key: Rime makes translator/max_sentences sentences as usual; the word model only picks
        // which one comes first (pinyin26-word-model-20261018.md, 2026-10-20).
        string ngram_model;
        if (config->GetString("t9/ngram_model", &ngram_model) && !ngram_model.empty())
          t9_ngram_ = T9NgramModel::Open(T9ResolveOnce("t9_ngram", ".lm", ngram_model));
        config->GetBool("t9/ngram_sentence_start", &t9_ngram_sentence_start_);
      }
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
  bool t9_sentence_rescore_ = false;
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
  std::shared_ptr<CylmModel> t9_cylm_;
  // One CyLM model per set of files for the process (memory-mapped); a failed open is remembered too.
  static std::shared_ptr<CylmModel> T9CylmOpen(const string& lexicon, const string& model, const string& model2,
                                               const string& params) {
    static std::mutex mutex;
    static std::map<string, std::shared_ptr<CylmModel>> opened;
    std::lock_guard<std::mutex> lock(mutex);
    const string key = lexicon + '\n' + model + '\n' + model2 + '\n' + params;
    auto found = opened.find(key);
    if (found != opened.end()) return found->second;
    std::shared_ptr<CylmModel> handle(
        cylm_open(lexicon.c_str(), model.c_str(), model2.empty() ? nullptr : model2.c_str(), params.c_str()),
        [](CylmModel* m) { if (m) cylm_close(m); });
    if (!handle.get()) handle.reset();
    return opened[key] = handle;
  }
  // CyLM's best sentence takes the place of Rime's sentence (words keep their places). Text only until the
  // C API returns the syllables: the candidate has no language, so the user dictionary does not learn it.
  void ApplyCylmSentence() {
    if (!t9_cylm_ || sentences_.empty()) return;
    const string& in = syllabifier_->input();
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
      auto& slot = sessions[{translator_, t9_cylm_.get()}];
      if (!slot) slot = cylm_session(t9_cylm_.get());
      session = slot;
    }
    if (!session) return;
    const string context = !t9_left_context_.empty() && t9_left_context_[0] == '\x1e' ? t9_left_context_.substr(1) : string();
    vector<char> out(16384);
    const int n = cylm_decode(session, in.c_str(), digits ? 1 : 0, context.c_str(), out.data(), static_cast<int>(out.size()));
    if (n <= 0 || !out[0]) return;
    const string text(out.data());
    const auto& rime = sentences_.front();
    if (text == rime->text()) return;
    DictEntry entry;
    entry.text = text;
    entry.weight = rime->weight();
    auto sentence = New<Sentence>(nullptr);
    sentence->Extend(entry, rime->end() - start_, rime->weight());
    sentence->Offset(start_);
    sentence->set_syllabifier(syllabifier_);
    sentence->set_quality(rime->quality());
    sentences_.front() = sentence;
  }
  bool CheckEmpty();]=])
t9_replace("  auto is_correction_match =" [=[
  if (t9_joint_) {
    PrepareJointCandidates(dict, user_dict);
    ApplyCylmSentence();
    return !CheckEmpty();
  }

  auto is_correction_match =]=])
t9_replace("  // A corrected learned phrase can win the general sentence beam even when" [=[
  const an<Sentence> rime_best = sentences_.empty() ? nullptr : sentences_.front();
  // A corrected learned phrase can win the general sentence beam even when]=])
t9_replace("    sentences_.push_front(exact_sentence_);\n  }\n  return !CheckEmpty();" [=[
    sentences_.push_front(exact_sentence_);
  }
  if (t9_sentence_rescore_ && sentences_.size() > 1) {
    // translator/max_sentences gave the n-best to choose from; the menu keeps one sentence, as with
    // max_sentences: 1 (followed by Rime's best when that one is a correction).
    auto corrected = [&](const an<Sentence>& sentence) {
      return enable_correction_ && syllabifier_->IsCorrection(sentence->code(), sentence->end() - start_);
    };
    an<Sentence> chosen = sentences_.front();
    const string& in = syllabifier_->input();
    size_t groups = 0;
    bool vowel = false, consonants_only = !in.empty();
    for (char ch : in) {
      // 14-key merged keys are capitals; E/U/O/A carry the vowels (er, ui, op, as).
      const bool v = ch && std::strchr("aeiouvEUOA", ch);
      if (v && !vowel) ++groups;
      vowel = v;
      if (ch < 'a' || ch > 'z' || v) consonants_only = false;
    }
    // Initials and short abbreviations (zjqa, zdky) keep Rime's dictionary order.
    if (t9_ngram_ && !consonants_only && groups >= 2) {
      // Substring -> model word id, kept across keys (consecutive n-bests share most substrings).
      thread_local std::unordered_map<string, int32_t> ids;
      thread_local const T9NgramModel* ids_model = nullptr;
      if (ids_model != t9_ngram_.get() || ids.size() > 65536) { ids.clear(); ids_model = t9_ngram_.get(); }
      auto word_id = [&](const string& word) {
        auto found = ids.find(word);
        if (found != ids.end()) return found->second;
        const int32_t id = t9_ngram_->Id(word);
        ids.emplace(word, id);
        return id;
      };
      // ln P(text): best split into model words (unigram Viterbi, words up to 8 characters), chained
      // with bigram/trigram history, so 被嫌弃了 and 被掀起了 compare as text whatever words Rime used;
      // plus the sentence-position rules Rime applied while making it (吧 not first, 被 not last).
      auto score = [&](const Sentence& sentence) {
        const string& text = sentence.text();
        vector<size_t> at{0};
        for (size_t i = 0; i < text.size();) {
          const unsigned char c = static_cast<unsigned char>(text[i]);
          i += c < 0x80 ? 1 : c < 0xE0 ? 2 : c < 0xF0 ? 3 : 4;
          at.push_back(std::min(i, text.size()));
        }
        const size_t n = at.size() - 1;
        vector<double> best(n + 1, -std::numeric_limits<double>::infinity());
        vector<size_t> from(n + 1, 0);
        vector<int32_t> word(n + 1, ::T9NgramModel::kUnseen);
        best[0] = 0;
        for (size_t end = 1; end <= n; ++end) {
          for (size_t start = end > 8 ? end - 8 : 0; start < end; ++start) {
            if (!std::isfinite(best[start])) continue;
            const int32_t id = word_id(text.substr(at[start], at[end] - at[start]));
            if (id == ::T9NgramModel::kUnseen && end - start > 1) continue;
            const double value = best[start] + t9_ngram_->Start(id, false);
            if (value > best[end]) { best[end] = value; from[end] = start; word[end] = id; }
          }
        }
        vector<int32_t> chain;
        for (size_t end = n; end > 0; end = from[end]) chain.push_back(word[end]);
        std::reverse(chain.begin(), chain.end());
        double total = 0;
        const int32_t root = t9_ngram_sentence_start_ ? t9_ngram_->Bos() : ::T9NgramModel::kUnseen;
        for (size_t k = 0; k < chain.size(); ++k)
          total += k == 0 ? t9_ngram_->Start(chain[0], t9_ngram_sentence_start_)
                          : t9_ngram_->LogProb3(k >= 2 ? chain[k - 2] : root, chain[k - 1], chain[k]);
        const auto& words = sentence.components();
        if (!words.empty() && poet_) {
          total += poet_->PositionAdjust(words.front().text, true, words.size() == 1);
          if (words.size() > 1) total += poet_->PositionAdjust(words.back().text, false, true);
        }
        return total;
      };
      double chosen_score = -std::numeric_limits<double>::infinity();
      for (const auto& sentence : sentences_) {
        if (corrected(sentence)) continue;  // a typo correction never wins on the word model alone
        const double value = score(*sentence);
        if (value > chosen_score) { chosen_score = value; chosen = sentence; }
      }
    }
    sentences_.clear();
    sentences_.push_back(chosen);
    if (rime_best && rime_best != chosen && corrected(rime_best)) sentences_.push_back(rime_best);
  }
  ApplyCylmSentence();
  return !CheckEmpty();]=])
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
