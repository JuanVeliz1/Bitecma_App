package com.bitecma.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LocalCacheDao {
    @Query("SELECT * FROM cached_operations ORDER BY updatedAt DESC")
    suspend fun getOperations(): List<CachedOperationEntity>

    @Query("DELETE FROM cached_operations")
    suspend fun clearOperations()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperations(items: List<CachedOperationEntity>)

    @Query("SELECT * FROM cached_master_lists")
    suspend fun getMasterLists(): List<CachedMasterListEntity>

    @Query("DELETE FROM cached_master_lists")
    suspend fun clearMasterLists()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMasterLists(items: List<CachedMasterListEntity>)

    @Query("SELECT * FROM pending_text_files ORDER BY createdAt ASC, id ASC")
    suspend fun getPendingFiles(): List<PendingTextFileEntity>

    @Query("DELETE FROM pending_text_files")
    suspend fun clearPendingFiles()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingFiles(items: List<PendingTextFileEntity>)

    @Query("SELECT COUNT(*) FROM cached_operations")
    suspend fun countOperations(): Int

    @Query("SELECT COUNT(*) FROM cached_master_lists")
    suspend fun countMasterLists(): Int

    @Query("SELECT COUNT(*) FROM pending_text_files")
    suspend fun countPendingFiles(): Int

    @Transaction
    suspend fun replaceOperations(items: List<CachedOperationEntity>) {
        clearOperations()
        if (items.isNotEmpty()) insertOperations(items)
    }

    @Transaction
    suspend fun replaceMasterLists(items: List<CachedMasterListEntity>) {
        clearMasterLists()
        if (items.isNotEmpty()) insertMasterLists(items)
    }

    @Transaction
    suspend fun replacePendingFiles(items: List<PendingTextFileEntity>) {
        clearPendingFiles()
        if (items.isNotEmpty()) insertPendingFiles(items)
    }
}
