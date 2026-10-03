package com.kingzcheung.xime.association

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object AssociationService {
    private const val TAG = "AssociationService"
    
    @Volatile private var trieEngine: TrieAssociationEngine? = null
    @Volatile private var isInitialized = false
    private val initializationMutex = Mutex()
    
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) {
            return@withContext true
        }
        
        initializationMutex.withLock {
            if (isInitialized) return@withLock true

            try {
                val engine = TrieAssociationEngine.getInstance()
                val trieInit = engine.initialize(context)
                if (trieInit) trieEngine = engine
                isInitialized = trieInit
                Log.i(TAG, "Association service initialized: trie=$trieInit")
                trieInit
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize association service", e)
                false
            }
        }
    }
    
    suspend fun getAssociations(
        context: Context,
        inputText: String,
        isAsciiMode: Boolean,
        topK: Int = 5
    ): List<String> = withContext(Dispatchers.Default) {
        if (inputText.isEmpty()) {
            return@withContext emptyList()
        }
        
        try {
            val candidates = if (isAsciiMode) {
                getEnglishAssociations(context, inputText, topK)
            } else {
                getChineseAssociations(context, inputText, topK)
            }
            
            candidates.map { it.text }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get associations", e)
            emptyList()
        }
    }
    
    private suspend fun getEnglishAssociations(
        context: Context,
        prefix: String,
        topK: Int
    ): List<AssociationCandidate> {
        // A first keystroke can precede PredictionManager's background warm-up.
        // Await that same IO initialization so it can still produce suggestions.
        if (!initialize(context)) {
            Log.w(TAG, "Trie engine could not initialize for English associations")
            return emptyList()
        }

        return trieEngine?.predict(prefix, topK).orEmpty()
    }
    
    private suspend fun getChineseAssociations(
        context: Context,
        inputText: String,
        topK: Int
    ): List<AssociationCandidate> = withContext(Dispatchers.Default) {
        try {
            if (!AssociationManager.isInitialized()) {
                val initSuccess = withContext(Dispatchers.IO) {
                    AssociationManager.initialize(context)
                }
                if (!initSuccess) {
                    Log.e(TAG, "AssociationManager initialization failed")
                    return@withContext emptyList()
                }
            }
            
            val candidates = AssociationManager.predict(inputText, topK)
            
            candidates
        } catch (e: Exception) {
            Log.e(TAG, "Chinese associations failed", e)
            emptyList()
        }
    }
    
    fun isInitialized(): Boolean = isInitialized
    
    fun isTrieInitialized(): Boolean = trieEngine?.isInitialized() ?: false
    
    fun release() {
        trieEngine?.release()
        trieEngine = null
        isInitialized = false
    }
}
