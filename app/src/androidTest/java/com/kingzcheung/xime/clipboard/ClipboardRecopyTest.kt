package com.kingzcheung.xime.clipboard

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.clipboard.db.ClipboardDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ClipboardRecopyTest {
    @Test fun copyingConsumedTextAgainRestoresPreviewWithoutCreatingADuplicate() = runBlocking {
        val dao = ClipboardDatabase.getInstance(InstrumentationRegistry.getInstrumentation().targetContext).clipboardDao()
        val text = "完整复制回归 " + UUID.randomUUID() + "\n第二行保留"
        var id: Long? = null
        try {
            dao.upsertAndTrim(text, 1000, 1000)
            val first = requireNotNull(dao.findByText(text))
            id = first.id
            dao.markConsumed(first.id)
            assertTrue(requireNotNull(dao.findByText(text)).consumed)
            dao.upsertAndTrim(text, 2000, 1000)
            val copied = requireNotNull(dao.findByText(text))
            assertFalse(copied.consumed)
            assertEquals(first.id, copied.id)
            assertEquals(2000L, copied.timestamp)
            assertEquals(text, copied.text)
            assertEquals(1, dao.observeAll().first().count { it.text == text })
        } finally {
            id?.let { dao.deleteClipboardById(it) }
        }
    }
}
