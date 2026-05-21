package com.bitecma.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.bitecma.app.network.BoteMaestroDto
import com.bitecma.app.network.CaletaDto
import com.bitecma.app.network.EspecieDto
import com.bitecma.app.network.AuthLoginRequest
import com.bitecma.app.network.UploadTextFileRequest
import com.bitecma.app.network.OperacionUpsertRequest
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.OperacionBoteDto
import com.bitecma.app.network.OpaDto
import com.bitecma.app.network.RegionDto
import com.bitecma.app.network.RetrofitClient
import com.bitecma.app.network.SectorAmerbDto
import com.google.gson.Gson

object DataManager {
    private const val PREFS = "bitecma_cache"
    private const val KEY_CACHE_V1 = "cache_v1"
    private val gson = Gson()

    data class PendingTextFile(
        val name: String,
        val opId: String? = null,
        val text: String
    )

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

    // Maestro de Botes
    val botes = mutableStateListOf(
        BoteMaestro("5MENTARIO", "RIQUELME", "963244", "1980", "I — Tarapacá"),
        BoteMaestro("ABDON I", "CAVANCHA", "700599", "3490", "I — Tarapacá"),
        BoteMaestro("ABRAHAM", "RIQUELME", "18200", "934", "I — Tarapacá"),
        BoteMaestro("VICENTE ANDRÉS I", "CHAN-CHAN", "123456", "788", "XIV — Los Ríos")
    )

    // Maestro de Especies
    val especies = mutableStateListOf(
        EspecieMaestra(1, "Loco", "Concholepas concholepas"),
        EspecieMaestra(2, "Choro", "Choromytilus chorus"),
        EspecieMaestra(5, "Erizo rojo", "Loxechinus albus"),
        EspecieMaestra(7, "Lapa rosada", "Fissurella cumingi"),
        EspecieMaestra(25, "Macha", "Mesodesma donacium")
    )

    val regiones = mutableStateListOf<RegionDto>()
    val sectoresAmerb = mutableStateListOf<SectorAmerbDto>()
    val caletas = mutableStateListOf<CaletaDto>()
    val opas = mutableStateListOf<OpaDto>()
    val botesMaestros = mutableStateListOf<BoteMaestroDto>()
    val especiesMaestras = mutableStateListOf<EspecieDto>()

    val operacionesBd = mutableStateListOf<OperacionDto>()

    val dirtyOperacionIds = mutableStateListOf<String>()
    val pendingTextFiles = mutableStateListOf<PendingTextFile>()

    val operacionesLc = mutableStateListOf(
        OperacionDto(
            id = "OP-2026-001",
            sector = "Chan-chan",
            region = 14,
            fechaInicio = "2026-04-21",
            fechaFin = "2026-04-21",
            botes = listOf(
                OperacionBoteDto(nombre = "VICENTE ANDRÉS I", zona = 1, buzo = "CHINO", densTipo = "transecto"),
                OperacionBoteDto(nombre = "DANIELITO I", zona = 2, buzo = "RAMÓN", densTipo = "cuadrante")
            )
        )
    )

    fun loadCache(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_CACHE_V1, null) ?: return
        val payload = runCatching { gson.fromJson(json, CachePayload::class.java) }.getOrNull() ?: return
        operacionesBd.clear()
        operacionesBd.addAll(payload.operacionesBd)
        operacionesLc.clear()
        operacionesLc.addAll(payload.operacionesLc)
        dirtyOperacionIds.clear()
        dirtyOperacionIds.addAll(payload.dirtyOperacionIds.distinct())
        pendingTextFiles.clear()
        pendingTextFiles.addAll(payload.pendingTextFiles)

        regiones.clear()
        regiones.addAll(payload.regiones)
        sectoresAmerb.clear()
        sectoresAmerb.addAll(payload.sectoresAmerb)
        caletas.clear()
        caletas.addAll(payload.caletas)
        opas.clear()
        opas.addAll(payload.opas)
        botesMaestros.clear()
        botesMaestros.addAll(payload.botesMaestros)
        especiesMaestras.clear()
        especiesMaestras.addAll(payload.especiesMaestras)
    }

    fun persistCache(context: Context) {
        val payload = CachePayload(
            operacionesBd = operacionesBd.toList(),
            operacionesLc = operacionesLc.toList(),
            dirtyOperacionIds = dirtyOperacionIds.toList(),
            pendingTextFiles = pendingTextFiles.toList(),
            regiones = regiones.toList(),
            sectoresAmerb = sectoresAmerb.toList(),
            caletas = caletas.toList(),
            opas = opas.toList(),
            botesMaestros = botesMaestros.toList(),
            especiesMaestras = especiesMaestras.toList()
        )
        val json = gson.toJson(payload)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CACHE_V1, json).apply()
    }

    fun upsertOperacionInMemory(op: OperacionDto) {
        val idxBd = operacionesBd.indexOfFirst { it.id == op.id }
        if (idxBd >= 0) {
            operacionesBd[idxBd] = op
            return
        }
        val idxLc = operacionesLc.indexOfFirst { it.id == op.id }
        if (idxLc >= 0) {
            operacionesLc[idxLc] = op
        } else {
            operacionesLc.add(0, op)
        }
    }

    fun markOperacionDirty(opId: String) {
        if (dirtyOperacionIds.contains(opId)) return
        dirtyOperacionIds.add(opId)
    }

    fun enqueueTextFile(req: UploadTextFileRequest) {
        pendingTextFiles.add(PendingTextFile(name = req.name, opId = req.opId, text = req.text))
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

        val updated = runCatching {
            val res = RetrofitClient.apiService.actualizarOperacion(op.id, req)
            if (res.isSuccessful && res.body()?.ok == true) res.body()?.data else null
        }.getOrNull()
        if (updated != null) return updated

        return runCatching {
            val res = RetrofitClient.apiService.crearOperacion(req)
            if (res.isSuccessful && res.body()?.ok == true) res.body()?.data else null
        }.getOrNull()
    }

    private fun applySavedOperacion(saved: OperacionDto) {
        val idx = operacionesBd.indexOfFirst { it.id == saved.id }
        if (idx >= 0) operacionesBd[idx] = saved else operacionesBd.add(0, saved)
        operacionesLc.removeAll { it.id == saved.id }
        dirtyOperacionIds.removeAll { it == saved.id }
    }

    private suspend fun flushPendingOps(): Boolean {
        var any = false
        val pendingLc = operacionesLc.toList()
        pendingLc.forEach { op ->
            val saved = uploadOperacion(op)
            if (saved != null) {
                any = true
                applySavedOperacion(saved)
            }
        }

        val dirtyIds = dirtyOperacionIds.toList()
        dirtyIds.forEach { id ->
            val op = (operacionesBd + operacionesLc).firstOrNull { it.id == id } ?: return@forEach
            val saved = uploadOperacion(op)
            if (saved != null) {
                any = true
                applySavedOperacion(saved)
            }
        }

        return any
    }

    private suspend fun flushPendingFiles() {
        val pending = pendingTextFiles.toList()
        pending.forEach { f ->
            val res = runCatching {
                RetrofitClient.apiService.uploadTextFile(UploadTextFileRequest(name = f.name, opId = f.opId, text = f.text))
            }.getOrNull()
            if (res != null && res.isSuccessful && res.body()?.ok == true) {
                pendingTextFiles.remove(f)
            }
        }
    }

    suspend fun tryUploadOperacion(context: Context, op: OperacionDto): Boolean {
        if (AppState.forceOffline) return false
        if (AppState.authToken.isNullOrBlank()) return false
        val saved = uploadOperacion(op) ?: return false
        applySavedOperacion(saved)
        persistCache(context)
        return true
    }

    suspend fun syncAllFromServer(context: Context) {
        if (AppState.forceOffline) return
        if (AppState.authToken.isNullOrBlank()) return

        val dirtySet = dirtyOperacionIds.toSet()
        val localById = (operacionesBd + operacionesLc).associateBy { it.id }

        val syncOk = runCatching {
            val regRes = RetrofitClient.apiService.getRegiones()
            if (regRes.isSuccessful && regRes.body()?.ok == true) {
                regiones.clear()
                regiones.addAll(regRes.body()?.data ?: emptyList())
            }

            val secRes = RetrofitClient.apiService.getSectoresAmerb()
            if (secRes.isSuccessful && secRes.body()?.ok == true) {
                sectoresAmerb.clear()
                sectoresAmerb.addAll(secRes.body()?.data ?: emptyList())
            }

            val calRes = RetrofitClient.apiService.getCaletas()
            if (calRes.isSuccessful && calRes.body()?.ok == true) {
                caletas.clear()
                caletas.addAll(calRes.body()?.data ?: emptyList())
            }

            val opaRes = RetrofitClient.apiService.getOpas()
            if (opaRes.isSuccessful && opaRes.body()?.ok == true) {
                opas.clear()
                opas.addAll(opaRes.body()?.data ?: emptyList())
            }

            val botesRes = RetrofitClient.apiService.getBotes()
            if (botesRes.isSuccessful && botesRes.body()?.ok == true) {
                botesMaestros.clear()
                botesMaestros.addAll(botesRes.body()?.data ?: emptyList())
            }

            val especiesRes = RetrofitClient.apiService.getEspecies()
            if (especiesRes.isSuccessful && especiesRes.body()?.ok == true) {
                especiesMaestras.clear()
                especiesMaestras.addAll(especiesRes.body()?.data ?: emptyList())
            }

            val opsRes = RetrofitClient.apiService.getOperaciones()
            if (opsRes.isSuccessful && opsRes.body()?.ok == true) {
                val serverOps = opsRes.body()?.data ?: emptyList()
                val serverIds = serverOps.map { it.id }.toSet()
                operacionesBd.clear()
                operacionesBd.addAll(serverOps.map { op ->
                    if (dirtySet.contains(op.id)) localById[op.id] ?: op else op
                })
                operacionesLc.removeAll { it.id in serverIds }
            }

            true
        }.getOrElse {
            false
        }

        AppState.isOnline = syncOk

        if (syncOk) {
            val anyUploaded = flushPendingOps()
            flushPendingFiles()
            if (anyUploaded) {
                runCatching {
                    val opsRes = RetrofitClient.apiService.getOperaciones()
                    if (opsRes.isSuccessful && opsRes.body()?.ok == true) {
                        val serverOps = opsRes.body()?.data ?: emptyList()
                        val serverIds = serverOps.map { it.id }.toSet()
                        val dirtyNow = dirtyOperacionIds.toSet()
                        val localNow = (operacionesBd + operacionesLc).associateBy { it.id }
                        operacionesBd.clear()
                        operacionesBd.addAll(serverOps.map { op ->
                            if (dirtyNow.contains(op.id)) localNow[op.id] ?: op else op
                        })
                        operacionesLc.removeAll { it.id in serverIds }
                    }
                }
            }
        }

        persistCache(context)
    }

    suspend fun ensureBitecmaOnlineSession(context: Context): Boolean {
        if (!AppState.isBitecmaUser()) return false
        if (AppState.forceOffline) return false
        if (!AppState.authToken.isNullOrBlank()) {
            AppState.isOnline = true
            AppState.persistSession(context)
            return true
        }
        val res = runCatching {
            RetrofitClient.apiService.login(AuthLoginRequest(correo = "bitecma@bitecma.cl", password = "12345678"))
        }.getOrNull() ?: return false
        if (!res.isSuccessful) return false
        val body = res.body() ?: return false
        if (body.ok != true || body.token.isNullOrBlank()) return false

        AppState.isOnline = true
        AppState.authToken = body.token
        val user = body.user
        AppState.currentUserId = user?.uid ?: AppState.currentUserId
        AppState.currentUserName = user?.nombre ?: AppState.currentUserName
        AppState.currentUserRole = user?.rol ?: AppState.currentUserRole
        AppState.persistSession(context)
        return true
    }
}
