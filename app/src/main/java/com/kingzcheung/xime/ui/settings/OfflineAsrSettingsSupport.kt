package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 语音转文本设置页内的离线引擎切换与模型区块。
 */
internal object OfflineAsrSettingsSupport {
    @Composable
    fun EngineSelector(
        useLocal: Boolean,
        onUseLocalChange: (Boolean) -> Unit
    ) {
        OfflineAsrEngineSelector(useLocal = useLocal, onUseLocalChange = onUseLocalChange)
    }

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
