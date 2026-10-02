package com.kingzcheung.xime.runtime.adaptation

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.kingzcheung.xime.util.FileLogger

/** API 28 keeps thermal status unknown. Probe failures remain visible in the snapshot. */
class AndroidRuntimeSignalSource(context: Context) : RuntimeSignalSource {
    private val context = context.applicationContext
    private val failures = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val activity get() = context.getSystemService(ActivityManager::class.java)
    private val power get() = context.getSystemService(PowerManager::class.java)

    private fun <T> read(source: String, action: () -> T?): T? = try {
        action().also { if (it == null) failures.add(source) else failures.remove(source) }
    } catch (error: RuntimeException) {
        if (failures.add(source)) FileLogger.w("RuntimeObservation", "$source unavailable (${error.javaClass.simpleName})")
        null
    }

    private val device by lazy {
        DeviceCapabilityProbe.read(this.context).also {
            if (it.totalMemoryBytes == null) failures.add("totalMemory")
            if (it.lowRam == null) failures.add("lowRam")
            if (it.processors == null) failures.add("processors")
            if (it.maxCpuKHz == null) failures.add("maxCpuFrequency")
        }
    }

    override fun capabilities() = device

    override fun sample(): RuntimeSignals {
        val memory = read("memory") { activity?.let { manager ->
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
                .takeIf { it.totalMem > 0 && it.availMem > 0 && it.availMem <= it.totalMem }
        } }
        val battery = read("battery") { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
        val thermal = if (Build.VERSION.SDK_INT >= 29) read("thermal") { power?.let(ThermalApi::read) }
            else null
        val powerSave = read("powerSave") { power?.isPowerSaveMode }
        val animations = read("animations") { ValueAnimator.areAnimatorsEnabled() }
        return RuntimeSignals(
            sampledAtMs = SystemClock.elapsedRealtime(),
            powerSave = powerSave,
            batteryLow = battery?.takeIf { it.hasExtra(BatteryManager.EXTRA_BATTERY_LOW) }
                ?.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false),
            charging = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)?.takeIf { it >= 0 }?.let { it != 0 },
            thermal = thermal ?: ThermalState.UNKNOWN,
            availableMemoryBytes = memory?.availMem?.takeIf { it > 0 },
            memoryLow = memory?.lowMemory,
            animationsEnabled = animations,
            unavailableSources = failures.toSet() + if (Build.VERSION.SDK_INT < 29) setOf("thermal:API<29") else emptySet(),
        )
    }

    override fun subscribe(onChanged: () -> Unit): AutoCloseable {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { onChanged() }
        }
        val receiverRegistered = read("powerEvents") {
            ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            true
        } == true
        val thermal = if (Build.VERSION.SDK_INT >= 29) read("thermalEvents") {
            power?.let { ThermalApi.subscribe(context, it, onChanged) }
        } else null
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { onChanged() }
        }
        val animationsRegistered = read("animationEvents") {
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
            true
        } == true
        return AutoCloseable {
            if (receiverRegistered) read("powerEventsClose") { context.unregisterReceiver(receiver); true }
            if (thermal != null) read("thermalEventsClose") { thermal.close(); true }
            if (animationsRegistered) read("animationEventsClose") { context.contentResolver.unregisterContentObserver(observer); true }
        }
    }

    @RequiresApi(29)
    private object ThermalApi {
        fun read(power: PowerManager): ThermalState = when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalState.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalState.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalState.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.SHUTDOWN
            else -> ThermalState.UNKNOWN
        }

        fun subscribe(context: Context, power: PowerManager, onChanged: () -> Unit): AutoCloseable {
            val listener = PowerManager.OnThermalStatusChangedListener { onChanged() }
            power.addThermalStatusListener(context.mainExecutor, listener)
            return AutoCloseable { power.removeThermalStatusListener(listener) }
        }
    }
}
