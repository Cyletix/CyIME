package com.kingzcheung.xime.settings

import android.app.Application
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import com.kingzcheung.xime.ui.settings.SchemaSettingsContent
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.viewmodel.SchemaSettingsViewModel
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w360dp-h680dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RimeFileImportTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val schema = "schema:\n  schema_id: example\n  name: 示例\n  version: '1.0'\n"

    @Test fun missingCompanionsAreNamedInTheImportReport() {
        val file = File(app.cacheDir, "example.schema.yaml").apply {
            writeText(schema + "  dependencies: [stroke]\ntranslator:\n  dictionary: words # 主词库\n")
        }
        rule.setContent { XimeTheme { SchemaSettingsContent(onBack = {}) } }
        rule.runOnIdle {
            ViewModelProvider(rule.activity)[SchemaSettingsViewModel::class.java].importSchemaFile(Uri.fromFile(file))
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("方案导入结果").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("words.dict.yaml", substring = true).assertIsDisplayed()
        rule.onNodeWithText("stroke.schema.yaml", substring = true).assertIsDisplayed()
        System.getenv("CYIME_RENDER_OUT")?.let { dir ->
            val view = ShadowDialog.getLatestDialog().window!!.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            rule.runOnIdle { view.draw(Canvas(image)) }
            File(dir, "rime-import-result.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        rule.onNodeWithText("知道了").performClick()
        rule.onNodeWithText("方案导入结果").assertDoesNotExist()
    }

    @Test fun wrappedRepositoryAndLooseDefinitionUseSameConflictChecks() = runBlocking {
        val rime = SchemaManager.getRimeDir(app).apply { mkdirs() }
        val userDefaults = File(rime, "default.yaml").apply { writeText("config_version: 'local'\n") }
        File(rime, "default.custom.yaml").writeText("patch:\n  schema_list:\n    - schema: current\n")
        File(rime, "current.schema.yaml").writeText("schema:\n  schema_id: current\n  name: Current\n")
        val archive = File(app.cacheDir, "example-main.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            for ((name, content) in mapOf("example.schema.yaml" to schema,
                    "aux.schema.yaml" to "schema:\n  schema_id: aux\n  name: AAA\n",
                    "default.yaml" to "schema_list:\n  - schema: example\n  - schema: aux\n",
                    "dicts/table.dict.yaml" to "dictionary")) {
                zip.putNextEntry(ZipEntry("publish/rime/$name"))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("publish/fcitx/table.txt"))
            zip.write("other platform".toByteArray())
        }
        val imported = ImportManager.import(app, Uri.fromFile(archive))
        assertTrue(imported.toString(), imported is ImportManager.ImportResult.Content)
        val result = imported as ImportManager.ImportResult.Content
        assertTrue(result.message, result.success && result.installedDirect)
        assertEquals("dictionary", File(rime, "dicts/table.dict.yaml").readText())
        assertFalse(File(rime, "publish/fcitx/table.txt").exists())
        val enabled = SchemaManager.getEnabledSchemas(app)
        assertTrue("example" in enabled && "current" in enabled)
        assertFalse("aux" in enabled)
        assertTrue(userDefaults.readText().contains("config_version: 'local'"))

        val replacement = File(app.cacheDir, "example.schema.yaml.TXT").apply { writeText(schema + "# changed\n") }
        val pending = ImportManager.import(app, Uri.fromFile(replacement)) as ImportManager.ImportResult.Content
        assertNotNull(pending.pendingPackageId)
        assertEquals(schema, File(rime, "example.schema.yaml").readText())
    }

    @Test fun unsupportedArchiveAndWrongSchemaNameAreNotInstalled() = runBlocking {
        val rar = File(app.cacheDir, "example.rar").apply { writeText("unsupported") }
        val wrong = File(app.cacheDir, "download.schema.yaml").apply { writeText(schema) }
        val refused = ImportManager.import(app, Uri.fromFile(rar)) as ImportManager.ImportResult.Failed
        assertTrue(refused.reason.contains(".zip"))
        val imported = ImportManager.import(app, Uri.fromFile(wrong))
        assertTrue(imported.toString(), imported is ImportManager.ImportResult.Content)
        val report = imported as ImportManager.ImportResult.Content
        assertFalse(report.success)
        assertTrue(report.message, report.message.contains("example.schema.yaml"))
        assertFalse(File(SchemaManager.getRimeDir(app), wrong.name).exists())
        assertFalse("download" in SchemaManager.getEnabledSchemas(app))
    }
}
