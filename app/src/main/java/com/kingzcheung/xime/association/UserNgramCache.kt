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
                    if (root.has("profileReference")) {
                        val reference = root.getString("profileReference")
                        require(reference.matches(Regex("[a-f0-9]{64}"))) { "无效的个人方案引用" }
                        val profile = File(cacheFile.parentFile, "learning-profiles/$reference.json")
                        root.put("profile", org.json.JSONObject(profile.readText()))
                    }
                    replaceInMemory(PersonalLearningData.decode(root.toString()))
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
        val results = bigramTrie.next(tokens.takeLast(1)).toMap().toMutableMap()
        if (tokens.size >= 2) trigramTrie.next(tokens.takeLast(2)).forEach { (text, score) ->
            results[text] = maxOf(results[text] ?: 0f, score)
        }
        results.entries.sortedByDescending { it.value }.take(topK).map { it.key to it.value }
    }

    fun profileCandidates(context: String): List<AssociationCandidate> = synchronized(stateLock) { profileIndex.predict(context) }
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
    }
    suspend fun save() {
        val caller = currentCoroutineContext()[Job]
        synchronized(stateLock) {
            if (pendingSave !== caller) pendingSave?.cancel()
            pendingSave = null
        }
        withContext(Dispatchers.IO) { fileMutex.withLock {
            check(loaded) { "学习数据尚未成功读取，不能覆盖保存" }
            write(snapshot())
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
