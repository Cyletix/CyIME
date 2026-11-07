package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.kingzcheung.xime.service.CandidateState

/** Observe list exhaustion without subscribing the entire keyboard to every keystroke. */
@Composable
internal fun CollapseEmptyCandidatePage(
    expanded: Boolean,
    candidates: State<CandidateState>,
    isDeleteDragging: () -> Boolean = { false },
    onCollapse: () -> Unit,
) {
    val currentCollapse = rememberUpdatedState(onCollapse)
    val currentDragging = rememberUpdatedState(isDeleteDragging)
    LaunchedEffect(expanded, candidates) {
        if (expanded) {
            snapshotFlow {
                !currentDragging.value() && candidates.value.let { it.candidates.isEmpty() && it.associationCandidates.isEmpty() }
            }.collect { empty -> if (empty) currentCollapse.value() }
        }
    }
}
