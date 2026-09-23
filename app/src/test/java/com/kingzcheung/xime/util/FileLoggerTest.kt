package com.kingzcheung.xime.util

import com.kingzcheung.xime.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FileLoggerTest {
    private val previousEnabled = FileLogger.isVerboseLoggingEnabled()

    @After fun restoreLogging() {
        FileLogger.setVerboseLoggingEnabled(previousEnabled)
    }

    @Test fun disabledVerboseLoggingDoesNotEvaluateHotPathMessages() {
        FileLogger.setVerboseLoggingEnabled(false)
        var evaluations = 0
        repeat(1_000) {
            FileLogger.i("FileLoggerTest") {
                evaluations++
                "T9 diagnostic $it"
            }
        }
        assertEquals(0, evaluations)
    }

    @Test fun explicitEnableEvaluatesMessagesOnlyInDebugBuilds() {
        FileLogger.setVerboseLoggingEnabled(true)
        var evaluations = 0
        FileLogger.i("FileLoggerTest") {
            evaluations++
            "Enabled diagnostic"
        }
        assertEquals(if (BuildConfig.DEBUG) 1 else 0, evaluations)
    }
}
