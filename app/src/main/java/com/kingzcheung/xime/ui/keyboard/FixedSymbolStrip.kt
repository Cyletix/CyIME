package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FixedSymbolStrip(symbols: List<String>, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp).horizontalScroll(rememberScrollState())) {
        symbols.forEach { symbol ->
            TextButton(onClick = { onSelect(symbol) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                Text(symbol, maxLines = 1)
            }
        }
    }
}
