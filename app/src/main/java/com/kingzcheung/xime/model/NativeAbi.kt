package com.kingzcheung.xime.model

/**
 * 这个进程实际跑的 ABI，按需下载的原生库（拍照识别引擎、日语转换引擎）按它选文件。
 * 不用 Build.SUPPORTED_ABIS 的第一个：64 位手机上装 32 位安装包时，进程是 32 位的，要 armeabi-v7a 的库。
 */
object NativeAbi {
    val current: String = of(System.getProperty("os.arch").orEmpty())

    /** os.arch → Android ABI 名。32 位 ARM 进程在 64 位内核上报 armv8l，和 armv7l 一样归到 armeabi-v7a。 */
    internal fun of(arch: String): String = when (arch.lowercase()) {
        "aarch64", "arm64" -> "arm64-v8a"
        "x86_64", "amd64" -> "x86_64"
        "x86", "i386", "i486", "i586", "i686" -> "x86"
        else -> "armeabi-v7a"
    }
}
