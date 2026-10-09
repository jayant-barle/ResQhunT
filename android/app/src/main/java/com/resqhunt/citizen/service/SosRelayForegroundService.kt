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
import androidx.core.app.NotificationCompat
import com.resqhunt.citizen.ResQhunTApp
import com.resqhunt.citizen.mesh.NearbyConnectionsManager
import com.resqhunt.citizen.ui.MainActivity

class SosRelayForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "resqhunt_emergency_channel"
        const val NOTIFICATION_ID = 9110
        const val ACTION_START_RELAY = "com.resqhunt.ACTION_START_RELAY"
        const val ACTION_STOP_RELAY = "com.resqhunt.ACTION_STOP_RELAY"

        fun start(context: Context) {
            val intent = Intent(context, SosRelayForegroundService::class.java).apply {
                action = ACTION_START_RELAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SosRelayForegroundService::class.java).apply {
                action = ACTION_STOP_RELAY
            }
            context.stopService(intent)
        }
    }

    private val nearbyManager: NearbyConnectionsManager
        get() = (application as ResQhunTApp).nearbyManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RELAY) {
            nearbyManager.stopAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start active BLE/Wi-Fi cluster advertising and discovery on the shared manager
        nearbyManager.startAdvertising()
        nearbyManager.startDiscovery()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ResQhunT Emergency Mode Active")
            .setContentText("Mesh relay beacon active. Searching for nearby rescue peers...")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
}
