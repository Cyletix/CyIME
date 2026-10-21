package com.kingzcheung.xime.clipboard.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

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
        "replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(" +
        "searchKey, " +
        "'a', '2'), 'b', '2'), 'c', '2'), " +
        "'d', '3'), 'e', '3'), 'f', '3'), " +
        "'g', '4'), 'h', '4'), 'i', '4'), " +
        "'j', '5'), 'k', '5'), 'l', '5'), " +
        "'m', '6'), 'n', '6'), 'o', '6'), " +
        "'p', '7'), 'q', '7'), 'r', '7'), 's', '7'), " +
        "'t', '8'), 'u', '8'), 'v', '8'), " +
        "'w', '9'), 'x', '9'), 'y', '9'), 'z', '9')" +
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
        val existing = findByText(text)
        if (existing != null) {
            refreshCopiedItem(existing.id, now)
        } else {
            insert(ClipboardEntry(text = text, timestamp = now, searchKey = searchKey))
            val unpinned = countUnpinned()
            if (unpinned > maxItems) {
                trimUnpinned(unpinned - maxItems)
            }
        }
    }

    suspend fun insertPinnedText(text: String, code: String, now: Long, searchKey: String = ""): Long =
        insert(ClipboardEntry(text = text, code = code, timestamp = now, isPinned = true, searchKey = searchKey))
}

/** The part of a record the search-key backfill needs. */
data class SearchKeySource(val id: Long, val text: String)
