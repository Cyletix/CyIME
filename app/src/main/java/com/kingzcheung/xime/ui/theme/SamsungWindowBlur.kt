package com.kingzcheung.xime.ui.theme

import android.graphics.Color
import android.os.Build
import android.view.View
import com.kingzcheung.xime.util.FileLogger
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/** Optional One UI live-window blur. No capture, hidden-API exemption or system setting changes. */
internal object SamsungWindowBlur {
    private data class Api(
        val builder: Constructor<*>, val mode: Int,
        val radius: Method, val color: Method, val corners: Method,
        val build: Method, val apply: Method,
    )

    private val api: Api? by lazy {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true) || Build.VERSION.SDK_INT < 31) null
        else runCatching {
            val info = Class.forName("android.view.SemBlurInfo")
            val builder = Class.forName("android.view.SemBlurInfo\$Builder")
            fun Class<*>.method(name: String, vararg types: Class<*>): Method =
                runCatching { getMethod(name, *types) }.getOrElse { getMethod("hidden_$name", *types) }
            Api(
                builder.getConstructor(Int::class.javaPrimitiveType),
                info.getField("BLUR_MODE_WINDOW").getInt(null),
                builder.method("setRadius", Int::class.javaPrimitiveType!!),
                builder.method("setBackgroundColor", Int::class.javaPrimitiveType!!),
                builder.method("setBackgroundCornerRadius", Float::class.javaPrimitiveType!!),
                builder.method("build"), View::class.java.method("semSetBlurInfo", info),
            )
        }.onFailure { FileLogger.w("SamsungWindowBlur", "One UI live window blur API unavailable", it) }.getOrNull()
    }

    val isAvailable: Boolean get() = api != null

    /**
     * 键盘这时能不能用 One UI 模糊顶替系统模糊。系统省电模式会关掉所有应用的窗口模糊（跨窗口模糊不可用），
     * 这时不再用 One UI 的接口把模糊重新打开：省电模式本来就是要省这份 GPU，键盘照系统没有模糊时的样子显示。
     */
    fun usableFor(context: android.content.Context): Boolean = isAvailable &&
        context.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode != true

    fun apply(view: View, radiusPx: Int, cornerRadiusPx: Float): Boolean {
        val methods = api ?: return false
        return runCatching {
            val builder = methods.builder.newInstance(methods.mode)
            methods.radius.invoke(builder, radiusPx.coerceAtLeast(0))
            // Theme tint belongs to Compose. The native layer only blurs the real background.
            methods.color.invoke(builder, Color.TRANSPARENT)
            methods.corners.invoke(builder, cornerRadiusPx)
            methods.apply.invoke(view, methods.build.invoke(builder))
        }.onFailure { FileLogger.w("SamsungWindowBlur", "One UI live window blur rejected", it) }.isSuccess
    }

    fun clear(view: View) {
        val methods = api ?: return
        runCatching { methods.apply.invoke(view, null) }
            .onFailure { FileLogger.w("SamsungWindowBlur", "Unable to clear One UI blur", it) }
    }
}
