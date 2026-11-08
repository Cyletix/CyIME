// T9ConvertPreedit 单元测试
//
// 对应 Kotlin T9PreeditConverter.kt 的 convertT9PreeditToPinyin()
// 覆盖场景：常规多音节、末尾简拼、单段多音节、中文混合、空 comment
// 一段按键跨几个音节时逐个音节显示、用空格分开（2026-11-08 起；以前连写）
#include <gtest/gtest.h>

#include "t9_filter.h"            // T9ConvertPreedit / T9ConvertCandidatePreedit
#include "t9_pinyin_map.h"        // NormalizePinyinComment

using rime::T9ConvertPreedit;
using rime::T9ConvertCandidatePreedit;

// ── 常规场景 ──

TEST(T9PreeditConverterTest, RegularMultiSyllable) {
    // "54482" + "ji gua" → "ji gua"（音节之间有分隔，2026-11-08 以前连写成 jigua）
    EXPECT_EQ(T9ConvertPreedit("54482", "ji gua"), "ji gua");
}

TEST(T9PreeditConverterTest, RegularThreeSyllable) {
    // "5482" + "ji hua" → "j hua"（四个键：j 是打短的 ji）
    EXPECT_EQ(T9ConvertPreedit("5482", "ji hua"), "j hua");
}

TEST(T9PreeditConverterTest, SeparatorInPreedit) {
    // "ji'5" + "ji kan" → "ji k"（末尾单数字 5→k）
    EXPECT_EQ(T9ConvertPreedit("ji'5", "ji kan"), "ji k");
}

TEST(T9PreeditConverterTest, MultiDigitAfterSeparator) {
    // "ji'43" + "ji kan" → "ji kan"（末尾多数字→完整拼音）
    EXPECT_EQ(T9ConvertPreedit("ji'43", "ji kan"), "ji kan");
}

TEST(T9PreeditConverterTest, SpaceSeparatorInPreedit) {
    // "ji 5" + "ji kan" → "ji k"
    EXPECT_EQ(T9ConvertPreedit("ji 5", "ji kan"), "ji k");
}

// ── 末尾单数字简拼 ──

TEST(T9PreeditConverterTest, LastSingleDigitJianpin) {
    // "5" + "le" → "l"
    EXPECT_EQ(T9ConvertPreedit("5", "le"), "l");
}

TEST(T9PreeditConverterTest, LastSingleDigitZhChSh) {
    // "9" + "zhong" → "zh"
    EXPECT_EQ(T9ConvertPreedit("9", "zhong"), "z");
    // "2" + "cheng" → "ch"
    EXPECT_EQ(T9ConvertPreedit("2", "cheng"), "c");
    // "7" + "shen" → "sh"
    EXPECT_EQ(T9ConvertPreedit("7", "shen"), "s");
}

TEST(T9PreeditConverterTest, LastSingleDigitAfterSeparator) {
    // "ji'4" + "ji guo" → "ji g"
    EXPECT_EQ(T9ConvertPreedit("ji'4", "ji guo"), "ji g");
}

// ── 单段多音节 ──

TEST(T9PreeditConverterTest, SingleSegmentMultiplePinyins) {
    // "564" + "le ming" → "l mi"（三个键各对一个字母）
    EXPECT_EQ(T9ConvertPreedit("564", "le ming"), "l mi");
}

// ── 中文混合 ──

TEST(T9PreeditConverterTest, ChineseMixedPreedit) {
    // "公民7" + "gong min" → "公民g"（末尾单数字 7→g）
    EXPECT_EQ(T9ConvertPreedit("\xe5\x85\xac\xe6\xb0\x91" "7", "gong min"),
              "\xe5\x85\xac\xe6\xb0\x91" "g");
}

TEST(T9PreeditConverterTest, ChineseAtEnd) {
    // "7公民" + "shen" → "shen公民"（7不是末尾段，不触发简拼）
    EXPECT_EQ(T9ConvertPreedit("7\xe5\x85\xac\xe6\xb0\x91", "shen"),
              "shen\xe5\x85\xac\xe6\xb0\x91");
}

// ── 边界情况 ──

TEST(T9PreeditConverterTest, EmptyPreedit) {
    EXPECT_EQ(T9ConvertPreedit("", "ji gua"), "");
}

TEST(T9PreeditConverterTest, EmptyComment) {
    EXPECT_EQ(T9ConvertPreedit("54482", ""), "54482");
}

// ── 英文九键（无拼音注释）──
// 英文 table_translator 方案（如 melt_eng_t9）候选无拼音注释：
// 输入 8378 匹配 "test"，preedit 应显示候选词文本而非数字。

TEST(T9PreeditConverterTest, EnglishNoCommentUsesCandidateText) {
    EXPECT_EQ(T9ConvertCandidatePreedit("8378", "", "test"), "test");
    EXPECT_EQ(T9ConvertCandidatePreedit("8378", "", "vest"), "vest");
}

TEST(T9PreeditConverterTest, EnglishUnitySuffixCommentUsesCandidateText) {
    // '~' 为 librime 统一编码后缀标记（melt_eng '~s'/'~ed'），非拼音 → 显示候选词
    EXPECT_EQ(T9ConvertCandidatePreedit("8378", "~s", "tests"), "tests");
    EXPECT_EQ(T9ConvertCandidatePreedit("8378", "~ed", "tested"), "tested");
}

TEST(T9PreeditConverterTest, ChinesePinyinCommentStillConverts) {
    // 中文九键候选带拼音注释 → 仍走数字→拼音转换，行为不变
    EXPECT_EQ(T9ConvertCandidatePreedit("54482", "ji gua", "计划"), "ji gua");
    EXPECT_EQ(T9ConvertCandidatePreedit("5", "le", "了"), "l");
}

// 2026-11-08「九键拼音分词没显示分词符，最后一个没有分词显示」：按键原样的 preedit（按键查到的词、选过左侧拼音后的
// 剩余部分）里一段数字跨几个音节时，以前只取一个音节：整段连写成 nihaoma，ni'42662 只剩 ni hao，最后一个音节没了。
TEST(T9PreeditConverterTest, KeyRunSpanningSyllablesShowsEverySyllable) {
    EXPECT_EQ(T9ConvertPreedit("6442662", "ni hao ma"), "ni hao ma");
    EXPECT_EQ(T9ConvertPreedit("ni'42662", "ni hao ma"), "ni hao ma");
    EXPECT_EQ(T9ConvertPreedit("ni'hao'6262", "ni hao ma ma"), "ni hao ma ma");
    EXPECT_EQ(T9ConvertPreedit("64426 62", "ni hao ma"), "ni hao ma");
    // 打短的音节照按的键显示：963 我们的 = w m d，9366 怎么 = zen m。
    EXPECT_EQ(T9ConvertPreedit("963", "wo men de"), "w m d");
    EXPECT_EQ(T9ConvertPreedit("9366", "zen me"), "zen m");
    EXPECT_EQ(T9ConvertPreedit("94324364", "zhe cheng"), "zhe cheng");
}

TEST(T9PreeditConverterTest, KeyRunRulesThatAlreadyWorkStayTheSame) {
    EXPECT_EQ(T9ConvertPreedit("644 26", "ni hao"), "ni hao");
    EXPECT_EQ(T9ConvertPreedit("644 26 62", "ni hao ma"), "ni hao ma");
    EXPECT_EQ(T9ConvertPreedit("ni'hao'6", "ni hao ma"), "ni hao m");
    // 两个键对两个打短的音节。按键和读音对不上时照旧（整段一个时连写）。
    EXPECT_EQ(T9ConvertPreedit("64", "ni hao"), "n h");
    EXPECT_EQ(T9ConvertPreedit("22", "ni hao"), "nihao");
}

// 英文表里的词注释是按键本身（fuck: 3825），不是拼音：排第一时编码显示这个词，不再停在上一键的拼音。
TEST(T9PreeditConverterTest, EnglishKeysCommentUsesCandidateText) {
    EXPECT_EQ(T9ConvertCandidatePreedit("3825", "3825", "fuck"), "fuck");
    EXPECT_EQ(T9ConvertCandidatePreedit("29426", "29426", "B站"), "B站");
}

TEST(T9PreeditConverterTest, NoDigits) {
    // preedit 无数字 → 原样返回
    EXPECT_EQ(T9ConvertPreedit("jigua", "ji gua"), "jigua");
}

TEST(T9PreeditConverterTest, CommentWhitespaceOnly) {
    EXPECT_EQ(T9ConvertPreedit("54482", "   "), "54482");
}

// ── 大小写处理 ──

TEST(T9PreeditConverterTest, UppercaseComment) {
    // comment 中的拼音大写时，转换结果应小写
    EXPECT_EQ(T9ConvertPreedit("54482", "JI GUA"), "ji gua");
}

TEST(T9PreeditConverterTest, MixedCaseComment) {
    EXPECT_EQ(T9ConvertPreedit("54482", "Ji Gua"), "ji gua");
}

// ── 带声调 comment（带声调方案场景）──
// 声调元音通过 NormalizePinyinComment 归一化为纯 ASCII 字母，
// 不依赖逐字节 ASCII 过滤（避免 jī→j、huà→hu 的 bug）。

TEST(T9PreeditConverterTest, TonedCommentRegular) {
    // "54482" + "jī huà" → "ji hua"（声调字符归一化后与无声调一致）
    EXPECT_EQ(T9ConvertPreedit("54482", "jī huà"), "ji hua");
}

TEST(T9PreeditConverterTest, TonedCommentThreeSyllable) {
    // "5482" + "jī huà" → "j hua"
    EXPECT_EQ(T9ConvertPreedit("5482", "jī huà"), "j hua");
}

TEST(T9PreeditConverterTest, TonedCommentAllTones) {
    // 覆盖全部四种声调：āáǎà ēéěè īíǐì ōóǒò ūúǔù ǖǘǚǜ
    EXPECT_EQ(T9ConvertPreedit("54482", "ī í ǐ ì"), "iiii");
    EXPECT_EQ(T9ConvertPreedit("54482", "ā á ǎ à"), "aaaa");
    EXPECT_EQ(T9ConvertPreedit("54482", "ē é ě è"), "eeee");
    EXPECT_EQ(T9ConvertPreedit("54482", "ō ó ǒ ò"), "oooo");
    EXPECT_EQ(T9ConvertPreedit("54482", "ū ú ǔ ù"), "uuuu");
    // ü 映射为 v（与 speller xlit 一致）
    EXPECT_EQ(T9ConvertPreedit("54482", "ǖ ǘ ǚ ǜ"), "vvvv");
    EXPECT_EQ(T9ConvertPreedit("54482", "ü"), "v");
}

TEST(T9PreeditConverterTest, TonedCommentNandM) {
    // ńňǹ→n, ḿ→m
    EXPECT_EQ(T9ConvertPreedit("54482", "ń ň ǹ"), "nnn");
    EXPECT_EQ(T9ConvertPreedit("54482", "ḿ"), "m");
}

TEST(T9PreeditConverterTest, TonedCommentWeirdDelimiter) {
    // 带声调 comment 混合全角括号 → 括号被过滤，声调被归一化
    EXPECT_EQ(T9ConvertPreedit("54482", "〔jī〕〔huà〕"), "jihua");
}

TEST(T9PreeditConverterTest, TonedCommentSingleDigitJianpin) {
    // "5" + "jī" → "j"（单数字段取首字母，不依赖完整拼音）
    EXPECT_EQ(T9ConvertPreedit("5", "jī"), "j");
}

TEST(T9PreeditConverterTest, TonedCommentSingleSegmentMulti) {
    // "54482" + "jī huà" → "ji hua"（单段多音节，按音节分开）
    // 验证 NormalizePinyinComment 替代逐字节过滤后单段多音节仍能正确拼出
    EXPECT_EQ(T9ConvertPreedit("54482", "jī huà"), "ji hua");
}

TEST(T9PreeditConverterTest, TonedCommentMixedCase) {
    // 大写声调 comment → 归一化后小写
    EXPECT_EQ(T9ConvertPreedit("54482", "JĪ HUÀ"), "ji hua");
}

TEST(T9PreeditConverterTest, TonedCommentPreserveExisting) {
    // 有声调 comment 已有的无声调场景不受影响（回归）
    EXPECT_EQ(T9ConvertPreedit("54482", "ji gua"), "ji gua");
    EXPECT_EQ(T9ConvertPreedit("54482", "ji hua"), "ji hua");
    EXPECT_EQ(T9ConvertPreedit("5", "le"), "l");
    EXPECT_EQ(T9ConvertPreedit("9", "zhong"), "z");
}

// ── NormalizePinyinComment（调频声调保真的基础）──
// 归一化用于：comment 匹配（容忍带调/无调差异）与带声调变体选择。
// 核心不变量：带调与无声调归一化结果一致。

TEST(NormalizePinyinCommentTest, TonedAndTonelessEquivalent) {
    // 核心不变量：带声调与无声调的归一化结果一致（调频匹配的前提）
    EXPECT_EQ(rime::NormalizePinyinComment("jì huà"),
              rime::NormalizePinyinComment("ji hua"));
    EXPECT_EQ(rime::NormalizePinyinComment("jī"),
              rime::NormalizePinyinComment("ji"));
    EXPECT_EQ(rime::NormalizePinyinComment("huà"),
              rime::NormalizePinyinComment("hua"));
}

TEST(NormalizePinyinCommentTest, Basic) {
    // 保留空格（音节分隔符），声调归一化为无声调 ASCII
    EXPECT_EQ(rime::NormalizePinyinComment("jì huà"), "ji hua");
    EXPECT_EQ(rime::NormalizePinyinComment("ji hua"), "ji hua");
    EXPECT_EQ(rime::NormalizePinyinComment("jī guā"), "ji gua");
}

TEST(NormalizePinyinCommentTest, NeutralTonePreserved) {
    // 轻声音节（簸箕 ji / 笑话 hua）全 ASCII，归一化原样保留（含空格）
    EXPECT_EQ(rime::NormalizePinyinComment("bò ji"), "bo ji");
    EXPECT_EQ(rime::NormalizePinyinComment("xiào hua"), "xiao hua");
    EXPECT_EQ(rime::NormalizePinyinComment("ji"), "ji");
    EXPECT_EQ(rime::NormalizePinyinComment("hua"), "hua");
}

TEST(NormalizePinyinCommentTest, NonLetterCharsDropped) {
    // 非字母字符（分隔符/括号/数字）被丢弃，与无声调拼音对齐
    EXPECT_EQ(rime::NormalizePinyinComment("〔jì〕〔huà〕"), "jihua");
    EXPECT_EQ(rime::NormalizePinyinComment("nǐ3"), "ni");
}

TEST(NormalizePinyinCommentTest, UppercaseNormalized) {
    EXPECT_EQ(rime::NormalizePinyinComment("JĪ HUÀ"), "ji hua");
    EXPECT_EQ(rime::NormalizePinyinComment("JI HUA"), "ji hua");
}

TEST(T9PreeditConverterTest, DoesNotInventTrailingLetters) {
    EXPECT_EQ(T9ConvertPreedit("74", "shi"), "sh");
    EXPECT_EQ(T9ConvertPreedit("746", "shou"), "sho");
    EXPECT_EQ(T9ConvertPreedit("7468", "shou"), "shou");
}

// ── 双拼九键（t9/double_pinyin）：两键一字 ──

TEST(T9DoublePinyinPreeditTest, TwoKeysPerSyllable) {
    // 小鹤 zhong guo = vs go → 87 46
    EXPECT_EQ(rime::T9ConvertDoublePinyinPreedit("87 46", "zhong guo"), "zhong guo");
    EXPECT_EQ(rime::T9ConvertDoublePinyinPreedit("87'46", "zhōng guó"), "zhong guo");
    EXPECT_EQ(rime::T9ConvertDoublePinyinPreedit("8746", "zhong guo"), "zhongguo");
}

TEST(T9DoublePinyinPreeditTest, SingleKeyShowsInitial) {
    EXPECT_EQ(rime::T9ConvertDoublePinyinPreedit("87 4", "zhong guo"), "zhong g");
    EXPECT_EQ(rime::T9ConvertDoublePinyinPreedit("8", "zhong"), "zh");
    EXPECT_EQ(rime::T9ConvertDoublePinyinPreedit("2", "an"), "a");
}

TEST(T9DoublePinyinPreeditTest, KeepsCommittedTextAndUncoveredKeys) {
    EXPECT_EQ(rime::T9ConvertDoublePinyinPreedit("中46", "guo"), "中guo");
    EXPECT_EQ(rime::T9ConvertDoublePinyinPreedit("874656", "zhong guo"), "zhongguo56");
    EXPECT_EQ(rime::T9ConvertDoublePinyinPreedit("8378", ""), "8378");
    EXPECT_EQ(rime::T9ConvertDoublePinyinPreedit("8378", "~s"), "8378");
}
