package com.resqhunt.citizen.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.data.local.entity.RelayMessageEntity
import com.resqhunt.citizen.domain.model.DeliveryState
import com.resqhunt.citizen.domain.model.MeshIntegrity
import com.resqhunt.citizen.domain.model.MeshMessageEnvelope
import com.resqhunt.citizen.domain.model.MeshPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

class ResqHuntSyncClient(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    companion object {
        private const val TAG = "ResQhunT_SyncClient"
        private const val PREFS_NAME = "resqhunt_prefs"
        private const val KEY_BASE_URL = "gateway_base_url"
        const val DEFAULT_BASE_URL = "http://10.0.2.2:5000"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            val sanitized = value.trim().trimEnd('/')
            prefs.edit().putString(KEY_BASE_URL, sanitized).apply()
        }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val _lastSyncSuccessTime = MutableStateFlow<Long?>(null)
    val lastSyncSuccessTime: StateFlow<Long?> = _lastSyncSuccessTime.asStateFlow()

    data class SyncResult(
        val success: Boolean,
        val processedCount: Int,
        val message: String
    )

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Flushes buffered local emergency requests and forwarded mesh envelopes to backend.
     * Prevents duplicate incidents on retry using the messageId/requestId idempotency mechanism.
     */
    suspend fun syncPendingWithServer(): SyncResult = withContext(Dispatchers.IO) {
        _isSyncing.value = true

        if (!isOnline()) {
            val msg = "Device is offline. Connect to Wi-Fi or mobile network to sync with cloud."
            _lastSyncError.value = msg
            _isSyncing.value = false
            return@withContext SyncResult(false, 0, msg)
        }

        val pendingSosList = database.sosDao().getPendingUploadRequests()
        val unSyncedRelayMsgs = database.relayMessageDao().getUnsyncedMessages()

        if (pendingSosList.isEmpty() && unSyncedRelayMsgs.isEmpty()) {
            val msg = "No pending emergency requests to sync."
            _lastSyncError.value = null
            _isSyncing.value = false
            return@withContext SyncResult(true, 0, msg)
        }

        val envelopes = mutableListOf<MeshMessageEnvelope>()
        val includedRequestIds = mutableSetOf<String>()

        // 1. Add existing mesh message envelopes
        for (msg in unSyncedRelayMsgs) {
            try {
                val envelope = MeshMessageEnvelope.fromJson(msg.rawJsonEnvelope)
                if (envelope.messageType == "EMERGENCY_SOS" && envelope.payload != null) {
                    envelopes.add(envelope)
                    includedRequestIds.add(envelope.requestId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Skipping malformed raw envelope ${msg.messageId}", e)
            }
        }

        // 2. Synthesize envelopes for any local SOS entities not yet covered
        for (sos in pendingSosList) {
            if (!includedRequestIds.contains(sos.requestId)) {
                val messageId = "msg_sync_" + UUID.randomUUID().toString()
                val payload = MeshPayload(
                    category = sos.category,
                    severity = sos.severity,
                    affectedCount = sos.affectedCount,
                    description = sos.description,
                    latitude = sos.latitude,
                    longitude = sos.longitude,
                    locationAccuracy = sos.locationAccuracy,
                    locationAddress = sos.locationAddress
                )
                val rawContent = "${sos.requestId}:${sos.category}:${sos.severity}:${sos.affectedCount}:${sos.description}"
                val checksum = MeshMessageEnvelope.calculateChecksum(rawContent)

                val env = MeshMessageEnvelope(
                    messageId = messageId,
                    requestId = sos.requestId,
                    originDeviceId = "gateway_client",
                    messageType = "EMERGENCY_SOS",
                    protocolVersion = 1,
                    createdAt = sos.createdAt,
                    expiresAt = sos.createdAt + 86400000L,
                    hopCount = 0,
                    maxHops = 5,
                    payload = payload,
                    integrity = MeshIntegrity(checksum = checksum)
                )
                envelopes.add(env)
                includedRequestIds.add(sos.requestId)

                // Cache in relay_messages table as well
                database.relayMessageDao().insertMessage(
                    RelayMessageEntity(
                        messageId = messageId,
                        requestId = sos.requestId,
                        originDeviceId = "gateway_client",
                        hopCount = 0,
                        maxHops = 5,
                        expiresAt = env.expiresAt,
                        rawJsonEnvelope = env.toJson(),
                        status = "PENDING_FORWARD"
                    )
                )
            }
        }

        if (envelopes.isEmpty()) {
            _isSyncing.value = false
            return@withContext SyncResult(true, 0, "No valid emergency payloads found to transmit.")
        }

        val requestPayload = mapOf("envelopes" to envelopes)
        val jsonBody = Gson().toJson(requestPayload)
        val targetUrl = "${baseUrl.trimEnd('/')}/api/sos/sync"

        try {
            val httpRequest = Request.Builder()
                .url(targetUrl)
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonObject = try {
                    JsonParser.parseString(responseBody).asJsonObject
                } catch (e: Exception) {
                    JsonObject()
                }

                val processedCount = jsonObject.get("processed")?.asInt ?: envelopes.size
                val resultsArray = jsonObject.getAsJsonArray("results")

                // Update confirmed requests in Room
                if (resultsArray != null) {
                    for (item in resultsArray) {
                        val obj = item.asJsonObject
                        val reqId = obj.get("requestId")?.asString
                        val state = obj.get("status")?.asString ?: DeliveryState.SERVER_RECEIVED.name
                        val score = obj.get("priorityScore")?.asFloat ?: 50f
                        val category = obj.get("priorityCategory")?.asString ?: "MEDIUM"

                        if (reqId != null) {
                            database.sosDao().updateServerState(reqId, state, score, category)
                        }
                    }
                } else {
                    for (env in envelopes) {
                        database.sosDao().updateDeliveryState(env.requestId, DeliveryState.SERVER_RECEIVED.name)
                    }
                }

                // Update relay records to SYNCED_SERVER
                for (env in envelopes) {
                    database.relayMessageDao().updateStatus(env.messageId, "SYNCED_SERVER")
                }

                _lastSyncSuccessTime.value = System.currentTimeMillis()
                _lastSyncError.value = null
                _isSyncing.value = false

                val msg = "Successfully synced $processedCount incident(s) to cloud gateway!"
                Log.i(TAG, msg)
                return@withContext SyncResult(true, processedCount, msg)
            } else {
                val errorMsg = "Gateway rejected sync (HTTP ${response.code}): $responseBody"
                Log.w(TAG, errorMsg)
                _lastSyncError.value = errorMsg
                _isSyncing.value = false
                return@withContext SyncResult(false, 0, errorMsg)
            }
        } catch (e: ConnectException) {
            val errorMsg = "Cannot connect to gateway at $targetUrl (Connection refused). Verify backend server is running and accessible."
            Log.e(TAG, errorMsg, e)
            _lastSyncError.value = errorMsg
            _isSyncing.value = false
            return@withContext SyncResult(false, 0, errorMsg)
        } catch (e: SocketTimeoutException) {
            val errorMsg = "Gateway connection timed out after 12s at $targetUrl."
            Log.e(TAG, errorMsg, e)
            _lastSyncError.value = errorMsg
            _isSyncing.value = false
            return@withContext SyncResult(false, 0, errorMsg)
        } catch (e: UnknownHostException) {
            val errorMsg = "Unknown gateway host: ${e.message}. Check Gateway Base URL in Settings."
            Log.e(TAG, errorMsg, e)
            _lastSyncError.value = errorMsg
            _isSyncing.value = false
            return@withContext SyncResult(false, 0, errorMsg)
        } catch (e: Exception) {
            val errorMsg = "Sync failed: ${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, errorMsg, e)
            _lastSyncError.value = errorMsg
            _isSyncing.value = false
            return@withContext SyncResult(false, 0, errorMsg)
        }
    }

    /**
     * Fetches current server-side status for an incident (e.g. COORDINATOR_ACKNOWLEDGED, ASSIGNED, RESOLVED).
     */
    suspend fun checkIncidentStatus(requestId: String): DeliveryState? = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext null

        val targetUrl = "${baseUrl.trimEnd('/')}/api/incidents/$requestId"
        try {
            val request = Request.Builder().url(targetUrl).get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val root = JsonParser.parseString(body).asJsonObject
                val incident = root.getAsJsonObject("incident")
                if (incident != null) {
                    val serverStateStr = incident.get("deliveryState")?.asString
                    val score = incident.get("priorityScore")?.asFloat ?: 50f
                    val cat = incident.get("priorityCategory")?.asString ?: "MEDIUM"

                    if (serverStateStr != null) {
                        database.sosDao().updateServerState(requestId, serverStateStr, score, cat)
                        return@withContext try {
                            DeliveryState.valueOf(serverStateStr)
                        } catch (e: Exception) {
                            DeliveryState.SERVER_RECEIVED
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed checking status for incident $requestId: ${e.message}")
        }
        return@withContext null
    }
}
