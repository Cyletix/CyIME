package com.kingzcheung.xime.ui

import android.app.Application
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.test.runner.AndroidJUnitRunner
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.AppFonts
import com.kingzcheung.xime.ui.theme.KeyboardThemes

/** Real Compose rendering without starting ARM-only inference/Rime services on an x86 emulator.
 * Only isolated UI tests may use this runner; it cannot verify IME or model integration.
 */
class UiRenderingTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        check(arguments.getString("realIme") != "true") {
            "Real IME tests require AndroidJUnitRunner (-PuiRenderingOnly=false); newApplication precedes runner arguments."
        }
        super.onCreate(arguments)
    }
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)

    override fun callApplicationOnCreate(app: Application) {
        super.callApplicationOnCreate(app)
        // Keep only test activities awake; do not change the device's timeout preference.
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        AppFonts.initialize(app)
        KeyboardThemes.initFromConfig(app)
        KeysConfigHelper.loadConfig(app)
    }
}
