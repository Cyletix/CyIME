package com.kingzcheung.xime.association

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NgramFusionEngine(private val context: Context, private val userNgramCache: UserNgramCache = UserNgramCache(context)) {
    companion object {
        private const val TAG = "NgramFusionEngine"
        private const val DEFAULT_LAMBDA = 0.7f
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
        
        val userCandidates = userNgramCache.getUserCandidates(context, 10)
        
        val allCandidates = mutableMapOf<String, Float>()
        
        baseModel?.predict(context, 10)?.forEach { allCandidates[it.text] = it.score }
        normalizeModelCandidates(modelCandidates).forEach { candidate ->
            allCandidates[candidate.text] = candidate.score
        }
        userNgramCache.profileCandidates(context).forEach { candidate ->
            allCandidates[candidate.text] = maxOf(allCandidates[candidate.text] ?: 0f, candidate.score)
        }
        
        userCandidates.forEach { (word, userScore) ->
            val existingScore = allCandidates[word]
            if (existingScore != null) {
                val effectiveLambda = 0.3f
                allCandidates[word] = effectiveLambda * existingScore + (1 - effectiveLambda) * userScore
            } else {
                allCandidates[word] = userScore
            }
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
