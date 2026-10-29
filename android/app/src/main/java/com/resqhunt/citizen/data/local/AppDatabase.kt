package com.resqhunt.citizen.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sos_requests ADD COLUMN locationTimestamp INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE sos_requests ADD COLUMN locationSource TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE relay_messages ADD COLUMN lastReceivedFromEndpointId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE relay_messages ADD COLUMN forwardedEndpoints TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "resqhunt_offline.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
