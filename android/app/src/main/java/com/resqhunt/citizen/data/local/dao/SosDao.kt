package com.resqhunt.citizen.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.resqhunt.citizen.data.local.entity.SosEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SosDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSos(sos: SosEntity)

    @Update
    suspend fun updateSos(sos: SosEntity)

    @Query("SELECT * FROM sos_requests WHERE requestId = :id LIMIT 1")
    suspend fun getSosById(id: String): SosEntity?

    @Query("SELECT * FROM sos_requests ORDER BY createdAt DESC")
    fun getAllSosFlow(): Flow<List<SosEntity>>

    @Query("SELECT * FROM sos_requests ORDER BY createdAt DESC LIMIT 1")
    fun getLatestSosFlow(): Flow<SosEntity?>

    @Query("SELECT * FROM sos_requests WHERE deliveryState IN ('CREATED', 'STORED_LOCALLY', 'RELAY_PENDING')")
    suspend fun getPendingUploadRequests(): List<SosEntity>

    @Query("UPDATE sos_requests SET deliveryState = :newState, updatedAt = :timestamp WHERE requestId = :requestId")
    suspend fun updateDeliveryState(requestId: String, newState: String, timestamp: Long = System.currentTimeMillis())
}
