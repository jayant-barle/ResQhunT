package com.resqhunt.citizen.service

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks and logs the explicit Android application lifecycle state to distinguish between:
 * 1. FOREGROUND: Activity currently visible to user.
 * 2. BACKGROUND: App running in background with foreground service beacon active.
 * 3. PROCESS_KILLED: Process was terminated by OS (LMK / task swipe) and subsequently revived via START_STICKY or TileService.
 * 4. FORCE_STOPPED: App explicitly stopped by user in Settings; OS excludes package from broadcasts.
 */
object AppLifecycleStateTracker : Application.ActivityLifecycleCallbacks {

    private const val TAG = "ResQhunT_Lifecycle"

    enum class ProcessLifecycleState(val label: String, val description: String) {
        FOREGROUND("FOREGROUND", "App UI is visible on screen."),
        BACKGROUND("BACKGROUND", "App in background; SosRelayForegroundService keeping mesh active."),
        PROCESS_KILLED_RECOVERED("PROCESS_KILLED (RECOVERED)", "Process was killed by Android and revived via START_STICKY, BootReceiver, or TileService."),
        FORCE_STOPPED_BLOCKED("FORCE_STOPPED (UNREACHABLE)", "App force-stopped in Settings. OS blocks all broadcasts and background services until manual launch.")
    }

    private var activeActivitiesCount = 0
    val processId: Int = Process.myPid()
    val processStartTimeMs: Long = System.currentTimeMillis()
    private val uptimeStartMs: Long = SystemClock.elapsedRealtime()

    var wasProcessKilledAndRecreated: Boolean = false
        private set

    var revivalSource: String? = null
        private set

    private val _currentState = MutableStateFlow(ProcessLifecycleState.BACKGROUND)
    val currentState: StateFlow<ProcessLifecycleState> = _currentState.asStateFlow()

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        Log.i(TAG, "[LIFECYCLE_INIT] ResQhunT process initialized. PID=$processId, StartTime=$processStartTimeMs")
    }

    fun markProcessRevived(source: String) {
        wasProcessKilledAndRecreated = true
        revivalSource = source
        _currentState.value = ProcessLifecycleState.PROCESS_KILLED_RECOVERED
        Log.i(TAG, "[STATE: PROCESS_KILLED] Process revived by Android OS via source: '$source'. PID=$processId")
    }

    fun getUptimeSeconds(): Long {
        return (SystemClock.elapsedRealtime() - uptimeStartMs) / 1000
    }

    fun logCurrentState(contextTag: String): String {
        val state = _currentState.value
        val summary = "[STATE: ${state.label}] PID=$processId | Uptime=${getUptimeSeconds()}s | ActiveActivities=$activeActivitiesCount | RevivedBy=${revivalSource ?: "ColdLaunch"}"
        Log.i(contextTag, summary)
        return summary
    }

    override fun onActivityStarted(activity: Activity) {
        activeActivitiesCount++
        if (activeActivitiesCount > 0) {
            _currentState.value = ProcessLifecycleState.FOREGROUND
            Log.d(TAG, "[STATE: FOREGROUND] Activity ${activity.localClassName} visible. ActiveCount=$activeActivitiesCount")
        }
    }

    override fun onActivityStopped(activity: Activity) {
        activeActivitiesCount = maxOf(0, activeActivitiesCount - 1)
        if (activeActivitiesCount == 0) {
            _currentState.value = ProcessLifecycleState.BACKGROUND
            Log.d(TAG, "[STATE: BACKGROUND] No visible activities. App operating in background via foreground service. ActiveCount=0")
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
