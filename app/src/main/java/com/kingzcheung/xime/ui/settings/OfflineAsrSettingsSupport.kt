package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 语音转文本设置页内的本地模型区块。
 */
internal object OfflineAsrSettingsSupport {
    @Composable
    fun ModelSection() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            OfflineModelCard()
        }
    }
}
