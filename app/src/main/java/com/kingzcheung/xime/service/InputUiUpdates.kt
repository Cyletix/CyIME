package com.kingzcheung.xime.service

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun consumeInputUiUpdates(
    updates: ReceiveChannel<suspend () -> Unit>,
    onFailure: (Exception) -> Unit,
) {
    for (work in updates) {
        try {
            work()
        } catch (cancelled: CancellationException) {
            // An expired editor cancels only its snapshot; destroying the service still
            // stops this consumer. Otherwise every subsequent key would lose its UI update.
            currentCoroutineContext().ensureActive()
        } catch (failure: Exception) {
            onFailure(failure)
        }
    }
}
