package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.ui.keyboard.ToolbarButtonIcon
import com.kingzcheung.xime.ui.keyboard.ToolbarItemsRow

@Composable
fun ToolbarCustomizeView(
    toolbarButtons: List<String>,
    pluginButtons: List<ToolbarButtonItem.Plugin> = emptyList(),
    keyTextColor: Color,
    backgroundColor: Color,
    accentColor: Color,
    keyBgColor: Color,
    onUpdateToolbarButtons: ((List<String>) -> Unit)?,
    onDismiss: () -> Unit,
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier
) {
    val builtinButtons = ToolbarButton.entries.map { ToolbarButtonItem.Builtin(it) }
    val allButtons = builtinButtons + pluginButtons
    val itemById = remember(allButtons) { allButtons.associateBy { it.id } }
    val enabledIds = toolbarButtons.toSet()

    fun toggleButton(item: ToolbarButtonItem) {
        val newList = toolbarButtons.toMutableList()
        if (item.id in toolbarButtons) newList.remove(item.id) else newList.add(item.id)
        onUpdateToolbarButtons?.invoke(newList)
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // 图标按钮容器色：按键背景与强调色的混合色调（带主题色但不过于强烈）
    val iconButtonContainer = androidx.compose.ui.graphics.lerp(
        keyBgColor,
        accentColor,
        0.25f
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = if (isLandscape) 50.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconButtonContainer)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "确定",
                    tint = keyTextColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            ToolbarItemsRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            ) {
                val previewButtons = toolbarButtons.mapNotNull { itemById[it] }
                if (previewButtons.isNotEmpty()) {
                    previewButtons.forEach { button ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(iconButtonContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            ToolbarButtonIcon(
                                item = button,
                                tint = keyTextColor.copy(0.6f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(keyBgColor)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(allButtons, key = { it.id }) { button ->
                    val isEnabled = button.id in enabledIds
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { toggleButton(button) }
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isEnabled) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isEnabled) Color.Transparent else keyTextColor.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ToolbarButtonIcon(
                                item = button,
                                tint = if (isEnabled) accentColor else keyTextColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = button.label,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = keyTextColor.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 15.dp else bottomPaddingDp.dp))
    }
}
