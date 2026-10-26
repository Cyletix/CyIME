// CyIME settings and shared resources of one script translation: the nine-key joint decoder,
// the 26-key sentence rescoring and CyLM. ScriptTranslation derives from this struct, so the
// decoder code reads these members by their own names (cmake/T9PhraseBeforeSentence.cmake).
#ifndef CYIME_T9_SCRIPT_OPTIONS_H_
#define CYIME_T9_SCRIPT_OPTIONS_H_

#include <cstddef>
#include <memory>
#include <unordered_map>
#include <utility>

#include <cylm.h>
#include <rime/common.h>
#include <rime/dict/vocabulary.h>

class T9NgramModel;

namespace rime {

class Config;
class Context;
class Dictionary;
class Grammar;
class GramDb;

struct T9ScriptOptions {
  T9ScriptOptions();
  ~T9ScriptOptions();
  // Reads t9/*, cylm/* and the app's context properties; called once per translation.
  void LoadT9Options(Config* config, Context* context);

  bool t9_joint_ = false;
  bool t9_sentence_rescore_ = false;
  using T9Readings = std::unordered_map<string, vector<std::pair<string, double>>>;
  std::shared_ptr<const T9Readings> t9_readings_;
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
  std::shared_ptr<CylmModel> t9_cylm_;

  // Resource paths resolved once per type and name. Resolving stats files in the user and shared
  // data directories, and the translation reading this config is rebuilt on every key. A model
  // installed later already needs a restart (T9NgramModel::Open remembers missing files too).
  static string T9ResolveOnce(const char* type_name, const char* suffix, const string& name);
  // Loaded once per file and kept: a translation (and this member) is rebuilt on every key, so a
  // weak cache reread the file per key. Lines are text<TAB>spelling<TAB>ln share.
  static std::shared_ptr<const T9Readings> T9LoadReadings(const string& path);
  // Syllable text -> id of a dictionary's syllabary (CyLM returns pinyin as text), built once per dictionary.
  static const std::unordered_map<string, SyllableId>& T9SyllableIds(Dictionary* dict);
  // One CyLM model per set of files for the process (memory-mapped); a failed open is remembered too.
  static std::shared_ptr<CylmModel> T9CylmOpen(const string& lexicon, const string& model, const string& model2,
                                               const string& reranker, const string& vocab, const string& params,
                                               const string& prefer, const string& adapt);
};

}  // namespace rime

#endif  // CYIME_T9_SCRIPT_OPTIONS_H_
