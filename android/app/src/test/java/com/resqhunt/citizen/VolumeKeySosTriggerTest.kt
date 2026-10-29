package com.resqhunt.citizen

import android.view.KeyEvent
import com.resqhunt.citizen.service.VolumeKeySosTriggerDetector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VolumeKeySosTriggerTest {

    private lateinit var detector: VolumeKeySosTriggerDetector

    @Before
    fun setUp() {
        detector = VolumeKeySosTriggerDetector(
            prefs = null,
            requiredHoldDurationMs = 3000L,
            cooldownMs = 10000L
        )
        detector.isEnabled = true
        detector.isTestMode = false
    }

    @Test
    fun testSingleVolumeUpDoesNotTriggerAndAllowsNormalVolumeAdjustment() {
        val t0 = 10000L

        // User presses Volume Up alone
        val rDown = detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, t0)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.NotHandled, rDown)
        assertTrue(detector.isVolumeUpPressed)
        assertFalse(detector.isVolumeDownPressed)

        // User releases Volume Up
        val rUp = detector.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP, t0 + 100L)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.NotHandled, rUp)
        assertFalse(detector.isVolumeUpPressed)
    }

    @Test
    fun testSingleVolumeDownDoesNotTriggerAndAllowsNormalVolumeAdjustment() {
        val t0 = 10000L

        // User presses Volume Down alone
        val rDown = detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, t0)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.NotHandled, rDown)
        assertFalse(detector.isVolumeUpPressed)
        assertTrue(detector.isVolumeDownPressed)

        // User releases Volume Down
        val rUp = detector.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN, t0 + 100L)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.NotHandled, rUp)
        assertFalse(detector.isVolumeDownPressed)
    }

    @Test
    fun testSimultaneousVolumeKeysStartCountdownAndTriggerAfter3Seconds() {
        val t0 = 10000L

        // Press Volume Up first
        val rUp = detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, t0)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.NotHandled, rUp)

        // Press Volume Down while Volume Up is held (simultaneous press!)
        val rDown = detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, t0 + 50L)
        assertTrue(rDown is VolumeKeySosTriggerDetector.KeyActionResult.CountdownStarted)
        assertEquals(3000L, (rDown as VolumeKeySosTriggerDetector.KeyActionResult.CountdownStarted).totalDurationMs)

        // Mid-hold progress check at 1.5 seconds elapsed (50% progress)
        val rMid = detector.checkHoldProgress(t0 + 50L + 1500L)
        assertTrue(rMid is VolumeKeySosTriggerDetector.KeyActionResult.CountdownProgress)
        val prog = rMid as VolumeKeySosTriggerDetector.KeyActionResult.CountdownProgress
        assertEquals(1500L, prog.elapsedMs)
        assertEquals(1500L, prog.remainingMs)
        assertEquals(0.5f, prog.progress, 0.01f)

        // Hold completed at 3.0 seconds!
        val rTrigger = detector.checkHoldProgress(t0 + 50L + 3000L)
        assertTrue(rTrigger is VolumeKeySosTriggerDetector.KeyActionResult.Triggered)
        assertFalse((rTrigger as VolumeKeySosTriggerDetector.KeyActionResult.Triggered).isTestMode)

        // Verify cooldown is active
        assertTrue(detector.isInCooldown(t0 + 50L + 3500L))
        assertEquals(9500L, detector.remainingCooldownMs(t0 + 50L + 3500L))
    }

    @Test
    fun testReleasingEitherKeyBefore3SecondsCancelsCountdown() {
        val t0 = 10000L

        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, t0)
        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, t0 + 20L)

        // User releases Volume Up after 1.8 seconds (< 3.0 seconds required)
        val rRelease = detector.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP, t0 + 20L + 1800L)
        assertTrue(rRelease is VolumeKeySosTriggerDetector.KeyActionResult.Cancelled)
        assertEquals(1800L, (rRelease as VolumeKeySosTriggerDetector.KeyActionResult.Cancelled).heldMs)

        // Verify countdown is terminated and did NOT fire
        assertFalse(detector.isInCooldown(t0 + 2000L))
        val rAfterCancel = detector.checkHoldProgress(t0 + 20L + 3500L)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.NotHandled, rAfterCancel)
    }

    @Test
    fun testContinuousHoldDoesNotRepeatedlyTrigger() {
        val t0 = 10000L

        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, t0)
        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, t0)

        // Triggers at 3000ms
        val rTrigger = detector.checkHoldProgress(t0 + 3000L)
        assertTrue(rTrigger is VolumeKeySosTriggerDetector.KeyActionResult.Triggered)

        // User keeps holding keys past 3000ms (e.g. at 4000ms, 5000ms)
        val rStayHeld1 = detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, t0 + 4000L)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.ConsumedHoldingAlreadyTriggered, rStayHeld1)

        val rStayHeld2 = detector.checkHoldProgress(t0 + 5000L)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.ConsumedHoldingAlreadyTriggered, rStayHeld2)

        // Release keys
        val rRelease1 = detector.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP, t0 + 6000L)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.Consumed, rRelease1)
        val rRelease2 = detector.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN, t0 + 6010L)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.Consumed, rRelease2)
    }

    @Test
    fun testCooldownBlocksImmediateRetrigger() {
        val t0 = 10000L

        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, t0)
        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, t0)
        detector.checkHoldProgress(t0 + 3000L) // Triggered at t0 + 3000L

        // Release keys
        detector.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP, t0 + 3500L)
        detector.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN, t0 + 3500L)

        // Try again at 5 seconds after trigger (during 10s cooldown)
        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, t0 + 8000L)
        val rBlocked = detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, t0 + 8000L)
        assertTrue(rBlocked is VolumeKeySosTriggerDetector.KeyActionResult.InCooldown)

        // Release keys after blocked attempt
        detector.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP, t0 + 8500L)
        detector.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN, t0 + 8500L)

        // Try again after 10s cooldown has fully expired (t0 + 3000L + 10001L)
        val tAfterCooldown = t0 + 13005L
        assertFalse(detector.isInCooldown(tAfterCooldown))

        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, tAfterCooldown)
        val rNew = detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, tAfterCooldown)
        assertTrue(rNew is VolumeKeySosTriggerDetector.KeyActionResult.CountdownStarted)
    }

    @Test
    fun testDisabledShortcutLeavesAllKeysUnhandled() {
        detector.isEnabled = false

        val rUp = detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, 10000L)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.NotHandled, rUp)

        val rDown = detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, 10050L)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.NotHandled, rDown)
    }

    @Test
    fun testTestModeFlagPassedToTriggerResult() {
        detector.isTestMode = true
        val t0 = 10000L

        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, t0)
        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, t0)

        val rTrigger = detector.checkHoldProgress(t0 + 3000L)
        assertTrue(rTrigger is VolumeKeySosTriggerDetector.KeyActionResult.Triggered)
        assertTrue((rTrigger as VolumeKeySosTriggerDetector.KeyActionResult.Triggered).isTestMode)
    }

    @Test
    fun testResetClearsActiveCountdown() {
        val t0 = 10000L

        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, t0)
        detector.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, t0)
        detector.checkHoldProgress(t0 + 1000L)

        // Activity goes to background / onPause -> reset()
        detector.reset()

        assertFalse(detector.isVolumeUpPressed)
        assertFalse(detector.isVolumeDownPressed)
        assertEquals(0L, detector.holdStartTimeMs)

        val rAfterReset = detector.checkHoldProgress(t0 + 3500L)
        assertEquals(VolumeKeySosTriggerDetector.KeyActionResult.NotHandled, rAfterReset)
    }
}
