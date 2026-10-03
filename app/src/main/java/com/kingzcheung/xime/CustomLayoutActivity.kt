package com.kingzcheung.xime

import android.os.Bundle
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.kingzcheung.xime.ui.settings.SettingsSection
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.keyboard.customLayoutKeyColors
import com.kingzcheung.xime.ui.theme.XimeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomLayoutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var darkMode by remember { mutableIntStateOf(SettingsPreferences.getDarkMode(this)) }
            var themeId by remember { mutableStateOf(SettingsPreferences.getKeyboardTheme(this)) }
            DisposableEffect(Unit) {
                val prefs = SettingsPreferences.getPrefsPublic(this@CustomLayoutActivity)
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    darkMode = SettingsPreferences.getDarkMode(this@CustomLayoutActivity)
                    themeId = SettingsPreferences.getKeyboardTheme(this@CustomLayoutActivity)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val dark = when (darkMode) { 0 -> false; 1 -> true; else -> isSystemInDarkTheme() }
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            // Match MainActivity and the IME: do not fall back to the YAML dark-theme ID.
            XimeTheme(darkTheme = dark, themeId = themeId) {
                Surface(Modifier.fillMaxSize()) {
                    CustomLayoutManager(themeId, dark,
                        createNew = savedInstanceState == null && intent.getBooleanExtra("create_layout", false)) { finish() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomLayoutManager(themeId: String, dark: Boolean, createNew: Boolean = false, close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var layouts by remember { mutableStateOf<List<CustomKeyboardLayout>>(emptyList()) }
    var id by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var rowText by rememberSaveable { mutableStateOf("") }
    var red by rememberSaveable { mutableStateOf(false) }
    var redMoved by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    fun edit(layout: CustomKeyboardLayout) {
        id = layout.id; name = layout.name; rowText = layout.withSpareSlot().rows.joinToString("/") { it.joinToString(",") }
        red = layout.redVowels; redMoved = layout.redMoved; selected = null; message = ""
    }
    fun draft() = CustomKeyboardLayout(id.orEmpty(), name.trim(), rowText.split('/').map { it.split(',') }, red, redMoved)
    fun update(layout: CustomKeyboardLayout) { rowText = layout.rows.joinToString("/") { it.joinToString(",") } }
    fun back() { if (id != null) discard = true else close() }
    BackHandler { back() }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { CustomKeyboardLayouts.load(context) } }
            .onSuccess { layouts = it; if (createNew) edit(CustomKeyboardLayout.fresh()) }.onFailure { message = "无法读取布局：${it.message}" }
    }
    DisposableEffect(context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == CustomKeyboardLayouts.STATUS) message = prefs.getString(key, "").orEmpty()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "自定义布局" else "编辑布局") },
                navigationIcon = {
                    IconButton(onClick = { back() }, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { paddingValues ->
    Column(Modifier.fillMaxSize().padding(paddingValues).imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (id == null) {
            com.kingzcheung.xime.ui.settings.QwertySymbolEditor()
            Text("中文按键布局：调整字母排列，保留词库和功能键。")
            Button(onClick = { edit(CustomKeyboardLayout.fresh()) }, enabled = !busy) { Text("新建布局") }
            layouts.forEach { layout ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(layout.name, style = MaterialTheme.typography.titleMedium)
                        Text("${layout.typingRows().sumOf { it.size }}个按键 · ${layout.movedLetters().size}个字母改位", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { edit(layout) }, enabled = !busy) { Text("编辑") }
                            TextButton(onClick = { edit(layout.copy(id = CustomKeyboardLayout.fresh().id, name = (layout.name.take(27) + " 副本"))) }, enabled = !busy) { Text("复制") }
                            TextButton(onClick = { deleteId = layout.id }, enabled = !busy) { Text("删除") }
                        }
                    }
                }
            }
        } else {
            SettingsSection(title = "方案名称") {
            OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, label = { Text("方案名称") },
                modifier = Modifier.fillMaxWidth().padding(16.dp), singleLine = true, enabled = !busy)
            }
            val layout = draft()
            SettingsSection(title = "按键排列") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("长按0.3秒，拖到另一按键后松手交换。点选一个单键，可与右侧单键合并；合并键可拆回。", style = MaterialTheme.typography.bodySmall)
            CustomLayoutGrid(layout, selected, !busy, themeId, dark, { selected = it }, { from, to ->
                val changed = layout.swap(from, to)
                if (changed == layout && from != to) message = "跨行交换需要相同宽度；请先拆分合并键。"
                update(changed)
            })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("独立分号键", modifier = Modifier.weight(1f))
                Switch(checked = layout.hasSemicolon(), onCheckedChange = { update(layout.setSemicolonEnabled(it)); selected = null }, enabled = !busy)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected?.let { update(layout.merge(it)) }; selected = null },
                    enabled = !busy && selected != null && layout.merge(selected!!) != layout) { Text("合并右侧") }
                OutlinedButton(onClick = { selected?.let { update(layout.split(it)) }; selected = null },
                    enabled = !busy && selected?.length == 2) { Text("拆分") }
                TextButton(onClick = { update(layout.copy(rows = listOf("qwertyuiop", "asdfghjkl_", "zxcvbnm").map { it.map(Char::toString) })); selected = null }, enabled = !busy) { Text("恢复原位") }
            }
            }
            }
            SettingsSection(title = "按键强调") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("强调元音 A E I O U", modifier = Modifier.weight(1f))
                Switch(checked = red, onCheckedChange = { red = it }, enabled = !busy)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("强调改位字母", modifier = Modifier.weight(1f))
                Switch(checked = redMoved, onCheckedChange = { redMoved = it }, enabled = !busy)
            }
            Text("开启后，对应字母的文字和按键底色随主题强调；两个选项均关闭时使用普通按键颜色。备用空位不显示在键盘中。只有开启“独立分号键”才增加分号按键。", style = MaterialTheme.typography.bodySmall)
            }
            }
            Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && layout.valid(), onClick = {
                busy = true; message = "正在保存布局…"
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { CustomKeyboardLayouts.save(context, layout); CustomKeyboardLayouts.load(context) } }
                        .onSuccess { layouts = it; id = null; message = SettingsPreferences.getPrefsPublic(context).getString(CustomKeyboardLayouts.STATUS, "已保存").orEmpty() }
                        .onFailure { message = "保存失败：${it.message}" }
                    busy = false
                }
            }) { Text(if (busy) "保存中…" else "保存方案") }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
    }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(onClick = { discard = false; id = null }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
    deleteId?.let { target -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("删除自定义方案？") },
        text = { Text("只删除这个布局，不删除词库和学习记录。当前正在使用它时会切回中文26键。") },
        confirmButton = { TextButton(onClick = {
            deleteId = null; busy = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { CustomKeyboardLayouts.delete(context, target); CustomKeyboardLayouts.load(context) } }
                    .onSuccess { layouts = it; message = "布局已删除" }.onFailure { message = "删除失败：${it.message}" }
                busy = false
            }
        }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } }) }
}

@Composable
private fun CustomLayoutGrid(layout: CustomKeyboardLayout, selected: String?, enabled: Boolean, theme: String, dark: Boolean,
    onSelect: (String) -> Unit, onSwap: (String, String) -> Unit) {
    val accent = com.kingzcheung.xime.ui.theme.KeyboardThemes.getPrimaryColor(theme, dark)
    val keyBackground = com.kingzcheung.xime.ui.theme.KeyboardThemes.getKeyBackgroundColor(theme, dark)
    val keyForeground = com.kingzcheung.xime.ui.theme.KeyboardThemes.getKeyTextColor(theme, dark)
    val bounds = remember { mutableMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var anchor by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val currentSwap by rememberUpdatedState(onSwap)
    val config = LocalViewConfiguration.current
    val density = LocalDensity.current
    val dragConfig = remember(config) { object : ViewConfiguration by config { override val longPressTimeoutMillis = 300L } }
    CompositionLocalProvider(LocalViewConfiguration provides dragConfig) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(186.dp).onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
                val slot = (maxWidth - 27.dp) / 10
                val positions = layout.rows.flatMapIndexed { rowIndex, row ->
                    var column = (10 - row.sumOf { it.length }) / 2f
                    row.map { key ->
                        val x = (slot + 3.dp) * column
                        column += key.length
                        Triple(key, x, 64.dp * rowIndex)
                    }
                }
                positions.forEach { (letter, x, y) ->
                    key(letter) {
                        val isDragging = dragging == letter
                        val position by animateOffsetAsState(
                            if (isDragging) pointer - origin - anchor else with(density) { Offset(x.toPx(), y.toPx()) },
                            animationSpec = if (isDragging) snap() else tween(180), label = "layout-key-position")
                        val scale by animateFloatAsState(if (isDragging) 1.06f else 1f, tween(120), label = "layout-key-lift")
                        val emptyOutline = if (target == letter || selected == letter) accent else MaterialTheme.colorScheme.outlineVariant
                        val colors = customLayoutKeyColors(layout, letter, keyBackground, keyForeground, accent)
                        Surface(color = if (letter == CustomKeyboardLayout.EMPTY_SLOT) androidx.compose.ui.graphics.Color.Transparent else colors.first, contentColor = colors.second, shape = RoundedCornerShape(9.dp),
                            shadowElevation = if (isDragging) 6.dp else 0.dp,
                            border = if (letter == CustomKeyboardLayout.EMPTY_SLOT) null else BorderStroke(if (target == letter || selected == letter) 2.dp else 1.dp,
                                if (target == letter || selected == letter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                                .drawBehind {
                                    if (letter == CustomKeyboardLayout.EMPTY_SLOT) drawRoundRect(emptyOutline,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(),
                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                                }
                                .width(slot * letter.length + 3.dp * (letter.length - 1)).height(58.dp)
                                .zIndex(if (isDragging) 1f else 0f).graphicsLayer { scaleX = scale; scaleY = scale }
                                .semantics { contentDescription = if (letter == CustomKeyboardLayout.EMPTY_SLOT) "备用空位，长按拖动" else "按键${letter.uppercase()}，长按拖动" }
                                .onGloballyPositioned { bounds[letter] = it.boundsInRoot() }
                                .clickable(enabled) { onSelect(letter) }
                                .pointerInput(letter, enabled, layout.rows) {
                                    if (enabled) detectDragGesturesAfterLongPress(
                                        onDragStart = { local -> anchor = local; pointer = (bounds[letter]?.topLeft ?: Offset.Zero) + local; dragging = letter },
                                        onDragEnd = { target?.let { currentSwap(letter, it) }; dragging = null; target = null },
                                        onDragCancel = { dragging = null; target = null },
                                        onDrag = { change, delta -> change.consume(); pointer += delta
                                            target = bounds.entries.firstOrNull { it.key != letter && it.key in layout.rows.flatten() && it.value.contains(pointer) }?.key })
                                }) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(com.kingzcheung.xime.ui.keyboard.customLayoutLabel(layout, if (letter == CustomKeyboardLayout.EMPTY_SLOT) "空位" else letter.uppercase(), colors.first, accent, keyForeground),
                                    fontSize = if (letter.length == 1) 19.sp else 17.sp, modifier = Modifier.padding(2.dp), maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (dragging != null) Text("${dragging!!.uppercase()} → ${target?.uppercase() ?: "拖到目标按键"}", style = MaterialTheme.typography.labelMedium)
        }
    }
}
