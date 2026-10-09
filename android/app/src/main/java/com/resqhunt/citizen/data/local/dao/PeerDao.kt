package com.resqhunt.citizen.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resqhunt.citizen.data.local.entity.PeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(peer: PeerEntity)

    @Query("SELECT * FROM nearby_peers ORDER BY lastSeenTimestamp DESC")
    fun getAllPeersFlow(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM nearby_peers WHERE isConnected = 1")
    suspend fun getConnectedPeers(): List<PeerEntity>

    @Query("UPDATE nearby_peers SET isConnected = :connected WHERE endpointId = :endpointId")
    suspend fun updateConnectionState(endpointId: String, connected: Boolean)

    @Query("DELETE FROM nearby_peers WHERE endpointId = :endpointId")
    suspend fun removePeer(endpointId: String)
}
