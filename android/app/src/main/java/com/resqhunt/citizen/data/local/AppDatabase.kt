package com.resqhunt.citizen.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.resqhunt.citizen.data.local.dao.PeerDao
import com.resqhunt.citizen.data.local.dao.RelayMessageDao
import com.resqhunt.citizen.data.local.dao.ResourceCacheDao
import com.resqhunt.citizen.data.local.dao.SosDao
import com.resqhunt.citizen.data.local.entity.PeerEntity
import com.resqhunt.citizen.data.local.entity.RelayMessageEntity
import com.resqhunt.citizen.data.local.entity.ResourceCacheEntity
import com.resqhunt.citizen.data.local.entity.SosEntity

@Database(
    entities = [
        SosEntity::class,
        RelayMessageEntity::class,
        PeerEntity::class,
        ResourceCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sosDao(): SosDao
    abstract fun relayMessageDao(): RelayMessageDao
    abstract fun peerDao(): PeerDao
    abstract fun resourceCacheDao(): ResourceCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "resqhunt_offline.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
