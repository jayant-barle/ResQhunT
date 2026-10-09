package com.resqhunt.citizen.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resqhunt.citizen.data.local.entity.ResourceCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourceCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(resources: List<ResourceCacheEntity>)

    @Query("SELECT * FROM resource_cache ORDER BY category ASC")
    fun getAllCachedResourcesFlow(): Flow<List<ResourceCacheEntity>>
}
