package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeCandidate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpandedCandidateLoaderTest {
    @Test fun loadsInBoundedBatchesWithoutDuplicatingGlobalIndices() = runTest {
        val offsets = mutableListOf<Pair<Int, Int>>()
        val publications = mutableListOf<List<RimeCandidate>>()
        val loader = ExpandedCandidateLoader(this, StandardTestDispatcher(testScheduler)) { revision, offset, count ->
            assertEquals(42L, revision)
            offsets += offset to count
            List(count) { RimeCandidate((offset + it).toString(), "") }
        }
        loader.load(42, { true }, publications::add)
        assertTrue(publications.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(0,64,128,192,256,320,384,448), offsets.map { it.first })
        assertEquals(52, offsets.last().second)
        assertEquals(64, publications.first().size)
        assertEquals((0 until 500).map(Int::toString), publications.last().map { it.text })
    }
    @Test fun staleEngineSnapshotIsNotPublishedAsEmptyCandidates() = runTest {
        var published = false
        val loader = ExpandedCandidateLoader(this, StandardTestDispatcher(testScheduler)) { _, _, _ -> null }
        loader.load(1, { true }) { published = true }
        advanceUntilIdle()
        assertFalse(published)
    }
    @Test fun editorChangeDuringReadDropsTheBatch() = runTest {
        var current = true
        var published = false
        val loader = ExpandedCandidateLoader(this, StandardTestDispatcher(testScheduler)) { _, _, _ ->
            current = false
            listOf(RimeCandidate("旧输入框", ""))
        }
        loader.load(1, { current }) { published = true }
        advanceUntilIdle()
        assertFalse(published)
    }
    @Test fun replacementCancelsOlderLoadAndEmptyListEndsValidSnapshot() = runTest {
        val publications = mutableListOf<List<RimeCandidate>>()
        val revisions = mutableListOf<Long>()
        val loader = ExpandedCandidateLoader(this, StandardTestDispatcher(testScheduler)) { revision, _, _ ->
            revisions += revision
            emptyList()
        }
        loader.load(1, { true }, publications::add)
        loader.load(2, { true }, publications::add)
        advanceUntilIdle()
        assertEquals(listOf(2L), revisions)
        assertEquals(listOf(emptyList<RimeCandidate>()), publications)
    }
}
