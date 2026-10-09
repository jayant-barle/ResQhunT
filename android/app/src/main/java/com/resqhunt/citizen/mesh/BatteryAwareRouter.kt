package com.resqhunt.citizen.mesh

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

class BatteryAwareRouter(private val context: Context) {

    data class BatteryStatus(
        val percentage: Int,
        val isCharging: Boolean,
        val isLowBattery: Boolean
    )

    fun getBatteryStatus(): BatteryStatus {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = try {
            ContextCompat.registerReceiver(
                context,
                null,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            null
        }

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        val percentage = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            50 // Safe fallback
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val isLow = percentage < 15 && !isCharging

        return BatteryStatus(
            percentage = percentage,
            isCharging = isCharging,
            isLowBattery = isLow
        )
    }

    /**
     * Determines interval between discovery cycles based on battery state.
     */
    fun getRecommendedDiscoveryPauseMs(): Long {
        val status = getBatteryStatus()
        return when {
            status.isCharging -> 0L // Continuous scanning when connected to power
            status.percentage >= 50 -> 5000L // Light pause (5s)
            status.percentage >= 20 -> 15000L // Moderate pause (15s)
            else -> 45000L // Aggressive battery conservation: 45s pause between scans
        }
    }
}
