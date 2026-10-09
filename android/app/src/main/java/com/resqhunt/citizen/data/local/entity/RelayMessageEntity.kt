package com.resqhunt.citizen.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "relay_messages",
    indices = [Index(value = ["requestId"])]
)
data class RelayMessageEntity(
    @PrimaryKey
    val messageId: String,
    val requestId: String,
    val originDeviceId: String,
    val hopCount: Int,
    val maxHops: Int,
    val expiresAt: Long,
    val rawJsonEnvelope: String,
    val status: String, // PENDING_FORWARD, FORWARDED, SYNCED_SERVER, EXPIRED
    val createdAt: Long = System.currentTimeMillis()
)
