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

    @Query("UPDATE clipboard_entries SET text = :text, code = COALESCE(:code, code), timestamp = :now WHERE id = :id AND isQuickSend = 0")
    suspend fun updateClipboardItem(id: Long, text: String, code: String?, now: Long)

    @Query("UPDATE clipboard_entries SET consumed = 1 WHERE id = :id")
    suspend fun markConsumed(id: Long)

    @Query("DELETE FROM clipboard_entries")
    suspend fun deleteAll()

    @Transaction
    suspend fun upsertAndTrim(text: String, now: Long, maxItems: Int) {
        val existing = findByText(text)
        if (existing != null) {
            refreshCopiedItem(existing.id, now)
        } else {
            insert(ClipboardEntry(text = text, timestamp = now))
            val unpinned = countUnpinned()
            if (unpinned > maxItems) {
                trimUnpinned(unpinned - maxItems)
            }
        }
    }

    suspend fun insertPinnedText(text: String, code: String, now: Long): Long =
        insert(ClipboardEntry(text = text, code = code, timestamp = now, isPinned = true))
}
