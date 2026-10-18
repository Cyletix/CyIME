package com.kingzcheung.xime.association

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NgramFusionEngine(private val context: Context, private val userNgramCache: UserNgramCache = UserNgramCache(context)) {
    companion object {
        private const val TAG = "NgramFusionEngine"
        private const val DEFAULT_LAMBDA = 0.7f
        /** Mixture weights over probabilities: corpus table, downloaded neural model, personal data. */
        const val BASE_WEIGHT = 0.7f
        const val MODEL_WEIGHT = 0.15f
        const val USER_WEIGHT = 0.15f
        /** Typed observations reach full weight after this many events on the same context. */
        const val USER_CONFIDENCE_EVENTS = 8f
    }
    
    private var lambda = DEFAULT_LAMBDA
    private var isInitialized = false
    private var baseModel: BaseAssociationModel? = null
    
    suspend fun initialize(): Boolean {
        if (isInitialized) return true
        
        val result = userNgramCache.initialize()
        baseModel = withContext(Dispatchers.IO) {
            try {
                context.assets.open(BaseAssociationModel.ASSET).use { input ->
                    java.util.zip.GZIPInputStream(input).reader(Charsets.UTF_8).use(BaseAssociationModel::read)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Bundled association model failed to load", error)
                null
            }
        }
        isInitialized = result || baseModel != null
        return isInitialized
    }
    
    fun recordUserInput(text: String) {
        userNgramCache.recordInput(text)
    }
    
    fun fuseCandidates(
        modelCandidates: List<AssociationCandidate>,
        context: String
    ): List<AssociationCandidate> {
        if (!isInitialized) {
            return normalizeModelCandidates(modelCandidates)
        }
        
        // One scale: every source contributes a probability of the continuation given the
        // context, mixed with fixed weights. The corpus table leads; the optional neural model
        // (a softmax share over its own top-k, not calibrated) and personal observations only
        // add a bounded share, so a rare long phrase cannot outrank 个/是/些 after 这 on its own.
        val allCandidates = mutableMapOf<String, Float>()
        baseModel?.predict(context, BaseAssociationModel.MAX_PER_CONTEXT)?.forEach {
            allCandidates[it.text] = BASE_WEIGHT * it.score
        }
        normalizeModelCandidates(modelCandidates).forEach { candidate ->
            allCandidates[candidate.text] = (allCandidates[candidate.text] ?: 0f) + MODEL_WEIGHT * candidate.score
        }
        // Imported personal corpora carry their own counts (0.4·share); typed observations are
        // shares over few events, so they earn weight gradually (n/USER_CONFIDENCE_EVENTS).
        userNgramCache.profileCandidates(context).forEach { candidate ->
            allCandidates[candidate.text] = (allCandidates[candidate.text] ?: 0f) + USER_WEIGHT * candidate.score / 0.4f
        }
        val userCandidates = userNgramCache.getUserCandidates(context, 10)
        val confidence = minOf(1f, userNgramCache.observationCount(context) / USER_CONFIDENCE_EVENTS)
        userCandidates.forEach { (word, userScore) ->
            allCandidates[word] = (allCandidates[word] ?: 0f) + USER_WEIGHT * confidence * userScore
        }

        return allCandidates.map { (word, score) ->
            AssociationCandidate(word, score)
        }.sortedByDescending { it.score }
    }
    
    suspend fun saveCache() {
        userNgramCache.save()
    }
    
    fun clearCache() {
        userNgramCache.clear()
    }
    
    fun getCacheSize(): Int = userNgramCache.getCacheSize()
    fun learningData(): PersonalLearningData = userNgramCache.snapshot()
    suspend fun importLearningData(data: PersonalLearningData) = userNgramCache.importData(data)
    
    fun isInitialized(): Boolean = isInitialized
}
