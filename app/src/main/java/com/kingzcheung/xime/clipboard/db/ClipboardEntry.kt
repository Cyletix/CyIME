package com.kingzcheung.xime.clipboard.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "clipboard_entries",
    indices = [Index(value = ["text"])]
)
data class ClipboardEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    /** 固定内容的可选触发编码；保留旧插件读取协议。 */
    @ColumnInfo(defaultValue = "") val code: String = "",
    @ColumnInfo(defaultValue = "0") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
    /** Legacy storage flag retained so existing databases and backups remain readable. */
    @ColumnInfo(defaultValue = "0") val isQuickSend: Boolean = false,
    @ColumnInfo(defaultValue = "0") val consumed: Boolean = false,
    /** Pinyin initials and lowercase letters of the first characters, for search; "" = not computed yet. */
    @ColumnInfo(defaultValue = "") val searchKey: String = "",
    /** Received from a paired computer, phone or tablet rather than copied on this device. */
    @ColumnInfo(defaultValue = "0") val fromDevice: Boolean = false,
)
