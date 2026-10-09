package com.resqhunt.citizen.domain.model

import com.google.gson.Gson
import java.security.MessageDigest

data class MeshPayload(
    val category: String,
    val severity: String,
    val affectedCount: Int,
    val description: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val locationAddress: String? = null,
    val emergencyContacts: List<EmergencyContact>? = null
)

data class EmergencyContact(
    val name: String,
    val phone: String
)

data class MeshIntegrity(
    val checksum: String,
    val algorithm: String = "SHA-256"
)

data class MeshMessageEnvelope(
    val messageId: String,
    val requestId: String,
    val originDeviceId: String,
    val messageType: String = "EMERGENCY_SOS",
    val protocolVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 86400000L, // 24 hours TTL
    val hopCount: Int = 1,
    val maxHops: Int = 5,
    val payload: MeshPayload,
    val integrity: MeshIntegrity
) {
    fun toJson(): String {
        return Gson().toJson(this)
    }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return now > expiresAt
    }

    fun hasExceededHops(): Boolean {
        return hopCount >= maxHops
    }

    fun nextHopEnvelope(): MeshMessageEnvelope {
        return this.copy(hopCount = this.hopCount + 1)
    }

    companion object {
        fun fromJson(json: String): MeshMessageEnvelope {
            return Gson().fromJson(json, MeshMessageEnvelope::class.java)
        }

        fun calculateChecksum(content: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
