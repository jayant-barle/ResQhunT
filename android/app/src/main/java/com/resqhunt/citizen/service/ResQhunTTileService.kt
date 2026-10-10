package com.resqhunt.citizen.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile fallback for rapid SOS activation.
 * Allows one-tap emergency broadcast from notification shade / lock-screen QS panel
 * across Android 10-15 without relying on hardware button quirks.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ResQhunTTileService : TileService() {

    companion object {
        private const val TAG = "ResQhunT_TileService"
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = "ResQhunT SOS"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "One-Tap Emergency"
        }
        tile.contentDescription = "Trigger ResQhunT Emergency SOS"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val stateSummary = AppLifecycleStateTracker.logCurrentState(TAG)
        Log.i(TAG, "[TRIGGER_ACTION: TILE] Quick Settings SOS Tile clicked. Activating emergency workflow... | $stateSummary")

        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "SOS ACTIVATED"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Broadcasting to Mesh"
            }
            tile.updateTile()
        }

        val activationManager = EmergencyActivationManager.getInstance(applicationContext)
        scope.launch {
            activationManager.executeEmergencyActivation(source = "QUICK_SETTINGS_TILE")
        }
    }
}
