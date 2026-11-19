package com.kingzcheung.xime.ui.ai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kingzcheung.xime.ai.DeepSeekWeb

/**
 * DeepSeek 网页版问答页：中间是 chat.deepseek.com，底部两个按钮。
 *
 * 给没有接口密钥、但用 DeepSeek 账号登录网页版的用户用：在网页里正常提问，
 * 回到这里点「取最新回复」，页面把最后一条 AI 回复读出来交给 [DeepSeekWeb]，
 * 然后关闭本页，由输入法面板显示/上屏。纯代码搭布局，不依赖 Compose。
 */
class DeepSeekWebActivity : Activity() {
    companion object {
        private const val HOME_URL = "https://chat.deepseek.com/"
        private const val HINT = "登录 DeepSeek 后直接在网页里提问，回到这里点『取最新回复』"
    }

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        setupWebView()
        webView?.loadUrl(HOME_URL)
    }

    /** 竖排：顶部提示、中间 WebView（占满剩余高度）、底部两个按钮。 */
    private fun buildContent(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val hint = TextView(this).apply {
            text = HINT
            textSize = 13f
            setTextColor(themeColor(android.R.attr.textColorSecondary))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        root.addView(hint)

        val web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        webView = web
        root.addView(web)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        buttons.addView(
            Button(this).apply {
                text = "取最新回复"
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = dp(8) }
                setOnClickListener {
                    // evaluateJavascript 回调给的是 JSON 字符串，交给 DeepSeekWeb 还原。
                    webView?.evaluateJavascript(DeepSeekWeb.SCRIPT) { value ->
                        DeepSeekWeb.acceptResult(value)
                        finish()
                    }
                }
            }
        )
        buttons.addView(
            Button(this).apply {
                text = "关闭"
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { finish() }
            }
        )
        root.addView(buttons)
        return root
    }

    @Suppress("DEPRECATION")
    private fun setupWebView() {
        val web = webView ?: return
        web.settings.apply {
            // 网页版要跑自己的脚本、要把登录态和会话存在 DOM Storage 里。
            javaScriptEnabled = true
            domStorageEnabled = true
            // 本页只加载远端 https 页面，本地文件和 content:// 一律不许读。
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            // 不让网页自己弹新窗口；需要跳出的链接交给系统浏览器（见 WebViewClient）。
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return handleUrl(uri)
            }

            @Deprecated("兼容旧系统回调")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val uri = url?.let { Uri.parse(it) } ?: return false
                return handleUrl(uri)
            }

            /** http/https 留在网页里；其余 scheme（mailto、tel、应用深链等）交给系统。 */
            private fun handleUrl(uri: Uri): Boolean {
                if (uri.scheme == "http" || uri.scheme == "https") return false
                return openExternally(uri)
            }
        }
    }

    private fun openExternally(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            Toast.makeText(this, "没有能打开这个链接的应用", Toast.LENGTH_SHORT).show()
            // 已经拦下来了，别再让 WebView 报 ERR_UNKNOWN_URL_SCHEME。
            true
        }
    }

    /** 返回键先让网页后退，退不动再关页面；不调已废弃的 super 实现。 */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val web = webView
        if (web != null && web.canGoBack()) web.goBack() else finish()
    }

    override fun onDestroy() {
        webView?.let { web ->
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    /** 从当前主题取颜色（DayNight 下提示文字要跟着明暗变）。 */
    private fun themeColor(attr: Int): Int {
        val value = TypedValue()
        if (!theme.resolveAttribute(attr, value, true)) return Color.GRAY
        return if (value.resourceId != 0) getColor(value.resourceId) else value.data
    }
}
