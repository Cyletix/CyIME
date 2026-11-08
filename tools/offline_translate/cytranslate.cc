// CyIME offline translation: a thin JNI layer over slimt (Mozilla Firefox Translations models).
//
// One Engine = one direction (e.g. zh->en): the model, the source and target SentencePiece vocabularies
// and the lexical shortlist, all memory-mapped by slimt. Translation is synchronous and single-threaded
// (slimt::Blocking): the keyboard calls it from one background thread, one request at a time.
//
// Built two ways:
//   * Android: libcytranslate.so with the JNI entry points below (build-android.sh).
//   * Host:    cytranslate-cli for checking output and latency on a PC (build-host.sh), CYTRANSLATE_NO_JNI.
//
// Layer detection follows DavidVentura/slimt-sys (MIT), bindings/slimt_wrapper.cpp.

#include <cstring>
#include <exception>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

#include "slimt/Frontend.hh"
#include "slimt/Io.hh"
#include "slimt/Model.hh"
#include "slimt/Response.hh"

#include "cytranslate.h"

namespace cytranslate {
namespace {

// Largest "encoder_lN_" / "decoder_lN_" index in the model file: the Firefox models differ in
// decoder depth (tiny/base 2, base-memory 4), which slimt does not read from the file itself.
void detect_layers(const std::string &model_path, size_t &encoder_layers, size_t &decoder_layers) {
  encoder_layers = 0;
  decoder_layers = 0;
  slimt::io::MmapFile mmap(model_path);
  auto items = slimt::io::load_items(mmap.data());
  for (const auto &item : items) {
    const std::string &name = item.name;
    auto check = [&](const char *prefix, size_t &out) {
      const size_t length = std::strlen(prefix);
      if (name.compare(0, length, prefix) != 0) return;
      size_t index = 0;
      size_t pos = length;
      while (pos < name.size() && name[pos] >= '0' && name[pos] <= '9') {
        index = index * 10 + static_cast<size_t>(name[pos] - '0');
        ++pos;
      }
      if (pos != length && index > out) out = index;
    };
    check("encoder_l", encoder_layers);
    check("decoder_l", decoder_layers);
  }
}

}  // namespace

struct Engine {
  std::shared_ptr<slimt::Model> model;
  std::unique_ptr<slimt::Blocking> service;
  std::mutex mutex;
};

Engine *create(const std::string &model_path, const std::string &source_vocabulary,
               const std::string &target_vocabulary, const std::string &shortlist, std::string &error) {
  try {
    slimt::Package<std::string> package;
    package.model = model_path;
    package.vocabulary = source_vocabulary;
    // Same file on both sides = one shared vocabulary (zh-en); slimt aliases the target side when empty.
    package.target_vocabulary = target_vocabulary == source_vocabulary ? std::string() : target_vocabulary;
    package.shortlist = shortlist;
    size_t encoder_layers = 0;
    size_t decoder_layers = 0;
    detect_layers(model_path, encoder_layers, decoder_layers);
    slimt::Model::Config config = slimt::preset::tiny();
    if (encoder_layers > 0) config.encoder_layers = encoder_layers;
    if (decoder_layers > 0) config.decoder_layers = decoder_layers;
    auto engine = std::make_unique<Engine>();
    engine->model = std::make_shared<slimt::Model>(config, package);
    slimt::Config service_config;
    service_config.cache_size = 256;  // retyping the same sentence in the translate bar is free
    engine->service = std::make_unique<slimt::Blocking>(service_config);
    return engine.release();
  } catch (const std::exception &e) {
    error = e.what();
  } catch (...) {
    error = "unknown error";
  }
  return nullptr;
}

bool translate(Engine *engine, const std::string &text, std::string &out, std::string &error) {
  if (engine == nullptr) {
    error = "engine closed";
    return false;
  }
  try {
    std::lock_guard<std::mutex> lock(engine->mutex);
    slimt::Options options;
    std::vector<slimt::Response> responses = engine->service->translate(engine->model, {text}, options);
    out = responses.empty() ? std::string() : responses.front().target.text;
    return true;
  } catch (const std::exception &e) {
    error = e.what();
  } catch (...) {
    error = "unknown error";
  }
  return false;
}

void destroy(Engine *engine) { delete engine; }

}  // namespace cytranslate

#ifndef CYTRANSLATE_NO_JNI
#include <jni.h>

namespace {

std::string utf8(JNIEnv *env, jstring value) {
  if (value == nullptr) return {};
  // Java strings go through UTF-8 bytes, not GetStringUTFChars (modified UTF-8 breaks emoji).
  jclass string_class = env->FindClass("java/lang/String");
  jmethodID get_bytes = env->GetMethodID(string_class, "getBytes", "(Ljava/lang/String;)[B");
  jstring charset = env->NewStringUTF("UTF-8");
  auto bytes = static_cast<jbyteArray>(env->CallObjectMethod(value, get_bytes, charset));
  env->DeleteLocalRef(charset);
  env->DeleteLocalRef(string_class);
  if (bytes == nullptr) return {};
  jsize length = env->GetArrayLength(bytes);
  std::string out(static_cast<size_t>(length), '\0');
  env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(out.data()));
  env->DeleteLocalRef(bytes);
  return out;
}

jstring java_string(JNIEnv *env, const std::string &value) {
  jbyteArray bytes = env->NewByteArray(static_cast<jsize>(value.size()));
  env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(value.size()), reinterpret_cast<const jbyte *>(value.data()));
  jclass string_class = env->FindClass("java/lang/String");
  jmethodID init = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
  jstring charset = env->NewStringUTF("UTF-8");
  auto out = static_cast<jstring>(env->NewObject(string_class, init, bytes, charset));
  env->DeleteLocalRef(charset);
  env->DeleteLocalRef(bytes);
  env->DeleteLocalRef(string_class);
  return out;
}

void throw_error(JNIEnv *env, const std::string &message) {
  jclass error_class = env->FindClass("java/lang/IllegalStateException");
  if (error_class != nullptr) env->ThrowNew(error_class, message.c_str());
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_kingzcheung_xime_translate_OfflineTranslatorNative_nativeCreate(
    JNIEnv *env, jclass, jstring model, jstring source_vocabulary, jstring target_vocabulary, jstring shortlist) {
  std::string error;
  cytranslate::Engine *engine = cytranslate::create(utf8(env, model), utf8(env, source_vocabulary),
                                                    utf8(env, target_vocabulary), utf8(env, shortlist), error);
  if (engine == nullptr) {
    throw_error(env, error);
    return 0;
  }
  return reinterpret_cast<jlong>(engine);
}

JNIEXPORT jstring JNICALL Java_com_kingzcheung_xime_translate_OfflineTranslatorNative_nativeTranslate(
    JNIEnv *env, jclass, jlong handle, jstring text) {
  std::string out;
  std::string error;
  if (!cytranslate::translate(reinterpret_cast<cytranslate::Engine *>(handle), utf8(env, text), out, error)) {
    throw_error(env, error);
    return nullptr;
  }
  return java_string(env, out);
}

JNIEXPORT void JNICALL Java_com_kingzcheung_xime_translate_OfflineTranslatorNative_nativeDestroy(JNIEnv *, jclass,
                                                                                               jlong handle) {
  cytranslate::destroy(reinterpret_cast<cytranslate::Engine *>(handle));
}

}  // extern "C"
#endif  // CYTRANSLATE_NO_JNI
