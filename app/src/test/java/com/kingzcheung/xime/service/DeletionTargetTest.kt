package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Test

class DeletionTargetTest {
    @Test fun delayedCandidateBarCannotMakeCompositionDeleteTheDocument() {
        assertEquals(DeletionTarget.COMPOSITION, deletionTarget(true, false, false, false, false, false))
    }
    @Test fun partiallySelectedT9WordsStillBelongToTheIme() {
        assertEquals(DeletionTarget.COMPOSITION, deletionTarget(false, false, true, false, false, false))
    }
    @Test fun busyEngineNeverAuthorizesDocumentDeletion() {
        assertEquals(DeletionTarget.WAIT, deletionTarget(null, false, false, false, false, false))
    }
    @Test fun predictionBeforeAndAfterDeliveryHasTheSameDeleteTarget() {
        assertEquals(DeletionTarget.CANDIDATES, deletionTarget(false, false, false, false, false, true))
        assertEquals(DeletionTarget.CANDIDATES, deletionTarget(false, false, false, false, true, false))
    }
    /** 「退格直接删字」：联想挡着的那一下改成删字；关着、剪贴板胶囊、打字中都不变。 */
    @Test fun deleteSkipsAssociationOnlyForAssociations() {
        val association = deletionTarget(false, false, false, false, true, false)
        val inFlight = deletionTarget(false, false, false, false, false, true)
        assertEquals(DeletionTarget.CANDIDATES, association.skippingAssociation(enabled = false, clipboardShown = false))
        assertEquals(DeletionTarget.DOCUMENT, association.skippingAssociation(enabled = true, clipboardShown = false))
        assertEquals(DeletionTarget.DOCUMENT, inFlight.skippingAssociation(enabled = true, clipboardShown = false))
        assertEquals(DeletionTarget.CANDIDATES, association.skippingAssociation(enabled = true, clipboardShown = true))
        for (other in listOf(DeletionTarget.WAIT, DeletionTarget.COMPOSITION, DeletionTarget.ENGLISH, DeletionTarget.DOCUMENT))
            assertEquals(other, other.skippingAssociation(enabled = true, clipboardShown = false))
    }
    @Test fun idleEditorAndDirectEnglishStillDeleteNormally() {
        assertEquals(DeletionTarget.DOCUMENT, deletionTarget(false, false, false, false, false, false))
        assertEquals(DeletionTarget.ENGLISH, deletionTarget(false, false, false, true, false, true))
    }
}
