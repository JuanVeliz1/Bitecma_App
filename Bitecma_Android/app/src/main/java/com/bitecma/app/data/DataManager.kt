package com.bitecma.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.bitecma.app.network.LpSampleDto
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
        replaceOperacionStateList(operacionesBd, snapshot.operacionesBd)
        replaceOperacionStateList(operacionesLc, snapshot.operacionesLc)
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
        pendingTextFiles.addAll(snapshot.pendingTextFiles.map { it.toPendingTextFile() })

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
            pendingTextFiles = pendingTextFiles.toList().map { it.toRecord() },
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
        applySnapshot(payload.toSnapshot())
        persistCache(context)
    }

    fun persistCache(context: Context) {
        val snapshot = buildSnapshot()
        val payload = snapshot.toLegacyPayload()
        val appCtx = context.applicationContext
        persistJob?.cancel()
        persistJob = ioScope.launch {
            delay(250)
            val saved = runCatching { cacheStore(appCtx).saveSnapshot(snapshot) }.isSuccess
            persistLegacyCache(appCtx, payload, saved)
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
        clearLegacyCachePreferences(appCtx)
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

    private fun catalogPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun getLastCatalogSyncMs(context: Context): Long {
        return catalogPrefs(context).getLong(KEY_LAST_CATALOG_SYNC_MS, 0L)
    }

    private fun isCatalogSyncExpired(context: Context): Boolean {
        val last = getLastCatalogSyncMs(context)
        return last <= 0L || (System.currentTimeMillis() - last) >= CATALOG_SYNC_TTL_MS
    }

    private fun shouldRefreshCatalogs(context: Context, force: Boolean = false): Boolean {
        if (force || !hasCatalogData()) return true
        return isCatalogSyncExpired(context)
    }

    private fun markCatalogSyncSuccess(context: Context) {
        catalogPrefs(context)
            .edit()
            .putLong(KEY_LAST_CATALOG_SYNC_MS, System.currentTimeMillis())
            .apply()
    }

    private fun applyRefreshedCatalogSnapshot(context: Context, snapshot: CatalogSnapshot): Boolean {
        applyCatalogSnapshot(snapshot)
        markCatalogSyncSuccess(context)
        return true
    }

    private fun resolveCatalogRefreshFallback(allowCachedCatalogFallback: Boolean): Boolean {
        return allowCachedCatalogFallback && hasCatalogData()
    }

    fun getEstadoSyncOperacion(opId: String, source: OperacionSource? = null): EstadoSyncInfo {
        return estadosSyncOperacion[opId] ?: when (source) {
            OperacionSource.LC -> EstadoSyncInfo(EstadoSyncOperacion.SOLO_LOCAL)
            else -> EstadoSyncInfo(EstadoSyncOperacion.SINCRONIZADO)
        }
    }

    private fun currentSyncInfo(opId: String): EstadoSyncInfo? {
        return estadosSyncOperacion[opId]
    }

    private fun updateSyncInfo(
        opId: String,
        estado: EstadoSyncOperacion,
        ultimoError: String? = null,
        ultimoIntentoMs: Long? = null,
        ultimaSincronizacionMs: Long? = null,
    ) {
        estadosSyncOperacion[opId] = EstadoSyncInfo(
            estado = estado,
            ultimoError = ultimoError,
            ultimoIntentoMs = ultimoIntentoMs,
            ultimaSincronizacionMs = ultimaSincronizacionMs,
        )
    }

    private fun removeStaleSyncStates() {
        val ids = (operacionesBd + operacionesLc).map { it.id }.toSet()
        estadosSyncOperacion.keys.toList()
            .filterNot(ids::contains)
            .forEach(estadosSyncOperacion::remove)
    }

    private fun ensureBdSyncState(opId: String) {
        val current = currentSyncInfo(opId)
        if (dirtyOperacionIds.contains(opId)) {
            if (current == null || current.estado == EstadoSyncOperacion.SINCRONIZADO) {
                updateSyncInfo(opId = opId, estado = EstadoSyncOperacion.PENDIENTE)
            }
            return
        }

        if (current == null) {
            updateSyncInfo(opId = opId, estado = EstadoSyncOperacion.SINCRONIZADO)
        }
    }

    private fun ensureLocalOnlySyncState(opId: String) {
        if (currentSyncInfo(opId) == null) {
            updateSyncInfo(opId = opId, estado = EstadoSyncOperacion.SOLO_LOCAL)
        }
    }

    private fun normalizeSyncStates() {
        deduplicateOperationSources()
        removeStaleSyncStates()
        operacionesBd.forEach { op -> ensureBdSyncState(op.id) }
        operacionesLc.forEach { op -> ensureLocalOnlySyncState(op.id) }
    }

    private fun markOperacionSyncing(opId: String) {
        val current = currentSyncInfo(opId)
        updateSyncInfo(
            opId = opId,
            estado = EstadoSyncOperacion.SINCRONIZANDO,
            ultimoError = current?.ultimoError,
            ultimoIntentoMs = System.currentTimeMillis(),
            ultimaSincronizacionMs = current?.ultimaSincronizacionMs,
        )
    }

    private fun markOperacionSyncSuccess(opId: String) {
        val now = System.currentTimeMillis()
        updateSyncInfo(
            opId = opId,
            estado = EstadoSyncOperacion.SINCRONIZADO,
            ultimoError = null,
            ultimoIntentoMs = now,
            ultimaSincronizacionMs = now,
        )
    }

    fun markOperacionDirty(opId: String, esOperacionSoloLocal: Boolean = false) {
        if (!dirtyOperacionIds.contains(opId)) {
            dirtyOperacionIds.add(opId)
        }
        val current = currentSyncInfo(opId)
        val isOnlyLocal = esOperacionSoloLocal || operacionesBd.none { it.id == opId }
        val preserveError = current?.estado == EstadoSyncOperacion.ERROR && !current.ultimoError.isNullOrBlank()
        updateSyncInfo(
            opId = opId,
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
        val current = currentSyncInfo(opId)
        updateSyncInfo(
            opId = opId,
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
        updatePendingTextFile(fileId) { current ->
            current.copy(
            estado = EstadoSyncArchivo.PENDIENTE,
            ultimoError = null,
            ultimoIntentoMs = current.ultimoIntentoMs,
            )
        }
    }

    private fun markPendingFileSyncing(fileId: String) {
        updatePendingTextFile(fileId) { current ->
            current.copy(
            estado = EstadoSyncArchivo.SINCRONIZANDO,
            ultimoError = current.ultimoError,
            ultimoIntentoMs = System.currentTimeMillis(),
            )
        }
    }

    private fun markPendingFileError(fileId: String, message: String = "No se pudo sincronizar") {
        updatePendingTextFile(fileId) { current ->
            current.copy(
            estado = EstadoSyncArchivo.ERROR,
            ultimoError = message,
            ultimoIntentoMs = System.currentTimeMillis(),
            )
        }
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
                    lpMuestras = mergeLpMuestrasPreservingData(
                        localLp = requestedBote.lpMuestras,
                        remoteLp = savedBote.lpMuestras,
                    ),
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

    private fun mergeLpMuestrasPreservingData(
        localLp: Map<String, Map<String, List<LpSampleDto>>>?,
        remoteLp: Map<String, Map<String, List<LpSampleDto>>>?,
    ): Map<String, Map<String, List<LpSampleDto>>>? {
        if (localLp.isNullOrEmpty()) return remoteLp
        if (remoteLp.isNullOrEmpty()) return localLp

        val merged = linkedMapOf<String, Map<String, List<LpSampleDto>>>()
        val allSpeciesIds = (localLp.keys + remoteLp.keys).distinct()
        allSpeciesIds.forEach { speciesId ->
            val localBuckets = localLp[speciesId].orEmpty()
            val remoteBuckets = remoteLp[speciesId].orEmpty()
            val mergedBuckets = linkedMapOf<String, List<LpSampleDto>>()
            (localBuckets.keys + remoteBuckets.keys).distinct().forEach { bucketKey ->
                val remoteSamples = remoteBuckets[bucketKey]
                val localSamples = localBuckets[bucketKey]
                mergedBuckets[bucketKey] = if (!remoteSamples.isNullOrEmpty()) remoteSamples else localSamples.orEmpty()
            }
            merged[speciesId] = mergedBuckets
        }
        return merged
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
                lpMuestras = mergeLpMuestrasPreservingData(
                    localLp = local.lpMuestras,
                    remoteLp = remote.lpMuestras,
                ),
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
            replaceOperacionStateList(operacionesBd, nextBd)
        }
        if (operacionesLc.toList() != nextLc) {
            replaceOperacionStateList(operacionesLc, nextLc)
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
        val nextBd = serverOps.map { op ->
            val local = localById[op.id]
            when {
                dirtySet.contains(op.id) -> local ?: op
                local != null -> mergeOperacionPreservingData(local, op)
                else -> op
            }
        } + currentBdOrder.mapNotNull { id ->
            localById[id]?.takeIf { id !in serverIds && id in dirtySet }
        }
        replaceOperacionStateList(operacionesBd, nextBd)
        operacionesLc.removeAll { it.id in serverIds }
        normalizeSyncStates()
    }

    private fun replaceOperacionStateList(
        target: SnapshotStateList<OperacionDto>,
        next: List<OperacionDto>,
    ) {
        val shared = minOf(target.size, next.size)
        for (index in 0 until shared) {
            if (target[index] != next[index]) {
                target[index] = next[index]
            }
        }
        while (target.size > next.size) {
            target.removeAt(target.lastIndex)
        }
        if (next.size > target.size) {
            target.addAll(next.subList(target.size, next.size))
        }
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
        val fileId = file.stableId()
        markPendingFileSyncing(fileId)
        val result = DataRemoteSource.uploadTextFile(file.toUploadRequest())
        if (result.ok) {
            removePendingTextFile(fileId)
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
            return applyRefreshedCatalogSnapshot(context, remoteCatalog)
        }
        return resolveCatalogRefreshFallback(allowCachedCatalogFallback)
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

    private suspend fun fetchRemoteFilesOrEmpty(opId: String?): List<FileMetaDto> {
        return extractRemoteData(DataRemoteSource.getFiles(opId)).orEmpty()
    }

    private suspend fun buildRemoteUploadResult(
        context: Context,
        opId: String?,
    ): DocumentUploadResult {
        val refreshedFiles = fetchRemoteFilesOrEmpty(opId)
        persistCache(context)
        return DocumentUploadResult(uploaded = true, files = refreshedFiles)
    }

    private suspend fun uploadRemoteTextFile(
        context: Context,
        req: UploadTextFileRequest,
    ): DocumentUploadResult? {
        val uploadResult = DataRemoteSource.uploadTextFile(req)
        if (!uploadResult.ok) return null

        return buildRemoteUploadResult(context, req.opId)
    }

    private fun queueTextFileUpload(
        context: Context,
        req: UploadTextFileRequest,
    ): DocumentUploadResult {
        enqueueTextFile(req)
        persistCache(context)
        return DocumentUploadResult(uploaded = false)
    }

    private suspend fun <T> withRemoteSession(
        fallback: T,
        block: suspend () -> T,
    ): T {
        if (!hasRemoteSession()) return fallback
        return block()
    }

    suspend fun getRemoteFiles(opId: String?): List<FileMetaDto> {
        return withRemoteSession(emptyList()) {
            fetchRemoteFilesOrEmpty(opId)
        }
    }

    suspend fun uploadTextFileOrQueue(context: Context, req: UploadTextFileRequest): DocumentUploadResult {
        val uploaded = withRemoteSession<DocumentUploadResult?>(fallback = null) {
            uploadRemoteTextFile(context, req)
        }
        if (uploaded != null) return uploaded

        return queueTextFileUpload(context, req)
    }

    suspend fun downloadRemoteFile(fileId: String): FileContentDto? {
        return withRemoteSession<FileContentDto?>(fallback = null) {
            extractRemoteData(DataRemoteSource.getFile(fileId))
        }
    }

    suspend fun pingServidor(): Boolean {
        return withRemoteSession(fallback = false) {
            DataRemoteSource.ping()
        }
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
        val success = deleteRemoteOperacionIfNeeded(opId, source)
        if (!success) return false

        finalizeOperacionDeletion(context, opId, source)
        return true
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

    private fun findOperacionInMemory(opId: String): OperacionDto? {
        return (operacionesBd + operacionesLc).firstOrNull { it.id == opId }
    }

    private suspend fun refreshRemoteOperacionDetailIntoMemory(opId: String): OperacionDto? {
        val fresh = extractRemoteData(DataRemoteSource.getOperacion(opId)) ?: return null
        upsertOperacionInMemory(fresh)
        return findOperacionInMemory(opId) ?: fresh
    }

    private suspend fun tryCreateRemoteOperacion(
        context: Context,
        req: OperacionUpsertRequest,
        localFallback: OperacionDto,
    ): OperacionDto? {
        val saved = runCatching {
            val result = DataRemoteSource.crearOperacion(req)
            if (!result.ok) null else result.data ?: localFallback
        }.getOrNull() ?: return null

        applySavedOperacion(saved)
        persistCache(context)
        return saved
    }

    private suspend fun createLocalDirtyOperacion(
        context: Context,
        local: OperacionDto,
    ): OperacionDto {
        upsertOperacionInMemory(local)
        markOperacionDirty(local.id, esOperacionSoloLocal = true)
        persistCache(context)
        return operacionesLc.firstOrNull { it.id == local.id } ?: local
    }

    suspend fun refreshOperacionDetail(context: Context, opId: String): OperacionDto? {
        val local = findOperacionInMemory(opId)
        if (!hasRemoteSession()) {
            return local
        }
        val refreshed = refreshRemoteOperacionDetailIntoMemory(opId) ?: return local
        persistCache(context)
        return refreshed
    }

    suspend fun createOperacion(context: Context, req: OperacionUpsertRequest): OperacionDto {
        val local = buildLocalOperacion(req)
        if (AppState.isEffectivelyOnline()) {
            val saved = tryCreateRemoteOperacion(context, req, local)
            if (saved != null) return saved
        }

        return createLocalDirtyOperacion(context, local)
    }

    suspend fun syncAllFromServer(context: Context): Boolean {
        if (!syncMutex.tryLock()) return false
        try {
            if (!canStartRemoteSync(context)) {
                return false
            }

            val syncOk = performRemoteSyncCycle(context)
            finalizeSyncAttempt(context, syncOk)
            return syncOk
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun performRemoteSyncCycle(context: Context): Boolean {
        val anyUploaded = flushPendingOps()
        flushPendingFiles()

        val operationsSynced = refreshRemoteOperationsIntoMemory()
        val catalogsSynced = refreshCatalogsAfterSync(context, force = anyUploaded)
        return operationsSynced && catalogsSynced
    }

    private fun finalizeSyncAttempt(context: Context, syncOk: Boolean) {
        if (syncOk) AppState.registerSyncSuccess()
        else AppState.registerSyncFailure("No se pudo sincronizar con la nube")

        persistCache(context)
        reconcileBackgroundSync(context)
    }

    private suspend fun deleteRemoteOperacionIfNeeded(opId: String, source: OperacionSource): Boolean {
        if (source != OperacionSource.BD || !AppState.isEffectivelyOnline()) return true
        return DataRemoteSource.eliminarOperacion(opId).ok
    }

    private fun finalizeOperacionDeletion(context: Context, opId: String, source: OperacionSource) {
        removeOperacion(opId, source)
        persistCache(context)
    }

    private fun persistSessionAndReconcile(context: Context) {
        AppState.persistSession(context)
        reconcileBackgroundSync(context)
    }

    private fun resolveAuthenticatedOnlineSession(context: Context): Boolean {
        if (!AppState.authToken.isNullOrBlank()) {
            AppState.registerSyncSuccess()
            persistSessionAndReconcile(context)
            return true
        }

        AppState.registerSyncFailure("Necesitas iniciar sesión nuevamente para sincronizar")
        persistSessionAndReconcile(context)
        return false
    }

    suspend fun ensureAuthenticatedOnlineSession(context: Context): Boolean {
        if (!AppState.hasAuthenticatedSession()) return false
        if (AppState.forceOffline) return false
        if (!AppState.hasNetwork) return false
        return resolveAuthenticatedOnlineSession(context)
    }

    private fun String.toEstadoSyncOperacion(): EstadoSyncOperacion {
        return runCatching { EstadoSyncOperacion.valueOf(this) }.getOrDefault(EstadoSyncOperacion.PENDIENTE)
    }

    private fun String.toEstadoSyncArchivo(): EstadoSyncArchivo {
        return runCatching { EstadoSyncArchivo.valueOf(this) }.getOrDefault(EstadoSyncArchivo.PENDIENTE)
    }

    private fun PendingTextFile.toRecord(): PendingTextFileRecord {
        return PendingTextFileRecord(
            id = stableId(),
            name = name,
            opId = opId,
            text = text,
            status = estado.name,
            lastError = ultimoError,
            lastAttemptAt = ultimoIntentoMs,
            createdAt = createdAt,
        )
    }

    private fun PendingTextFileRecord.toPendingTextFile(): PendingTextFile {
        return PendingTextFile(
            id = id,
            name = name,
            opId = opId,
            text = text,
            estado = status.toEstadoSyncArchivo(),
            ultimoError = lastError,
            ultimoIntentoMs = lastAttemptAt,
            createdAt = createdAt,
        )
    }

    private fun PendingTextFile.stableId(): String {
        return id ?: buildPendingFileId(name, opId, createdAt)
    }

    private fun PendingTextFile.toUploadRequest(): UploadTextFileRequest {
        return UploadTextFileRequest(
            name = name,
            opId = opId,
            text = text,
        )
    }

    private fun pendingTextFileIndex(fileId: String): Int {
        return pendingTextFiles.indexOfFirst { it.id == fileId }
    }

    private fun updatePendingTextFile(fileId: String, transform: (PendingTextFile) -> PendingTextFile) {
        val index = pendingTextFileIndex(fileId)
        if (index < 0) return
        pendingTextFiles[index] = transform(pendingTextFiles[index])
    }

    private fun removePendingTextFile(fileId: String) {
        pendingTextFiles.removeAll { it.id == fileId }
    }

    private fun CachePayload.toSnapshot(): LocalCacheSnapshot {
        return LocalCacheSnapshot(
            operacionesBd = operacionesBd,
            operacionesLc = operacionesLc,
            dirtyOperacionIds = dirtyOperacionIds.distinct(),
            pendingTextFiles = pendingTextFiles.map { it.toRecord() },
            regiones = regiones,
            sectoresAmerb = sectoresAmerb,
            caletas = caletas,
            opas = opas,
            botesMaestros = botesMaestros,
            especiesMaestras = especiesMaestras,
        )
    }

    private fun LocalCacheSnapshot.toLegacyPayload(): CachePayload {
        return CachePayload(
            operacionesBd = operacionesBd,
            operacionesLc = operacionesLc,
            dirtyOperacionIds = dirtyOperacionIds,
            pendingTextFiles = pendingTextFiles.map { it.toPendingTextFile() },
            regiones = regiones,
            sectoresAmerb = sectoresAmerb,
            caletas = caletas,
            opas = opas,
            botesMaestros = botesMaestros,
            especiesMaestras = especiesMaestras,
        )
    }

    private fun persistLegacyCache(appCtx: Context, payload: CachePayload, savedToRoom: Boolean) {
        val editor = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (savedToRoom) {
            editor.remove(KEY_CACHE_V1).apply()
            return
        }

        val json = gson.toJson(payload)
        editor.putString(KEY_CACHE_V1, json).apply()
    }

    private fun clearLegacyCachePreferences(appCtx: Context) {
        appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CACHE_V1)
            .remove(KEY_LAST_CATALOG_SYNC_MS)
            .apply()
    }

    private fun buildPendingFileId(name: String, opId: String?, createdAt: Long): String {
        return "file-$createdAt-${opId ?: "general"}-${name.hashCode()}"
    }
}
