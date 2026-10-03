package com.kingzcheung.xime.association

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EnglishAssociationStartupTest {
    @Before fun resetBefore() = AssociationService.release()
    @After fun resetAfter() = AssociationService.release()

    private fun bundledTrie(): ByteArray = listOf(
        File("src/main/assets/english_trie.bin"),
        File("app/src/main/assets/english_trie.bin")
    ).first { it.isFile }.readBytes()

    private fun context(open: () -> InputStream): Context {
        val assets = mock<AssetManager>()
        whenever(assets.list("")).thenReturn(arrayOf("english_trie.bin"))
        whenever(assets.open("english_trie.bin")).thenAnswer { open() }
        return mock<Context>().also { whenever(it.assets).thenReturn(assets) }
    }

    @Test fun firstEnglishRequestLoadsBundledWordsWithoutPriorWarmUp() = runBlocking {
        val caller = Thread.currentThread()
        val bytes = bundledTrie()
        val context = context {
            assertNotSame("Asset loading must not block the calling thread", caller, Thread.currentThread())
            bytes.inputStream()
        }

        val words = AssociationService.getAssociations(context, "hel", isAsciiMode = true)

        assertEquals("help", words.first())
        assertTrue("Built-in word source should provide hello", "hello" in words)
    }

    @Test fun simultaneousFirstWordsWaitForAndReuseBackgroundWarmUp() = runBlocking {
        val bytes = bundledTrie()
        val opens = AtomicInteger()
        val enteredLoad = CountDownLatch(1)
        val finishLoad = CountDownLatch(1)
        val enteredRequests = CountDownLatch(8)
        val context = context {
            opens.incrementAndGet()
            enteredLoad.countDown()
            check(finishLoad.await(5, TimeUnit.SECONDS)) { "Timed out waiting to finish test asset load" }
            bytes.inputStream()
        }
        val warmUp = async(Dispatchers.Default) { AssociationService.initialize(context) }
        try {
            assertTrue("Warm-up should begin loading", enteredLoad.await(5, TimeUnit.SECONDS))
            val requests = List(8) {
                async(Dispatchers.Default) {
                    enteredRequests.countDown()
                    AssociationService.getAssociations(context, "hel", isAsciiMode = true)
                }
            }
            assertTrue("First-word requests should overlap loading", enteredRequests.await(5, TimeUnit.SECONDS))
            finishLoad.countDown()
            assertTrue(warmUp.await())
            requests.awaitAll().forEach { words ->
                assertEquals("First keystroke must not disappear during warm-up", "help", words.first())
                assertTrue("hello" in words)
            }
            assertEquals("Concurrent requests should load the dictionary once", 1, opens.get())
        } finally {
            finishLoad.countDown()
        }
    }

    @Test fun failedInitialLoadCanRecoverOnTheNextEnglishRequest() = runBlocking {
        val unavailable = context { throw IOException("Temporarily unavailable assets") }
        assertTrue(AssociationService.getAssociations(unavailable, "hel", isAsciiMode = true).isEmpty())
        assertFalse(AssociationService.isInitialized())

        val bytes = bundledTrie()
        val available = context { bytes.inputStream() }
        val words = AssociationService.getAssociations(available, "hel", isAsciiMode = true)

        assertEquals("help", words.first())
        assertTrue("hello" in words)
        assertTrue(AssociationService.isTrieInitialized())
    }
}
