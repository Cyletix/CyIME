// Host replay only: linked into the host librime_jni.so by host_tail.cmake and
// never part of the Android build. Reads the per-key work counters declared in
// librime-t9/src/t9_lookup_trace.h and seeds a preset user dictionary snapshot.
#include <jni.h>
#include <rime/common.h>
#include <rime/deployer.h>
#include <rime/dict/db.h>
#include <rime/dict/user_db.h>
#include <rime/lever/user_dict_manager.h>
#include <rime/service.h>
#include <t9_lookup_trace.h>
#include <string>

#if !defined(CYIME_T9_WORK_COUNTERS)
#error "The host replay adapter requires -DCYIME_T9_WORK_COUNTERS=ON"
#endif

namespace {
const char* const kWorkCounterNames[] = {
#define CYIME_T9_WORK_NAME(name) #name,
    CYIME_T9_WORK_COUNTER_LIST(CYIME_T9_WORK_NAME)
#undef CYIME_T9_WORK_NAME
};
static_assert(sizeof(kWorkCounterNames) / sizeof(kWorkCounterNames[0]) == rime::kT9WorkCounterCount,
              "work counter names");

std::string Utf8(JNIEnv* env, jstring value) {
  if (!value) return {};
  const char* chars = env->GetStringUTFChars(value, nullptr);
  if (!chars) return {};
  std::string result(chars);
  env->ReleaseStringUTFChars(value, chars);
  return result;
}
}  // namespace

extern "C" {

JNIEXPORT jobjectArray JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeHostWorkCounterNames(JNIEnv* env, jobject) {
  jclass string_class = env->FindClass("java/lang/String");
  jobjectArray result = env->NewObjectArray(rime::kT9WorkCounterCount, string_class, nullptr);
  for (int i = 0; i < rime::kT9WorkCounterCount; ++i) {
    jstring name = env->NewStringUTF(kWorkCounterNames[i]);
    env->SetObjectArrayElement(result, i, name);
    env->DeleteLocalRef(name);
  }
  return result;
}

// Returns and clears the calling thread's counters. The replay thread runs each
// key, flush, settlement and candidate inspection synchronously.
JNIEXPORT jlongArray JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeHostTakeWorkCounters(JNIEnv* env, jobject) {
  jlong values[rime::kT9WorkCounterCount];
  for (int i = 0; i < rime::kT9WorkCounterCount; ++i) {
    values[i] = static_cast<jlong>(rime::t9_work_counters[i]);
    rime::t9_work_counters[i] = 0;
  }
  jlongArray result = env->NewLongArray(rime::kT9WorkCounterCount);
  env->SetLongArrayRegion(result, 0, rime::kT9WorkCounterCount, values);
  return result;
}

// Merges a Rime userdb snapshot (*.userdb.txt) with librime's own restore path,
// then counts the live entries and tick of the named userdb. Call after
// deployment and before a session opens the userdb. Returns null on failure.
JNIEXPORT jlongArray JNICALL
Java_com_kingzcheung_xime_rime_RimeEngine_nativeHostRestoreUserDict(
    JNIEnv* env, jobject, jstring snapshot, jstring dict_name) {
  const std::string snapshot_path = Utf8(env, snapshot);
  const std::string name = Utf8(env, dict_name);
  if (snapshot_path.empty() || name.empty()) return nullptr;
  rime::UserDictManager manager(&rime::Service::instance().deployer());
  if (!manager.Restore(rime::path(snapshot_path))) return nullptr;
  auto* component = rime::UserDb::Require("userdb");
  if (!component) return nullptr;
  rime::the<rime::Db> db(component->Create(name));
  if (!db || !db->OpenReadOnly()) return nullptr;
  jlong entries = 0, tick = 0;
  std::string value;
  if (db->MetaFetch("/tick", &value)) tick = static_cast<jlong>(std::stoull(value));
  auto accessor = db->Query("");
  accessor->Jump(" ");  // skip metadata, as UserDictionary::Lookup does
  std::string key;
  while (!accessor->exhausted() && accessor->GetNextRecord(&key, &value)) {
    rime::UserDbValue entry(value);
    if (entry.commits >= 0) ++entries;
  }
  accessor.reset();
  db->Close();
  jlong values[2] = {entries, tick};
  jlongArray result = env->NewLongArray(2);
  env->SetLongArrayRegion(result, 0, 2, values);
  return result;
}

}  // extern "C"
