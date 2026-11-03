package com.kingzcheung.xime.clipboard.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

@Database(
    entities = [ClipboardEntry::class],
    version = 8,
    exportSchema = false
)
abstract class ClipboardDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao

    companion object {
        private const val DATABASE_NAME = "clipboard_history.db"

        /** 1.15.47 及以前的文件名，见 [moveLegacyDatabase]。 */
        internal const val LEGACY_DATABASE_NAME = "clipboard.db"

        private val SIDE_FILES = listOf("-wal", "-shm", "-journal")

        /** v1 → v2：新增 consumed 列（候选栏"已消费"剪贴板项过滤）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare(
                    "ALTER TABLE clipboard_entries ADD COLUMN consumed INTEGER NOT NULL DEFAULT 0"
                ).step()
            }
        }

        /** v2 → v3：新增 code 列（快捷发送触发编码）。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare(
                    "ALTER TABLE clipboard_entries ADD COLUMN code TEXT NOT NULL DEFAULT ''"
                ).step()
            }
        }

        /** v3 → v4：旧快捷发送原地成为固定记录，保留全部内容、编码及身份。 */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare(
                    "UPDATE clipboard_entries SET isPinned = 1, isQuickSend = 0 WHERE isQuickSend = 1"
                ).use { it.step() }
            }
        }

        /** v4 → v5：新增 searchKey 列（拼音首字母，供搜索）；旧记录由后台分批补齐。 */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare(
                    "ALTER TABLE clipboard_entries ADD COLUMN searchKey TEXT NOT NULL DEFAULT ''"
                ).use { it.step() }
            }
        }

        /** v5 → v6：新增 fromDevice 列（别的设备同步来的记录另算容量）；已有记录都算本机复制。 */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare(
                    "ALTER TABLE clipboard_entries ADD COLUMN fromDevice INTEGER NOT NULL DEFAULT 0"
                ).use { it.step() }
            }
        }

        /** v6 → v7：新增 sensitive 列（开关打开后记下的密码等，#95）；已有记录都不是。 */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare(
                    "ALTER TABLE clipboard_entries ADD COLUMN sensitive INTEGER NOT NULL DEFAULT 0"
                ).use { it.step() }
            }
        }

        /** v7 → v8：新增 grouped 列（放进自定义分组的记录照样保留，但不再固定）；由分组偏好补齐。 */
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare(
                    "ALTER TABLE clipboard_entries ADD COLUMN grouped INTEGER NOT NULL DEFAULT 0"
                ).use { it.step() }
            }
        }

        /**
         * 换文件名是为了能装回旧版：1.15.47 把 clipboard.db 升到 v8，旧版 Room 找不到 8→6 的迁移，
         * 键盘进程一启动就崩，输入法打不开。搬走以后旧版只会新建一个空的 clipboard.db，装回新版记录都在。
         * 只在新文件还没有时搬一次；旧版期间新建的 clipboard.db 留给旧版，不合并。
         * 先搬日志文件、最后搬主文件；搬不动就退回旧文件名，不丢记录。返回这次要打开的文件名。
         */
        internal fun moveLegacyDatabase(legacy: File, target: File): String {
            if (target.exists() || !legacy.exists()) return target.name
            val moved = mutableListOf<String>()
            for (suffix in SIDE_FILES) {
                val from = File(legacy.path + suffix)
                if (!from.exists()) continue
                if (!from.renameTo(File(target.path + suffix))) {
                    moved.forEach { File(target.path + it).renameTo(File(legacy.path + it)) }
                    return legacy.name
                }
                moved += suffix
            }
            if (legacy.renameTo(target)) return target.name
            moved.forEach { File(target.path + it).renameTo(File(legacy.path + it)) }
            return legacy.name
        }

        @Volatile
        private var instance: ClipboardDatabase? = null

        fun getInstance(context: Context): ClipboardDatabase {
            return instance ?: synchronized(this) {
                val app = context.applicationContext
                instance ?: Room.databaseBuilder<ClipboardDatabase>(
                    app,
                    moveLegacyDatabase(app.getDatabasePath(LEGACY_DATABASE_NAME), app.getDatabasePath(DATABASE_NAME))
                )
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    // 以后再装回这一版时，更新的版本留下的高版本库清空重建，不崩。
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                    .also { instance = it }
            }
        }

        fun scope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
    }
}
