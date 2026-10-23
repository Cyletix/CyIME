#ifndef CYIME_T9_ENGLISH_TRANSLATOR_H_
#define CYIME_T9_ENGLISH_TRANSLATOR_H_
#ifndef T9_ALGO_ONLY_BUILD
#include <rime/gear/table_translator.h>
#include <rime/gear/poet.h>
#include <rime/gear/unity_table_encoder.h>
#include <rime/schema.h>
#include <rime/ticket.h>
namespace rime {
class T9EnglishTranslator : public TableTranslator {
 public:
  explicit T9EnglishTranslator(const Ticket& ticket) : TableTranslator(ticket) {
    if (ticket.schema) {
      ticket.schema->config()->GetBool(name_space_ + "/digit_suffixes", &digit_suffixes_);
      ticket.schema->config()->GetInt(name_space_ + "/completion_limit", &completion_limit_);
      ticket.schema->config()->GetString(name_space_ + "/completion_keys", &completion_keys_);
    }
  }
  an<Translation> Query(const string& input, const Segment& segment) override;

 private:
  // "word" + remaining digits (steam42); off by default, they crowd out Chinese words.
  bool digit_suffixes_ = false;
  // Words that start with the typed keys (termin… for terminal), most common first; 0 = whole words only.
  int completion_limit_ = 0;
  // Keys that may start a completion: nine-key digits, or the 14-key keys (QETUOADGJZCBlm).
  string completion_keys_ = "23456789";
};
}  // namespace rime
#endif
#endif
