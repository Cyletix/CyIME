package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
) {
    val context = LocalContext.current
    // 最近使用（LRU）：作为第一个分类页，点击符号时置顶记录
    var recentSymbols by remember {
        mutableStateOf(RecentUsageStore.get(context, RecentUsageStore.KEY_RECENT_SYMBOLS))
    }
    val displayCategories = remember(recentSymbols) {
        listOf(SymbolCategory(name = "最近使用", id = "recentSymbols", symbols = recentSymbols)) +
            SymbolData.categories
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { displayCategories.size }
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        // 内容区：符号网格 + HorizontalPager
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = if (isLandscape) 50.dp else 4.dp)
                .padding(bottom = 4.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val category = displayCategories[page]
                val columns = if (isLandscape) 15 else 8

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
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    category.symbols.chunked(columns).forEach { rowSymbols ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
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

        // 返回固定在左下角，最近使用及其余分类从右侧依次排列。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = if (isLandscape) 50.dp else 4.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.width(48.dp).height(40.dp).clip(RoundedCornerShape(8.dp))
                    .background(keyBgColor).tolerantClick {
                        onHapticFeedback?.invoke()
                        onBack()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "返回键盘",
                    tint = textColor, modifier = Modifier.size(24.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
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
                backgroundColor = backgroundColor,
                textColor = textColor,
                modifier = Modifier.width(48.dp),
                fontSize = 12.sp
            )
        }

        // 底部留空（至少覆盖导航栏 inset 与键盘底部内边距）
        Spacer(modifier = Modifier.height(bottomPaddingDp.dp))
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
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPressed) androidx.compose.ui.graphics.lerp(backgroundColor, Color.Black, 0.2f)
                else backgroundColor
            ).keyGlow()
            .tolerantClick(
                showRipple = false,
                interactionSource = interactionSource,
                onClick = onClick
            ),
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
            .background(
                if (isSelected) selectedBackgroundColor
                else backgroundColor
            ).keyGlow()
            .tolerantClick(onClick = onClick)
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
