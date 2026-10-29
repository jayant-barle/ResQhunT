package com.resqhunt.citizen.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections

data class ActiveAlert(
    val requestId: String,
    val originDeviceId: String,
    val category: String,
    val severity: String,
    val affectedCount: Int,
    val description: String,
    val locationAddress: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val locationTimestamp: Long? = null,
    val locationSource: String? = null,
    val isTest: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

object EmergencyAlertManager {
    private const val TAG = "ResQhunT_Alert"
    const val CHANNEL_ID = "resqhunt_emergency_sos_alerts"
    const val NOTIFICATION_ID = 9999
    private const val AUTO_SILENCE_TIMEOUT_MS = 45000L // Safety timeout 45s
    private const val TEST_AUTO_SILENCE_TIMEOUT_MS = 6000L // 6s test timeout

    private val scope = CoroutineScope(Dispatchers.Main)
    private val alertedRequestIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val _activeAlert = MutableStateFlow<ActiveAlert?>(null)
    val activeAlert: StateFlow<ActiveAlert?> = _activeAlert.asStateFlow()

    private var activeRingtone: Ringtone? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private var activeVibrator: Vibrator? = null
    private var autoSilenceJob: Job? = null

    val vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 800, 400, 1200)

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Critical Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Loud audio alarm and vibration for incoming nearby emergency requests"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = this@EmergencyAlertManager.vibrationPattern
                setSound(soundUri, audioAttributes)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Triggers sound, vibration, heads-up notification, and in-app modal for an incoming valid SOS.
     * Guaranteed duplicate suppression: each requestId is only alerted once.
     */
    fun triggerSosAlert(context: Context, sos: SosEntity, originDeviceId: String): Boolean {
        // Guard against duplicate alerts for the same emergency request
        if (!alertedRequestIds.add(sos.requestId)) {
            Log.d(TAG, "Suppressed duplicate alert for emergency request: ${sos.requestId}")
            return false
        }

        val alert = ActiveAlert(
            requestId = sos.requestId,
            originDeviceId = originDeviceId,
            category = sos.category,
            severity = sos.severity,
            affectedCount = sos.affectedCount,
            description = sos.description,
            locationAddress = sos.locationAddress,
            latitude = sos.latitude,
            longitude = sos.longitude,
            locationAccuracy = sos.locationAccuracy,
            locationTimestamp = sos.locationTimestamp,
            locationSource = sos.locationSource,
            isTest = false
        )

        executeAlert(context, alert, AUTO_SILENCE_TIMEOUT_MS)
        return true
    }

    /**
     * In-app Test Alarm action so users/evaluators can test sound and vibration safely.
     */
    fun testAlarm(context: Context) {
        val testAlert = ActiveAlert(
            requestId = "test_" + java.util.UUID.randomUUID().toString().take(8),
            originDeviceId = "LOCAL_SELF_TEST",
            category = "TEST_ALARM",
            severity = "CRITICAL",
            affectedCount = 1,
            description = "ResQhunT Audio & Vibration Alarm Test. Verifying speaker, haptic motor, and notification channel.",
            locationAddress = "Device Audio Test Mode",
            isTest = true
        )

        executeAlert(context, testAlert, TEST_AUTO_SILENCE_TIMEOUT_MS)
    }

    private fun executeAlert(context: Context, alert: ActiveAlert, timeoutMs: Long) {
        // Stop any running prior alarm before starting new
        silenceAlert(context, keepActiveAlert = false)

        _activeAlert.value = alert
        createNotificationChannel(context)

        // 1. Audio playback (Alarm Stream)
        startAlarmAudio(context)

        // 2. Vibration
        startVibration(context)

        // 3. Post System Notification
        postAlertNotification(context, alert)

        // 4. Safety auto-silence timer
        autoSilenceJob?.cancel()
        autoSilenceJob = scope.launch {
            delay(timeoutMs)
            Log.i(TAG, "Auto-silenced alert after ${timeoutMs / 1000}s timeout")
            silenceAlert(context, keepActiveAlert = false)
        }
    }

    private fun startAlarmAudio(context: Context) {
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Try MediaPlayer first for looping alarm
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                activeMediaPlayer = mp
                Log.d(TAG, "Started MediaPlayer emergency alarm audio")
                return
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer failed, falling back to Ringtone: ${e.message}")
            }

            // Fallback: Ringtone
            val ringtone = RingtoneManager.getRingtone(context, alarmUri)
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.isLooping = true
                }
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                ringtone.play()
                activeRingtone = ringtone
                Log.d(TAG, "Started Ringtone emergency alarm audio")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start emergency alarm audio", e)
        }
    }

    private fun startVibration(context: Context) {
        try {
            val vibrator = getVibrator(context)
            activeVibrator = vibrator

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(vibrationPattern, 0) // repeat at 0
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(vibrationPattern, 0)
                }
                Log.d(TAG, "Started emergency vibration pattern")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun postAlertNotification(context: Context, alert: ActiveAlert) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Tap intent: Open MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("requestId", alert.requestId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action intent: Silence Alert
        val silenceIntent = Intent(context, EmergencyAlertActionReceiver::class.java).apply {
            action = EmergencyAlertActionReceiver.ACTION_SILENCE_ALERT
        }
        val silencePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            silenceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action intent: Cancel SOS
        val cancelIntent = Intent(context, EmergencyAlertActionReceiver::class.java).apply {
            action = EmergencyAlertActionReceiver.ACTION_CANCEL_SOS
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (alert.isTest) {
            "🚨 [TEST] EMERGENCY ALARM SOUNDING"
        } else {
            "🚨 INCOMING EMERGENCY SOS: ${alert.category}"
        }

        val text = if (alert.isTest) {
            "Testing alarm audio and vibration. Tap to open or Silence below."
        } else {
            "${alert.severity} • ${alert.description}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nOrigin Node: ${alert.originDeviceId}\nAffected: ${alert.affectedCount} person(s)"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_lock_silent_mode,
                "SILENCE ALERT",
                silencePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "CANCEL SOS",
                cancelPendingIntent
            )
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission denied or restricted by OS: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post emergency notification: ${e.message}")
        }
    }

    /**
     * User-controlled action to stop ringing, stop vibration, and dismiss the alert.
     */
    fun silenceAlert(context: Context? = null, keepActiveAlert: Boolean = false) {
        autoSilenceJob?.cancel()
        autoSilenceJob = null

        try {
            activeMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaPlayer", e)
        } finally {
            activeMediaPlayer = null
        }

        try {
            activeRingtone?.let {
                if (it.isPlaying) it.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Ringtone", e)
        } finally {
            activeRingtone = null
        }

        try {
            activeVibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling Vibrator", e)
        } finally {
            activeVibrator = null
        }

        context?.let { ctx ->
            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
        }

        if (!keepActiveAlert) {
            _activeAlert.value = null
        }

        Log.i(TAG, "Emergency alert silenced and reset.")
    }
}
