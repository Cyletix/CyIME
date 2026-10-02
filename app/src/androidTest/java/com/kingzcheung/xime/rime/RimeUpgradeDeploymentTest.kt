package com.kingzcheung.xime.rime

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RimeUpgradeDeploymentTest {
    @Test fun incrementalDeploymentRefreshesLiveConfigAndPreservesPersonalFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = RimeEngine.getInstance()
        assertTrue(RimeConfigHelper.prepareEngine(context))
        val schema = engine.getCurrentSchema()
        val user = File(context.filesDir, "rime")
        engine.destroy()
        val fixture = File(context.cacheDir, "rime-upgrade-${System.nanoTime()}").apply { mkdirs() }
        val config = File(fixture, "upgrade_test.schema.yaml")
        val original = """
            schema:
              schema_id: upgrade_test
              name: Before upgrade
              version: "1"
            engine:
              processors: [speller, express_editor]
              segmentors: [abc_segmentor, fallback_segmentor]
              translators: [script_translator]
            speller:
              alphabet: abcdefghijklmnopqrstuvwxyz
            translator:
              dictionary: upgrade_test
        """.trimIndent()
        config.writeText(original)
        File(fixture, "upgrade_test.dict.yaml").writeText("---\nname: upgrade_test\nversion: '1'\nsort: by_weight\n...\nhello\thello\t100\n")
        File(fixture, "default.yaml").writeText("config_version: '1'\nschema_list:\n  - schema: upgrade_test\n")
        val custom = File(fixture, "upgrade_test.custom.yaml").apply { writeText("patch:\n  menu/page_size: 17\n") }
        val personal = File(fixture, "personal.userdb/keep.bin").apply { parentFile!!.mkdirs(); writeText("personal data must survive") }
        try {
            engine.initialize(fixture.path, fixture.path)
            assertTrue(engine.deploy())
            assertTrue(engine.switchSchema("upgrade_test"))
            assertEquals("Before upgrade", engine.getSchemaString("upgrade_test", "schema/name"))
            config.writeText(original.replace("Before upgrade", "After upgrade").replace("version: \"1\"", "version: \"2\""))
            assertTrue(config.setLastModified(System.currentTimeMillis() + 2_000))
            assertTrue(engine.deployIncremental())
            assertEquals("upgrade_test", engine.getCurrentSchema())
            assertEquals("After upgrade", engine.getSchemaString("upgrade_test", "schema/name"))
            assertEquals("patch:\n  menu/page_size: 17\n", custom.readText())
            assertEquals("personal data must survive", personal.readText())
        } finally {
            engine.destroy()
            engine.initialize(user.path, user.path)
            assertTrue(engine.ensureSession())
            engine.switchSchema(schema)
            assertTrue(fixture.deleteRecursively())
        }
    }
}
