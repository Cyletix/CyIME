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

t9_replace("#include <rime/gear/poet.h>" "#include <rime/gear/poet.h>\n#include <rime/gear/grammar.h>\n#include <t9_candidate_pool.h>\n#include <t9_decode_cache.h>\n#include <t9_lookup_trace.h>\n#include <tuple>\n#include <t9_sentence_scorer.h>\n#include <octagram.h>\n#include <gram_db.h>\n#include <gram_encoding.h>\n#include <utf8.h>\n#include <rime/resource.h>\n#include <rime/service.h>\n#include <t9_ngram_model.h>\n#include <t9_suppression.h>\n#include <unordered_map>\n#include <fstream>\n#include <mutex>\n#include <limits>\n#include <cstring>\n#include <cylm.h>\n#include <sstream>")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime-t9/src")
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime-octagram/src")
# CyLM decoder (cylm/cylm.cpp, also built alone as libcylm.so): used only when a schema names its files.
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/cylm")
target_sources(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/cylm/cylm.cpp")
# Source properties are per directory: set it where rime-static is defined, or main() gets linked in.
set_source_files_properties("${CMAKE_SOURCE_DIR}/cylm/cylm.cpp" TARGET_DIRECTORY rime-static PROPERTIES COMPILE_DEFINITIONS "CYLM_LIBRARY;CYLM_ORT"
  # OrtStatus results of option setters are ignored on purpose in cylm.cpp; the NDK build treats that as an error.
  COMPILE_OPTIONS "-Wno-unused-result")
# The reranker uses the onnxruntime C API; rime-static links onnxruntime already (jni CMakeLists).
target_include_directories(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/onnxruntime/include")
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
        // Optional cylm/reranker + cylm/vocab: the ONNX reranker runs inside the same keystroke.
        // Optional cylm/prefer: "P<TAB>D" rows, P moves above its homophone D after ranking (cylm_prefer).
        string lexicon, model, model2, reranker, vocab, params, prefer;
        if (config->GetString("cylm/lexicon", &lexicon) && config->GetString("cylm/model", &model) &&
            !lexicon.empty() && !model.empty()) {
          config->GetString("cylm/model2", &model2);
          config->GetString("cylm/reranker", &reranker);
          config->GetString("cylm/vocab", &vocab);
          config->GetString("cylm/params", &params);
          config->GetString("cylm/prefer", &prefer);
          auto resolve = [](const string& name) { return name.empty() ? string() : T9ResolveOnce("cylm", "", name); };
          t9_cylm_ = T9CylmOpen(resolve(lexicon), resolve(model), resolve(model2),
                                vocab.empty() ? string() : resolve(reranker), resolve(vocab), params, resolve(prefer));
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
  // The word graph the last MakeSentences used (the incremental cache or a kept copy), so the
  // sentence rescoring can make a second n-best without corrected words.
  const WordGraph* t9_sentence_graph_ = nullptr;
  WordGraph t9_own_sentence_graph_;
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
  // Syllable text -> id of a dictionary's syllabary (CyLM returns pinyin as text), built once per dictionary.
  static const std::unordered_map<string, SyllableId>& T9SyllableIds(Dictionary* dict) {
    static std::mutex mutex;
    static std::map<const void*, std::unordered_map<string, SyllableId>> tables;
    std::lock_guard<std::mutex> lock(mutex);
    auto& ids = tables[dict->primary_table().get()];
    if (ids.empty()) {
      for (SyllableId id = 0;; ++id) {
        const string syllable = dict->primary_table()->GetSyllableById(id);
        if (syllable.empty()) break;
        ids.emplace(syllable, id);
      }
    }
    return ids;
  }
  // One CyLM model per set of files for the process (memory-mapped); a failed open is remembered too.
  static std::shared_ptr<CylmModel> T9CylmOpen(const string& lexicon, const string& model, const string& model2,
                                               const string& reranker, const string& vocab, const string& params,
                                               const string& prefer) {
    static std::mutex mutex;
    static std::map<string, std::shared_ptr<CylmModel>> opened;
    std::lock_guard<std::mutex> lock(mutex);
    const string key = lexicon + '\n' + model + '\n' + model2 + '\n' + reranker + '\n' + vocab + '\n' + params +
                       '\n' + prefer;
    auto found = opened.find(key);
    if (found != opened.end()) return found->second;
    std::shared_ptr<CylmModel> handle(
        reranker.empty()
            ? cylm_open(lexicon.c_str(), model.c_str(), model2.empty() ? nullptr : model2.c_str(), params.c_str())
            : cylm_open2(lexicon.c_str(), model.c_str(), model2.empty() ? nullptr : model2.c_str(), reranker.c_str(),
                         vocab.c_str(), params.c_str()),
        [](CylmModel* m) { if (m) cylm_close(m); });
    if (!handle.get()) handle.reset();
    else if (!prefer.empty()) cylm_prefer(handle.get(), prefer.c_str());
    return opened[key] = handle;
  }
  // CyLM's best sentence takes the place of Rime's sentence (words keep their places). With its pinyin
  // (cylm_decode2) it is one dictionary entry of the translator's language, learned like a typed phrase; a
  // syllable the dictionary does not know leaves it as plain text that is not learned.
  void ApplyCylmSentence(Dictionary* dict) {
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
    const int n = cylm_decode2(session, in.c_str(), digits ? 1 : 0, context.c_str(), out.data(), static_cast<int>(out.size()));
    if (n <= 0 || !out[0]) return;
    const string record(out.data());  // text \x1f pinyin \x1f spans
    const auto unit = record.find('\x1f');
    const string text = record.substr(0, unit);
    const auto& rime = sentences_.front();
    if (text.empty() || text == rime->text()) return;
    DictEntry entry;
    entry.text = text;
    entry.weight = rime->weight();
    const Language* language = nullptr;
    if (unit != string::npos && dict) {
      const auto end = record.find('\x1f', unit + 1);
      std::istringstream pinyin(record.substr(unit + 1, end == string::npos ? string::npos : end - unit - 1));
      const auto& ids = T9SyllableIds(dict);
      string syllable;
      Code code;
      while (pinyin >> syllable) {
        const auto found = ids.find(syllable);
        if (found == ids.end()) { code.clear(); break; }
        code.push_back(found->second);
      }
      if (!code.empty() && code.size() == static_cast<size_t>(utf8::unchecked::distance(text.begin(), text.end()))) {
        entry.code = code;
        language = translator_->language();
      }
    }
    auto sentence = New<Sentence>(language);
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
      // ln P(text): summed over its splits into model words (words up to 8 characters) under the
      // bigram/trigram chain, so 被嫌弃了 and 被掀起了 compare as text whatever words Rime used; plus the
      // sentence-position rules Rime applied while making it (吧 not first, 被 not last). A small beam of
      // last-word states per position carries the chain. A single unigram-chosen split read 他也会来的 as
      // 他也|会|来|的 (-26.4) against 他也|回来|的 (-25.4); its best split 他|也会|来|的 (-20.75) still trails
      // 他也|回来的 (-20.69), but the text's other readings add up (-20.56 against -20.67).
      auto score = [&](const Sentence& sentence) {
        const string& text = sentence.text();
        vector<size_t> at{0};
        for (size_t i = 0; i < text.size();) {
          const unsigned char c = static_cast<unsigned char>(text[i]);
          i += c < 0x80 ? 1 : c < 0xE0 ? 2 : c < 0xF0 ? 3 : 4;
          at.push_back(std::min(i, text.size()));
        }
        const size_t n = at.size() - 1;
        struct State { double value; int32_t prev2, prev1; bool started; };
        constexpr size_t kStates = 4;
        auto log_add = [](double a, double b) {
          if (!std::isfinite(a)) return b;
          if (!std::isfinite(b)) return a;
          return std::max(a, b) + std::log1p(std::exp(-std::fabs(a - b)));
        };
        const int32_t root = t9_ngram_sentence_start_ ? t9_ngram_->Bos() : ::T9NgramModel::kUnseen;
        vector<vector<State>> states(n + 1);
        states[0].push_back({0, root, root, false});
        for (size_t end = 1; end <= n; ++end) {
          auto& here = states[end];
          for (size_t start = end > 8 ? end - 8 : 0; start < end; ++start) {
            if (states[start].empty()) continue;
            const int32_t id = word_id(text.substr(at[start], at[end] - at[start]));
            if (id == ::T9NgramModel::kUnseen && end - start > 1) continue;
            for (const State& from : states[start]) {
              const double value = from.value + (from.started
                  ? t9_ngram_->LogProb3(from.prev2, from.prev1, id) : t9_ngram_->Start(id, t9_ngram_sentence_start_));
              const State next{value, from.started ? from.prev1 : root, id, true};
              // One state per last word (it decides the next transition), the best kStates kept.
              auto same = std::find_if(here.begin(), here.end(), [&](const State& s) { return s.prev1 == id; });
              if (same != here.end()) {
                const double sum = log_add(same->value, value);
                if (value > same->value) *same = next;
                same->value = sum;
                continue;
              }
              if (here.size() < kStates) { here.push_back(next); continue; }
              auto worst = std::min_element(here.begin(), here.end(),
                                            [](const State& a, const State& b) { return a.value < b.value; });
              if (value > worst->value) *worst = next;
            }
          }
        }
        double total = -std::numeric_limits<double>::infinity();
        for (const State& s : states[n]) total = log_add(total, s.value);
        const auto& words = sentence.components();
        if (!words.empty() && poet_) {
          total += poet_->PositionAdjust(words.front().text, true, words.size() == 1);
          if (words.size() > 1) total += poet_->PositionAdjust(words.back().text, false, true);
        }
        return total;
      };
      // A typo correction never wins on the word model alone. Corrected sentences would also take
      // n-best places from typed readings (qunawanle lost 你去哪玩了 to 你去哪完了 once swapped letters
      // were corrections), so the choice is among sentences made from the same graph without
      // corrected words: the typed n-best, made again only when corrections took places in it.
      deque<an<Sentence>> pool;
      for (const auto& sentence : sentences_)
        if (!corrected(sentence)) pool.push_back(sentence);
      if (pool.size() < sentences_.size() && t9_sentence_graph_ && poet_) {
        // Spans holding no corrected syllable keep all their words without looking at each one.
        vector<std::pair<size_t, size_t>> corrected_edges;
        for (const auto& from : syllabifier_->syllable_graph().edges)
          for (const auto& to : from.second)
            for (const auto& spelling : to.second)
              if (spelling.second.is_correction) { corrected_edges.emplace_back(from.first, to.first); break; }
        WordGraph typed;
        for (const auto& from : *t9_sentence_graph_)
          for (const auto& to : from.second) {
            const bool clean = std::none_of(corrected_edges.begin(), corrected_edges.end(), [&](const auto& edge) {
              return edge.first >= from.first && edge.second <= to.first;
            });
            auto& bucket = typed[from.first][to.first];
            if (clean) { bucket = to.second; continue; }
            for (const auto& entry : to.second)
              if (!syllabifier_->IsCorrection(entry->code, to.first, from.first)) bucket.push_back(entry);
          }
        auto remade = poet_->MakeSentences(typed, syllabifier_->syllable_graph().interpreted_length,
                                           translator_->GetPrecedingText(start_), max_sentences_,
                                           sentence_cutoff_threshold_);
        for (auto& sentence : remade) {
          sentence->Offset(start_);
          sentence->set_syllabifier(syllabifier_);
        }
        if (exact_sentence_ && !corrected(exact_sentence_)) remade.push_front(exact_sentence_);
        if (!remade.empty()) pool = std::move(remade);
      }
      double chosen_score = -std::numeric_limits<double>::infinity();
      for (const auto& sentence : pool) {
        const double value = score(*sentence);
        if (value > chosen_score) { chosen_score = value; chosen = sentence; }
      }
    }
    sentences_.clear();
    sentences_.push_back(chosen);
    if (rime_best && rime_best != chosen && corrected(rime_best)) sentences_.push_back(rime_best);
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
