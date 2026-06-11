package com.bitecma.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedOperationEntity::class,
        CachedMasterListEntity::class,
        PendingTextFileEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class LocalCacheDatabase : RoomDatabase() {
    abstract fun localCacheDao(): LocalCacheDao

    companion object {
        @Volatile
        private var INSTANCE: LocalCacheDatabase? = null

        fun getInstance(context: Context): LocalCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalCacheDatabase::class.java,
                    "bitecma_local_cache.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
