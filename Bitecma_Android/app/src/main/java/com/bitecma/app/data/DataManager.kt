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
import com.bitecma.app.network.CaletaDto
import com.bitecma.app.network.EspecieDto
import com.bitecma.app.network.AuthLoginRequest
import com.bitecma.app.network.ApiEnvelope
import com.bitecma.app.network.UploadTextFileRequest
import com.bitecma.app.network.OperacionUpsertRequest
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.OperacionBoteDto
import com.bitecma.app.network.OpaDto
import com.bitecma.app.network.RegionDto
import com.bitecma.app.network.RetrofitClient
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
import retrofit2.Response

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
        sectoresAmerb.clear()
        sectoresAmerb.addAll(snapshot.sectoresAmerb)
        caletas.clear()
        caletas.addAll(snapshot.caletas)
        opas.clear()
        opas.addAll(snapshot.opas)
        botesMaestros.clear()
        botesMaestros.addAll(snapshot.botesMaestros)
        especiesMaestras.clear()
        especiesMaestras.addAll(snapshot.especiesMaestras)
        refreshDerivedMasters()
        normalizeSyncStates()
    }

    private fun refreshDerivedMasters() {
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
                botes.clear()
                botes.addAll(mapped)
            }
        }

        if (especiesMaestras.isNotEmpty()) {
            especies.clear()
            especies.addAll(
                especiesMaestras.map { e ->
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
            sectoresAmerb = sectoresAmerb.toList(),
            caletas = caletas.toList(),
            opas = opas.toList(),
            botesMaestros = botesMaestros.toList(),
            especiesMaestras = especiesMaestras.toList(),
        )
    }

    private fun applyCatalogSnapshot(snapshot: CatalogSnapshot) {
        regiones.clear()
        regiones.addAll(snapshot.regiones)
        sectoresAmerb.clear()
        sectoresAmerb.addAll(snapshot.sectoresAmerb)
        caletas.clear()
        caletas.addAll(snapshot.caletas)
        opas.clear()
        opas.addAll(snapshot.opas)
        botesMaestros.clear()
        botesMaestros.addAll(snapshot.botesMaestros)
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

    fun reconcileBackgroundSync(context: Context) {
        SyncScheduler.reconcile(context.applicationContext, shouldRunBackgroundSync())
    }

    private fun hasCatalogData(): Boolean {
        return regiones.isNotEmpty() ||
            sectoresAmerb.isNotEmpty() ||
            caletas.isNotEmpty() ||
            opas.isNotEmpty() ||
            botesMaestros.isNotEmpty() ||
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

        fun resolveSavedOperacion(response: Response<ApiEnvelope<OperacionDto>>?): OperacionDto? {
            if (response == null || !response.isSuccessful) return null
            val body = response.body()
            if (body?.ok == false) return null
            val saved = body?.data ?: op
            return normalizeMutationSavedOperacion(op, saved)
        }

        val updated = runCatching {
            val res = RetrofitClient.apiService.actualizarOperacion(op.id, req)
            resolveSavedOperacion(res)
        }.getOrNull()
        if (updated != null) return updated

        return runCatching {
            val res = RetrofitClient.apiService.crearOperacion(req)
            resolveSavedOperacion(res)
        }.getOrNull()
    }

    private fun <T> extractEnvelopeData(response: Response<ApiEnvelope<T>>?): T? {
        if (response == null || !response.isSuccessful) return null
        val body = response.body() ?: return null
        if (body.ok != true) return null
        return body.data
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
            localById[id]?.takeIf { id !in serverIds }
        }
    )
        operacionesLc.removeAll { it.id in serverIds }
        normalizeSyncStates()
    }

    private suspend fun fetchRemoteOperations(): List<OperacionDto>? {
        val opsRes = runCatching { RetrofitClient.apiService.getOperaciones() }.getOrNull()
        return extractEnvelopeData(opsRes)
    }

    private suspend fun fetchRemoteCatalogSnapshot(currentCatalog: CatalogSnapshot = getCatalogSnapshot()): CatalogSnapshot? = coroutineScope {
        val regDeferred = async { runCatching { RetrofitClient.apiService.getRegiones() }.getOrNull() }
        val secDeferred = async { runCatching { RetrofitClient.apiService.getSectoresAmerb() }.getOrNull() }
        val calDeferred = async { runCatching { RetrofitClient.apiService.getCaletas() }.getOrNull() }
        val opaDeferred = async { runCatching { RetrofitClient.apiService.getOpas() }.getOrNull() }
        val botesDeferred = async { runCatching { RetrofitClient.apiService.getBotes() }.getOrNull() }
        val especiesDeferred = async { runCatching { RetrofitClient.apiService.getEspecies() }.getOrNull() }
        val regionesRemote = extractEnvelopeData(regDeferred.await())
        val sectoresRemote = extractEnvelopeData(secDeferred.await())
        val caletasRemote = extractEnvelopeData(calDeferred.await())
        val opasRemote = extractEnvelopeData(opaDeferred.await())
        val botesRemote = extractEnvelopeData(botesDeferred.await())
        val especiesRemote = extractEnvelopeData(especiesDeferred.await())
        CatalogSnapshot(
            regiones = preferNonEmptyCatalog(regionesRemote, currentCatalog.regiones),
            sectoresAmerb = preferNonEmptyCatalog(sectoresRemote, currentCatalog.sectoresAmerb),
            caletas = preferNonEmptyCatalog(caletasRemote, currentCatalog.caletas),
            opas = preferNonEmptyCatalog(opasRemote, currentCatalog.opas),
            botesMaestros = preferNonEmptyCatalog(botesRemote, currentCatalog.botesMaestros),
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

    private suspend fun flushPendingOps(): Boolean {
        var any = false
        val pendingLc = operacionesLc.toList()
        pendingLc.forEach { op ->
            markOperacionSyncing(op.id)
            val saved = uploadOperacion(op)
            if (saved != null) {
                any = true
                applySavedOperacion(saved)
            } else {
                markOperacionSyncError(op.id)
            }
        }

        val dirtyIds = dirtyOperacionIds.toList()
        dirtyIds.forEach { id ->
            val op = (operacionesBd + operacionesLc).firstOrNull { it.id == id } ?: return@forEach
            markOperacionSyncing(id)
            val saved = uploadOperacion(op)
            if (saved != null) {
                any = true
                applySavedOperacion(saved)
            } else {
                markOperacionSyncError(id)
            }
        }

        return any
    }

    private suspend fun flushPendingFiles() {
        val pending = pendingTextFiles.toList()
        pending.forEach { f ->
            val fileId = f.id ?: buildPendingFileId(f.name, f.opId, f.createdAt)
            markPendingFileSyncing(fileId)
            val res = runCatching {
                RetrofitClient.apiService.uploadTextFile(UploadTextFileRequest(name = f.name, opId = f.opId, text = f.text))
            }.getOrNull()
            if (res != null && res.isSuccessful && res.body()?.ok == true) {
                pendingTextFiles.removeAll { it.id == fileId }
            } else {
                markPendingFileError(fileId)
            }
        }
    }

    suspend fun tryUploadOperacion(context: Context, op: OperacionDto): Boolean {
        if (AppState.forceOffline) return false
        if (!AppState.hasNetwork) return false
        if (AppState.authToken.isNullOrBlank()) return false
        markOperacionSyncing(op.id)
        val saved = uploadOperacion(op)
        if (saved == null) {
            markOperacionSyncError(op.id)
            return false
        }
        applySavedOperacion(saved)
        AppState.registerSyncSuccess()
        persistCache(context)
        return true
    }

    suspend fun refreshCatalogs(context: Context, force: Boolean = false): Boolean {
        if (AppState.forceOffline) return false
        if (!AppState.hasNetwork) return false
        if (AppState.authToken.isNullOrBlank()) return false
        if (!shouldRefreshCatalogs(context, force)) return true

        val snapshot = runCatching { fetchRemoteCatalogSnapshot() }.getOrNull() ?: return false
        applyCatalogSnapshot(snapshot)
        markCatalogSyncSuccess(context)
        persistCache(context)
        return true
    }

    suspend fun refreshOperacionDetail(context: Context, opId: String): OperacionDto? {
        val local = (operacionesBd + operacionesLc).firstOrNull { it.id == opId }
        if (AppState.forceOffline || AppState.authToken.isNullOrBlank() || !AppState.hasNetwork) {
            return local
        }
        val fresh = extractEnvelopeData(runCatching { RetrofitClient.apiService.getOperacion(opId) }.getOrNull()) ?: return local
        upsertOperacionInMemory(fresh)
        persistCache(context)
        return (operacionesBd + operacionesLc).firstOrNull { it.id == opId } ?: fresh
    }

    suspend fun createOperacion(context: Context, req: OperacionUpsertRequest): OperacionDto {
        val local = buildLocalOperacion(req)
        if (AppState.isEffectivelyOnline()) {
            val saved = runCatching {
                val response = RetrofitClient.apiService.crearOperacion(req)
                if (!response.isSuccessful) {
                    null
                } else {
                    val body = response.body()
                    if (body?.ok == false) null else body?.data ?: local
                }
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
            if (AppState.forceOffline) return false
            if (!AppState.hasNetwork) {
                AppState.registerSyncFailure("Sin conectividad")
                reconcileBackgroundSync(context)
                return false
            }
            if (AppState.authToken.isNullOrBlank()) {
                reconcileBackgroundSync(context)
                return false
            }

            val anyUploaded = flushPendingOps()
            flushPendingFiles()

            var syncOk = true
            val refreshedOps = runCatching { fetchRemoteOperations() }.getOrNull()
            if (refreshedOps != null) {
                mergeServerOperations(refreshedOps)
            } else {
                syncOk = false
            }

            if (shouldRefreshCatalogs(context, force = anyUploaded)) {
                val remoteCatalog = runCatching { fetchRemoteCatalogSnapshot() }.getOrNull()
                if (remoteCatalog != null) {
                    applyCatalogSnapshot(remoteCatalog)
                    markCatalogSyncSuccess(context)
                } else if (!hasCatalogData()) {
                    syncOk = false
                }
            }

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
