package com.kingzcheung.xime.ui.keyboard

import android.content.Intent
import android.view.Gravity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingzcheung.xime.ai.AiAssistant
import com.kingzcheung.xime.ai.DeepSeekWeb
import com.kingzcheung.xime.service.ToolPanelEditTextHolder
import com.kingzcheung.xime.ui.settings.SettingsRoutes

/**
 * AI 助手面板（工具栏「AI」打开）：左边输入，右边实时显示回复。
 *
 * 输入复用工具面板那套 EditText 焦点与按键路由（[ToolPanelEditTextHolder]），
 * 中文拼音仍走 Rime、候选上屏进左边输入框；右上角三个按钮分别是模型、配置、网页版。
 * 回车发送由宿主 [com.kingzcheung.xime.service.XimeInputMethodService.triggerToolPanelGenerate] 分流到这里。
 */
@Composable
internal fun AiAssistantPanel(
    textColor: Color,
    accentColor: Color,
    keyBgColor: Color,
    onClose: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by AiAssistant.state.collectAsState()
    val webReply by DeepSeekWeb.latestReply.collectAsState()
    var modelMenuOpen by remember { mutableStateOf(false) }
    val cardBg = keyBgColor.copy(alpha = 0.5f)

    LaunchedEffect(Unit) { AiAssistant.open(context) }
    DisposableEffect(Unit) {
        onDispose {
            onFocusChange(false)
            AiAssistant.onPanelClosed()
            // 面板关了就把输入框从按键路由上摘掉，别让后续按键写进已经销毁的 EditText。
            ToolPanelEditTextHolder.editText = null
        }
    }

    // 网页版取回的回复：并进输出区，然后清掉，避免下次开面板又贴一遍。
    LaunchedEffect(webReply) {
        val reply = webReply?.trim().orEmpty()
        if (reply.isNotEmpty()) {
            AiAssistant.showExternalReply(reply)
            DeepSeekWeb.clearReply()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
        // ── 顶部：标题 + 模型 + 配置 + 网页版 + 关闭 ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("AI 助手", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            // 模型按钮：有列表就选，没有就先拉一次
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(keyBgColor)
                    .clickable { if (state.models.isEmpty()) refreshModels(context) else modelMenuOpen = true }
                    .padding(horizontal = 10.dp)
                    .testTag("ai-model-chip"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.model.ifBlank { "选择模型" },
                    color = textColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            if (modelMenuOpen) {
                OptionMenuPopup(
                    title = "选择模型",
                    options = state.models,
                    selected = state.model,
                    onChoose = { model ->
                        modelMenuOpen = false
                        AiAssistant.setModel(context, model)
                    },
                    onDismiss = { modelMenuOpen = false },
                )
            }
            Spacer(Modifier.weight(1f))
            HeaderIcon("配置", onClick = {
                val intent = Intent(context, com.kingzcheung.xime.MainActivity::class.java).apply {
                    putExtra("open_fragment", SettingsRoutes.AiAssistant)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                runCatching { context.startActivity(intent) }
            }) { Icon(Icons.Default.Settings, contentDescription = "AI 配置", tint = textColor.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)) }
            HeaderIcon("网页版", onClick = { DeepSeekWeb.start(context) }) {
                Icon(Icons.Default.Public, contentDescription = "DeepSeek 网页版", tint = textColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp))
            }
            HeaderIcon("关闭", onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // ── 左：输入区 ──
            Column(modifier = Modifier.weight(0.46f).fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    AndroidView(
                        factory = { ctx -> aiInputEditText(ctx, textColor, onFocusChange) },
                        modifier = Modifier.fillMaxSize().testTag("ai-input"),
                    )
                }
                Spacer(Modifier.height(6.dp))
                val streaming = state.status == AiAssistant.Status.STREAMING
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = if (streaming) 0.5f else 0.9f))
                        .clickable(enabled = state.configured) {
                            if (streaming) {
                                AiAssistant.cancel()
                            } else {
                                val text = ToolPanelEditTextHolder.editText?.text?.toString().orEmpty()
                                AiAssistant.send(context, text)
                                ToolPanelEditTextHolder.editText?.setText("")
                            }
                        }
                        .testTag("ai-send"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            streaming -> "停止"
                            !state.configured -> "先去配置"
                            else -> "发送"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // ── 右：AI 输出区 ──
            Box(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(cardBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                val outputScroll = rememberScrollState()
                LaunchedEffect(state.output, state.history.size, state.status) {
                    outputScroll.animateScrollTo(outputScroll.maxValue)
                }
                val shown = when {
                    !state.configured -> "还没填接口密钥：点右上角「配置」填地址和密钥；只想用 DeepSeek 网页版，就点「网页版」。"
                    state.status == AiAssistant.Status.ERROR -> "出错：${state.error}"
                    state.status == AiAssistant.Status.STREAMING && state.output.isEmpty() -> "生成中…"
                    state.output.isNotEmpty() -> state.output
                    state.history.isNotEmpty() -> lastAssistant(state.history)
                    else -> "左边输入问题，按回车或点「发送」。"
                }
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(outputScroll).testTag("ai-output"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = shown,
                        color = if (state.status == AiAssistant.Status.ERROR) Color(0xFFE57373) else textColor,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    if (state.status == AiAssistant.Status.STREAMING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = textColor.copy(alpha = 0.5f),
                            strokeWidth = 1.5.dp,
                        )
                    }
                }
            }
        }
    }
}

/** 右上角图标按钮：触摸 36dp，和工具面板的关闭按钮一样大。 */
@Composable
private fun HeaderIcon(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .testTag("ai-header:$label"),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 历史里最后一条 AI 回复（面板重新打开时用来回显上一次结果）。 */
private fun lastAssistant(history: List<com.kingzcheung.xime.ai.AiMessage>): String =
    history.lastOrNull { it.role == "assistant" }?.content.orEmpty()

/** 面板输入框：键盘按键经 [ToolPanelEditTextHolder] 打进来，多行、靠上。 */
private fun aiInputEditText(
    context: android.content.Context,
    textColor: Color,
    onFocusChange: (Boolean) -> Unit,
): android.widget.EditText = android.widget.EditText(context).apply {
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    setTextColor(textColor.hashCode())
    setHintTextColor(textColor.copy(alpha = 0.4f).hashCode())
    hint = "输入问题"
    textSize = 14f
    isSingleLine = false
    gravity = Gravity.TOP or Gravity.START
    setPadding(2, 2, 2, 2)
    onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> onFocusChange(hasFocus) }
    setOnClickListener { onFocusChange(true) }
    ToolPanelEditTextHolder.editText = this
    post { requestFocus() }
}

/** 「获取模型」：把失败原因用 Toast 告诉用户（面板里没有地方放长文本）。 */
private fun refreshModels(context: android.content.Context) {
    AiAssistant.refreshModels(context) { result ->
        result.exceptionOrNull()?.let { error ->
            android.os.Handler(context.mainLooper).post {
                android.widget.Toast.makeText(context, error.message ?: "获取模型失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
