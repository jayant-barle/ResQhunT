package com.resqhunt.citizen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.resqhunt.citizen.ResQhunTApp
import com.resqhunt.citizen.mesh.NearbyConnectionsManager
import com.resqhunt.citizen.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SosRelayForegroundService : Service() {

    companion object {
        private const val TAG = "ResQhunT_RelayService"
        const val CHANNEL_ID = "resqhunt_emergency_channel"
        const val NOTIFICATION_ID = 9110
        const val ACTION_START_RELAY = "com.resqhunt.ACTION_START_RELAY"
        const val ACTION_STOP_RELAY = "com.resqhunt.ACTION_STOP_RELAY"

        fun start(context: Context) {
            try {
                val intent = Intent(context, SosRelayForegroundService::class.java).apply {
                    action = ACTION_START_RELAY
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Catch ForegroundServiceStartNotAllowedException on Android 12+ or SecurityException
                Log.w(TAG, "Unable to start foreground service from current state: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SosRelayForegroundService::class.java).apply {
                    action = ACTION_STOP_RELAY
                }
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping foreground service: ${e.message}")
            }
        }
    }

    private val nearbyManager: NearbyConnectionsManager
        get() = (application as ResQhunTApp).nearbyManager

    private val activationManager by lazy {
        EmergencyActivationManager.getInstance(applicationContext)
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    private var screenReceiver: android.content.BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "ResQhunT foreground service initialized. Mesh beacon active. Supported shortcuts: In-App Volume Up + Down 3s hold, Lock-Screen notification action, and Quick Settings tile.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SosRelayForegroundService destroyed.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RELAY) {
            nearbyManager.stopAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val isProcessKilledRevival = intent == null || (flags and START_FLAG_REDELIVERY != 0)
        if (isProcessKilledRevival) {
            AppLifecycleStateTracker.markProcessRevived("START_STICKY_SERVICE_RESTART")
            Log.i(TAG, "[STATE: PROCESS_KILLED] Android OS restarted SosRelayForegroundService via START_STICKY following process termination.")
        } else {
            AppLifecycleStateTracker.logCurrentState(TAG)
            Log.i(TAG, "[STATE: BACKGROUND] SosRelayForegroundService active in background with ongoing notification.")
        }

        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 14+ (API 34+) and 15 (API 35+) require location permission at runtime
            // before specifying FOREGROUND_SERVICE_TYPE_LOCATION
            val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (hasLocation) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }

            try {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed startForeground with type $serviceType, falling back to connectedDevice: ${e.message}")
                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Fallback startForeground failed: ${fallbackEx.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "startForeground error: ${e.message}")
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start active BLE/Wi-Fi cluster advertising and discovery on the shared manager
        nearbyManager.startAdvertising()
        nearbyManager.startDiscovery()

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "[STATE: BACKGROUND] Task removed from Recents tray. Foreground service remains active with ongoing notification.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ResQhunT Emergency Mesh Active",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps offline device-to-device emergency mesh relay active."
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Lock-Screen Fallback Action: One-Tap Trigger SOS
        val triggerSosIntent = Intent(this, com.resqhunt.citizen.alert.EmergencyAlertActionReceiver::class.java).apply {
            action = com.resqhunt.citizen.alert.EmergencyAlertActionReceiver.ACTION_TRIGGER_SOS
        }
        val triggerSosPendingIntent = PendingIntent.getBroadcast(
            this,
            201,
            triggerSosIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ResQhunT Emergency Mode Active")
            .setContentText("Mesh beacon active. Tap SOS below, or use Volume Up + Down in app.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_dialog_alert,
                "🚨 TRIGGER SOS NOW",
                triggerSosPendingIntent
            )
            .build()
    }
}
