package com.bitecma.app.ui.dashboard

import android.content.Context
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager
import com.bitecma.app.data.MasterData
import com.bitecma.app.network.BoteMaestroDto
import com.bitecma.app.network.CaletaDto
import com.bitecma.app.network.DensidadUnidadDto
import com.bitecma.app.network.EspecieDto
import com.bitecma.app.network.LpSampleDto
import com.bitecma.app.network.OpaDto
import com.bitecma.app.network.OperacionBoteDto
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.SectorAmerbDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val algasComunesLpKeys = setOf(
    "Huiro negro",
    "Huiro palo",
    "Cochayuyo",
    "Huiro canutillo",
    "Huiro",
    "Luga roja",
    "Luga negra",
    "Luga cuchara",
).map(::normalizarTextoBusqueda).toSet()

private val algasCientificasLpKeys = setOf(
    "Lessonia berteroana",
    "Lessonia trabeculata",
    "Durvillaea antarctica",
    "Macrocystis integrifolia",
    "Macrocystis pyrifera",
    "Gigartina skottsbergii",
    "Sarcothalia crispata",
    "Mazzaella laminarioides",
).map(::normalizarTextoBusqueda).toSet()

fun normalizarTextoBusqueda(value: String?): String {
    return Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()
        .trim()
}

fun buildFallbackBotesMaestros(): List<BoteMaestroDto> {
    return MasterData.botes.mapIndexed { index, bote ->
        val regionParts = bote.regionId.split("—", limit = 2).map { it.trim() }
        BoteMaestroDto(
            id = -(index + 1),
            region_rom = regionParts.firstOrNull(),
            region = regionParts.getOrNull(1),
            nombre = bote.nombre,
            nrpa = bote.rpa,
            nmatricula = bote.matricula,
            caleta = bote.caleta,
        )
    }.distinctBy { bote ->
        listOf(
            normalizarTextoBusqueda(bote.nombre),
            normalizarTextoBusqueda(bote.caleta),
            normalizarTextoBusqueda(bote.nrpa),
        ).joinToString("|")
    }
}

fun prepararEstadoEdicionBote(
    bote: OperacionBoteDto,
    selectedSpeciesIds: MutableList<Int>,
    muestreoBySpeciesId: MutableMap<Int, Set<String>>,
    transectosList: MutableList<DensidadUnidadDto>,
) {
    selectedSpeciesIds.clear()
    muestreoBySpeciesId.clear()
    val densIds = densitySpeciesIdsFromTransectos(bote.transectos.orEmpty())
    val lpIds = densitySpeciesIdsFromLpMuestras(bote)
    val existingSpecies = (densIds + lpIds).distinct()
    selectedSpeciesIds.addAll(existingSpecies)
    densIds.forEach { sid -> muestreoBySpeciesId[sid] = (muestreoBySpeciesId[sid] ?: emptySet()) + "DENSIDAD" }
    lpIds.forEach { sid -> muestreoBySpeciesId[sid] = (muestreoBySpeciesId[sid] ?: emptySet()) + setOf("L-P", "DENSIDAD") }
    transectosList.clear()
    transectosList.addAll(bote.transectos ?: emptyList())
}

fun densitySpeciesIdsFromTransectos(transectos: List<DensidadUnidadDto>): List<Int> {
    return transectos
        .flatMap { it.counts?.keys ?: emptySet() }
        .mapNotNull { it.toIntOrNull() }
        .distinct()
        .sorted()
}

fun densitySpeciesIdsFromLpMuestras(bote: OperacionBoteDto?): List<Int> {
    return bote?.lpMuestras
        ?.keys
        ?.mapNotNull { it.toIntOrNull() }
        ?.distinct()
        ?.sorted()
        ?: emptyList()
}

fun densitySpeciesIdsForDraft(
    bote: OperacionBoteDto?,
    transectos: List<DensidadUnidadDto> = bote?.transectos.orEmpty(),
): List<Int> {
    return (densitySpeciesIdsFromTransectos(transectos) + densitySpeciesIdsFromLpMuestras(bote))
        .distinct()
        .sorted()
}

fun densityCountsForDraft(
    bote: OperacionBoteDto?,
    transectos: List<DensidadUnidadDto> = bote?.transectos.orEmpty(),
    previousCounts: Map<String, Int>? = null,
): Map<String, Int>? {
    val speciesIds = (previousCounts.orEmpty().keys.mapNotNull { it.toIntOrNull() } + densitySpeciesIdsForDraft(bote, transectos))
        .distinct()
        .sorted()
    if (speciesIds.isEmpty()) return previousCounts?.takeIf { it.isNotEmpty() }
    return speciesIds.associate { sid -> sid.toString() to (previousCounts?.get(sid.toString()) ?: 0) }
}

fun syncLpSpeciesWithDensity(
    bote: OperacionBoteDto,
    especiesById: Map<Int, EspecieDto>,
): OperacionBoteDto {
    val densityIds = densitySpeciesIdsFromTransectos(bote.transectos.orEmpty())
    if (densityIds.isEmpty()) return bote

    val lpM = (bote.lpMuestras ?: emptyMap()).toMutableMap()
    densityIds.forEach { sid ->
        val buckets = (lpM[sid.toString()] ?: emptyMap()).toMutableMap()
        defaultLpKindsForSpecies(especiesById[sid]).forEach { kind ->
            buckets.putIfAbsent(kind, emptyList())
        }
        lpM[sid.toString()] = buckets.toMap()
    }
    return bote.copy(lpMuestras = lpM.toMap())
}

fun validarNuevaOperacion(
    selectedRegionId: Int?,
    sectorAmerb: String,
    caleta: String,
    tipoOrg: String,
    organizacion: String,
    numSeguimiento: String,
    fechaInicio: String,
    fechaFin: String,
): String? {
    return when {
        selectedRegionId == null -> "Debe seleccionar una región"
        sectorAmerb.isBlank() -> "Debe seleccionar o escribir un sector AMERB"
        caleta.isBlank() -> "Debe seleccionar o escribir una caleta"
        tipoOrg.isBlank() -> "Debe seleccionar un tipo de organización"
        organizacion.isBlank() -> "Debe seleccionar o escribir una organización"
        numSeguimiento.isBlank() -> "Debe ingresar el N° de seguimiento"
        fechaInicio.isBlank() -> "Debe ingresar la fecha de inicio"
        fechaFin.isBlank() -> "Debe ingresar la fecha de término"
        else -> {
            try {
                val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val inicio = LocalDate.parse(fechaInicio, fmt)
                val fin = LocalDate.parse(fechaFin, fmt)
                if (fin.isBefore(inicio)) "La fecha de término no puede ser anterior a la de inicio" else null
            } catch (_: Exception) {
                "Formato de fecha inválido"
            }
        }
    }
}

fun todayIso(): String = LocalDate.now().toString()

fun validarBotes(updatedBotesUi: List<OperacionBoteDto>): String? {
    return when {
        updatedBotesUi.isEmpty() -> "Debe agregar al menos un bote"
        updatedBotesUi.any { (it.submareal ?: 1) != 0 && it.nombre.isNullOrBlank() } ->
            "Todos los botes submareales deben tener un nombre (Bote Maestro)"
        updatedBotesUi.any { it.buzo.isNullOrBlank() } ->
            "Todos los botes deben tener asignado un buzo"
        else -> null
    }
}

fun persistOperacionSnapshot(
    context: Context,
    operacion: OperacionDto,
    markDirtyWhenOffline: Boolean = true,
) {
    DataManager.upsertOperacionInMemory(operacion)
    if (markDirtyWhenOffline && !AppState.isEffectivelyOnline()) {
        DataManager.markOperacionDirty(operacion.id)
    }
    DataManager.persistCache(context)
}

fun trySyncOperacionSnapshot(
    scope: CoroutineScope,
    context: Context,
    operacion: OperacionDto,
    onSynced: (OperacionDto) -> Unit = {},
) {
    scope.launch {
        val ok = DataManager.tryUploadOperacion(context, operacion)
        if (!ok) {
            DataManager.markOperacionDirty(operacion.id)
            DataManager.persistCache(context)
        } else {
            onSynced(DataManager.operacionesBd.firstOrNull { it.id == operacion.id } ?: operacion)
        }
    }
}

fun replaceMatchingBote(
    botes: List<OperacionBoteDto>,
    currentBote: OperacionBoteDto,
    transform: (OperacionBoteDto) -> OperacionBoteDto,
): List<OperacionBoteDto> {
    var replaced = false
    return botes.map { bote ->
        if (!replaced && sameBoteIdentity(bote, currentBote)) {
            replaced = true
            transform(bote)
        } else {
            bote
        }
    }
}

fun sameBoteIdentity(left: OperacionBoteDto, right: OperacionBoteDto): Boolean {
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
        normalizarTextoBusqueda(left.nombre) == normalizarTextoBusqueda(right.nombre) &&
        normalizarTextoBusqueda(left.buzo) == normalizarTextoBusqueda(right.buzo)
}

fun totalLpSamples(bote: OperacionBoteDto): Int {
    return bote.lpMuestras
        ?.values
        ?.sumOf { buckets -> buckets.values.sumOf { samples -> samples.size } }
        ?: 0
}

fun isAlgaSpecies(especie: EspecieDto?): Boolean {
    if (especie == null) return false
    return algasComunesLpKeys.contains(normalizarTextoBusqueda(especie.com)) ||
        algasCientificasLpKeys.contains(normalizarTextoBusqueda(especie.sci))
}

fun normalizeLpKind(kind: String?): String {
    return when (kind.orEmpty().trim().uppercase()) {
        "LP", "L-P" -> "LP"
        "D" -> "D"
        else -> "L"
    }
}

fun lpKindLabel(kind: String?): String {
    return when (normalizeLpKind(kind)) {
        "LP" -> "L-P"
        else -> normalizeLpKind(kind)
    }
}

fun defaultLpKindsForSpecies(especie: EspecieDto?): Set<String> {
    return if (isAlgaSpecies(especie)) setOf("D") else setOf("LP")
}

fun normalizeLpBuckets(entry: Map<String, List<LpSampleDto>>?): Map<String, List<LpSampleDto>> {
    if (entry.isNullOrEmpty()) return emptyMap()
    val merged = linkedMapOf<String, MutableList<LpSampleDto>>()
    entry.forEach { (rawKind, samples) ->
        val kind = normalizeLpKind(rawKind)
        merged.getOrPut(kind) { mutableListOf() }.addAll(samples)
    }
    return merged.mapValues { (_, samples) -> samples.toList() }
}

fun orderedLpKinds(
    entry: Map<String, List<LpSampleDto>>?,
    especie: EspecieDto?,
): List<String> {
    val normalizedKinds = normalizeLpBuckets(entry).keys
    val kinds = if (normalizedKinds.isNotEmpty()) normalizedKinds else defaultLpKindsForSpecies(especie)
    return kinds.sortedBy {
        when (it) {
            "D" -> 0
            "LP" -> 1
            else -> 2
        }
    }
}

fun boteSelectionKey(bote: OperacionBoteDto): String {
    return listOfNotNull(bote.id?.takeIf { it.isNotBlank() }, bote.zona?.toString(), bote.nombre, bote.buzo)
        .joinToString("|")
}

fun densTipoLabel(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", true)) "Cuadrante" else "Transecto"
}

fun densTipoPluralLabel(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", true)) "Cuadrantes" else "Transectos"
}

fun normalizeDensTipo(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", true)) "cuadrante" else "transecto"
}

fun createDefaultBoteRows(count: Int = 4): List<OperacionBoteDto> {
    return (1..count).map { zona ->
        OperacionBoteDto(
            zona = zona,
            densTipo = "Transecto",
            submareal = 1,
        )
    }
}

fun createDefaultDensityUnits(
    densTipo: String?,
    count: Int = 6,
): List<DensidadUnidadDto> {
    return if (normalizeDensTipo(densTipo) == "cuadrante") {
        emptyList()
    } else {
        (1..count).map { num ->
            DensidadUnidadDto(
                num = num,
                tipo = "transecto",
                area = 120.0,
                counts = emptyMap(),
            )
        }
    }
}

fun speciesIdsFromCounts(counts: Map<String, Int>?): List<Int> {
    return counts.orEmpty().keys.mapNotNull { it.toIntOrNull() }.distinct().sorted()
}

fun mergeCountsForSpecies(
    previousCounts: Map<String, Int>?,
    selectedSpeciesIds: Collection<Int>,
): Map<String, Int> {
    val previous = previousCounts.orEmpty()
    return selectedSpeciesIds
        .distinct()
        .sorted()
        .associate { sid -> sid.toString() to (previous[sid.toString()] ?: 0) }
}

fun densityValueText(count: Int, area: Double?): String {
    val safeArea = area?.takeIf { it > 0.0 } ?: return "0.0000"
    return runCatching { String.format("%.4f", count.toDouble() / safeArea) }.getOrDefault("0.0000")
}

fun inferOperacionRegionId(
    op: OperacionDto,
    sectoresAmerb: List<SectorAmerbDto>,
    caletas: List<CaletaDto>,
    opas: List<OpaDto>,
): Int? {
    op.region?.let { return it }
    op.sectorAmerbId?.let { sectorId ->
        sectoresAmerb.firstOrNull { it.id == sectorId }?.region?.let { return it }
    }
    op.opaId?.let { opaId ->
        opas.firstOrNull { it.id == opaId }?.region?.let { return it }
    }

    val sectorAmerbKey = normalizarTextoBusqueda(op.sectorAmerb)
    if (sectorAmerbKey.isNotBlank()) {
        sectoresAmerb.firstOrNull { normalizarTextoBusqueda(it.nombre) == sectorAmerbKey }?.region?.let { return it }
    }

    val caletaKey = normalizarTextoBusqueda(op.sector)
    if (caletaKey.isNotBlank()) {
        caletas.firstOrNull { normalizarTextoBusqueda(it.nombre) == caletaKey }?.region?.let { return it }
    }

    val orgKey = normalizarTextoBusqueda(op.org)
    if (orgKey.isNotBlank()) {
        opas.firstOrNull {
            normalizarTextoBusqueda(it.nombre) == orgKey ||
                normalizarTextoBusqueda(it.nombrecorto) == orgKey
        }?.region?.let { return it }
    }

    return null
}
