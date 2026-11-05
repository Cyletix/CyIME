package com.kingzcheung.xime.clipboard

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tablet 11-05: 新建分组 in a record's 分组 menu opens the settings page; the group made there takes
 * that record. The add form on a group's tab is titled by the group it adds to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClipboardGroupCreateFromRecordTest {
    private val store get() = ClipboardGroupStore.getInstance(ApplicationProvider.getApplicationContext<Application>())
    private fun group(name: String) = store.state.value.groups.single { it.name == name }

    @Before fun clear() {
        store.state.value.groups.forEach { store.delete(it.id) }
        store.takeKeyboardEntry()
        store.takeAddTarget()
    }

    @Test fun aGroupMadeFromARecordsMenuTakesThatRecord() {
        store.setKeyboardEntry("text:7")
        val entry = store.takeKeyboardEntry()
        assertEquals("text:7", entry)
        assertNull("taken once", store.takeKeyboardEntry())
        assertNull(store.add("工作", listOf(entry!!)))
        assertEquals(group("工作").id, store.state.value.members["text:7"])
    }

    @Test fun fromManageGroupsNothingIsPutIn() {
        store.setKeyboardEntry(null)
        assertEquals("from the keyboard, no record", "", store.takeKeyboardEntry())
        assertNull(store.add("家人"))
        assertEquals(0, store.state.value.count(group("家人").id))
        assertNull("opened in settings itself", store.takeKeyboardEntry())
    }

    @Test fun aNameThatCannotBeUsedPutsNothingIn() {
        assertEquals("「地址」是自带分类", store.add("地址", listOf("text:8")))
        assertNull(store.state.value.members["text:8"])
    }

    @Test fun theAddFormSeesItsGroupWithoutTakingIt() {
        store.add("常用语")
        val phrases = group("常用语")
        store.setAddTarget(phrases.id)
        assertEquals(phrases, store.addTargetGroup())
        assertEquals("still there for saving", phrases.id, store.takeAddTarget())
        assertNull(store.addTargetGroup())
        store.setAddTarget(null)
        assertNull("from 全部: a fixed record", store.addTargetGroup())
    }
}
