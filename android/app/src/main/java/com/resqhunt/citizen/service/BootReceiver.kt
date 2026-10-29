package com.resqhunt.citizen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles system reboot and app upgrade events to restore the ResQhunT emergency mesh beacon
 * and locked-screen trigger monitoring if previously enabled by the user.
 *
 * NOTE (Honest Platform Limitation): If the user explicitly Force Stops the application from
 * Android Settings, Android places the package in a stopped state (FLAG_EXCLUDE_STOPPED_PACKAGES)
 * which suppresses all broadcasts including BOOT_COMPLETED until the app is manually opened.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ResQhunT_BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.i(TAG, "Received system boot/upgrade event: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val detector = VolumeKeySosTriggerDetector.fromContext(context)
            if (detector.isEnabled) {
                Log.i(TAG, "Emergency trigger monitor is enabled. Restoring foreground service...")
                try {
                    SosRelayForegroundService.start(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start foreground service on boot: ${e.message}")
                }
            } else {
                Log.d(TAG, "Emergency trigger is disabled by user; skipping automatic start on boot.")
            }
        }
    }
}
