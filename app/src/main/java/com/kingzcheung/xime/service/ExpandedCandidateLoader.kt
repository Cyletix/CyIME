package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeCandidate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Main owns requests/publication. Native generation runs in bounded background batches. */
internal class ExpandedCandidateLoader(
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher,
    private val readBatch: (revision: Long, offset: Int, count: Int) -> List<RimeCandidate>?,
) {
    private var request = 0L
    private var job: Job? = null

    fun cancel() { request++; job?.cancel(); job = null }

    fun load(revision: Long, isCurrent: () -> Boolean, publish: (List<RimeCandidate>) -> Unit) {
        cancel()
        if (revision == 0L) return
        val id = request
        job = scope.launch {
            val candidates = ArrayList<RimeCandidate>()
            while (id == request && isCurrent() && candidates.size < MAX_CANDIDATES) {
                val count = minOf(BATCH_SIZE, MAX_CANDIDATES - candidates.size)
                val batch = withContext(worker) { readBatch(revision, candidates.size, count) } ?: return@launch
                if (id != request || !isCurrent()) return@launch
                candidates.addAll(batch)
                publish(candidates.toList())
                if (batch.size < count) return@launch
            }
        }
    }

    companion object {
        const val BATCH_SIZE = 64
        const val MAX_CANDIDATES = 500
    }
}
