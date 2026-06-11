package com.bitecma.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_operations")
data class CachedOperationEntity(
    @PrimaryKey val id: String,
    val source: String,
    val dirty: Boolean,
    val syncStatus: String,
    val lastSyncError: String?,
    val lastSyncAttemptAt: Long?,
    val lastSyncSuccessAt: Long?,
    val payloadJson: String,
    val updatedAt: Long,
)

@Entity(tableName = "cached_master_lists")
data class CachedMasterListEntity(
    @PrimaryKey val key: String,
    val payloadJson: String,
    val updatedAt: Long,
)

@Entity(tableName = "pending_text_files")
data class PendingTextFileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val opId: String?,
    val text: String,
    val syncStatus: String,
    val lastSyncError: String?,
    val lastSyncAttemptAt: Long?,
    val createdAt: Long,
)
