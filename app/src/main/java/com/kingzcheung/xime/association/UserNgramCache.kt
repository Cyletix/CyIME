package com.kingzcheung.xime.association

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class UserNgramCache(context: Context) {
    private val cacheFile = File(context.filesDir, "user_ngram_cache.json")
    private val bigramTrie = NgramTrie()
    private val trigramTrie = NgramTrie()
    private val recentInputs = mutableListOf<String>()
    private var profileName = ""
    private var continuations = emptyList<PersonalContinuation>()
    private var profileIndex = PersonalContinuationIndex(emptyList())
    private val stateLock = Any()
    private val fileMutex = Mutex()
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSave: Job? = null
    private var loaded = false
    private var storedProfileRows: List<PersonalContinuation>? = null
    private var storedProfileReference: String? = null

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!loaded) {
                if (cacheFile.exists()) {
                    val root = org.json.JSONObject(AtomicFile(cacheFile).readFully().toString(Charsets.UTF_8))
                    val reference = if (root.has("profileReference")) root.getString("profileReference") else null
                    if (reference != null) {
                        require(reference.matches(Regex("[a-f0-9]{64}"))) { "无效的个人方案引用" }
                        val profile = File(cacheFile.parentFile, "learning-profiles/$reference.json")
                        root.put("profile", org.json.JSONObject(profile.readText()))
                    }
                    // 本机文件按存储上限读入：长期使用超过条目或次数上限时裁剪后继续学习，不再读取失败而冻结。
                    val data = PersonalLearningData.decode(root.toString(), storedLocally = true)
                    replaceInMemory(data)
                    // 个人语料已在不可变文件里：记下它，之后的保存只写小的观察文件，
                    // 不再把几万条语料整份重新编码、算哈希（旧版内嵌格式仍在首次保存时迁移）。
                    if (reference != null) synchronized(stateLock) {
                        storedProfileRows = continuations
                        storedProfileReference = reference
                    }
                }
                loaded = true
            }
            true
        }
    }

    fun recordInput(text: String) {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return
        synchronized(stateLock) {
            recentInputs.addAll(tokens)
            if (recentInputs.size > 100) recentInputs.subList(0, recentInputs.size - 100).clear()
            tokens.windowed(2).forEach { bigramTrie.insert(it) }
            tokens.windowed(3).forEach { trigramTrie.insert(it) }
            pendingSave?.cancel()
            pendingSave = saveScope.launch {
                delay(500)
                try { save() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Log.e("UserNgramCache", "Automatic learning save failed", error) }
            }
        }
    }

    private fun tokenize(text: String): List<String> = text.filter {
        it.isLetterOrDigit() || it in "，。！？、；：\"' \n\t"
    }.map { it.toString() }

    fun getUserCandidates(context: String, topK: Int = 5): List<Pair<String, Float>> = synchronized(stateLock) {
        val tokens = tokenize(context)
        if (tokens.isEmpty()) return@synchronized emptyList()
        val results = predictLearnedContinuation(bigramTrie, trigramTrie, tokens)
        results.sortedByDescending { it.second }.take(topK)
    }

    fun profileCandidates(context: String): List<AssociationCandidate> = synchronized(stateLock) { profileIndex.predict(context) }

    /** Observations behind [getUserCandidates] for this context: the trigram node when it has any, else the bigram node. */
    fun observationCount(context: String): Long = synchronized(stateLock) {
        val tokens = tokenize(context)
        if (tokens.isEmpty()) return@synchronized 0L
        val longer = if (tokens.size >= 2) trigramTrie.observations(tokens.takeLast(2)) else 0L
        if (longer > 0) longer else bigramTrie.observations(tokens.takeLast(1))
    }
    fun snapshot(): PersonalLearningData = synchronized(stateLock) {
        PersonalLearningData(bigramTrie.getAllEntries().map { LearnedSequence(it.first, it.second) },
            trigramTrie.getAllEntries().map { LearnedSequence(it.first, it.second) }, recentInputs.toList(), profileName, continuations)
    }
    private fun replaceInMemory(data: PersonalLearningData) = synchronized(stateLock) {
        bigramTrie.clear(); trigramTrie.clear()
        data.bigrams.forEach { bigramTrie.insert(it.tokens, it.count) }
        data.trigrams.forEach { trigramTrie.insert(it.tokens, it.count) }
        recentInputs.clear(); recentInputs.addAll(data.recentInputs)
        profileName = data.profileName
        continuations = data.continuations
        profileIndex = PersonalContinuationIndex(continuations)
    }
    private fun write(data: PersonalLearningData) {
        // Large imported corpora are immutable blobs. Ordinary typing rewrites only
        // the small observation file, not the entire corpus after every pause.
        if (storedProfileRows !== data.continuations) {
            storedProfileReference = if (data.continuations.isEmpty()) null else {
                val profile = org.json.JSONObject(data.encode()).getJSONObject("profile").toString().toByteArray(Charsets.UTF_8)
                val hash = java.security.MessageDigest.getInstance("SHA-256").digest(profile).joinToString("") { "%02x".format(it) }
                val file = File(cacheFile.parentFile, "learning-profiles/$hash.json")
                if (!file.exists()) {
                    check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
                    val atomic = AtomicFile(file)
                    val stream = atomic.startWrite()
                    try { stream.write(profile); atomic.finishWrite(stream) }
                    catch (error: Exception) { atomic.failWrite(stream); throw error }
                }
                hash
            }
            storedProfileRows = data.continuations
        }
        val root = org.json.JSONObject(data.copy(profileName = "", continuations = emptyList()).encode())
        storedProfileReference?.let { root.put("profileReference", it) }
        val atomic = AtomicFile(cacheFile)
        val stream = atomic.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
        removeUnreferencedProfiles(storedProfileReference)
    }

    /** 换方案后旧语料文件不再被引用（导入前快照自带完整语料），观察文件写成功后再删。 */
    private fun removeUnreferencedProfiles(current: String?) {
        File(cacheFile.parentFile, "learning-profiles").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") && it.name != "$current.json" }
            ?.forEach { if (!it.delete()) Log.w("UserNgramCache", "Unable to remove old learning profile ${it.name}") }
    }
    suspend fun save() {
        val caller = currentCoroutineContext()[Job]
        synchronized(stateLock) {
            if (pendingSave !== caller) pendingSave?.cancel()
            pendingSave = null
        }
        withContext(Dispatchers.IO) { fileMutex.withLock {
            check(loaded) { "学习数据尚未成功读取，不能覆盖保存" }
            val data = snapshot()
            val bounded = data.boundedForStorage()
            // 越过上限时内存里也换成裁剪后的数据，否则下次保存又要整份排序一次。
            if (bounded !== data) replaceInMemory(bounded)
            write(bounded)
        } }
    }
    suspend fun importData(data: PersonalLearningData) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            // Keep an exact recoverable pre-import snapshot. Validation happens before this method.
            File(cacheFile.parentFile, "user_learning_before_import.json").writeText(snapshot().encode())
            write(data)
            replaceInMemory(data)
        }
    }
    fun clear() = synchronized(stateLock) {
        replaceInMemory(PersonalLearningData())
        pendingSave?.cancel()
        pendingSave = saveScope.launch { save() }
    }
    fun getCacheSize(): Int = synchronized(stateLock) { bigramTrie.size() + trigramTrie.size() }
}
