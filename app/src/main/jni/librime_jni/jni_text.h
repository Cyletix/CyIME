#pragma once
#include <jni.h>
#include <utf8.h>
#include <algorithm>
#include <iterator>
#include <string>
#include <vector>

// Rime uses standard UTF-8. JNI's *UTF functions use modified UTF-8, which
// encodes a supplementary character as two surrogate halves instead of four bytes.
inline jstring CandidateString(JNIEnv* env, const std::string& text) {
    if (std::none_of(text.begin(), text.end(), [](unsigned char c) { return !c || c >= 0xf0; }))
        return env->NewStringUTF(text.c_str());
    std::vector<jchar> chars;
    chars.reserve(text.size());
    auto it = text.begin();
    while (it != text.end()) {
        const auto start = it;
        uint32_t cp;
        try { cp = utf8::next(it, text.end()); }
        catch (const utf8::exception&) { it = start + 1; cp = 0xfffd; }
        if (cp > 0xffff) {
            cp -= 0x10000;
            chars.push_back(static_cast<jchar>(0xd800 + (cp >> 10)));
            cp = 0xdc00 + (cp & 0x3ff);
        }
        chars.push_back(static_cast<jchar>(cp));
    }
    return env->NewString(chars.data(), static_cast<jsize>(chars.size()));
}

inline std::string CandidateUtf8(JNIEnv* env, jstring text) {
    if (!text) return {};
    auto size = env->GetStringLength(text);
    auto chars = env->GetStringChars(text, nullptr);
    if (!chars) return {};
    std::string value;
    for (jsize i = 0; i < size; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xd800 && cp <= 0xdfff) {
            if (cp <= 0xdbff && i + 1 < size && chars[i + 1] >= 0xdc00 && chars[i + 1] <= 0xdfff)
                cp = 0x10000 + ((cp - 0xd800) << 10) + (chars[++i] - 0xdc00);
            else cp = 0xfffd;
        }
        utf8::append(cp, std::back_inserter(value));
    }
    env->ReleaseStringChars(text, chars);
    return value;
}
