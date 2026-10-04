package com.kingzcheung.xime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ClipboardDraftSaveTest {
    @Test fun blankDraftIsRejectedBeforeAnyStorageOperation() {
        for (id in listOf(null, 7L)) {
            val result = submitClipboardDraft(" \n\t", "code", id,
                add = { _, _ -> error("Must not add a blank draft") },
                update = { _, _, _ -> error("Must not update a blank draft") },
            )
            assertEquals(ClipboardDraftSaveResult.EMPTY_TEXT, result)
            assertNotNull(result.errorMessage)
        }
    }

    @Test fun rejectedUpdateIsReportedWithoutCreatingAReplacementRecord() {
        var updateRequest: Triple<Long, String, String>? = null
        val result = submitClipboardDraft("用户修改的草稿", " code ", 7L,
            add = { _, _ -> error("Missing records must not silently create a new item") },
            update = { id, text, code ->
                updateRequest = Triple(id, text, code)
                false
            },
        )
        assertEquals(Triple(7L, "用户修改的草稿", "code"), updateRequest)
        assertEquals(ClipboardDraftSaveResult.MISSING_ITEM, result)
        assertNotNull(result.errorMessage)
    }

    @Test fun acceptedNewAndEditedDraftsPreserveTextAndNormalizeOnlyCode() {
        var added: Pair<String, String>? = null
        val addedResult = submitClipboardDraft("  正文\n", " code ", null,
            add = { text, code -> added = text to code },
            update = { _, _, _ -> error("A new draft has no existing record") },
        )
        assertEquals("  正文\n" to "code", added)
        assertEquals(ClipboardDraftSaveResult.ACCEPTED, addedResult)
        val editedResult = submitClipboardDraft("修改内容", "", 7L,
            add = { _, _ -> error("An edited draft is not a new record") },
            update = { id, text, code -> id == 7L && text == "修改内容" && code.isEmpty() },
        )
        assertEquals(ClipboardDraftSaveResult.ACCEPTED, editedResult)
    }
}
