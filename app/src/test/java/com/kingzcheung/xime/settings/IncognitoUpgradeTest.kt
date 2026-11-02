package com.kingzcheung.xime.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class IncognitoUpgradeTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun freshInstallStartsOffAndSameVersionRestartsKeepManualChoice() {
        SettingsPreferences.initializeIncognitoForVersion(app, 100)
        assertFalse(SettingsPreferences.isIncognito(app))
        SettingsPreferences.setIncognito(app, true)
        SettingsPreferences.initializeIncognitoForVersion(app, 100)
        assertTrue(SettingsPreferences.isIncognito(app))
        SettingsPreferences.setIncognito(app, false)
        SettingsPreferences.initializeIncognitoForVersion(app, 100)
        assertFalse(SettingsPreferences.isIncognito(app))
    }

    @Test fun legacyAndSubsequentUpgradesBothSwitchOff() {
        // Older releases saved only this boolean, even when the user had explicitly enabled it.
        SettingsPreferences.getPrefsPublic(app).edit().putBoolean(SettingsPreferences.KEY_INCOGNITO, true).apply()
        assertTrue(SettingsPreferences.isIncognito(app))
        SettingsPreferences.initializeIncognitoForVersion(app, 100)
        assertFalse(SettingsPreferences.isIncognito(app))
        SettingsPreferences.setIncognito(app, true)
        SettingsPreferences.initializeIncognitoForVersion(app, 101)
        assertFalse(SettingsPreferences.isIncognito(app))
    }

    @Test fun reinstallWithRestoredPreferencesStartsOffEvenAtTheSameVersion() {
        SettingsPreferences.initializeIncognitoForVersion(app, 100)
        SettingsPreferences.setIncognito(app, true)
        val installed = app.packageManager.getPackageInfo(app.packageName, 0)
        installed.firstInstallTime += 1000
        shadowOf(app.packageManager).installPackage(installed)
        SettingsPreferences.initializeIncognitoForVersion(app, 100)
        assertFalse(SettingsPreferences.isIncognito(app))
    }
}
