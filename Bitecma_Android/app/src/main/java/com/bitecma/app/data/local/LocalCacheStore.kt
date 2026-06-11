package com.bitecma.app.data.local

import androidx.room.withTransaction
import com.bitecma.app.network.BoteMaestroDto
import com.bitecma.app.network.CaletaDto
import com.bitecma.app.network.EspecieDto
import com.bitecma.app.network.OpaDto
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.RegionDto
import com.bitecma.app.network.SectorAmerbDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PendingTextFileRecord(
    val id: String,
    val name: String,
    val opId: String? = null,
    val text: String,
    val status: String,
    val lastError: String? = null,
    val lastAttemptAt: Long? = null,
    val createdAt: Long,
)

data class OperationSyncStateRecord(
    val opId: String,
    val status: String,
    val lastError: String? = null,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
)

data class LocalCacheSnapshot(
    val operacionesBd: List<OperacionDto> = emptyList(),
    val operacionesLc: List<OperacionDto> = emptyList(),
    val dirtyOperacionIds: List<String> = emptyList(),
    val operationSyncStates: List<OperationSyncStateRecord> = emptyList(),
    val pendingTextFiles: List<PendingTextFileRecord> = emptyList(),
    val regiones: List<RegionDto> = emptyList(),
    val sectoresAmerb: List<SectorAmerbDto> = emptyList(),
    val caletas: List<CaletaDto> = emptyList(),
    val opas: List<OpaDto> = emptyList(),
    val botesMaestros: List<BoteMaestroDto> = emptyList(),
    val especiesMaestras: List<EspecieDto> = emptyList(),
)

class LocalCacheStore(
    private val db: LocalCacheDatabase,
) {
    private val gson = Gson()

    suspend fun hasPersistedData(): Boolean {
        val dao = db.localCacheDao()
        return dao.countOperations() > 0 || dao.countMasterLists() > 0 || dao.countPendingFiles() > 0
    }

    suspend fun loadSnapshot(): LocalCacheSnapshot {
        val dao = db.localCacheDao()
        val operations = dao.getOperations()
        val masters = dao.getMasterLists().associateBy { it.key }
        val pendingFiles = dao.getPendingFiles()

        return LocalCacheSnapshot(
            operacionesBd = operations.filter { it.source == SOURCE_BD }.mapNotNull { parseObject<OperacionDto>(it.payloadJson) },
            operacionesLc = operations.filter { it.source == SOURCE_LC }.mapNotNull { parseObject<OperacionDto>(it.payloadJson) },
            dirtyOperacionIds = operations.filter { it.dirty }.map { it.id }.distinct(),
            operationSyncStates = operations.map {
                OperationSyncStateRecord(
                    opId = it.id,
                    status = it.syncStatus,
                    lastError = it.lastSyncError,
                    lastAttemptAt = it.lastSyncAttemptAt,
                    lastSuccessAt = it.lastSyncSuccessAt,
                )
            },
            pendingTextFiles = pendingFiles.map {
                PendingTextFileRecord(
                    id = it.id,
                    name = it.name,
                    opId = it.opId,
                    text = it.text,
                    status = it.syncStatus,
                    lastError = it.lastSyncError,
                    lastAttemptAt = it.lastSyncAttemptAt,
                    createdAt = it.createdAt,
                )
            },
            regiones = parseList(masters[KEY_REGIONES]?.payloadJson),
            sectoresAmerb = parseList(masters[KEY_SECTORES]?.payloadJson),
            caletas = parseList(masters[KEY_CALETAS]?.payloadJson),
            opas = parseList(masters[KEY_OPAS]?.payloadJson),
            botesMaestros = parseList(masters[KEY_BOTES]?.payloadJson),
            especiesMaestras = parseList(masters[KEY_ESPECIES]?.payloadJson),
        )
    }

    suspend fun saveSnapshot(snapshot: LocalCacheSnapshot) {
        val dao = db.localCacheDao()
        val now = System.currentTimeMillis()
        val dirtyIds = snapshot.dirtyOperacionIds.toSet()
        val syncStates = snapshot.operationSyncStates.associateBy { it.opId }

        val operationRows =
            snapshot.operacionesBd.map { op ->
                val state = syncStates[op.id]
                CachedOperationEntity(
                    id = op.id,
                    source = SOURCE_BD,
                    dirty = dirtyIds.contains(op.id),
                    syncStatus = state?.status ?: STATUS_SYNCED,
                    lastSyncError = state?.lastError,
                    lastSyncAttemptAt = state?.lastAttemptAt,
                    lastSyncSuccessAt = state?.lastSuccessAt,
                    payloadJson = gson.toJson(op),
                    updatedAt = now,
                )
            } + snapshot.operacionesLc.map { op ->
                val state = syncStates[op.id]
                CachedOperationEntity(
                    id = op.id,
                    source = SOURCE_LC,
                    dirty = dirtyIds.contains(op.id),
                    syncStatus = state?.status ?: STATUS_LOCAL_ONLY,
                    lastSyncError = state?.lastError,
                    lastSyncAttemptAt = state?.lastAttemptAt,
                    lastSyncSuccessAt = state?.lastSuccessAt,
                    payloadJson = gson.toJson(op),
                    updatedAt = now,
                )
            }

        val masterRows = listOf(
            CachedMasterListEntity(KEY_REGIONES, gson.toJson(snapshot.regiones), now),
            CachedMasterListEntity(KEY_SECTORES, gson.toJson(snapshot.sectoresAmerb), now),
            CachedMasterListEntity(KEY_CALETAS, gson.toJson(snapshot.caletas), now),
            CachedMasterListEntity(KEY_OPAS, gson.toJson(snapshot.opas), now),
            CachedMasterListEntity(KEY_BOTES, gson.toJson(snapshot.botesMaestros), now),
            CachedMasterListEntity(KEY_ESPECIES, gson.toJson(snapshot.especiesMaestras), now),
        )

        val pendingRows = snapshot.pendingTextFiles.map { file ->
            PendingTextFileEntity(
                id = file.id,
                name = file.name,
                opId = file.opId,
                text = file.text,
                syncStatus = file.status,
                lastSyncError = file.lastError,
                lastSyncAttemptAt = file.lastAttemptAt,
                createdAt = file.createdAt,
            )
        }

        db.withTransaction {
            dao.replaceOperations(operationRows)
            dao.replaceMasterLists(masterRows)
            dao.replacePendingFiles(pendingRows)
        }
    }

    private inline fun <reified T> parseList(json: String?): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson<List<T>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private inline fun <reified T> parseObject(json: String?): T? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, T::class.java) }.getOrNull()
    }

    companion object {
        private const val SOURCE_BD = "BD"
        private const val SOURCE_LC = "LC"
        private const val KEY_REGIONES = "regiones"
        private const val KEY_SECTORES = "sectores_amerb"
        private const val KEY_CALETAS = "caletas"
        private const val KEY_OPAS = "opas"
        private const val KEY_BOTES = "botes_maestros"
        private const val KEY_ESPECIES = "especies_maestras"
        private const val STATUS_SYNCED = "SINCRONIZADO"
        private const val STATUS_LOCAL_ONLY = "SOLO_LOCAL"
    }
}
