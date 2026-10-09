package com.resqhunt.citizen.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resource_cache")
data class ResourceCacheEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val category: String,
    val availableQuantity: Int,
    val unit: String,
    val location: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
