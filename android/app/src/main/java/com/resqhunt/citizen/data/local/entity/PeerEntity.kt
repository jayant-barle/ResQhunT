package com.resqhunt.citizen.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nearby_peers")
data class PeerEntity(
    @PrimaryKey
    val endpointId: String,
    val deviceName: String,
    val isConnected: Boolean,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
