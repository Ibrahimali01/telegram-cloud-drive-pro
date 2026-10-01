package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TelegramAccountEntity::class,
        DriveVaultEntity::class,
        DriveFileEntity::class,
        FileChunkEntity::class,
        ProxyConfigEntity::class,
        ShareLinkEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): TelegramAccountDao
    abstract fun vaultDao(): DriveVaultDao
    abstract fun fileDao(): DriveFileDao
    abstract fun chunkDao(): FileChunkDao
    abstract fun proxyDao(): ProxyConfigDao
    abstract fun shareLinkDao(): ShareLinkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "teledrive_pro.db"
                ).fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
