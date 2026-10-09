package com.resqhunt.citizen.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent

/**
 * Foreground-only Volume Up + Volume Down SOS Shortcut Detector.
 *
 * ARCHITECTURAL CONTRACT:
 * 1. Foreground Only:
 *    Operates strictly when ResQhunT is visible in the foreground and receives window key events.
 *    No accessibility services, no hidden APIs, and no background key interception.
 * 2. Simultaneous Key Press & 3-Second Hold:
 *    Triggers SOS ONLY when both KeyEvent.KEYCODE_VOLUME_UP and KeyEvent.KEYCODE_VOLUME_DOWN
 *    are pressed simultaneously and held for 3 continuous seconds.
 * 3. Normal Volume Operation Preserved:
 *    Single volume key presses (Up alone or Down alone) return NotHandled, allowing the Android
 *    operating system to adjust device audio volume normally.
 * 4. Immediate Cancellation:
 *    Releasing either volume key before 3.0 seconds immediately cancels the countdown without firing.
 * 5. Cooldown & Duplicate Prevention:
 *    Once activated, holding the keys continuously will NOT re-trigger SOS. A 10-second cooldown
 *    is enforced after activation.
 */
class VolumeKeySosTriggerDetector(
    private val prefs: SharedPreferences? = null,
    var requiredHoldDurationMs: Long = 3000L,
    var cooldownMs: Long = 10000L
) {
    companion object {
        private const val TAG = "ResQhunT_VolumeKeyDetector"
        const val PREFS_NAME = "resqhunt_trigger_prefs"
        const val KEY_TRIGGER_ENABLED = "volume_key_trigger_enabled"
        const val KEY_TEST_MODE = "volume_key_test_mode"

        fun fromContext(context: Context): VolumeKeySosTriggerDetector {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return VolumeKeySosTriggerDetector(prefs)
        }
    }

    sealed class KeyActionResult {
        object NotHandled : KeyActionResult()
        object Consumed : KeyActionResult()
        object Disabled : KeyActionResult()
        object ConsumedHoldingAlreadyTriggered : KeyActionResult()
        data class InCooldown(val remainingMs: Long) : KeyActionResult()
        data class CountdownStarted(val totalDurationMs: Long) : KeyActionResult()
        data class CountdownProgress(val elapsedMs: Long, val remainingMs: Long, val progress: Float) : KeyActionResult()
        data class Cancelled(val heldMs: Long) : KeyActionResult()
        data class Triggered(val isTestMode: Boolean) : KeyActionResult()
    }

    private var inMemoryEnabled: Boolean = true
    private var inMemoryTestMode: Boolean = false

    var isEnabled: Boolean
        get() = prefs?.getBoolean(KEY_TRIGGER_ENABLED, inMemoryEnabled) ?: inMemoryEnabled
        set(value) {
            inMemoryEnabled = value
            prefs?.edit()?.putBoolean(KEY_TRIGGER_ENABLED, value)?.apply()
        }

    var isTestMode: Boolean
        get() = prefs?.getBoolean(KEY_TEST_MODE, inMemoryTestMode) ?: inMemoryTestMode
        set(value) {
            inMemoryTestMode = value
            prefs?.edit()?.putBoolean(KEY_TEST_MODE, value)?.apply()
        }

    @Volatile var isVolumeUpPressed: Boolean = false
        private set

    @Volatile var isVolumeDownPressed: Boolean = false
        private set

    @Volatile var holdStartTimeMs: Long = 0L
        private set

    @Volatile var lastTriggerTimeMs: Long = 0L
        private set

    @Volatile var hasTriggeredForCurrentHold: Boolean = false
        private set

    private fun logD(msg: String) {
        try { Log.d(TAG, msg) } catch (_: Throwable) {}
    }

    private fun logI(msg: String) {
        try { Log.i(TAG, msg) } catch (_: Throwable) {}
    }

    /**
     * Process an Android key-down event.
     * @param keyCode KeyEvent keycode
     * @param currentTimeMs Current system timestamp
     * @return KeyActionResult indicating whether event was consumed and state progression
     */
    @Synchronized
    fun onKeyDown(keyCode: Int, currentTimeMs: Long = System.currentTimeMillis()): KeyActionResult {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return KeyActionResult.NotHandled
        }

        if (!isEnabled) {
            logD("Volume key event ignored: Feature disabled by user.")
            return KeyActionResult.NotHandled
        }

        // Update key pressed states
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            isVolumeUpPressed = true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            isVolumeDownPressed = true
        }

        val bothKeysDown = isVolumeUpPressed && isVolumeDownPressed

        // If only one key is down, do not consume it — let OS adjust volume normally!
        if (!bothKeysDown) {
            holdStartTimeMs = 0L
            return KeyActionResult.NotHandled
        }

        // Both keys are down simultaneously:
        if (hasTriggeredForCurrentHold) {
            // Already triggered for this continuous press; consume key to prevent volume change
            return KeyActionResult.ConsumedHoldingAlreadyTriggered
        }

        // Check Cooldown
        if (isInCooldown(currentTimeMs)) {
            val remaining = remainingCooldownMs(currentTimeMs)
            logD("Dual volume hold ignored: Cooldown active (${remaining / 1000}s remaining).")
            return KeyActionResult.InCooldown(remaining)
        }

        // Start countdown if not already started
        if (holdStartTimeMs == 0L) {
            holdStartTimeMs = currentTimeMs
            logI("Dual volume keys pressed simultaneously! Starting 3-second SOS countdown.")
            return KeyActionResult.CountdownStarted(requiredHoldDurationMs)
        }

        // Calculate hold progress
        val elapsed = currentTimeMs - holdStartTimeMs
        if (elapsed >= requiredHoldDurationDurationMsSafe()) {
            hasTriggeredForCurrentHold = true
            lastTriggerTimeMs = currentTimeMs
            holdStartTimeMs = 0L
            logI("DUAL VOLUME KEYS HELD FOR 3 SECONDS: EMERGENCY TRIGGER CONFIRMED! (testMode=$isTestMode)")
            return KeyActionResult.Triggered(isTestMode)
        }

        val progress = (elapsed.toFloat() / requiredHoldDurationDurationMsSafe().toFloat()).coerceIn(0f, 1f)
        val remaining = (requiredHoldDurationDurationMsSafe() - elapsed).coerceAtLeast(0L)
        return KeyActionResult.CountdownProgress(elapsed, remaining, progress)
    }

    /**
     * Periodically check elapsed hold time while keys remain held down.
     */
    @Synchronized
    fun checkHoldProgress(currentTimeMs: Long = System.currentTimeMillis()): KeyActionResult {
        if (!isEnabled) return KeyActionResult.Disabled
        if (hasTriggeredForCurrentHold && isVolumeUpPressed && isVolumeDownPressed) {
            return KeyActionResult.ConsumedHoldingAlreadyTriggered
        }
        if (!isVolumeUpPressed || !isVolumeDownPressed || holdStartTimeMs == 0L) {
            return KeyActionResult.NotHandled
        }

        val elapsed = currentTimeMs - holdStartTimeMs
        if (elapsed >= requiredHoldDurationDurationMsSafe()) {
            hasTriggeredForCurrentHold = true
            lastTriggerTimeMs = currentTimeMs
            holdStartTimeMs = 0L
            logI("DUAL VOLUME KEYS 3-SECOND TIMER ELAPSED: EMERGENCY TRIGGER CONFIRMED! (testMode=$isTestMode)")
            return KeyActionResult.Triggered(isTestMode)
        }

        val progress = (elapsed.toFloat() / requiredHoldDurationDurationMsSafe().toFloat()).coerceIn(0f, 1f)
        val remaining = (requiredHoldDurationDurationMsSafe() - elapsed).coerceAtLeast(0L)
        return KeyActionResult.CountdownProgress(elapsed, remaining, progress)
    }

    /**
     * Process an Android key-up event.
     */
    @Synchronized
    fun onKeyUp(keyCode: Int, currentTimeMs: Long = System.currentTimeMillis()): KeyActionResult {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return KeyActionResult.NotHandled
        }

        val wasCountingDown = (holdStartTimeMs > 0L) && (isVolumeUpPressed && isVolumeDownPressed)
        val elapsed = if (holdStartTimeMs > 0L) currentTimeMs - holdStartTimeMs else 0L
        val hadTriggered = hasTriggeredForCurrentHold

        // Release the key
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            isVolumeUpPressed = false
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            isVolumeDownPressed = false
        }

        // If both keys are now released, reset the hold trigger flag for next time
        if (!isVolumeUpPressed && !isVolumeDownPressed) {
            hasTriggeredForCurrentHold = false
        }

        if (wasCountingDown && !hadTriggered && elapsed < requiredHoldDurationDurationMsSafe()) {
            holdStartTimeMs = 0L
            logI("Volume key released before 3 seconds (${elapsed}ms held). Countdown CANCELLED.")
            return KeyActionResult.Cancelled(elapsed)
        }

        holdStartTimeMs = 0L
        return if (hadTriggered || wasCountingDown) KeyActionResult.Consumed else KeyActionResult.NotHandled
    }

    /**
     * Resets all key tracking and active countdowns.
     * Must be called on Activity onPause/onStop to guarantee foreground-only behavior.
     */
    @Synchronized
    fun reset() {
        isVolumeUpPressed = false
        isVolumeDownPressed = false
        holdStartTimeMs = 0L
        hasTriggeredForCurrentHold = false
        logD("VolumeKeySosTriggerDetector reset.")
    }

    @Synchronized
    fun markTriggered(currentTimeMs: Long = System.currentTimeMillis()) {
        lastTriggerTimeMs = currentTimeMs
        hasTriggeredForCurrentHold = true
        holdStartTimeMs = 0L
    }

    fun isInCooldown(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (lastTriggerTimeMs <= 0L) return false
        val timeSince = currentTimeMs - lastTriggerTimeMs
        return timeSince in 0 until cooldownMs
    }

    fun remainingCooldownMs(currentTimeMs: Long = System.currentTimeMillis()): Long {
        if (lastTriggerTimeMs <= 0L) return 0L
        val timeSince = currentTimeMs - lastTriggerTimeMs
        return if (timeSince in 0 until cooldownMs) cooldownMs - timeSince else 0L
    }

    private fun requiredHoldDurationDurationMsSafe(): Long = requiredHoldDurationMs
}
