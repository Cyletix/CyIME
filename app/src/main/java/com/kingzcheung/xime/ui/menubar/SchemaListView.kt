package com.kingzcheung.xime.ui.menubar

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.keyboard.keyboardPanelBackground
import com.kingzcheung.xime.ui.keyboard.keyboardPhysicalMetrics
import com.kingzcheung.xime.ui.keyboard.CALIBRATED_QWERTY_PITCH_MM
import com.kingzcheung.xime.ui.settings.LayoutPreviewChoices
import com.kingzcheung.xime.ui.settings.ProfileChoiceRow

/** Language stays unchanged; layout/encoding selectors expose installed combinations. */
@Composable
fun SchemaListView(
    schemas: List<SchemaInfo>, currentSchemaId: String,
    backgroundColor: Color, accentColor: Color, keyTextColor: Color, keyBgColor: Color,
    onSelectSchema: (String) -> Unit,
    modifier: Modifier = Modifier, availableSchemas: List<SchemaInfo> = schemas,
) {
    val context = LocalContext.current
    val all = InputModes.available((schemas + availableSchemas).distinctBy { it.schemaId })
        .filter { it.profile.mode == InputMode.KEYBOARD }
    val current = all.firstOrNull { it.schemaId == currentSchemaId }
    val entries = all.filter { current != null && it.profile.language == current.profile.language }
    var layoutId by remember(currentSchemaId, current?.profile) { mutableStateOf(current?.profile?.layout?.id) }
    val layouts = InputProfileSelection.layoutRow(entries, all)
    val schemes = entries.filter { entry -> layoutId?.let { InputProfileSelection.offers(entry, it, all) } == true }
    BoxWithConstraints(modifier.fillMaxWidth().keyboardPanelBackground(backgroundColor).testTag("input-profile-panel")) {
    val density = LocalDensity.current
    val display = LocalView.current.display
    val configuration = LocalConfiguration.current
    val targetCellDp = remember(context, display, configuration, density.density) {
        keyboardPhysicalMetrics(context, display, density.density)?.dpFromMillimeters(CALIBRATED_QWERTY_PITCH_MM)
    }
    // 分体键盘与正常 26 键错位一致；预览随「三行错位」设置。
    val qwertyStagger = com.kingzcheung.xime.ui.keyboard.LocalKeyboardInputPreferences.current.let {
        com.kingzcheung.xime.ui.keyboard.resolvedQwertyStagger(it.qwertyStagger, it.qwertyStaggerCustom,
            ((maxWidth.value - 16f) / 10f).coerceAtLeast(0f), targetCellDp)
    }
    val typography = MaterialTheme.typography
    // Both rows share the actual content height; no independent preview/text caps
    // that leave the scheme row tiny and most of a tall panel unused.
    val (layoutHeight, schemeHeight) = with(density) {
        val schemeHeight = maxOf(48.dp,
            typography.labelSmall.lineHeight.toDp() + typography.bodyMedium.lineHeight.toDp() + 6.dp)
        val layoutLabelHeight = typography.labelLarge.lineHeight.toDp() + 4.dp
        val minimum = maxOf(schemeHeight, layoutLabelHeight + 24.dp)
        val available = if (constraints.hasBoundedHeight) maxHeight - 17.dp else minimum * 2.5f
        // Keep 60:40 except when a short panel needs the scheme's minimum touch/text height.
        val bottom = (available * .4f).coerceAtLeast(schemeHeight)
        (available - bottom).coerceAtLeast(minimum) to bottom
    }
    Column(Modifier.fillMaxWidth()
        .verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp)) {
        if (current == null) {
            Text("正在读取当前输入方式…", color = keyTextColor)
        } else {
            val switches = layouts.associate { it.id to InputProfileSelection.switchLayout(entries, current, it.id, all) }
            LayoutPreviewChoices(layouts, current.profile.language, layoutId,
                "profile-layout-list", itemTagPrefix = "panel-layout:",
                unavailableIds = switches.filterValues { it?.keepsScheme == false }.keys,
                rowHeight = layoutHeight,
                qwertyPreviewStagger = qwertyStagger,
                onManage = {
                    context.startActivity(Intent(context, com.kingzcheung.xime.CustomLayoutActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                containerColor = keyBgColor, contentColor = keyTextColor, accentColor = accentColor) { id ->
                layoutId = id
                // This is an explicit combination choice, not a layout rebind: one tap switches,
                // keeping the current scheme when the layout offers it (拼音 26 → 拼音 14).
                // 布局没有当前方案（小鹤双拼 → 九键）时照样切换，但要说出换成了什么方案。
                switches[id]?.let { switch ->
                    if (!switch.keepsScheme) layouts.firstOrNull { it.id == id }?.let { layout ->
                        android.widget.Toast.makeText(context, InputProfileSelection.switchHint(layout, current, switch.entry),
                            android.widget.Toast.LENGTH_LONG).show()
                    }
                    LetterLayouts.selectFor(context, switch.entry, id)
                    onSelectSchema(switch.entry.schemaId)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp), color = keyTextColor.copy(alpha = .15f))
            ProfileChoiceRow("方案", "profile-scheme-list",
                schemes.map { it.schemaId to it.schemeLabel },
                currentSchemaId, itemTagPrefix = "panel-scheme:",
                previews = schemes.associate { it.schemaId to InputProfiles.schemePreview(it.profile) },
                rowHeight = schemeHeight,
                containerColor = keyBgColor, contentColor = keyTextColor, accentColor = accentColor,
                onManage = {
                    context.startActivity(Intent(context, com.kingzcheung.xime.MainActivity::class.java)
                        .putExtra("open_fragment", "schema").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, onSelect = onSelectSchema)
        }
    }
    }
}
