#include <rime/common.h>
#include <rime/registry.h>
#include <rime_api.h>
#include <vector>
#include "t9_processor.h"
#include "t9_filter.h"
#include "t9_date_translator.h"
#include "t9_sentence_scorer.h"
#include "t9_english_translator.h"
#include "datetime_filter.h"

using namespace rime;

namespace {

static void rime_t9_initialize() {
    LOG(INFO) << "registering components from module 't9'.";
    Registry& r = Registry::instance();
    r.Register("t9_processor", new Component<T9Processor>);
    r.Register("t9_filter", new Component<T9Filter>);
    r.Register("t9_date_translator", new Component<T9DateTranslator>);
    r.Register("t9_english_translator", new Component<T9EnglishTranslator>);
    // Current time/date after 时间/日期/星期 on every Chinese pinyin layout (datetime_filter.h).
    r.Register("cyime_datetime", new Component<DatetimeFilter>);
}

static void rime_t9_finalize() {
    T9ReleaseSentenceScorer();
}

}  // namespace

// P7（2026-07-19）：T9 方案 schema 注入 patch 定义。
//
// 组件注册名与注入配置名在此单一地点维护，
// Kotlin 端通过 JNI 查询，不再硬编码。
// 格式："search_pattern|patch_key|patch_value"
std::vector<std::string> rime::GetT9SchemaPatches() {
    return {
        "t9_processor|engine/processors/@before 0|t9_processor",
        "t9_filter|engine/filters/@before 0|t9_filter",
        "t9_date_translator|engine/translators/@before 0|t9_date_translator",
        "isDisplayOriginalPreedit|t9/isDisplayOriginalPreedit|false",
    };
}

RIME_REGISTER_MODULE(t9)
