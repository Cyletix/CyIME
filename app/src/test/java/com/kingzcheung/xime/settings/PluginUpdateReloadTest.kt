package com.kingzcheung.xime.settings

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.plugin.core.config.IPluginConfigurable
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 11-03：0.3.0 上覆盖装 0.3.1，设置页还是旧版，要关了再开。装完正在运行的插件要立刻换成新版。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PluginUpdateReloadTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val id = "com.example.reload_test"

    @Before fun setup() {
        PluginManager.initialize(app)
    }

    @After fun cleanup() = runBlocking {
        PluginManager.unloadPlugin(id)
        PluginManager.installerManager.uninstallPlugin(id)
        Unit
    }

    private fun xipk(version: String, key: String): File {
        val file = File(app.cacheDir, "reload-$version.xipk")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.yaml"))
            zip.write("id: $id\nname: 测试\nversion: $version\ntype: tool\nentry: main.lua\n".toByteArray())
            zip.putNextEntry(ZipEntry("main.lua"))
            zip.write(
                ("local plugin = {}\n" +
                    "function plugin.getSettingsSchema() return { { type = \"text\", key = \"$key\", label = \"$key\" } } end\n" +
                    "return plugin\n").toByteArray()
            )
        }
        return file
    }

    private fun runningKeys(): List<String?> =
        (PluginManager.getPluginInstance(id) as IPluginConfigurable).getSettingsSchema().map { it.key }

    @Test fun overwriteInstallFromFileReplacesRunningPlugin() = runBlocking {
        assertTrue(ImportManager.import(app, Uri.fromFile(xipk("1.0.0", "old"))) is ImportManager.ImportResult.Plugin)
        SettingsPreferences.setPluginEnabled(app, id, true)
        assertTrue(PluginManager.launchPlugin(id))
        assertEquals(listOf("old"), runningKeys())

        assertTrue(ImportManager.import(app, Uri.fromFile(xipk("1.1.0", "new"))) is ImportManager.ImportResult.Plugin)
        assertEquals(listOf("new"), runningKeys())
        assertEquals("1.1.0", PluginManager.getAllInstallPlugins().single { it.id == id }.version)
    }

    @Test fun updatingStoppedPluginDoesNotStartIt() = runBlocking {
        assertTrue(ImportManager.import(app, Uri.fromFile(xipk("1.0.0", "old"))) is ImportManager.ImportResult.Plugin)
        SettingsPreferences.setPluginEnabled(app, id, false)
        assertTrue(ImportManager.import(app, Uri.fromFile(xipk("1.1.0", "new"))) is ImportManager.ImportResult.Plugin)
        assertEquals(null, PluginManager.getPluginInstance(id))
        assertEquals(false, SettingsPreferences.isPluginEnabled(app, id))
    }
}
