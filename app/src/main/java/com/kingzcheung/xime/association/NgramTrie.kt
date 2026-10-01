package com.kingzcheung.xime.association

data class TrieNode(
    val children: MutableMap<String, TrieNode> = mutableMapOf(),
    var count: Int = 0,
    var frequency: Float = 0f,
    var prefixCount: Int = 0
)

class NgramTrie {
    private val root = TrieNode()
    
    fun insert(ngram: List<String>, count: Int = 1) {
        require(count > 0)
        if (ngram.isEmpty()) return
        
        var node = root
        var isNewPath = false
        
        for (i in ngram.indices) {
            val token = ngram[i]
            
            if (!node.children.containsKey(token)) {
                isNewPath = true
            }
            
            val nextNode = node.children.getOrPut(token) { TrieNode() }
            
            if (isNewPath) {
                nextNode.prefixCount++
            }
            
            node = nextNode
        }
        
        node.count = Math.addExact(node.count, count)
        updateFrequency(node)
    }
    
    private fun updateFrequency(node: TrieNode) {
        if (node.prefixCount > 0) {
            node.frequency = (node.count.toFloat() / node.prefixCount).coerceIn(0f, 1f)
        }
    }
    
    fun getFrequency(ngram: List<String>): Float {
        if (ngram.isEmpty()) return 0f
        
        var node = root
        
        for (token in ngram.dropLast(1)) {
            node = node.children[token] ?: return 0f
        }
        
        val prefixCount = node.children.values.sumOf { it.count.toLong() }
        if (prefixCount == 0L) return 0f
        
        val lastToken = ngram.last()
        val lastNode = node.children[lastToken] ?: return 0f
        
        val frequency = lastNode.count.toFloat() / prefixCount.toFloat()
        
        return frequency.coerceIn(0f, 1f)
    }

    fun next(prefix: List<String>): List<Pair<String, Float>> {
        var node = root
        for (token in prefix) node = node.children[token] ?: return emptyList()
        val total = node.children.values.sumOf { it.count.toLong() }.toFloat()
        if (total == 0f) return emptyList()
        return node.children.map { (token, child) -> token to child.count / total }
    }
    
    fun getAllEntries(): List<Pair<List<String>, Int>> {
        val entries = mutableListOf<Pair<List<String>, Int>>()
        collectEntries(root, emptyList(), entries)
        return entries
    }
    
    private fun collectEntries(
        node: TrieNode,
        path: List<String>,
        entries: MutableList<Pair<List<String>, Int>>
    ) {
        if (node.count > 0) {
            entries.add(path to node.count)
        }
        node.children.forEach { (token, childNode) ->
            collectEntries(childNode, path + token, entries)
        }
    }
    
    fun clear() {
        root.children.clear()
        root.count = 0
        root.frequency = 0f
        root.prefixCount = 0
    }
    
    fun size(): Int {
        var count = 0
        fun countNodes(node: TrieNode) {
            count += node.count
            node.children.values.forEach { countNodes(it) }
        }
        countNodes(root)
        return count
    }
}
