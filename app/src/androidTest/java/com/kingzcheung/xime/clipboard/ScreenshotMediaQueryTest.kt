package com.kingzcheung.xime.clipboard

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only a private in-memory database; never writes or deletes the user's gallery. */
@RunWith(AndroidJUnit4::class)
class ScreenshotMediaQueryTest {
    @Test fun screenshotsSurviveMoreThanSixtyNewerCameraPhotos() = checkQuery(hasRelativePath = true)
    @Test fun androidNineCanFilterByBucketAndNameWithoutRelativePath() = checkQuery(hasRelativePath = false)

    private fun checkQuery(hasRelativePath: Boolean) {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE images (_id INTEGER, _display_name TEXT, bucket_display_name TEXT, date_added INTEGER" +
                if (hasRelativePath) ", relative_path TEXT)" else ")")
            fun add(id: Int, name: String, bucket: String?, path: String?) {
                val values = ContentValues().apply {
                    put("_id", id); put("_display_name", name); put("bucket_display_name", bucket); put("date_added", id)
                    if (hasRelativePath) put("relative_path", path)
                }
                db.insertOrThrow("images", null, values)
            }
            add(1, "Screenshot_20261007_091220.jpg", "Screenshots", "DCIM/Screenshots/")
            add(2, "Screenshot_20261007_091221.jpg", "My pictures", "Pictures/My pictures/")
            add(3, "edited.jpg", "截图", "Pictures/截图/")
            for (id in 10..99) add(id, "20261007_${id}.jpg", "Camera", "DCIM/Camera/")
            // Broad SQL prefilter is followed by the strict source check before taking 60 results.
            for (id in 100..169) add(id, "Screenshot guide $id.png", "Download", "Download/")
            val filter = ScreenshotImageSource.queryFilter("_display_name", "bucket_display_name",
                "relative_path".takeIf { hasRelativePath })
            val ids = mutableListOf<Int>()
            db.query("images", null, filter.selection, filter.arguments.toTypedArray(), null, null, "date_added DESC").use { cursor ->
                while (cursor.moveToNext() && ids.size < 60) {
                    fun string(column: String) = cursor.getString(cursor.getColumnIndexOrThrow(column))
                    if (ScreenshotImageSource.matches(string("_display_name"), string("bucket_display_name"),
                            if (hasRelativePath) string("relative_path") else null)) ids += cursor.getInt(0)
                }
            }
            assertEquals(listOf(3, 2, 1), ids)
        }
    }
}
