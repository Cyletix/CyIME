package com.kingzcheung.xime.translate

/**
 * libcytranslate.so（tools/offline_translate：slimt 引擎 + JNI），只有 arm64-v8a 和 x86_64 的安装包里有；
 * 其他 CPU 上 [load] 返回 false，离线翻译显示为不支持。
 * 一个句柄 = 一个翻译方向（模型、词表、短名单都按文件映射），同一时间只给一个线程用。
 */
internal object OfflineTranslatorNative {
    @JvmStatic external fun nativeCreate(model: String, sourceVocabulary: String, targetVocabulary: String,
                                         shortlist: String): Long
    @JvmStatic external fun nativeTranslate(handle: Long, text: String): String
    @JvmStatic external fun nativeDestroy(handle: Long)

    private var loaded: Boolean? = null

    @Synchronized fun load(): Boolean {
        loaded?.let { return it }
        return runCatching { System.loadLibrary("cytranslate") }.isSuccess.also { loaded = it }
    }
}
