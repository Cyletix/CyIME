package com.kingzcheung.xime.ocr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.model.BuiltinModelCatalog
import com.kingzcheung.xime.model.ModelDownloadState
import com.kingzcheung.xime.model.ModelManager

/** 轻量版第一次拍照识别：说明要下载的模型和大小，点了才下载（国内魔搭、海外 GitHub，按地区自动选）。 */
@Composable
internal fun OcrModelDownloadPrompt(textColor: Color, accent: Color, modifier: Modifier = Modifier, onReady: () -> Unit) {
    val context = LocalContext.current
    val states by ModelManager.downloadStates.collectAsState()
    val state = states[BuiltinModelCatalog.OCR_MODEL]
    val model = ModelManager.getModel(BuiltinModelCatalog.OCR_MODEL)
    val size = model?.resolvedVersion()?.size.orEmpty()
    LaunchedEffect(state) { if (state is ModelDownloadState.Complete) onReady() }
    Column(modifier.padding(horizontal = 24.dp).testTag("ocr-model-download"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when (state) {
                is ModelDownloadState.Downloading ->
                    if (state.progress > 0f) "正在下载拍照识别模型 ${(state.progress * 100).toInt()}%…" else "正在下载拍照识别模型…"
                is ModelDownloadState.Error -> "下载失败：${state.message}"
                else -> "拍照识别要先下载识别模型（约 $size），下载一次以后不联网也能用。"
            },
            color = textColor, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
        )
        if (state !is ModelDownloadState.Downloading && model != null) {
            TextButton(onClick = { ModelManager.downloadModelInBackground(context, model) }) {
                Text(if (state is ModelDownloadState.Error) "重试" else "下载", color = accent)
            }
        }
    }
}
