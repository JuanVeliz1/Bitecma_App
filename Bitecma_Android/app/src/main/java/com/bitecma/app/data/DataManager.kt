package com.bitecma.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import com.bitecma.app.data.local.LocalCacheDatabase
import com.bitecma.app.data.local.LocalCacheSnapshot
import com.bitecma.app.data.local.LocalCacheStore
import com.bitecma.app.data.local.OperationSyncStateRecord
import com.bitecma.app.data.local.PendingTextFileRecord
import com.bitecma.app.network.BoteMaestroDto
import com.bitecma.app.network.AuthUser
import com.bitecma.app.network.CaletaDto
import com.bitecma.app.network.EspecieDto
import com.bitecma.app.network.FileContentDto
import com.bitecma.app.network.FileMetaDto
import com.bitecma.app.network.UploadTextFileRequest
import com.bitecma.app.network.OperacionUpsertRequest
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.OperacionBoteDto
import com.bitecma.app.network.OpaDto
import com.bitecma.app.network.RegionDto
import com.bitecma.app.network.SectorAmerbDto
import com.bitecma.app.sync.SyncScheduler
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex

enum class OperacionSource {
    BD,
    LC,
}

object DataManager {
    enum class EstadoSyncOperacion {
        SOLO_LOCAL,
        PENDIENTE,
        SINCRONIZANDO,
        ERROR,
        SINCRONIZADO,
    }

    enum class EstadoSyncArchivo {
        PENDIENTE,
        SINCRONIZANDO,
        ERROR,
    }

    data class EstadoSyncInfo(
        val estado: EstadoSyncOperacion,
        val ultimoError: String? = null,
        val ultimoIntentoMs: Long? = null,
        val ultimaSincronizacionMs: Long? = null,
    )

    private const val PREFS = "bitecma_cache"
    private const val KEY_CACHE_V1 = "cache_v1"
    private const val KEY_LAST_CATALOG_SYNC_MS = "last_catalog_sync_ms"
    private const val CATALOG_SYNC_TTL_MS = 6 * 60 * 60 * 1000L
    private val gson = Gson()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null
    private val syncMutex = Mutex()
    @Volatile
    private var localStore: LocalCacheStore? = null

    data class PendingTextFile(
        val id: String? = null,
        val name: String,
        val opId: String? = null,
        val text: String,
        val estado: EstadoSyncArchivo = EstadoSyncArchivo.PENDIENTE,
        val ultimoError: String? = null,
        val ultimoIntentoMs: Long? = null,
        val createdAt: Long = System.currentTimeMillis(),
    )

    data class DocumentUploadResult(
        val uploaded: Boolean,
        val files: List<FileMetaDto> = emptyList(),
    )

    data class LoginResult(
        val success: Boolean,
        val successMessage: String? = null,
        val errorMessage: String? = null,
    )

    data class CatalogSnapshot(
        val regiones: List<RegionDto> = emptyList(),
        val sectoresAmerb: List<SectorAmerbDto> = emptyList(),
        val caletas: List<CaletaDto> = emptyList(),
        val opas: List<OpaDto> = emptyList(),
        val botesMaestros: List<BoteMaestroDto> = emptyList(),
        val especiesMaestras: List<EspecieDto> = emptyList(),
    )

    private data class RemoteSyncSnapshot(
        val catalog: CatalogSnapshot,
        val operaciones: List<OperacionDto>,
    )

    private fun <T> preferNonEmptyCatalog(remote: List<T>?, current: List<T>): List<T> {
        return when {
            remote == null -> current
            remote.isEmpty() && current.isNotEmpty() -> current
            else -> remote
        }
    }

    private data class CachePayload(
        val operacionesBd: List<OperacionDto> = emptyList(),
        val operacionesLc: List<OperacionDto> = emptyList(),
        val dirtyOperacionIds: List<String> = emptyList(),
        val pendingTextFiles: List<PendingTextFile> = emptyList(),
        val regiones: List<RegionDto> = emptyList(),
        val sectoresAmerb: List<SectorAmerbDto> = emptyList(),
        val caletas: List<CaletaDto> = emptyList(),
        val opas: List<OpaDto> = emptyList(),
        val botesMaestros: List<BoteMaestroDto> = emptyList(),
        val especiesMaestras: List<EspecieDto> = emptyList()
    )

    val botes = mutableStateListOf<BoteMaestro>()

    val especies = mutableStateListOf<EspecieMaestra>()

    val regiones = mutableStateListOf<RegionDto>()
    val sectoresAmerb = mutableStateListOf<SectorAmerbDto>()
    val caletas = mutableStateListOf<CaletaDto>()
    val opas = mutableStateListOf<OpaDto>()
    val botesMaestros = mutableStateListOf<BoteMaestroDto>()
    val especiesMaestras = mutableStateListOf<EspecieDto>()

    val operacionesBd = mutableStateListOf<OperacionDto>()

    val dirtyOperacionIds = mutableStateListOf<String>()
    val pendingTextFiles = mutableStateListOf<PendingTextFile>()
    val estadosSyncOperacion = mutableStateMapOf<String, EstadoSyncInfo>()

    val operacionesLc = mutableStateListOf<OperacionDto>()

    private fun clearInMemoryData() {
        botes.clear()
        especies.clear()
        regiones.clear()
        sectoresAmerb.clear()
        caletas.clear()
        opas.clear()
        botesMaestros.clear()
        especiesMaestras.clear()
        operacionesBd.clear()
        dirtyOperacionIds.clear()
        pendingTextFiles.clear()
        estadosSyncOperacion.clear()
        operacionesLc.clear()
    }

    private fun cacheStore(context: Context): LocalCacheStore {
        return localStore ?: synchronized(this) {
            localStore ?: LocalCacheStore(LocalCacheDatabase.getInstance(context.applicationContext)).also { localStore = it }
        }
    }

    private fun isSnapshotEmpty(snapshot: LocalCacheSnapshot): Boolean {
        return snapshot.operacionesBd.isEmpty() &&
            snapshot.operacionesLc.isEmpty() &&
            snapshot.pendingTextFiles.isEmpty() &&
            snapshot.regiones.isEmpty() &&
            snapshot.sectoresAmerb.isEmpty() &&
            snapshot.caletas.isEmpty() &&
            snapshot.opas.isEmpty() &&
            snapshot.botesMaestros.isEmpty() &&
            snapshot.especiesMaestras.isEmpty()
    }

    private fun clearManualOnlyCatalogs() {
        sectoresAmerb.clear()
        caletas.clear()
        opas.clear()
        botesMaestros.clear()
    }

    private fun hasLegacyManualCatalogs(snapshot: LocalCacheSnapshot): Boolean {
        return snapshot.sectoresAmerb.isNotEmpty() ||
            snapshot.caletas.isNotEmpty() ||
            snapshot.opas.isNotEmpty() ||
            snapshot.botesMaestros.isNotEmpty()
    }

    private fun applySnapshot(snapshot: LocalCacheSnapshot) {
        operacionesBd.clear()
        operacionesBd.addAll(snapshot.operacionesBd)
        operacionesLc.clear()
        operacionesLc.addAll(snapshot.operacionesLc)
        dirtyOperacionIds.clear()
        dirtyOperacionIds.addAll(snapshot.dirtyOperacionIds.distinct())
        estadosSyncOperacion.clear()
        estadosSyncOperacion.putAll(
            snapshot.operationSyncStates.associate { record ->
                record.opId to EstadoSyncInfo(
                    estado = record.status.toEstadoSyncOperacion(),
                    ultimoError = record.lastError,
                    ultimoIntentoMs = record.lastAttemptAt,
                    ultimaSincronizacionMs = record.lastSuccessAt,
                )
            },
        )
        pendingTextFiles.clear()
        pendingTextFiles.addAll(
            snapshot.pendingTextFiles.map {
                PendingTextFile(
                    id = it.id,
                    name = it.name,
                    opId = it.opId,
                    text = it.text,
                    estado = it.status.toEstadoSyncArchivo(),
                    ultimoError = it.lastError,
                    ultimoIntentoMs = it.lastAttemptAt,
                    createdAt = it.createdAt,
                )
            },
        )

        regiones.clear()
        regiones.addAll(snapshot.regiones)
        clearManualOnlyCatalogs()
        especiesMaestras.clear()
        especiesMaestras.addAll(snapshot.especiesMaestras)
        refreshDerivedMasters()
        normalizeSyncStates()
    }

    private fun refreshDerivedMasters() {
        botes.clear()
        if (botesMaestros.isNotEmpty()) {
            val mapped = botesMaestros.mapNotNull { b ->
                val nombre = b.nombre?.trim().takeUnless { it.isNullOrEmpty() } ?: return@mapNotNull null
                val regionLabel = listOfNotNull(b.region_rom, b.region).joinToString(" — ").ifBlank { "S/I" }
                BoteMaestro(
                    nombre = nombre,
                    caleta = b.caleta?.ifBlank { "S/I" } ?: "S/I",
                    rpa = b.nrpa?.ifBlank { "S/I" } ?: "S/I",
                    matricula = b.nmatricula?.ifBlank { "S/I" } ?: "S/I",
                    regionId = regionLabel,
                )
            }.distinctBy { it.nombre.uppercase() }
            if (mapped.isNotEmpty()) {
                botes.addAll(mapped)
            }
        }

        especies.clear()
        if (especiesMaestras.isNotEmpty()) {
            especies.addAll(
                especiesMaestras
                    .distinctBy { "${it.com.trim().lowercase()}|${it.sci?.trim()?.lowercase().orEmpty()}" }
                    .map { e ->
                    EspecieMaestra(
                        id = e.id,
                        nombreComun = e.com,
                        nombreCientifico = e.sci ?: "",
                    )
                },
            )
        }
    }

    fun getCatalogSnapshot(): CatalogSnapshot {
        return CatalogSnapshot(
            regiones = regiones.toList(),
            sectoresAmerb = emptyList(),
            caletas = emptyList(),
            opas = emptyList(),
            botesMaestros = emptyList(),
            especiesMaestras = especiesMaestras.toList(),
        )
    }

    private fun applyCatalogSnapshot(snapshot: CatalogSnapshot) {
        regiones.clear()
        regiones.addAll(snapshot.regiones)
        clearManualOnlyCatalogs()
        especiesMaestras.clear()
        especiesMaestras.addAll(snapshot.especiesMaestras)
        refreshDerivedMasters()
    }

    fun getRegionLabelMap(): Map<String, String> {
        return regiones
            .mapNotNull { region ->
                val rom = region.rom?.trim().takeUnless { it.isNullOrBlank() } ?: return@mapNotNull null
                rom to listOfNotNull(region.rom, region.nom).joinToString(" — ").ifBlank { "Región ${region.id}" }
            }
            .toMap()
    }

    private fun buildSnapshot(): LocalCacheSnapshot {
        return LocalCacheSnapshot(
            operacionesBd = operacionesBd.toList(),
            operacionesLc = operacionesLc.toList(),
            dirtyOperacionIds = dirtyOperacionIds.toList(),
            operationSyncStates = estadosSyncOperacion.map { (opId, info) ->
                OperationSyncStateRecord(
                    opId = opId,
                    status = info.estado.name,
                    lastError = info.ultimoError,
                    lastAttemptAt = info.ultimoIntentoMs,
                    lastSuccessAt = info.ultimaSincronizacionMs,
                )
            },
            pendingTextFiles = pendingTextFiles.toList().map {
                PendingTextFileRecord(
                    id = it.id ?: buildPendingFileId(it.name, it.opId, it.createdAt),
                    name = it.name,
                    opId = it.opId,
                    text = it.text,
                    status = it.estado.name,
                    lastError = it.ultimoError,
                    lastAttemptAt = it.ultimoIntentoMs,
                    createdAt = it.createdAt,
                )
            },
            regiones = regiones.toList(),
            sectoresAmerb = sectoresAmerb.toList(),
            caletas = caletas.toList(),
            opas = opas.toList(),
            botesMaestros = botesMaestros.toList(),
            especiesMaestras = especiesMaestras.toList(),
        )
    }

    suspend fun loadCache(context: Context) {
        val roomSnapshot = runCatching { withContext(Dispatchers.IO) { cacheStore(context).loadSnapshot() } }.getOrNull()
        if (roomSnapshot != null && !isSnapshotEmpty(roomSnapshot)) {
            applySnapshot(roomSnapshot)
            if (hasLegacyManualCatalogs(roomSnapshot)) {
                persistCache(context)
            }
            reconcileBackgroundSync(context)
            return
        }

        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_CACHE_V1, null) ?: return
        val payload = runCatching { gson.fromJson(json, CachePayload::class.java) }.getOrNull() ?: return
        applySnapshot(
            LocalCacheSnapshot(
                operacionesBd = payload.operacionesBd,
                operacionesLc = payload.operacionesLc,
                dirtyOperacionIds = payload.dirtyOperacionIds.distinct(),
                pendingTextFiles = payload.pendingTextFiles.map {
                    PendingTextFileRecord(
                        id = it.id ?: buildPendingFileId(it.name, it.opId, it.createdAt),
                        name = it.name,
                        opId = it.opId,
                        text = it.text,
                        status = it.estado.name,
                        lastError = it.ultimoError,
                        lastAttemptAt = it.ultimoIntentoMs,
                        createdAt = it.createdAt,
                    )
                },
                regiones = payload.regiones,
                sectoresAmerb = payload.sectoresAmerb,
                caletas = payload.caletas,
                opas = payload.opas,
                botesMaestros = payload.botesMaestros,
                especiesMaestras = payload.especiesMaestras,
            ),
        )
        persistCache(context)
    }

    fun persistCache(context: Context) {
        val snapshot = buildSnapshot()
        val payload = CachePayload(
            operacionesBd = snapshot.operacionesBd,
            operacionesLc = snapshot.operacionesLc,
            dirtyOperacionIds = snapshot.dirtyOperacionIds,
            pendingTextFiles = snapshot.pendingTextFiles.map {
                PendingTextFile(
                    id = it.id,
                    name = it.name,
                    opId = it.opId,
                    text = it.text,
                    estado = it.status.toEstadoSyncArchivo(),
                    ultimoError = it.lastError,
                    ultimoIntentoMs = it.lastAttemptAt,
                    createdAt = it.createdAt,
                )
            },
            regiones = snapshot.regiones,
            sectoresAmerb = snapshot.sectoresAmerb,
            caletas = snapshot.caletas,
            opas = snapshot.opas,
            botesMaestros = snapshot.botesMaestros,
            especiesMaestras = snapshot.especiesMaestras
        )
        val appCtx = context.applicationContext
        // Schedule background sync immediately so pending operations are not stranded
        // if the process dies before the debounced persistence job finishes.
        reconcileBackgroundSync(appCtx)
        persistJob?.cancel()
        persistJob = ioScope.launch {
            delay(250)
            val saved = runCatching { cacheStore(appCtx).saveSnapshot(snapshot) }.isSuccess
            val editor = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (saved) {
                editor.remove(KEY_CACHE_V1).apply()
            } else {
                val json = gson.toJson(payload)
                editor.putString(KEY_CACHE_V1, json).apply()
            }
            reconcileBackgroundSync(appCtx)
        }
    }

    suspend fun clearLocalSessionData(context: Context) {
        val appCtx = context.applicationContext
        persistJob?.cancel()
        persistJob = null
        clearInMemoryData()
        withContext(Dispatchers.IO) {
            runCatching { cacheStore(appCtx).clearSnapshot() }
        }
        appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CACHE_V1)
            .remove(KEY_LAST_CATALOG_SYNC_MS)
            .apply()
        reconcileBackgroundSync(appCtx)
    }

    fun hasPendingSyncWork(): Boolean {
        return pendingTextFiles.isNotEmpty() ||
            estadosSyncOperacion.values.any {
                it.estado == EstadoSyncOperacion.SOLO_LOCAL ||
                    it.estado == EstadoSyncOperacion.PENDIENTE ||
                    it.estado == EstadoSyncOperacion.ERROR
            }
    }

    fun shouldRunBackgroundSync(): Boolean {
        return !AppState.forceOffline &&
            AppState.hasAuthenticatedSession() &&
            (AppState.hasNetwork || AppState.authToken != null || hasPendingSyncWork())
    }

    private fun hasRemoteSession(): Boolean {
        return !AppState.forceOffline &&
            AppState.hasNetwork &&
            !AppState.authToken.isNullOrBlank()
    }

    fun reconcileBackgroundSync(context: Context) {
        SyncScheduler.reconcile(context.applicationContext, shouldRunBackgroundSync())
    }

    private fun hasCatalogData(): Boolean {
        return regiones.isNotEmpty() ||
            especiesMaestras.isNotEmpty()
    }

    private fun shouldRefreshCatalogs(context: Context, force: Boolean = false): Boolean {
        if (force || !hasCatalogData()) return true
        val last = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CATALOG_SYNC_MS, 0L)
        return last <= 0L || (System.currentTimeMillis() - last) >= CATALOG_SYNC_TTL_MS
    }

    private fun markCatalogSyncSuccess(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CATALOG_SYNC_MS, System.currentTimeMillis())
            .apply()
    }

    fun getEstadoSyncOperacion(opId: String, source: OperacionSource? = null): EstadoSyncInfo {
        return estadosSyncOperacion[opId] ?: when (source) {
            OperacionSource.LC -> EstadoSyncInfo(EstadoSyncOperacion.SOLO_LOCAL)
            else -> EstadoSyncInfo(EstadoSyncOperacion.SINCRONIZADO)
        }
    }

    private fun normalizeSyncStates() {
        deduplicateOperationSources()
        val ids = (operacionesBd + operacionesLc).map { it.id }.toSet()
        estadosSyncOperacion.keys.toList().filterNot { ids.contains(it) }.forEach { estadosSyncOperacion.remove(it) }

        operacionesBd.forEach { op ->
            val current = estadosSyncOperacion[op.id]
            if (dirtyOperacionIds.contains(op.id)) {
                if (current == null || current.estado == EstadoSyncOperacion.SINCRONIZADO) {
                    estadosSyncOperacion[op.id] = EstadoSyncInfo(EstadoSyncOperacion.PENDIENTE)
                }
            } else if (current == null) {
                estadosSyncOperacion[op.id] = EstadoSyncInfo(EstadoSyncOperacion.SINCRONIZADO)
            }
        }

        operacionesLc.forEach { op ->
            val current = estadosSyncOperacion[op.id]
            if (current == null) {
                estadosSyncOperacion[op.id] = EstadoSyncInfo(EstadoSyncOperacion.SOLO_LOCAL)
            }
        }
    }

    private fun markOperacionSyncing(opId: String) {
        val current = estadosSyncOperacion[opId]
        estadosSyncOperacion[opId] = EstadoSyncInfo(
            estado = EstadoSyncOperacion.SINCRONIZANDO,
            ultimoError = current?.ultimoError,
            ultimoIntentoMs = System.currentTimeMillis(),
            ultimaSincronizacionMs = current?.ultimaSincronizacionMs,
        )
    }

    private fun markOperacionSyncSuccess(opId: String) {
        estadosSyncOperacion[opId] = EstadoSyncInfo(
            estado = EstadoSyncOperacion.SINCRONIZADO,
            ultimoError = null,
            ultimoIntentoMs = System.currentTimeMillis(),
            ultimaSincronizacionMs = System.currentTimeMillis(),
        )
    }

    fun markOperacionDirty(opId: String, esOperacionSoloLocal: Boolean = false) {
        if (!dirtyOperacionIds.contains(opId)) {
            dirtyOperacionIds.add(opId)
        }
        val current = estadosSyncOperacion[opId]
        val isOnlyLocal = esOperacionSoloLocal || operacionesBd.none { it.id == opId }
        val preserveError = current?.estado == EstadoSyncOperacion.ERROR && !current.ultimoError.isNullOrBlank()
        estadosSyncOperacion[opId] = EstadoSyncInfo(
            estado = when {
                preserveError -> EstadoSyncOperacion.ERROR
                isOnlyLocal -> EstadoSyncOperacion.SOLO_LOCAL
                else -> EstadoSyncOperacion.PENDIENTE
            },
            ultimoError = if (preserveError) current?.ultimoError else null,
            ultimoIntentoMs = current?.ultimoIntentoMs,
            ultimaSincronizacionMs = current?.ultimaSincronizacionMs,
        )
    }

    fun markOperacionSyncError(opId: String, message: String = "No se pudo sincronizar") {
        val current = estadosSyncOperacion[opId]
        estadosSyncOperacion[opId] = EstadoSyncInfo(
            estado = EstadoSyncOperacion.ERROR,
            ultimoError = message,
            ultimoIntentoMs = System.currentTimeMillis(),
            ultimaSincronizacionMs = current?.ultimaSincronizacionMs,
        )
    }

    fun upsertOperacionInMemory(op: OperacionDto) {
        val idxBd = operacionesBd.indexOfFirst { it.id == op.id }
        if (idxBd >= 0) {
            operacionesBd[idxBd] = mergeOperacionPreservingData(operacionesBd[idxBd], op)
            normalizeSyncStates()
            return
        }
        val idxLc = operacionesLc.indexOfFirst { it.id == op.id }
        if (idxLc >= 0) {
            operacionesLc[idxLc] = mergeOperacionPreservingData(operacionesLc[idxLc], op)
        } else {
            operacionesLc.add(0, op)
        }
        normalizeSyncStates()
    }

    fun enqueueTextFile(req: UploadTextFileRequest) {
        pendingTextFiles.add(
            PendingTextFile(
                id = buildPendingFileId(req.name, req.opId, System.currentTimeMillis()),
                name = req.name,
                opId = req.opId,
                text = req.text,
            ),
        )
    }

    fun getPendingTextFiles(opId: String? = null): List<PendingTextFile> {
        return pendingTextFiles
            .filter { it.opId == opId }
            .sortedByDescending { it.createdAt }
    }

    fun retryPendingTextFile(fileId: String) {
        val idx = pendingTextFiles.indexOfFirst { it.id == fileId }
        if (idx < 0) return
        val current = pendingTextFiles[idx]
        pendingTextFiles[idx] = current.copy(
            estado = EstadoSyncArchivo.PENDIENTE,
            ultimoError = null,
            ultimoIntentoMs = current.ultimoIntentoMs,
        )
    }

    private fun markPendingFileSyncing(fileId: String) {
        val idx = pendingTextFiles.indexOfFirst { it.id == fileId }
        if (idx < 0) return
        val current = pendingTextFiles[idx]
        pendingTextFiles[idx] = current.copy(
            estado = EstadoSyncArchivo.SINCRONIZANDO,
            ultimoError = current.ultimoError,
            ultimoIntentoMs = System.currentTimeMillis(),
        )
    }

    private fun markPendingFileError(fileId: String, message: String = "No se pudo sincronizar") {
        val idx = pendingTextFiles.indexOfFirst { it.id == fileId }
        if (idx < 0) return
        val current = pendingTextFiles[idx]
        pendingTextFiles[idx] = current.copy(
            estado = EstadoSyncArchivo.ERROR,
            ultimoError = message,
            ultimoIntentoMs = System.currentTimeMillis(),
        )
    }

    fun removeOperacion(opId: String, source: OperacionSource) {
        if (source == OperacionSource.BD) {
            operacionesBd.removeAll { it.id == opId }
        } else {
            operacionesLc.removeAll { it.id == opId }
        }
        dirtyOperacionIds.removeAll { it == opId }
        estadosSyncOperacion.remove(opId)
    }

    private suspend fun uploadOperacion(op: OperacionDto): OperacionDto? {
        val req = OperacionUpsertRequest(
            id = op.id,
            region = op.region,
            sector = op.sector,
            sectorAmerbId = op.sectorAmerbId,
            sectorAmerb = op.sectorAmerb,
            tipoOrg = op.tipoOrg,
            opaId = op.opaId,
            org = op.org,
            numSeg = op.numSeg,
            fechaInicio = op.fechaInicio,
            fechaFin = op.fechaFin,
            botes = op.botes
        )

        fun normalizeMutationSavedOperacion(requested: OperacionDto, saved: OperacionDto): OperacionDto {
            val requestedBotes = requested.botes.orEmpty()
            if (requestedBotes.isEmpty()) return saved
            val savedBotes = saved.botes
            if (savedBotes.isNullOrEmpty()) return saved.copy(botes = requestedBotes)

            val normalizedBotes = savedBotes.map { savedBote ->
                val requestedBote = requestedBotes.firstOrNull { sameBoatIdentity(it, savedBote) } ?: return@map savedBote
                savedBote.copy(
                    zona = savedBote.zona ?: requestedBote.zona,
                    nombre = savedBote.nombre ?: requestedBote.nombre,
                    buzo = savedBote.buzo ?: requestedBote.buzo,
                    densTipo = savedBote.densTipo ?: requestedBote.densTipo,
                    submareal = savedBote.submareal ?: requestedBote.submareal,
                    boteMaestroId = savedBote.boteMaestroId ?: requestedBote.boteMaestroId,
                    lpMuestras = when {
                        !savedBote.lpMuestras.isNullOrEmpty() -> savedBote.lpMuestras
                        else -> requestedBote.lpMuestras
                    },
                    transectos = when {
                        !savedBote.transectos.isNullOrEmpty() -> savedBote.transectos
                        else -> requestedBote.transectos
                    },
                )
            }
            val additionalRequested = requestedBotes.filterNot { requestedBote ->
                normalizedBotes.any { savedBote -> sameBoatIdentity(savedBote, requestedBote) }
            }
            return saved.copy(botes = normalizedBotes + additionalRequested)
        }

        fun resolveSavedOperacion(result: RemoteEnvelopeResult<OperacionDto>): OperacionDto? {
            if (!result.ok) return null
            val saved = result.data ?: op
            return normalizeMutationSavedOperacion(op, saved)
        }

        val updated = resolveSavedOperacion(DataRemoteSource.actualizarOperacion(op.id, req))
        if (updated != null) return updated

        return resolveSavedOperacion(DataRemoteSource.crearOperacion(req))
    }

    private fun <T> extractRemoteData(result: RemoteEnvelopeResult<T>): T? {
        if (!result.ok) return null
        return result.data
    }

    private fun normalizedBoatField(value: String?): String {
        return value?.trim()?.lowercase().orEmpty()
    }

    private fun operacionDataScore(op: OperacionDto): Int {
        return (op.botes?.size ?: 0) * 100 +
            (if (op.region != null) 1 else 0) +
            (if (op.sectorAmerbId != null) 1 else 0) +
            (if (!op.sectorAmerb.isNullOrBlank()) 1 else 0) +
            (if (!op.org.isNullOrBlank()) 1 else 0) +
            (if (op.numSeg != null) 1 else 0) +
            (if (!op.fechaInicio.isNullOrBlank()) 1 else 0) +
            (if (!op.fechaFin.isNullOrBlank()) 1 else 0)
    }

    private fun sameBoatIdentity(left: OperacionBoteDto, right: OperacionBoteDto): Boolean {
        val leftId = left.id?.trim()
        val rightId = right.id?.trim()
        if (!leftId.isNullOrBlank() && !rightId.isNullOrBlank()) {
            return leftId == rightId
        }
        if (left.zona != null && right.zona != null && left.boteMaestroId != null && right.boteMaestroId != null) {
            if (left.zona == right.zona && left.boteMaestroId == right.boteMaestroId) {
                return true
            }
        }
        return left.zona == right.zona &&
            normalizedBoatField(left.nombre) == normalizedBoatField(right.nombre) &&
            normalizedBoatField(left.buzo) == normalizedBoatField(right.buzo)
    }

    private fun mergeBotesPreservingData(
        localBotes: List<OperacionBoteDto>,
        remoteBotes: List<OperacionBoteDto>,
    ): List<OperacionBoteDto> {
    if (remoteBotes.isEmpty()) return emptyList()
    if (localBotes.isEmpty()) return remoteBotes

    return remoteBotes.map { remote ->
        val local = localBotes.firstOrNull { sameBoatIdentity(it, remote) }
        if (local == null) {
            remote
        } else {
            remote.copy(
                zona = remote.zona ?: local.zona,
                nombre = remote.nombre ?: local.nombre,
                buzo = remote.buzo ?: local.buzo,
                densTipo = remote.densTipo ?: local.densTipo,
                submareal = remote.submareal ?: local.submareal,
                boteMaestroId = remote.boteMaestroId ?: local.boteMaestroId,
                lpMuestras = remote.lpMuestras ?: local.lpMuestras,
                transectos = remote.transectos ?: local.transectos,
            )
        }
        }
    }

    private fun mergeOperacionPreservingData(local: OperacionDto, remote: OperacionDto): OperacionDto {
        return remote.copy(
            region = remote.region ?: local.region,
            sectorAmerbId = remote.sectorAmerbId ?: local.sectorAmerbId,
            sectorAmerb = remote.sectorAmerb ?: local.sectorAmerb,
            tipoOrg = remote.tipoOrg ?: local.tipoOrg,
            opaId = remote.opaId ?: local.opaId,
            org = remote.org ?: local.org,
            numSeg = remote.numSeg ?: local.numSeg,
            fechaInicio = remote.fechaInicio ?: local.fechaInicio,
            fechaFin = remote.fechaFin ?: local.fechaFin,
        botes = remote.botes?.let { mergeBotesPreservingData(local.botes.orEmpty(), it) } ?: local.botes
        )
    }

    private fun mergeOperationSnapshots(first: OperacionDto, second: OperacionDto): OperacionDto {
        val primary = if (operacionDataScore(second) > operacionDataScore(first)) second else first
        val secondary = if (primary === second) first else second
        return mergeOperacionPreservingData(secondary, primary)
    }

    private fun deduplicateOperationsList(ops: List<OperacionDto>): List<OperacionDto> {
        val merged = linkedMapOf<String, OperacionDto>()
        ops.forEach { op ->
            merged[op.id] = merged[op.id]
                ?.let { existing -> mergeOperationSnapshots(existing, op) }
                ?: op
        }
        return merged.values.toList()
    }

    private fun deduplicateOperationSources() {
        val dedupBd = deduplicateOperationsList(operacionesBd)
        val dedupLc = deduplicateOperationsList(operacionesLc)
        val nextBd = dedupBd.toMutableList()
        val nextLc = mutableListOf<OperacionDto>()

        dedupLc.forEach { lcOp ->
            val bdIndex = nextBd.indexOfFirst { it.id == lcOp.id }
            if (bdIndex >= 0) {
                nextBd[bdIndex] = mergeOperationSnapshots(nextBd[bdIndex], lcOp)
            } else {
                nextLc.add(lcOp)
            }
        }

        if (operacionesBd.toList() != nextBd) {
            operacionesBd.clear()
            operacionesBd.addAll(nextBd)
        }
        if (operacionesLc.toList() != nextLc) {
            operacionesLc.clear()
            operacionesLc.addAll(nextLc)
        }
    }

    private fun mergeServerOperations(serverOps: List<OperacionDto>) {
        deduplicateOperationSources()
        val dirtySet = dirtyOperacionIds.toSet()
        val currentBdOrder = operacionesBd.map { it.id }
        val localById = (operacionesBd + operacionesLc)
            .groupBy { it.id }
            .mapValues { (_, ops) -> ops.reduce { acc, op -> mergeOperationSnapshots(acc, op) } }
        val serverIds = serverOps.map { it.id }.toSet()
        operacionesBd.clear()
        operacionesBd.addAll(serverOps.map { op ->
            val local = localById[op.id]
            when {
                dirtySet.contains(op.id) -> local ?: op
                local != null -> mergeOperacionPreservingData(local, op)
                else -> op
            }
        })
        operacionesBd.addAll(
            currentBdOrder.mapNotNull { id ->
                localById[id]?.takeIf { id !in serverIds && id in dirtySet }
            },
        )
        operacionesLc.removeAll { it.id in serverIds }
        normalizeSyncStates()
    }

    private suspend fun fetchRemoteOperations(): List<OperacionDto>? {
        return extractRemoteData(DataRemoteSource.getOperaciones())
    }

    private suspend fun fetchRemoteCatalogSnapshot(currentCatalog: CatalogSnapshot = getCatalogSnapshot()): CatalogSnapshot? = coroutineScope {
        val regDeferred = async { DataRemoteSource.getRegiones() }
        val especiesDeferred = async { DataRemoteSource.getEspecies() }
        val regionesRemote = extractRemoteData(regDeferred.await())
        val especiesRemote = extractRemoteData(especiesDeferred.await())
        CatalogSnapshot(
            regiones = preferNonEmptyCatalog(regionesRemote, currentCatalog.regiones),
            sectoresAmerb = emptyList(),
            caletas = emptyList(),
            opas = emptyList(),
            botesMaestros = emptyList(),
            especiesMaestras = preferNonEmptyCatalog(especiesRemote, currentCatalog.especiesMaestras),
        )
    }

    private fun buildLocalOperacion(req: OperacionUpsertRequest): OperacionDto {
        return OperacionDto(
            id = req.id,
            sector = req.sector,
            region = req.region,
            sectorAmerbId = req.sectorAmerbId,
            sectorAmerb = req.sectorAmerb,
            tipoOrg = req.tipoOrg,
            opaId = req.opaId,
            org = req.org,
            numSeg = req.numSeg,
            fechaInicio = req.fechaInicio,
            fechaFin = req.fechaFin,
            botes = req.botes,
        )
    }

    private fun applySavedOperacion(saved: OperacionDto) {
        val idx = operacionesBd.indexOfFirst { it.id == saved.id }
        if (idx >= 0) {
            operacionesBd[idx] = mergeOperacionPreservingData(operacionesBd[idx], saved)
        } else {
            operacionesBd.add(0, saved)
        }
        operacionesLc.removeAll { it.id == saved.id }
        dirtyOperacionIds.removeAll { it == saved.id }
        markOperacionSyncSuccess(saved.id)
        normalizeSyncStates()
    }

    private suspend fun syncPendingOperacion(op: OperacionDto): Boolean {
        markOperacionSyncing(op.id)
        val saved = uploadOperacion(op)
        if (saved != null) {
            applySavedOperacion(saved)
            return true
        }
        markOperacionSyncError(op.id)
        return false
    }

    private suspend fun syncPendingFile(file: PendingTextFile) {
        val fileId = file.id ?: buildPendingFileId(file.name, file.opId, file.createdAt)
        markPendingFileSyncing(fileId)
        val result = DataRemoteSource.uploadTextFile(
            UploadTextFileRequest(
                name = file.name,
                opId = file.opId,
                text = file.text,
            ),
        )
        if (result.ok) {
            pendingTextFiles.removeAll { it.id == fileId }
        } else {
            markPendingFileError(fileId)
        }
    }

    private suspend fun flushPendingOps(): Boolean {
        var any = false
        val pendingLc = operacionesLc.toList()
        pendingLc.forEach { op ->
            if (syncPendingOperacion(op)) {
                any = true
            }
        }

        val dirtyIds = dirtyOperacionIds.toList()
        dirtyIds.forEach { id ->
            val op = (operacionesBd + operacionesLc).firstOrNull { it.id == id } ?: return@forEach
            if (syncPendingOperacion(op)) {
                any = true
            }
        }

        return any
    }

    private suspend fun flushPendingFiles() {
        val pending = pendingTextFiles.toList()
        pending.forEach { syncPendingFile(it) }
    }

    private fun canStartRemoteSync(context: Context): Boolean {
        if (AppState.forceOffline) {
            return false
        }
        if (!AppState.hasNetwork) {
            AppState.registerSyncFailure("Sin conectividad")
            reconcileBackgroundSync(context)
            return false
        }
        if (AppState.authToken.isNullOrBlank()) {
            reconcileBackgroundSync(context)
            return false
        }
        return true
    }

    private suspend fun refreshRemoteOperationsIntoMemory(): Boolean {
        val refreshedOps = runCatching { fetchRemoteOperations() }.getOrNull() ?: return false
        mergeServerOperations(refreshedOps)
        return true
    }

    private suspend fun refreshCatalogsIntoMemory(
        context: Context,
        force: Boolean,
        allowCachedCatalogFallback: Boolean,
    ): Boolean {
        if (!shouldRefreshCatalogs(context, force = force)) return true
        val remoteCatalog = runCatching { fetchRemoteCatalogSnapshot() }.getOrNull()
        if (remoteCatalog != null) {
            applyCatalogSnapshot(remoteCatalog)
            markCatalogSyncSuccess(context)
            return true
        }
        return allowCachedCatalogFallback && hasCatalogData()
    }

    private suspend fun refreshCatalogsAfterSync(context: Context, force: Boolean): Boolean {
        return refreshCatalogsIntoMemory(
            context = context,
            force = force,
            allowCachedCatalogFallback = true,
        )
    }

    suspend fun tryUploadOperacion(context: Context, op: OperacionDto): Boolean {
        if (!hasRemoteSession()) return false
        if (!syncPendingOperacion(op)) {
            return false
        }
        AppState.registerSyncSuccess()
        persistCache(context)
        return true
    }

    suspend fun getRemoteFiles(opId: String?): List<FileMetaDto> {
        if (!hasRemoteSession()) return emptyList()
        return extractRemoteData(DataRemoteSource.getFiles(opId)).orEmpty()
    }

    suspend fun uploadTextFileOrQueue(context: Context, req: UploadTextFileRequest): DocumentUploadResult {
        val canUpload = hasRemoteSession()
        if (canUpload) {
            val uploadResult = DataRemoteSource.uploadTextFile(req)
            if (uploadResult.ok) {
                val refreshedFiles = getRemoteFiles(req.opId)
                persistCache(context)
                return DocumentUploadResult(uploaded = true, files = refreshedFiles)
            }
        }

        enqueueTextFile(req)
        persistCache(context)
        return DocumentUploadResult(uploaded = false)
    }

    suspend fun downloadRemoteFile(fileId: String): FileContentDto? {
        if (!hasRemoteSession()) return null
        return extractRemoteData(DataRemoteSource.getFile(fileId))
    }

    suspend fun pingServidor(): Boolean {
        if (!hasRemoteSession()) return false
        return DataRemoteSource.ping()
    }

    private suspend fun applyAuthenticatedSession(
        context: Context,
        email: String,
        token: String,
        user: AuthUser?,
    ): LoginResult {
        AppState.isOnline = true
        AppState.authToken = token
        AppState.currentUserEmail = email
        AppState.forceOffline = false
        AppState.hasVerifiedSession = true
        AppState.currentUserId = user?.uid ?: user?.id
        AppState.currentUserName = user?.nombre
        AppState.currentUserRole = user?.rol
        AppState.persistSession(context)
        AppState.hasNetwork = true
        AppState.saveLastLoginNow(context, email)
        reconcileBackgroundSync(context)
        SyncScheduler.scheduleImmediate(context)
        runCatching { syncAllFromServer(context) }
        return LoginResult(
            success = true,
            successMessage = "Bienvenido ${user?.nombre ?: ""} (Online)",
        )
    }

    private fun resolveLoginFailure(result: RemoteLoginResult): LoginResult {
        if (result.error != null) {
            return LoginResult(
                success = false,
                errorMessage = result.error,
            )
        }

        val code = result.code
        return if (code == 401 || code == 403) {
            LoginResult(success = false, errorMessage = "Correo o contraseña incorrectos")
        } else {
            LoginResult(success = false, errorMessage = "No se pudo conectar con el servidor ($code)")
        }
    }

    suspend fun loginUsuario(context: Context, email: String, password: String): LoginResult {
        val result = DataRemoteSource.login(email, password)
        if (result.code == null) return LoginResult(
            success = false,
            errorMessage = "Necesitas internet para iniciar sesión con tu cuenta registrada",
        )

        val token = result.token
        if (result.ok && !token.isNullOrBlank()) {
            return applyAuthenticatedSession(
                context = context,
                email = email,
                token = token,
                user = result.user,
            )
        }
        return resolveLoginFailure(result)
    }

    suspend fun deleteOperacion(context: Context, opId: String, source: OperacionSource): Boolean {
        var success = true
        if (source == OperacionSource.BD && AppState.isEffectivelyOnline()) {
            success = DataRemoteSource.eliminarOperacion(opId).ok
        }

        if (success) {
            removeOperacion(opId, source)
            persistCache(context)
        }
        return success
    }

    suspend fun refreshCatalogs(context: Context, force: Boolean = false): Boolean {
        if (!hasRemoteSession()) return false
        val refreshed = refreshCatalogsIntoMemory(
            context = context,
            force = force,
            allowCachedCatalogFallback = false,
        )
        if (!refreshed) return false
        persistCache(context)
        return true
    }

    suspend fun refreshOperacionDetail(context: Context, opId: String): OperacionDto? {
        val local = (operacionesBd + operacionesLc).firstOrNull { it.id == opId }
        if (!hasRemoteSession()) {
            return local
        }
        val fresh = extractRemoteData(DataRemoteSource.getOperacion(opId)) ?: return local
        upsertOperacionInMemory(fresh)
        persistCache(context)
        return (operacionesBd + operacionesLc).firstOrNull { it.id == opId } ?: fresh
    }

    suspend fun createOperacion(context: Context, req: OperacionUpsertRequest): OperacionDto {
        val local = buildLocalOperacion(req)
        if (AppState.isEffectivelyOnline()) {
            val saved = runCatching {
                val result = DataRemoteSource.crearOperacion(req)
                if (!result.ok) null else result.data ?: local
            }.getOrNull()
            if (saved != null) {
                applySavedOperacion(saved)
                persistCache(context)
                return saved
            }
        }

        upsertOperacionInMemory(local)
        markOperacionDirty(local.id, esOperacionSoloLocal = true)
        persistCache(context)
        return (operacionesLc.firstOrNull { it.id == local.id } ?: local)
    }

    suspend fun syncAllFromServer(context: Context): Boolean {
        if (!syncMutex.tryLock()) return false
        try {
            if (!canStartRemoteSync(context)) {
                return false
            }

            val anyUploaded = flushPendingOps()
            flushPendingFiles()

            val operationsSynced = refreshRemoteOperationsIntoMemory()
            val catalogsSynced = refreshCatalogsAfterSync(context, force = anyUploaded)
            val syncOk = operationsSynced && catalogsSynced

            if (syncOk) AppState.registerSyncSuccess()
            else AppState.registerSyncFailure("No se pudo sincronizar con la nube")

            persistCache(context)
            reconcileBackgroundSync(context)
            return syncOk
        } finally {
            syncMutex.unlock()
        }
    }

    suspend fun ensureAuthenticatedOnlineSession(context: Context): Boolean {
        if (!AppState.hasAuthenticatedSession()) return false
        if (AppState.forceOffline) return false
        if (!AppState.hasNetwork) return false
        if (!AppState.authToken.isNullOrBlank()) {
            AppState.registerSyncSuccess()
            AppState.persistSession(context)
            reconcileBackgroundSync(context)
            return true
        }
        AppState.registerSyncFailure("Necesitas iniciar sesión nuevamente para sincronizar")
        AppState.persistSession(context)
        reconcileBackgroundSync(context)
        return false
    }

    private fun String.toEstadoSyncOperacion(): EstadoSyncOperacion {
        return runCatching { EstadoSyncOperacion.valueOf(this) }.getOrDefault(EstadoSyncOperacion.PENDIENTE)
    }

    private fun String.toEstadoSyncArchivo(): EstadoSyncArchivo {
        return runCatching { EstadoSyncArchivo.valueOf(this) }.getOrDefault(EstadoSyncArchivo.PENDIENTE)
    }

    private fun buildPendingFileId(name: String, opId: String?, createdAt: Long): String {
        return "file-$createdAt-${opId ?: "general"}-${name.hashCode()}"
    }
}
