package com.resqhunt.citizen.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.gson.Gson
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.domain.model.DeliveryState
import com.resqhunt.citizen.domain.model.MeshMessageEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ResqHuntSyncClient(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    companion object {
        private const val TAG = "ResQhunT_SyncClient"
        // Default local gateway URL; user-configurable in ProfileSettings
        var baseUrl: String = "http://10.0.2.2:5000" // 10.0.2.2 maps to host machine in Android Emulator
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Flushes buffered local emergency requests and forwarded mesh envelopes to backend.
     */
    suspend fun syncPendingWithServer(): Int = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            Log.d(TAG, "No internet connectivity. Remaining in offline mesh mode.")
            return@withContext 0
        }

        val pendingMessages = database.relayMessageDao().getPendingForwardMessages()
        if (pendingMessages.isEmpty()) {
            return@withContext 0
        }

        val envelopes = mutableListOf<MeshMessageEnvelope>()
        for (msg in pendingMessages) {
            try {
                envelopes.add(MeshMessageEnvelope.fromJson(msg.rawJsonEnvelope))
            } catch (e: Exception) {
                Log.e(TAG, "Skipping malformed message ${msg.messageId}", e)
            }
        }

        if (envelopes.isEmpty()) return@withContext 0

        val requestPayload = mapOf("envelopes" to envelopes)
        val jsonBody = Gson().toJson(requestPayload)

        try {
            val httpRequest = Request.Builder()
                .url("$baseUrl/api/sos/sync")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.i(TAG, "Successfully synced ${envelopes.size} envelopes to server. Response: $responseBody")

                // Mark messages and SOS as SERVER_RECEIVED
                for (env in envelopes) {
                    database.relayMessageDao().updateStatus(env.messageId, "SYNCED_SERVER")
                    database.sosDao().updateDeliveryState(env.requestId, DeliveryState.SERVER_RECEIVED.name)
                }
                return@withContext envelopes.size
            } else {
                Log.w(TAG, "Sync rejected by server: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network failure during sync with $baseUrl", e)
        }

        return@withContext 0
    }
}
