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
    if (ticket.schema) ticket.schema->config()->GetBool(name_space_ + "/digit_suffixes", &digit_suffixes_);
  }
  an<Translation> Query(const string& input, const Segment& segment) override;

 private:
  // "word" + remaining digits (steam42); off by default, they crowd out Chinese words.
  bool digit_suffixes_ = false;
};
}  // namespace rime
#endif
#endif
