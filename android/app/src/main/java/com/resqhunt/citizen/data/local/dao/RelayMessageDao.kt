package com.resqhunt.citizen.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.resqhunt.citizen.data.local.entity.RelayMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RelayMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: RelayMessageEntity): Long

    @Query("SELECT COUNT(*) FROM relay_messages WHERE messageId = :messageId")
    suspend fun hasMessage(messageId: String): Int

    @Query("SELECT * FROM relay_messages WHERE status = 'PENDING_FORWARD' AND expiresAt > :now ORDER BY createdAt ASC")
    suspend fun getPendingForwardMessages(now: Long = System.currentTimeMillis()): List<RelayMessageEntity>

    @Query("SELECT * FROM relay_messages WHERE status IN ('PENDING_FORWARD', 'SENDING', 'FORWARDED') AND hopCount < maxHops AND expiresAt > :now ORDER BY createdAt ASC")
    suspend fun getEligibleForwardMessages(now: Long = System.currentTimeMillis()): List<RelayMessageEntity>

    @Query("SELECT * FROM relay_messages WHERE status != 'SYNCED_SERVER' AND expiresAt > :now ORDER BY createdAt ASC")
    suspend fun getUnsyncedMessages(now: Long = System.currentTimeMillis()): List<RelayMessageEntity>

    @Query("SELECT * FROM relay_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): RelayMessageEntity?

    @Query("SELECT * FROM relay_messages WHERE requestId = :requestId")
    suspend fun getMessagesForRequest(requestId: String): List<RelayMessageEntity>

    @Query("SELECT * FROM relay_messages ORDER BY createdAt DESC")
    fun getAllMessagesFlow(): Flow<List<RelayMessageEntity>>

    @Query("UPDATE relay_messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Query("UPDATE relay_messages SET status = :status, forwardedEndpoints = :forwardedEndpoints WHERE messageId = :messageId")
    suspend fun updateStatusAndEndpoints(messageId: String, status: String, forwardedEndpoints: String)

    @Query("UPDATE relay_messages SET forwardedEndpoints = :forwardedEndpoints WHERE messageId = :messageId")
    suspend fun updateForwardedEndpoints(messageId: String, forwardedEndpoints: String)

    @Query("DELETE FROM relay_messages WHERE expiresAt <= :now")
    suspend fun pruneExpiredMessages(now: Long = System.currentTimeMillis()): Int
}

