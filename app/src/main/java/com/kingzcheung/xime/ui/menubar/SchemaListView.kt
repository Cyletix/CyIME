package com.kingzcheung.xime.ui.menubar

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.keyboard.keyboardPanelBackground
import kotlin.math.roundToInt
import kotlin.math.abs

private enum class ProfilePanelPage { CHOICES, MANAGEMENT }
private data class ProfileChoice(val id: String, val label: String, val current: Boolean)

/** The toolbar edits the active language's combination. Language switching belongs to the globe. */
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
    // Never silently show the first entry as current while the live session is still loading.
    val current = all.firstOrNull { it.schemaId == currentSchemaId }
    val entries = all.filter { current != null && it.profile.language == current.profile.language }
    var layoutId by remember(currentSchemaId, current?.profile) {
        mutableStateOf(current?.profile?.layout?.id)
    }
    val layouts = entries.map { it.profile.layout }.distinctBy { it.id }
    val schemes = entries.filter { it.profile.layout.id == layoutId }
    var page by remember { mutableStateOf(ProfilePanelPage.CHOICES) }
    val canAddLayout = current?.profile?.language == InputLanguage.CHINESE

    Column(modifier.fillMaxWidth().keyboardPanelBackground(backgroundColor).testTag("input-profile-panel")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page != ProfilePanelPage.CHOICES) {
                IconButton(
                    onClick = { page = ProfilePanelPage.CHOICES },
                    modifier = Modifier.testTag("profile-management-back"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回输入方案", tint = keyTextColor)
                }
            }
            Text(
                when (page) {
                    ProfilePanelPage.CHOICES -> "输入方案"
                    ProfilePanelPage.MANAGEMENT -> "管理输入方案"
                },
                Modifier.weight(1f), color = keyTextColor,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
            )
            if (page == ProfilePanelPage.CHOICES && canAddLayout) {
                TextButton(
                    onClick = { page = ProfilePanelPage.MANAGEMENT },
                    modifier = Modifier.testTag("profile-more"),
                ) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp), tint = keyTextColor)
                    Spacer(Modifier.width(4.dp))
                    Text("管理", color = keyTextColor)
                }
            }
        }
        when {
            page == ProfilePanelPage.MANAGEMENT -> {
                // Keep keyboard tools inside their existing IME surface, without a second Popup window.
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp).testTag("profile-management"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (canAddLayout) ProfileManagementAction(
                        "添加自定义布局", "设计自己的键盘排列", Icons.Default.Add,
                        "add-layout-tile", keyTextColor, accentColor,
                    ) {
                        context.startActivity(
                            Intent(context, com.kingzcheung.xime.CustomLayoutActivity::class.java)
                                .putExtra("create_layout", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
            current != null -> {
                Row(
                    Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileChoiceColumn(
                        title = "键盘布局", icon = Icons.Default.Keyboard,
                        choices = layouts.map { ProfileChoice(it.id, it.displayName, it.id == current.profile.layout.id) },
                        focusId = layoutId, tag = "profile-layout-list", itemTagPrefix = "panel-layout:",
                        foreground = keyTextColor, accent = accentColor, modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { id ->
                        layoutId = id
                        // A compatible layout switches immediately. Otherwise browse its real schemes
                        // without pretending the current language or encoding has changed.
                        InputProfileSelection.changeLayout(entries, current, id)?.let { onSelectSchema(it.schemaId) }
                    }
                    key(layoutId) {
                        ProfileChoiceColumn(
                            title = "输入方案", icon = Icons.Default.Translate,
                            choices = schemes.map { entry ->
                                val hasVariants = schemes.count { it.profile.scheme == entry.profile.scheme } > 1
                                val variantName = entry.name.takeIf { it.isNotBlank() && it != entry.schemaId }
                                val showVariant = hasVariants || entry.profile.scheme in setOf(
                                    InputScheme.DOUBLE_PINYIN, InputScheme.WUBI, InputScheme.WUBI_PINYIN,
                                    InputScheme.EXTERNAL,
                                )
                                val label = if (showVariant && variantName != null) variantName
                                    else entry.profile.scheme.displayName
                                ProfileChoice(entry.schemaId, label, entry.schemaId == currentSchemaId)
                            },
                            focusId = currentSchemaId, tag = "profile-scheme-list", itemTagPrefix = "panel-scheme:",
                            foreground = keyTextColor, accent = accentColor, modifier = Modifier.weight(1f).fillMaxHeight(),
                            onSelect = onSelectSchema,
                        )
                    }
                }
            }
            else -> Text("正在读取当前方案…", Modifier.padding(16.dp), color = keyTextColor)
        }
    }
}

@Composable
private fun ProfileChoiceColumn(
    title: String, icon: ImageVector, choices: List<ProfileChoice>, focusId: String?,
    tag: String, itemTagPrefix: String, foreground: Color, accent: Color,
    modifier: Modifier = Modifier, onSelect: (String) -> Unit,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    var viewportHeight by remember { mutableIntStateOf(0) }
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val centers = remember(choices.map { it.id }) { mutableStateMapOf<String, Float>() }
    val focusedCenter = centers[focusId]
    LaunchedEffect(focusId, focusedCenter, viewportHeight) {
        if (focusedCenter != null && viewportHeight > 0) {
            scroll.scrollTo((focusedCenter - viewportHeight / 2f).roundToInt().coerceAtLeast(0))
        }
    }
    Surface(
        modifier, shape = RoundedCornerShape(16.dp), color = foreground.copy(alpha = 0.035f),
        border = BorderStroke(1.dp, foreground.copy(alpha = 0.09f)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Leave space for the labels when accessibility text is enlarged.
                if (density.fontScale <= 1.3f) Icon(icon, null, Modifier.size(18.dp), tint = accent)
                Text(title, color = foreground.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
            }
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewportHeight = it.height }
                    .onGloballyPositioned { viewportCoordinates = it }
                    .testTag("$tag-viewport"),
            ) {
                // The roomy panel frames the current choice in its centre. Short panels keep the
                // whole viewport available to scrolling rather than reserving decorative space.
                val centeringPadding = if (maxHeight >= 144.dp) (maxHeight - 48.dp) / 2 else 0.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 6.dp, vertical = centeringPadding)
                        .testTag(tag),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    choices.forEach { choice ->
                        val highlighted = choice.id == focusId
                        Surface(
                            Modifier.fillMaxWidth().onGloballyPositioned {
                                val viewport = viewportCoordinates
                                if (viewport != null && viewport.isAttached) {
                                    // Root coordinates include every wrapper's padding. Adding the
                                    // current scroll offset gives a content coordinate that stays
                                    // constant while the user scrolls the column.
                                    val center = it.positionInRoot().y + it.size.height / 2f -
                                        viewport.positionInRoot().y + scroll.value
                                    val previous = centers[choice.id]
                                    if (previous == null || abs(previous - center) > 0.5f) centers[choice.id] = center
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                choice.current -> accent.copy(alpha = 0.17f)
                                highlighted -> foreground.copy(alpha = 0.05f)
                                else -> Color.Transparent
                            },
                            border = BorderStroke(1.dp, when {
                                choice.current -> accent.copy(alpha = 0.5f)
                                highlighted -> foreground.copy(alpha = 0.18f)
                                else -> Color.Transparent
                            }),
                        ) {
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("$itemTagPrefix${choice.id}").semantics { selected = choice.current }
                                    .clickable(role = Role.RadioButton) { onSelect(choice.id) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    choice.label, Modifier.fillMaxWidth(),
                                    color = foreground.copy(alpha = if (highlighted || choice.current) 1f else 0.7f),
                                    style = if (choice.current && density.fontScale <= 1.3f)
                                        MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (highlighted || choice.current) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileManagementAction(
    label: String, description: String, icon: ImageVector, tag: String,
    foreground: Color, accent: Color, onClick: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = foreground.copy(alpha = 0.05f),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag)
                .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = accent)
            Column(Modifier.weight(1f)) {
                Text(label, color = foreground, style = MaterialTheme.typography.bodyMedium)
                Text(description, color = foreground.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
