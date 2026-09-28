package com.kingzcheung.xime.speech

import android.media.AudioRecord

/** One recording session owns its microphone independently of the retained ASR model. */
internal class RecordingAudioOwner(initial: AudioRecord? = null) {
    private val lock = Any()
    private var record = initial
    private var closed = false

    fun acquire(factory: () -> AudioRecord?): AudioRecord? {
        synchronized(lock) {
            if (closed) return null
            record?.let { return it }
        }
        val created = factory() ?: return null
        return synchronized(lock) {
            if (closed) { dispose(created); null }
            else { record = created; created }
        }
    }

    fun start(): Boolean = synchronized(lock) {
        val current = record
        if (closed || current == null) return false
        if (current.recordingState != AudioRecord.RECORDSTATE_RECORDING) current.startRecording()
        true
    }

    fun close() = synchronized(lock) {
        closed = true
        val current = record
        record = null
        if (current != null) dispose(current)
    }

    private fun dispose(current: AudioRecord) {
        try { current.stop() } catch (_: Exception) { }
        try { current.release() } catch (_: Exception) { }
    }
}
