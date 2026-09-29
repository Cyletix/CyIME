package com.kingzcheung.xime.plugin.core.lua

import android.app.Application
import com.kingzcheung.xime.plugin.core.config.NoopPluginConfigStore
import com.kingzcheung.xime.plugin.core.model.PluginContext
import com.kingzcheung.xime.plugin.core.model.PluginInfo
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LuaLifecycleFailureTest {
    @get:Rule val temp = TemporaryFolder()
    private fun runtime(script: String): LuaScriptRuntime {
        val dir = temp.newFolder()
        File(dir, "main.lua").writeText(script)
        return LuaScriptRuntime("failure-test", dir, "main.lua", NoopPluginConfigStore, callTimeoutMs = 200)
    }
    @Test fun pcallCannotSwallowDeadlineAndPreventUnload() {
        val runtime = runtime("return { spin = function() while true do pcall(function() while true do end end) end end }")
        assertTrue(runtime.load())
        val start = System.nanoTime()
        assertThrows(Exception::class.java) { runtime.callChecked("spin") }
        runtime.close()
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000)
    }
    @Test fun missingOptionalExportDiffersFromFailedExport() {
        val runtime = runtime("return { broken = function() error('failure') end }")
        try {
            assertTrue(runtime.load())
            assertTrue(runtime.callChecked("absent").isnil())
            assertThrows(Exception::class.java) { runtime.callChecked("broken") }
        } finally { runtime.close() }
    }
    @Test fun invalidSchemaCannotLoadAsConfigured() {
        val runtime = runtime("return { getSettingsSchema = function() error('schema unavailable') end }")
        val info = PluginInfo(id = "test", name = "test", description = "test", iconResId = 0, versionCode = 1, versionName = "1", path = "", type = "tool")
        val context = PluginContext(application = Application(), pluginInfo = info, configStore = NoopPluginConfigStore)
        val adapter = LuaPluginAdapter(runtime, context)
        try { assertThrows(Exception::class.java) { adapter.onLoad(context) } }
        finally { runtime.close() }
    }
    @Test fun entryAndOnLoadFailuresAreNotSuccessfulLoads() {
        val invalid = runtime("return 42")
        try { assertFalse(invalid.load()) } finally { invalid.close() }
        val runtime = runtime("return { onLoad = function() error('no') end }")
        try { assertTrue(runtime.load()); assertFalse(runtime.callOnLoad()) }
        finally { runtime.close() }
    }
}
