package com.resqhunt.citizen.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sos_requests")
data class SosEntity(
    @PrimaryKey
    val requestId: String,
    val category: String,
    val severity: String,
    val affectedCount: Int,
    val description: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val locationAddress: String? = null,
    val deliveryState: String, // CREATED, STORED_LOCALLY, RELAY_PENDING, RELAYED_TO_PEER, SERVER_RECEIVED, etc.
    val priorityScore: Float,
    val priorityCategory: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
