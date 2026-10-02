#ifndef CYIME_T9_ENGLISH_TRANSLATOR_H_
#define CYIME_T9_ENGLISH_TRANSLATOR_H_
#ifndef T9_ALGO_ONLY_BUILD
#include <rime/gear/table_translator.h>
#include <rime/gear/poet.h>
#include <rime/gear/unity_table_encoder.h>
namespace rime {
class T9EnglishTranslator : public TableTranslator {
 public:
  explicit T9EnglishTranslator(const Ticket& ticket) : TableTranslator(ticket) {}
  an<Translation> Query(const string& input, const Segment& segment) override;
};
}  // namespace rime
#endif
#endif
