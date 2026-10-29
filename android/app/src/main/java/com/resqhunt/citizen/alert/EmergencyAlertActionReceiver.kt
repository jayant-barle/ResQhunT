package com.resqhunt.citizen.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmergencyAlertActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SILENCE_ALERT = "com.resqhunt.ACTION_SILENCE_ALERT"
        const val ACTION_TRIGGER_SOS = "com.resqhunt.ACTION_TRIGGER_SOS"
        const val ACTION_CANCEL_SOS = "com.resqhunt.ACTION_CANCEL_SOS"
        private const val TAG = "ResQhunT_AlertReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.i(TAG, "Received broadcast action: $action")
        when (action) {
            ACTION_SILENCE_ALERT -> {
                EmergencyAlertManager.silenceAlert(context)
            }
            ACTION_TRIGGER_SOS -> {
                val stateSummary = com.resqhunt.citizen.service.AppLifecycleStateTracker.logCurrentState(TAG)
                Log.i(TAG, "[TRIGGER_ACTION: NOTIFICATION] Triggering emergency SOS from notification action... | $stateSummary")
                val activationManager = com.resqhunt.citizen.service.EmergencyActivationManager.getInstance(context)
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    activationManager.executeEmergencyActivation(source = "LOCKSCREEN_NOTIFICATION")
                }
            }
            ACTION_CANCEL_SOS -> {
                Log.i(TAG, "Cancelling emergency SOS from notification action...")
                val activationManager = com.resqhunt.citizen.service.EmergencyActivationManager.getInstance(context)
                activationManager.cancelEmergencyActivation(context)
            }
        }
    }
}
