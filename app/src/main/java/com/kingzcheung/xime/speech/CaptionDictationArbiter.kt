package com.kingzcheung.xime.speech

/**
 * Who gets the shared engine when captions and keyboard dictation both want it (user 2026-10-22):
 * tapping the keyboard mic is an explicit choice and wins, so running captions are put on hold and
 * resume when dictation ends. Captions starting on their own (call captions, retries) never
 * interrupt dictation; they wait on hold and start when it ends.
 *
 * Every decision runs inside [engine]'s state lock, the same one each engine start checks first,
 * so a claim made here is seen before any start can replace a session.
 */
internal class CaptionDictationArbiter<C : Any>(
    private val engine: ResidentSpeechSessionOwner,
    /** The binder identity of a caption callback. */
    private val ownerOf: (C) -> Any,
) {
    @Volatile var captionOwner: Any? = null; private set
    private var captionCallback: C? = null
    private var held: C? = null
    /** Dictation starts in progress, before the engine shows them as starting. */
    private var dictationClaims = 0

    /**
     * Dictation is starting. Returns the caption that must give way (cancel its engine session and
     * tell it onHold(true)), or null. Always pair with [dictationStartDone].
     */
    fun dictationStarting(): C? = engine.atomically {
        dictationClaims++
        val yielded = captionCallback.takeIf { captionOwner != null }
        if (yielded != null) { captionOwner = null; captionCallback = null; held = yielded }
        yielded
    }

    fun dictationStartDone() = engine.atomically { dictationClaims-- }

    /** Captions want to start: true = go ahead (claimed); false = dictation is on, [callback] now waits on hold. */
    fun claimCaption(callback: C): Boolean = engine.atomically {
        val owner = ownerOf(callback)
        // Busy with no caption claim means dictation; another caption owner is still replaced.
        val dictating = dictationClaims > 0 || (engine.busyWithOther(owner) && captionOwner == null)
        if (dictating) { held = callback; false }
        else { captionOwner = owner; captionCallback = callback; held = null; true }
    }

    /** A failed or superseded caption start gives its claim back, if [stillCurrent]. */
    fun releaseClaim(owner: Any, stillCurrent: () -> Boolean) = engine.atomically {
        if (captionOwner == owner && stillCurrent()) { captionOwner = null; captionCallback = null }
    }

    /** The caption page or bar closed: forget its claim and any hold waiting for dictation. */
    fun dropCaption(owner: Any) = engine.atomically {
        if (captionOwner == owner) { captionOwner = null; captionCallback = null }
        if (held?.let(ownerOf) == owner) held = null
    }

    fun isHeld(owner: Any): Boolean = engine.atomically { held?.let(ownerOf) == owner }

    /** Dictation may be over: the caption waiting on hold, to be told onHold(false), or null. */
    fun takeResumable(): C? = engine.atomically {
        if (dictationClaims > 0 || captionOwner != null || !engine.isIdle) null else held.also { held = null }
    }

    /** Service shutdown. */
    fun clear() = engine.atomically { captionOwner = null; captionCallback = null; held = null }
}
