package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import com.kingzcheung.xime.keyboard.commonSymbolsFor
import com.kingzcheung.xime.keyboard.KeyboardInputPage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.data.RecentUsageStore
import com.kingzcheung.xime.data.SymbolCategory
import com.kingzcheung.xime.data.SymbolData
import kotlinx.coroutines.launch

@Composable
fun SymbolKeyboardLayout(
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    keyBgColor: Color,
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier,
    /** 分类 tab 切换与返回按钮的振动钩子（符号点击/删除经 onSelect 由调用方统一振动）。 */
    onHapticFeedback: (() -> Unit)? = null,
    onNumber: () -> Unit,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
    specialKeyBackgroundColor: Color = accentColor,
    specialKeyTextColor: Color = textColor,
) {
    val context = LocalContext.current
    // 常用在首屏；最近使用（LRU）作为紧邻分类，点击符号时置顶记录
    var recentSymbols by remember {
        mutableStateOf(RecentUsageStore.get(context, RecentUsageStore.KEY_RECENT_SYMBOLS))
    }
    val textLabel = LocalTextModeLabel.current
    val displayCategories = remember(recentSymbols, textLabel) {
        listOf(SymbolCategory(name = "常用", id = "common", symbols = commonSymbolsFor(textLabel)),
            SymbolCategory(name = "最近使用", id = "recentSymbols", symbols = recentSymbols)) +
            SymbolData.categories
    }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = 0, // Common symbols are always the first page, including on a fresh install.
        pageCount = { displayCategories.size }
    )

    KeyboardKeySpacingScope(modifier.padding(bottom = bottomPaddingDp.dp)) { bodyModifier ->
    CompositionLocalProvider(LocalKeyVisualPadding provides PaddingValues(2.dp)) {
    Column(
        modifier = bodyModifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
    ) {
        // 内容区：符号网格 + HorizontalPager
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3f)
                .padding(bottom = 4.dp)
        ) {
            val columns = (maxWidth.value / 48f).toInt().coerceIn(6, 15)
            CompositionLocalProvider(LocalKeyboardKeySpacingScale provides keyboardKeySpacingScale(
                maxWidth.value / columns, maxWidth.value / columns)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val category = displayCategories[page]

                if (category.symbols.isEmpty()) {
                    // 最近使用为空时的占位提示
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无最近使用",
                            color = textColor.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                } else Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Top
                ) {
                    category.symbols.chunked(columns).forEach { rowSymbols ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            rowSymbols.forEach { symbol ->
                                SymbolButton(
                                    symbol = symbol,
                                    onClick = {
                                        recentSymbols = RecentUsageStore.record(
                                            context, RecentUsageStore.KEY_RECENT_SYMBOLS, symbol
                                        )
                                        onSelect(symbol)
                                    },
                                    modifier = Modifier.weight(1f),
                                    textColor = textColor,
                                    backgroundColor = keyBgColor,
                                )
                            }
                            repeat(columns - rowSymbols.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        }

        // 左侧双槽位与文本/数字键盘同位；右侧仍是分类和删除。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (slot in 1..2) {
                KeyboardModeKey(
                    slot = slot, page = KeyboardInputPage.SYMBOLS,
                    onKeyPress = { action -> if (action == "abc") onBack() else onNumber() },
                    onKeyPressDown = { onHapticFeedback?.invoke() },
                    backgroundColor = specialKeyBackgroundColor, textColor = specialKeyTextColor,
                    modifier = Modifier.weight(LocalModeSlotWeight.current),
                    shadowEnabled = shadowEnabled, shadowElevation = shadowElevation,
                    shadowShapeRadius = shadowShapeRadius,
                )
            }
            Row(
                modifier = Modifier
                    .weight(4.2f - 2 * LocalModeSlotWeight.current)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                displayCategories.forEachIndexed { index, category ->
                    SymbolCategoryTab(
                        name = category.name,
                        isSelected = index == pagerState.currentPage,
                        onClick = {
                            onHapticFeedback?.invoke()
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        backgroundColor = backgroundColor,
                        textColor = textColor,
                        selectedBackgroundColor = accentColor
                    )
                }
            }

            ActionKeyButton(
                text = "删除",
                onClick = { onSelect("delete") },
                backgroundColor = specialKeyBackgroundColor,
                textColor = specialKeyTextColor,
                modifier = Modifier.weight(0.8f),
                fontSize = 12.sp
            )
        }

    }
    }
    }
}

@Composable
private fun SymbolButton(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    backgroundColor: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .tolerantClick(
                showRipple = false,
                interactionSource = interactionSource,
                onClick = onClick
            )
            .padding(scaledKeyVisualPadding())
            .keyGlow(Modifier.clip(RoundedCornerShape(8.dp))
                .background(
                    if (isPressed) androidx.compose.ui.graphics.lerp(backgroundColor, Color.Black, 0.2f)
                    else backgroundColor
                )),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = textColor,
            fontFamily = AppFonts.keyFontFamily
        )
    }
}

@Composable
private fun SymbolCategoryTab(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    selectedBackgroundColor: Color = textColor.copy(alpha = 0.15f),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .tolerantClick(onClick = onClick)
            .keyGlow(Modifier.clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) selectedBackgroundColor
                else backgroundColor
            ))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (isSelected) textColor else textColor.copy(alpha = 0.5f)
        )
    }
}
