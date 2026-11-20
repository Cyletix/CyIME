package com.kingzcheung.xime.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PackageButtonLayoutIsolationTest {

    @Test
    fun importedPackageButtonLayoutAppliesOnlyToPackageSchema(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val rime = SchemaManager.getRimeDir(app).apply { mkdirs() }
        File(rime, "xime.custom.yaml").delete()
        File(rime, SchemaManifestManager.PACKAGE_XIME_DIR).deleteRecursively()

        val staged = File(app.cacheDir, "pkg-button-layout").apply {
            deleteRecursively()
            mkdirs()
        }
        File(staged, "luan17.schema.yaml").writeText("schema:\n  schema_id: luan17\n  name: 乱17\n")
        File(staged, "xime.custom.yaml").writeText(
            """
            keyboard:
              qwerty:
                button_layout: compact
              luan17_layout:
                schemas: [luan17]
                layout:
                  rows:
                    - [q, [w, e], [r, t], [y, u], i, [o, p]]
                    - [a, [s, d], [f, g], h, [j, k], l]
                    - [z, [x, c], v, [b, n], m]
            """.trimIndent()
        )
        SchemaManifestManager.installStagedFiles(
            app, "pkg_luan17", "乱17", "1", true,
            staged, listOf("luan17.schema.yaml", "xime.custom.yaml")
        )

        KeysConfigHelper.loadConfig(app)

        assertEquals(ButtonLayout.STANDARD, KeysConfigHelper.getButtonLayout(false, "rime_ice"))
        assertEquals(ButtonLayout.STANDARD, KeysConfigHelper.getButtonLayout(false, "pinyin_17jian"))
        assertEquals(ButtonLayout.COMPACT, KeysConfigHelper.getButtonLayout(false, "luan17"))
        assertEquals(ButtonLayout.STANDARD, KeysConfigHelper.getButtonLayout(true, "luan17"))

        // 卸载后恢复干净
        SchemaManager.uninstallPackage(app, "pkg_luan17")
        KeysConfigHelper.loadConfig(app)
        assertEquals(ButtonLayout.STANDARD, KeysConfigHelper.getButtonLayout(false, "luan17"))
        assertEquals(ButtonLayout.STANDARD, KeysConfigHelper.getButtonLayout(false, "rime_ice"))
    }

    @Test
    fun importedPackageSectionButtonLayoutOverridesBuiltInFallbackOnlyForItsSchema(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val rime = SchemaManager.getRimeDir(app).apply { mkdirs() }
        File(rime, "xime.custom.yaml").delete()
        File(rime, SchemaManifestManager.PACKAGE_XIME_DIR).deleteRecursively()

        val staged = File(app.cacheDir, "pkg-section-layout").apply {
            deleteRecursively()
            mkdirs()
        }
        File(staged, "luan17_b.schema.yaml").writeText("schema:\n  schema_id: luan17_b\n  name: 乱17B\n")
        File(staged, "xime.custom.yaml").writeText(
            """
            keyboard:
              qwerty_17:
                schemas: [luan17_b]
                button_layout: compact
            """.trimIndent()
        )
        SchemaManifestManager.installStagedFiles(
            app, "pkg_luan17_b", "乱17B", "1", true,
            staged, listOf("luan17_b.schema.yaml", "xime.custom.yaml")
        )

        KeysConfigHelper.loadConfig(app)

        assertEquals(ButtonLayout.STANDARD, KeysConfigHelper.getButtonLayout(false, "pinyin_17jian"))
        assertEquals(ButtonLayout.COMPACT, KeysConfigHelper.getButtonLayout(false, "luan17_b"))
        SchemaManager.uninstallPackage(app, "pkg_luan17_b")
    }

    @Test
    fun imported26KeyAndSingleLetterPackageKeepsCompactAndMnemonicsIsolated(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val rime = SchemaManager.getRimeDir(app).apply { mkdirs() }
        File(rime, "xime.custom.yaml").delete()
        File(rime, SchemaManifestManager.PACKAGE_XIME_DIR).deleteRecursively()

        // 场景 A：无自定义分节的 26 键方案（仅通过 manifest 关联，且配置放在 keyboard.qwerty 下）
        val staged26 = File(app.cacheDir, "pkg-26-compact").apply {
            deleteRecursively()
            mkdirs()
        }
        File(staged26, "wubi_custom26.schema.yaml").writeText("schema:\n  schema_id: wubi_custom26\n  name: 自定五笔\n")
        File(staged26, "xime.custom.yaml").writeText(
            """
            keyboard:
              qwerty:
                button_layout: compact
                keys:
                  q:
                    tap: "Q"
                    swipe_down:
                      label: "金 钅"
                      action: none
                      display: key
            """.trimIndent()
        )
        SchemaManifestManager.installStagedFiles(
            app, "pkg_wubi26", "自定五笔", "1", false,
            staged26, listOf("wubi_custom26.schema.yaml", "xime.custom.yaml")
        )

        // 场景 B：单字母自定义分节（LayoutKind.ALPHABETIC）+ patch 写法 + 助记
        val stagedSingle = File(app.cacheDir, "pkg-single-compact").apply {
            deleteRecursively()
            mkdirs()
        }
        File(stagedSingle, "lxsq_single.schema.yaml").writeText("schema:\n  schema_id: lxsq_single\n  name: 单字母方案\n")
        File(stagedSingle, "xime.custom.yaml").writeText(
            """
            patch:
              keyboard:
                qwerty_lxsq_single:
                  schemas: [lxsq_single]
                  button_layout: compact
                  layout:
                    rows:
                      - [q, w, e, r, t, y, u, i, o, p]
                      - [a, s, d, f, g, h, j, k, l]
                      - [z, x, c, v, b, n, m]
                  keys:
                    q:
                      swipe_down:
                        label: "手"
                        action: none
                        display: key
            """.trimIndent()
        )
        SchemaManifestManager.installStagedFiles(
            app, "pkg_lxsq_single", "单字母方案", "1", false,
            stagedSingle, listOf("lxsq_single.schema.yaml", "xime.custom.yaml")
        )

        KeysConfigHelper.loadConfig(app)

        // 26 键导入方案专属生效 compact 与助记
        assertEquals(ButtonLayout.COMPACT, KeysConfigHelper.getButtonLayout(false, "wubi_custom26"))
        KeysConfigHelper.setActiveKeyboardSchema("wubi_custom26")
        assertEquals("金 钅", KeysConfigHelper.getKeyGesture("q", false)?.swipeDown?.label)
        assertEquals(DisplayMode.KEY, KeysConfigHelper.getKeyGesture("q", false)?.swipeDown?.display)

        // 单字母自定义分节（走 QwertySwipeSymbols 分支）同样保留 compact 与助记
        assertEquals(ButtonLayout.COMPACT, KeysConfigHelper.getButtonLayout(false, "lxsq_single"))
        KeysConfigHelper.setActiveKeyboardSchema("lxsq_single")
        assertEquals("手", KeysConfigHelper.getKeyGesture("q", false)?.swipeDown?.label)

        // 内置方案保持 standard 与原有默认助记，完全隔离
        assertEquals(ButtonLayout.STANDARD, KeysConfigHelper.getButtonLayout(false, "rime_ice"))
        KeysConfigHelper.setActiveKeyboardSchema("rime_ice")
        assertEquals(false, KeysConfigHelper.getKeyGesture("q", false)?.swipeDown?.label == "金 钅")

        SchemaManager.uninstallPackage(app, "pkg_wubi26")
        SchemaManager.uninstallPackage(app, "pkg_lxsq_single")
        KeysConfigHelper.loadConfig(app)
        assertEquals(ButtonLayout.STANDARD, KeysConfigHelper.getButtonLayout(false, "wubi_custom26"))
        assertEquals(ButtonLayout.STANDARD, KeysConfigHelper.getButtonLayout(false, "lxsq_single"))
    }

    @Test fun packageQwertyCompactIsNotShadowedByBoilerplateSectionStandard() {
        val pkgYaml = """
            keyboard:
              qwerty:
                button_layout: compact
              qwerty_lxsq17:
                schemas: [lxsq17, wanxiang]
                button_layout: standard
                layout:
                  rows:
                    - [h, s, z, b, x, m]
                    - [l, d, y, w, j, n]
                    - [c, q, g, f, t]
        """.trimIndent()

        val layouts = KeysConfigHelper.parsePackageSchemaButtonLayouts(listOf(pkgYaml))
        assertEquals(ButtonLayout.COMPACT, layouts["lxsq17"])
        assertEquals(ButtonLayout.COMPACT, layouts["wanxiang"])
        assertEquals(null, layouts["rime_ice"])
    }
}
