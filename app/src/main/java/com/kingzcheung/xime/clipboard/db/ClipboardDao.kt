package com.kingzcheung.xime.clipboard.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Characters ignored at both ends when two clips are compared: copying "地址" and "地址\n" (or with a
 * full-width / no-break space) is the same record (user 10-30: 剪贴板去重).
 */
internal const val CLIP_TRIM = "' ' || char(9) || char(10) || char(13) || char(12288) || char(160)"

@Dao
interface ClipboardDao {

    @Query("SELECT * FROM clipboard_entries WHERE isQuickSend = 0 ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ClipboardEntry>>

    /**
     * Every fixed record plus the newest [limit] unfixed ones. The board grows [limit] page by
     * page as it scrolls, so a history of thousands is never read into memory at once.
     */
    @Query("SELECT * FROM clipboard_entries WHERE isQuickSend = 0 AND (isPinned = 1 OR id IN " +
        "(SELECT id FROM clipboard_entries WHERE isQuickSend = 0 AND isPinned = 0 ORDER BY timestamp DESC LIMIT :limit)) " +
        "ORDER BY timestamp DESC")
    fun observeWindow(limit: Int): Flow<List<ClipboardEntry>>

    @Query("SELECT COUNT(*) FROM clipboard_entries WHERE isPinned = 0 AND isQuickSend = 0")
    fun observeUnpinnedCount(): Flow<Int>

    /** Text fragment or pinyin initials, anywhere; fixed records first, then newest. */
    @Query("SELECT * FROM clipboard_entries WHERE isQuickSend = 0 AND " +
        "(text LIKE :pattern ESCAPE '\\' OR searchKey LIKE :pattern ESCAPE '\\') " +
        "ORDER BY isPinned DESC, timestamp DESC LIMIT :limit")
    suspend fun search(pattern: String, limit: Int): List<ClipboardEntry>

    /** Pinyin initials only (a word still being typed); fixed records first, then newest. */
    @Query("SELECT * FROM clipboard_entries WHERE isQuickSend = 0 AND searchKey LIKE :pattern " +
        "ORDER BY isPinned DESC, timestamp DESC LIMIT :limit")
    suspend fun searchKey(pattern: String, limit: Int): List<ClipboardEntry>

    /** Initials as 9-key digits ([pattern] holds digits): the search key read through the keypad. */
    @Query("SELECT * FROM clipboard_entries WHERE isQuickSend = 0 AND " +
        com.kingzcheung.xime.clipboard.ClipboardSearch.T9_KEY_SQL +
        " LIKE :pattern ORDER BY isPinned DESC, timestamp DESC LIMIT :limit")
    suspend fun searchKeyDigits(pattern: String, limit: Int): List<ClipboardEntry>

    @Query("SELECT id, substr(text, 1, 2000) AS text FROM clipboard_entries WHERE searchKey = '' AND isQuickSend = 0 LIMIT :limit")
    suspend fun missingSearchKeys(limit: Int): List<SearchKeySource>

    @Query("UPDATE clipboard_entries SET searchKey = :key WHERE id = :id")
    suspend fun setSearchKey(id: Long, key: String)

    /** Compatibility feed for the existing plugin API: fixed text and its optional trigger code. */
    @Query("SELECT * FROM clipboard_entries WHERE isPinned = 1 AND isQuickSend = 0 ORDER BY timestamp DESC")
    fun observeQuickSend(): Flow<List<ClipboardEntry>>

    @Query("SELECT * FROM clipboard_entries WHERE text = :text AND isQuickSend = 0 ORDER BY isPinned ASC, timestamp DESC, id DESC LIMIT 1")
    suspend fun findByText(text: String): ClipboardEntry?

    /** The record holding this text, ends' whitespace ignored: a history copy first, else a fixed one. */
    @Query("SELECT * FROM clipboard_entries WHERE isQuickSend = 0 AND trim(text, " + CLIP_TRIM + ") = trim(:text, " + CLIP_TRIM + ") " +
        "ORDER BY isPinned ASC, timestamp DESC, id DESC LIMIT 1")
    suspend fun findSameText(text: String): ClipboardEntry?

    @Query("SELECT * FROM clipboard_entries WHERE isQuickSend = 0 AND isPinned = 1 AND code = :code AND " +
        "trim(text, " + CLIP_TRIM + ") = trim(:text, " + CLIP_TRIM + ") ORDER BY timestamp DESC LIMIT 1")
    suspend fun findPinnedSameText(text: String, code: String): ClipboardEntry?

    /** Other history copies of this text; fixed records are never removed here. */
    @Query("DELETE FROM clipboard_entries WHERE isQuickSend = 0 AND isPinned = 0 AND id != :keepId AND " +
        "trim(text, " + CLIP_TRIM + ") = trim(:text, " + CLIP_TRIM + ")")
    suspend fun deleteHistoryCopies(text: String, keepId: Long)

    /**
     * One record per text: drops history copies of a fixed text and, among history copies, all but
     * the newest. Fixed records (and their codes) are kept as they are.
     */
    @Query("DELETE FROM clipboard_entries WHERE isQuickSend = 0 AND isPinned = 0 AND (" +
        "trim(text, " + CLIP_TRIM + ") IN (SELECT trim(text, " + CLIP_TRIM + ") FROM clipboard_entries WHERE isQuickSend = 0 AND isPinned = 1) " +
        "OR id NOT IN (SELECT id FROM (SELECT id, MAX(timestamp) FROM clipboard_entries WHERE isQuickSend = 0 AND isPinned = 0 " +
        "GROUP BY trim(text, " + CLIP_TRIM + "))))")
    suspend fun deleteDuplicateHistory(): Int

    /** A copy differing only in surrounding whitespace replaces the history record's text. */
    @Query("UPDATE clipboard_entries SET text = :text, searchKey = :searchKey, timestamp = :timestamp, consumed = 0 " +
        "WHERE id = :id AND isQuickSend = 0")
    suspend fun replaceCopiedText(id: Long, text: String, searchKey: String, timestamp: Long)

    @Query("UPDATE clipboard_entries SET isPinned = 1, code = :code WHERE id = :id AND isQuickSend = 0")
    suspend fun pinWithCode(id: Long, code: String)

    /** Atomic and idempotent; no deduplication may discard different codes on equal text. */
    @Query("UPDATE clipboard_entries SET isPinned = 1, isQuickSend = 0 WHERE isQuickSend = 1")
    suspend fun migrateQuickSendToPinned()

    @Query("SELECT * FROM clipboard_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ClipboardEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipboardEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ClipboardEntry>)

    @Query("UPDATE clipboard_entries SET timestamp = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long)

    @Query("UPDATE clipboard_entries SET isPinned = :pinned WHERE id = :id AND isQuickSend = 0")
    suspend fun setClipboardPinned(id: Long, pinned: Boolean)

    // 再次复制是新的用户动作，同一条历史记录也应重新出现在预览栏。
    @Query("UPDATE clipboard_entries SET timestamp = :timestamp, consumed = 0 WHERE id = :id AND isQuickSend = 0")
    suspend fun refreshCopiedItem(id: Long, timestamp: Long)


    @Query("DELETE FROM clipboard_entries WHERE isQuickSend = 0 AND id = :id")
    suspend fun deleteClipboardById(id: Long)

    @Query("DELETE FROM clipboard_entries WHERE isQuickSend = 0 AND id IN (:ids)")
    suspend fun deleteClipboardByIds(ids: List<Long>)

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0 AND isQuickSend = 0")
    suspend fun clearAllClipboard()

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0 AND isQuickSend = 0")
    suspend fun clearUnpinned()

    @Query("SELECT COUNT(*) FROM clipboard_entries WHERE isPinned = 0 AND isQuickSend = 0")
    suspend fun countUnpinned(): Int

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0 AND isQuickSend = 0 AND id IN (SELECT id FROM clipboard_entries WHERE isPinned = 0 AND isQuickSend = 0 ORDER BY timestamp ASC LIMIT :limit)")
    suspend fun trimUnpinned(limit: Int)

    // An edit clears the search key; the backfill recomputes it from the new text.
    @Query("UPDATE clipboard_entries SET text = :text, code = COALESCE(:code, code), timestamp = :now, searchKey = '' WHERE id = :id AND isQuickSend = 0")
    suspend fun updateClipboardItem(id: Long, text: String, code: String?, now: Long)

    @Query("UPDATE clipboard_entries SET consumed = 1 WHERE id = :id")
    suspend fun markConsumed(id: Long)

    @Query("DELETE FROM clipboard_entries")
    suspend fun deleteAll()

    @Transaction
    suspend fun upsertAndTrim(text: String, now: Long, maxItems: Int, searchKey: String = "") {
        if (upsert(text, now, searchKey)) {
            val unpinned = countUnpinned()
            if (unpinned > maxItems) {
                trimUnpinned(unpinned - maxItems)
            }
        }
    }

    /**
     * A copy already in history (ends' whitespace ignored) moves to the top, taking the new copy's exact
     * text; a fixed record keeps its text. A new one is added. One record per text remains. True when added.
     */
    @Transaction
    suspend fun upsert(text: String, now: Long, searchKey: String = "", fromDevice: Boolean = false): Boolean {
        val existing = findSameText(text)
        val keep = when {
            existing == null -> insert(ClipboardEntry(text = text, timestamp = now, searchKey = searchKey, fromDevice = fromDevice))
            !existing.isPinned && existing.text != text -> existing.id.also { replaceCopiedText(it, text, searchKey, now) }
            else -> existing.id.also { refreshCopiedItem(it, now) }
        }
        // Copied here again: a received record becomes this device's own. A received copy of text
        // already here only moves it up.
        if (existing != null && existing.fromDevice && !fromDevice) setFromDevice(keep, false)
        deleteHistoryCopies(text, keep)
        return existing == null
    }

    @Query("UPDATE clipboard_entries SET fromDevice = :fromDevice WHERE id = :id")
    suspend fun setFromDevice(id: Long, fromDevice: Boolean)

    @Query("SELECT COUNT(*) FROM clipboard_entries WHERE fromDevice = 1 AND isPinned = 0 AND isQuickSend = 0")
    suspend fun countReceived(): Int

    @Query("SELECT COALESCE(SUM(length(CAST(text AS BLOB)) + length(searchKey)), 0) FROM clipboard_entries " +
        "WHERE fromDevice = 1 AND isPinned = 0 AND isQuickSend = 0")
    suspend fun receivedBytes(): Long

    @Query("DELETE FROM clipboard_entries WHERE id IN (SELECT id FROM clipboard_entries " +
        "WHERE fromDevice = 1 AND isPinned = 0 AND isQuickSend = 0 ORDER BY timestamp ASC, id ASC LIMIT :limit)")
    suspend fun deleteOldestReceived(limit: Int)

    /**
     * A copy received from another device. Received records that are not fixed keep their own room
     * ([maxCount] records, [maxBytes]); only the oldest of them make way, never this device's own
     * copies or fixed records. True when added.
     */
    @Transaction
    suspend fun addReceived(text: String, now: Long, searchKey: String, maxCount: Int, maxBytes: Long): Boolean {
        val added = upsert(text, now, searchKey, fromDevice = true)
        countReceived().let { if (it > maxCount) deleteOldestReceived(it - maxCount) }
        while (receivedBytes() > maxBytes && countReceived() > 0) deleteOldestReceived(1)
        return added
    }

    /**
     * Fixing text: the same fixed text and code only moves up; text already in history is fixed in
     * place (no second copy); otherwise a new fixed record. Different codes on equal text stay apart.
     */
    @Transaction
    suspend fun addPinned(text: String, code: String, now: Long, searchKey: String = ""): Long {
        val id = findPinnedSameText(text, code)?.id?.also { refreshCopiedItem(it, now) }
            ?: findSameText(text)?.takeIf { !it.isPinned }?.id?.also {
                replaceCopiedText(it, text, searchKey, now)
                pinWithCode(it, code)
            }
            ?: insertPinnedText(text, code, now, searchKey)
        deleteHistoryCopies(text, id)
        return id
    }

    /**
     * Bytes of text and search keys stored (fixed and history), re-read whenever records change.
     * Received records that are not fixed have their own room and are not counted here.
     */
    @Query("SELECT COALESCE(SUM(length(CAST(text AS BLOB)) + length(searchKey)), 0) FROM clipboard_entries " +
        "WHERE isQuickSend = 0 AND (fromDevice = 0 OR isPinned = 1)")
    fun observeStoredBytes(): Flow<Long>

    suspend fun insertPinnedText(text: String, code: String, now: Long, searchKey: String = ""): Long =
        insert(ClipboardEntry(text = text, code = code, timestamp = now, isPinned = true, searchKey = searchKey))
}

/** The part of a record the search-key backfill needs. */
data class SearchKeySource(val id: Long, val text: String)
