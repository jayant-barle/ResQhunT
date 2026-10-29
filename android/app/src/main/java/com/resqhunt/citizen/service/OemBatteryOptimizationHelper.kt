package com.resqhunt.citizen.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Cross-brand helper for battery optimization exemptions and manufacturer-specific guidance
 * across Android 10-15 (API 29-35), including Samsung, Xiaomi/Redmi/POCO, Vivo/iQOO,
 * OPPO/Realme/OnePlus, and Motorola/Google Pixel.
 */
object OemBatteryOptimizationHelper {
    private const val TAG = "ResQhunT_OemBattery"

    data class OemGuidance(
        val manufacturer: String,
        val brandName: String,
        val isBatteryOptimized: Boolean,
        val guidanceSteps: List<String>,
        val nativeSosGuide: String
    )

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Direct ignore battery prompt failed, falling back to settings list: ${e.message}")
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                    return true
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Fallback settings list also failed: ${fallbackEx.message}")
                }
            }
        }
        return false
    }

    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details settings: ${e.message}")
            false
        }
    }

    fun getOemGuidance(context: Context): OemGuidance {
        val mfr = Build.MANUFACTURER.lowercase()
        val isIgnored = isBatteryOptimizationIgnored(context)

        return when {
            mfr.contains("samsung") -> OemGuidance(
                manufacturer = Build.MANUFACTURER,
                brandName = "Samsung (OneUI)",
                isBatteryOptimized = !isIgnored,
                guidanceSteps = listOf(
                    "Settings > Apps > ResQhunT > Battery: Set to 'Unrestricted'.",
                    "Settings > Battery and device care > Battery > Background usage limits: Ensure ResQhunT is NOT placed in 'Sleeping apps' or 'Deep sleeping apps'.",
                    "Lock Screen: In Settings > Lock screen > Notifications, select 'Details' and keep notification content visible."
                ),
                nativeSosGuide = "Samsung Emergency SOS: Go to Settings > Safety and emergency > Emergency SOS. Turn ON the toggle and add emergency contacts. Pressing the power button 5 times rapidly triggers Samsung native emergency calls/messages."
            )
            mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") -> OemGuidance(
                manufacturer = Build.MANUFACTURER,
                brandName = "Xiaomi / Redmi / POCO (MIUI / HyperOS)",
                isBatteryOptimized = !isIgnored,
                guidanceSteps = listOf(
                    "Settings > Apps > Manage apps > ResQhunT: Enable 'Autostart'.",
                    "Settings > Apps > Manage apps > ResQhunT > Battery saver: Select 'No restrictions'.",
                    "Security App > Speed boost > Lock apps: Lock ResQhunT in the recent apps tray so clearing apps does not kill the mesh beacon.",
                    "Settings > Notifications & Control center: Allow lock screen notifications for ResQhunT."
                ),
                nativeSosGuide = "Xiaomi Emergency SOS: Go to Settings > Passwords & security > Emergency SOS. Turn ON and add trusted contacts. 5 rapid power-button presses sends emergency SMS with location."
            )
            mfr.contains("oppo") || mfr.contains("realme") || mfr.contains("oneplus") -> OemGuidance(
                manufacturer = Build.MANUFACTURER,
                brandName = "OPPO / Realme / OnePlus (ColorOS / OxygenOS)",
                isBatteryOptimized = !isIgnored,
                guidanceSteps = listOf(
                    "Settings > Apps > App management > ResQhunT > Battery usage: Turn ON 'Allow background activity' and 'Allow auto-launch'.",
                    "Settings > Battery > Advanced settings > App battery management: Turn off aggressive background freezing for ResQhunT.",
                    "Recent Apps: Swipe down or tap lock icon on ResQhunT to lock it in memory."
                ),
                nativeSosGuide = "OPPO/OnePlus Emergency SOS: Go to Settings > Safety & emergency > Emergency SOS. Enable rapid 5-press emergency calls."
            )
            mfr.contains("vivo") || mfr.contains("iqoo") -> OemGuidance(
                manufacturer = Build.MANUFACTURER,
                brandName = "Vivo / iQOO (Funtouch OS / OriginOS)",
                isBatteryOptimized = !isIgnored,
                guidanceSteps = listOf(
                    "Settings > Battery > Background power consumption: Set ResQhunT to 'High background power consumption'.",
                    "Settings > Applications and Permissions > Permission management > Autostart: Turn ON for ResQhunT.",
                    "Settings > Notifications > Lock screen: Enable show notifications on lock screen."
                ),
                nativeSosGuide = "Vivo Emergency SOS: Go to Settings > Safety & emergency > Emergency call. Configure emergency contacts for 5-press quick dialing."
            )
            mfr.contains("motorola") || mfr.contains("google") -> OemGuidance(
                manufacturer = Build.MANUFACTURER,
                brandName = "Motorola / Google Pixel (Stock Android)",
                isBatteryOptimized = !isIgnored,
                guidanceSteps = listOf(
                    "Settings > Apps > ResQhunT > App battery usage: Select 'Unrestricted'.",
                    "Settings > Notifications > Notifications on lock screen: Set to 'Show conversations and silent notifications'.",
                    "Quick Settings: Swipe down QS panel, tap edit pencil, and add 'ResQhunT SOS' tile to your active tiles."
                ),
                nativeSosGuide = "Stock Android Emergency SOS: Go to Settings > Safety & emergency > Emergency SOS. Turn ON 'Use Emergency SOS' to call 112/911 on 5 quick power presses."
            )
            else -> OemGuidance(
                manufacturer = Build.MANUFACTURER,
                brandName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} (Android)",
                isBatteryOptimized = !isIgnored,
                guidanceSteps = listOf(
                    "Settings > Apps > ResQhunT > Battery: Select 'Unrestricted' or 'Don't optimize'.",
                    "If your device has an 'Autostart' or 'Background Launch' setting, turn it ON for ResQhunT.",
                    "Add the 'ResQhunT SOS' Quick Settings tile to your notification shade for instant access."
                ),
                nativeSosGuide = "Device Emergency SOS: Check Settings > Safety & emergency for your device's native power-button SOS configuration."
            )
        }
    }
}
