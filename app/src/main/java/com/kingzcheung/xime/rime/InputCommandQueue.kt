package com.kingzcheung.xime.rime

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.ContinuationInterceptor

/** One FIFO for a complete input action, including its suspended editor delivery and cleanup. */
class InputCommandQueue(parent: CoroutineScope, dispatcher: CoroutineDispatcher) : AutoCloseable {
    private val lifetime = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + lifetime + dispatcher)
    private data class Entry(val turn: CompletableDeferred<Unit>, val job: Job)
    private val jobs = Channel<Entry>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (entry in jobs) {
                entry.turn.complete(Unit)
                entry.job.join()
            }
        }
    }

    fun submit(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        val turn = CompletableDeferred<Unit>()
        val job = scope.launch(context + current.asContextElement(this)) {
            turn.await()
            block()
        }
        enqueue(turn, job)
        return job
    }

    /** A nested engine step belongs to the current action; an external call gets its own FIFO slot. */
    suspend fun <T> execute(block: suspend () -> T): T {
        if (current.get() === this) return block()
        val caller = currentCoroutineContext().minusKey(Job).minusKey(ContinuationInterceptor)
        val turn = CompletableDeferred<Unit>()
        val result = scope.async(caller + current.asContextElement(this)) { turn.await(); block() }
        enqueue(turn, result)
        try { return result.await() }
        finally { result.cancel() }
    }

    private fun enqueue(turn: CompletableDeferred<Unit>, job: Job) {
        if (!jobs.trySend(Entry(turn, job)).isSuccess) job.cancel()
    }

    override fun close() {
        jobs.close()
        lifetime.cancel()
    }

    private companion object {
        val current = ThreadLocal<InputCommandQueue?>()
    }
}
