package com.kingzcheung.xime.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Animation
import androidx.compose.material.icons.twotone.Collections
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.BackgroundMotionEffect
import com.kingzcheung.xime.settings.BackgroundMotionPreferences
import com.kingzcheung.xime.settings.BackgroundMotionSpeed
import com.kingzcheung.xime.settings.PerformanceCosts
import com.kingzcheung.xime.ui.theme.BackgroundMotionPath
import com.kingzcheung.xime.ui.theme.CustomImageTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kingzcheung.xime.ui.theme.rememberBackgroundMotionSettings
import com.kingzcheung.xime.ui.theme.withBackgroundMotion

/**
 * 主题与外观 › 背景图片 › 动态背景：一个开关（默认关），开着时再选效果和速度，有「我的图片」时还能加轮播图片。
 * 只对图片背景起作用；游戏、系统省电、系统关了动画时键盘上不动（见 BackgroundMotionGate）。
 */
@Composable
internal fun ColumnScope.BackgroundMotionSettingsItems(hasCustomImage: Boolean) {
    val context = LocalContext.current
    val settings = rememberBackgroundMotionSettings()
    val scope = rememberCoroutineScope()
    val slides = remember(settings.slides) { CustomImageTheme.slides(context, settings.slides) }
    val slidePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(BackgroundMotionPreferences.MAX_SLIDES)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) { CustomImageTheme.addSlides(context, uris) }
            if (added < uris.size) android.widget.Toast.makeText(context,
                if (added == 0 && slides.size >= BackgroundMotionPreferences.MAX_SLIDES) "最多加 ${BackgroundMotionPreferences.MAX_SLIDES} 张"
                else "有 ${uris.size - added} 张没有加上", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    SettingsToggleItem(icon = Icons.TwoTone.Animation, title = "动态背景",
        subtitle = "图片背景缓慢移动，游戏、省电和关闭动画时不动",
        checked = settings.enabled,
        onCheckedChange = { BackgroundMotionPreferences.setEnabled(context, it) },
        costItem = PerformanceCosts.Item.BACKGROUND_MOTION)
    if (!settings.enabled) return
    Column(Modifier.settingsSearchAnchor("动态效果").fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("效果", style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth().choiceCardRowHeight(84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackgroundMotionEffect.entries.forEach { effect ->
                IllustratedChoiceCard(effect.label, settings.effect == effect,
                    onClick = { BackgroundMotionPreferences.setEffect(context, effect) },
                    modifier = Modifier.weight(1f), tag = "background-motion-effect:${effect.name}") {
                    MotionIllustration(effect, PREVIEW_SHOT_MS)
                }
            }
        }
        Text("速度", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().choiceCardRowHeight(84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackgroundMotionSpeed.entries.forEach { speed ->
                IllustratedChoiceCard(speed.label, settings.speed == speed,
                    onClick = { BackgroundMotionPreferences.setSpeed(context, speed) },
                    modifier = Modifier.weight(1f), tag = "background-motion-speed:${speed.name}") {
                    // 示意图按真实速度的比例放快，三张之间快慢的差别和键盘上一样。
                    MotionIllustration(settings.effect, speed.shotMs * PREVIEW_SHOT_MS / BackgroundMotionSpeed.NORMAL.shotMs)
                }
            }
        }
    }
    // 轮播只给「我的图片」：主图之外再加几张，每走完一圈淡入换下一张。
    if (!hasCustomImage) return
    SettingsItem(icon = Icons.TwoTone.Collections, title = "轮播图片",
        subtitle = if (slides.isEmpty()) "和我的图片轮流显示" else "已加 ${slides.size} 张，每圈换一张",
        onClick = { slidePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        showArrow = true)
    if (slides.isNotEmpty()) {
        SettingsItem(icon = Icons.TwoTone.Delete, title = "清空轮播图片",
            subtitle = "我的图片保留",
            onClick = { scope.launch(Dispatchers.IO) { CustomImageTheme.clearSlides(context) } })
    }
}

/** 示意图比键盘上快 6 倍，一眼看得出怎么动。 */
private const val PREVIEW_SHOT_MS = 3_000L

/** 一小张风景（天空、太阳、山）按 [effect] 动；只在设置页显示时走。 */
@Composable
private fun MotionIllustration(effect: BackgroundMotionEffect, shotMs: Long) {
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            elapsed.longValue += (now - last).coerceIn(0L, 100L)
            last = now
        }
    }
    val sky = MaterialTheme.colorScheme.primaryContainer
    val hill = MaterialTheme.colorScheme.primary
    val sun = MaterialTheme.colorScheme.tertiary
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).drawBehind {
        val frame = Rect(Offset.Zero, size)
        withBackgroundMotion(BackgroundMotionPath.at(effect, elapsed.longValue, shotMs), frame) {
            drawRect(Brush.verticalGradient(listOf(sky, Color.White.copy(alpha = .6f).compositeOver(sky))))
            drawCircle(sun, radius = size.minDimension * .16f, center = Offset(size.width * .72f, size.height * .34f))
            drawPath(Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, size.height * .72f)
                lineTo(size.width * .3f, size.height * .46f)
                lineTo(size.width * .55f, size.height * .7f)
                lineTo(size.width * .8f, size.height * .52f)
                lineTo(size.width, size.height * .66f)
                lineTo(size.width, size.height)
                close()
            }, hill)
        }
    })
}
