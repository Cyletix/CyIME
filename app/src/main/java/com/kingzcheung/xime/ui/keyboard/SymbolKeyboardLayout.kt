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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.data.RecentUsageStore
import com.kingzcheung.xime.data.SymbolCategory
import com.kingzcheung.xime.data.SymbolData
import com.kingzcheung.xime.settings.KeysConfigHelper
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
    onSelectExact: (String) -> Unit = onSelect,
    isFloatingMode: Boolean = false,
) {
    val context = LocalContext.current
    // 常用在首屏；最近使用（LRU）作为紧邻分类，点击符号时置顶记录
    var recentSymbols by remember {
        mutableStateOf(RecentUsageStore.get(context, RecentUsageStore.KEY_RECENT_SYMBOLS))
    }
    val textLabel = LocalTextModeLabel.current
    val japanese = LocalKeyboardPunctuation.current?.japanese == true
    val displayCategories = remember(recentSymbols, textLabel, japanese) {
        listOf(SymbolCategory(name = "常用", id = "common", symbols = commonSymbolsFor(textLabel)),
            SymbolCategory(name = "最近", id = "recentSymbols", symbols = recentSymbols)) +
            SymbolData.categories.map { category ->
                if (japanese && category.id == "punctuationSymbols") category.copy(name = "全")
                else category
            }
    }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = 0, // Common symbols are always the first page, including on a fresh install.
        pageCount = { displayCategories.size }
    )

    val keySpacing = KeysConfigHelper.getKeyboardKeyConfig().spacingFor("qwerty")
    KeyboardKeySpacingScope(modifier.padding(bottom = bottomPaddingDp.dp),
        policy = KeyVisualPolicy.Qwerty, allowShrink = isFloatingMode, applyGutter = true,
        growthSpacing = keySpacing) { bodyModifier ->
    CompositionLocalProvider(LocalKeyVisualPadding provides PaddingValues(
        horizontal = keySpacing.first?.dp ?: 2.dp,
        vertical = keySpacing.second?.dp ?: 2.dp,
    )) {
    Column(
        modifier = bodyModifier
            .fillMaxWidth()
            .keyboardPanelBackground(backgroundColor)
            .padding(bottom = 8.dp)
    ) {
        // 内容区：符号网格 + HorizontalPager
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3f)
                .padding(bottom = 4.dp)
        ) {
            val grid = symbolGridGeometry(maxWidth.value, maxHeight.value)
            val columns = grid.columns
            val symbolFontSize = (20f * keyContentScale(maxWidth.value / columns, grid.rowHeightDp)).sp
            // 符号网格自成一格：按网格自身格宽算度量（不套主体键宽上限/gutter）
            val gridMetrics = keyVisualMetrics(
                policy = KeyVisualPolicy.Qwerty.copy(maxKeyWidth = Float.MAX_VALUE, minGutter = 0f),
                availableWidthDp = maxWidth.value,
                availableHeightDp = grid.rowHeightDp * 3f,
                columns = columns.toFloat(),
                rows = 3f, verticalInsetDp = 0f,
            )
            CompositionLocalProvider(LocalKeyboardKeyVisualMetrics provides gridMetrics) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val category = displayCategories[page]
                val preserveWidth = category.id == "englishSymbols"

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
                                    preserveWidth = preserveWidth,
                                    onClick = {
                                        recentSymbols = RecentUsageStore.record(
                                            context, RecentUsageStore.KEY_RECENT_SYMBOLS, symbol
                                        )
                                        if (preserveWidth) onSelectExact(symbol) else onSelect(symbol)
                                    },
                                    modifier = Modifier.weight(1f).height(grid.rowHeightDp.dp).testTag("symbol-key:$symbol"),
                                    fontSize = symbolFontSize,
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

        // 和标准 26 键共用底栏权重；分类只占余下空间，不挤压两侧入口。
        val bottom = QwertyBottomRowWeights.Standard
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
                    modifier = Modifier.weight(bottom.mode),
                    shadowEnabled = shadowEnabled, shadowElevation = shadowElevation,
                    shadowShapeRadius = shadowShapeRadius,
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(bottom.total - 2 * bottom.mode - bottom.enter)
                    .fillMaxHeight()
                    .testTag("symbol-category-strip"),
                contentAlignment = Alignment.CenterStart,
            ) {
                // 高键盘中分类不无限放大；窄窗口仍保留可读宽度，通过滚动访问其余分类。
                val tabHeight = minOf(maxHeight, 72.dp)
                val tabWidth = (tabHeight * 1.1f).coerceIn(48.dp, 88.dp)
                val categoryColor = lerp(keyBgColor, textColor, 0.07f)
                Row(
                    Modifier.fillMaxWidth().height(tabHeight).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    displayCategories.forEachIndexed { index, category ->
                        SymbolCategoryTab(
                            name = category.name,
                            isSelected = index == pagerState.currentPage,
                            onClick = {
                                onHapticFeedback?.invoke()
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            backgroundColor = categoryColor,
                            textColor = textColor,
                            selectedBackgroundColor = lerp(categoryColor, accentColor, 0.24f),
                            modifier = Modifier.width(tabWidth).fillMaxHeight()
                                .testTag("symbol-category:${category.id}"),
                        )
                    }
                }
            }

            SwipeableIconKeyButton(
                icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.Backspace),
                onClick = { onSelect("delete") },
                onLongClick = { onSelect("delete") },
                backgroundColor = specialKeyBackgroundColor,
                iconColor = specialKeyTextColor,
                modifier = Modifier.weight(bottom.enter).testTag("symbol-delete")
                    .semantics { contentDescription = "删除" },
                shadowEnabled = shadowEnabled,
                shadowElevation = shadowElevation,
                shadowShapeRadius = shadowShapeRadius,
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
    preserveWidth: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .tolerantClick(
                showRipple = false,
                interactionSource = interactionSource,
                onClick = onClick
            )
            .padding(scaledKeyVisualPadding())
            .keyGlow(Modifier.clip(RoundedCornerShape(LocalKeyCornerRadius.current))
                .background(frostedKeyColor(backgroundColor, textColor,
                    LocalKeyboardInputPreferences.current.frostedGlass, pressed = isPressed))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (preserveWidth) symbol else punctuationKeyLabel(symbol),
            fontSize = fontSize,
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
    selectedBackgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val fill = if (isSelected) selectedBackgroundColor else backgroundColor
    Box(
        modifier = modifier.padding(scaledKeyVisualPadding())
            .clip(RoundedCornerShape(LocalKeyCornerRadius.current))
            .semantics(mergeDescendants = true) {
                selected = isSelected
                role = Role.Tab
                onClick { onClick(); true }
            }
            .tolerantClick(interactionSource = interactionSource, onClick = onClick)
            .keyGlow(Modifier.background(frostedKeyColor(
                fill, textColor, LocalKeyboardInputPreferences.current.frostedGlass,
                pressed = pressed, highlighted = isSelected,
            )))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, color = if (isSelected) textColor else textColor.copy(alpha = 0.65f),
            fontSize = 16.sp, maxLines = 1, textAlign = TextAlign.Center,
            fontFamily = AppFonts.keyFontFamily)
    }
}
