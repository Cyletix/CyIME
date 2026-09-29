package com.kingzcheung.xime.rime

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.settings.JapaneseSchemas
import com.kingzcheung.xime.settings.SchemaManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.rules.ExternalResource

/** Japanese is opt-in. Engine tests must install their fixtures, not assume a user's setup. */
class JapaneseSchemaFixture : ExternalResource() {
    private var previous: List<String>? = null

    override fun before(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(RimeConfigHelper.prepareEngine(context))
        previous = SchemaManager.getEnabledSchemas(context)
        SchemaManager.setEnabledSchemas(context, (previous!! + JapaneseSchemas.ids).distinct())
        assertTrue(RimeConfigHelper.redeploy(context))
        JapaneseSchemas.ids.forEach {
            assertTrue("Missing compiled Japanese fixture: $it", SchemaManager.isSchemaCompiled(context, it))
        }
    }

    override fun after(): Unit = runBlocking {
        previous?.let {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            SchemaManager.setEnabledSchemas(context, it)
            assertTrue(RimeConfigHelper.redeploy(context))
        }
    }
}
