package com.resqhunt.citizen

import android.app.Application
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.data.remote.ResqHuntSyncClient
import com.resqhunt.citizen.mesh.NearbyConnectionsManager
import com.resqhunt.citizen.mesh.StoreAndForwardRelayEngine

class ResQhunTApp : Application() {

    companion object {
        lateinit var instance: ResQhunTApp
            private set
    }

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    val nearbyManager: NearbyConnectionsManager by lazy {
        NearbyConnectionsManager(this)
    }

    val relayEngine: StoreAndForwardRelayEngine by lazy {
        StoreAndForwardRelayEngine(this, nearbyManager, database)
    }

    val syncClient: ResqHuntSyncClient by lazy {
        ResqHuntSyncClient(this, database)
    }

    val locationManager: com.resqhunt.citizen.location.EmergencyLocationManager by lazy {
        com.resqhunt.citizen.location.EmergencyLocationManager.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.resqhunt.citizen.service.AppLifecycleStateTracker.init(this)
        com.resqhunt.citizen.alert.EmergencyAlertManager.createNotificationChannel(this)
    }
}
