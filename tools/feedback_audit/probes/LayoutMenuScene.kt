package com.kingzcheung.xime.audit

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.CustomLayoutActivity
import com.kingzcheung.xime.CustomLayoutManager
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.keyboard.AppFonts
import com.kingzcheung.xime.ui.menubar.SchemaListView
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import java.io.File

/** Switch between the real panel and manager in the shell window; capture their navigation Intent. */
@Composable
internal fun LayoutMenuScene(app: Application, record: (String) -> Unit, mode: String) {
    val catalog = remember {
        check(app.filesDir.canonicalPath.startsWith("/data/local/tmp/cyime-footer."))
        val dir = SchemaManager.getRimeDir(app)
        val build = File(dir, "build").apply { mkdirs() }
        val ids = listOf("rime_ice", "t9_pinyin", "stroke", "custom_pinyin_abcdef0123456789abcdef0123456789")
        File(app.filesDir, "custom-keyboard-layouts.json").writeText(
            """[{"id":"custom_pinyin_abcdef0123456789abcdef0123456789","name":"双指","rows":"q,w,e,r,t,y,u,i,o,p/a,s,d,f,g,h,j,k,l/z,x,c,v,b,n,m","formatVersion":3}]""")
        for (id in ids) {
            val yaml = "schema:\n  schema_id: $id\n  name: $id\n  version: '1'\n"
            File(dir, "$id.schema.yaml").writeText(yaml)
            File(build, "$id.schema.yaml").writeText(yaml)
        }
        SchemaManager.setEnabledSchemas(app, ids)
        SettingsPreferences.setCurrentSchema(app, "rime_ice")
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        SchemaManager.keyboardMenuSchemas(app)
    }
    var manager by remember { mutableStateOf<InputLanguage?>(null) }
    val window = LocalContext.current
    val navigation = remember(window) { object : ContextWrapper(window) {
        override fun startActivity(intent: Intent) {
            check(intent.component?.className == CustomLayoutActivity::class.java.name)
            manager = checkNotNull(intent.getStringExtra(CustomLayoutActivity.EXTRA_LANGUAGE)?.let(InputLanguage::fromId))
            record("manage:${manager!!.id}")
        }
    } }
    XimeTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            val language = manager
            if (language != null) {
                val owner = LocalLifecycleOwner.current
                val back = remember(owner) { object : OnBackPressedDispatcherOwner {
                    override val lifecycle = owner.lifecycle
                    override val onBackPressedDispatcher = OnBackPressedDispatcher()
                } }
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides back) {
                    CustomLayoutManager("default", true, initialLanguage = language) { manager = null }
                }
            }
            else CompositionLocalProvider(LocalContext provides navigation) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    val color = MaterialTheme.colorScheme
                    SchemaListView(catalog, "rime_ice", color.surface, color.primary, color.onSurface, color.surfaceVariant,
                        onSelectSchema = { record("select:$it") }, modifier = Modifier.fillMaxWidth().height(250.dp))
                }
            }
        }
    }
}
