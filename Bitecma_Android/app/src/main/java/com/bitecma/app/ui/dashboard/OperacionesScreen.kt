package com.bitecma.app.ui.dashboard
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import com.bitecma.app.network.*
import com.bitecma.app.data.*
import com.bitecma.app.ui.bitecmaAmberBg
import com.bitecma.app.ui.bitecmaBlueBg
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaDangerBg
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaNavyStrong
import com.bitecma.app.ui.bitecmaPurpleBg
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSoftBackgroundAlt
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaSuccessBg
import com.bitecma.app.ui.bitecmaTeal
import com.bitecma.app.ui.bitecmaTealContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate

private data class OperacionItem(
    val op: OperacionDto,
    val source: OperacionSource
)

private fun mergeBotesForUi(
    first: List<OperacionBoteDto>,
    second: List<OperacionBoteDto>,
): List<OperacionBoteDto> {
    if (first.isEmpty()) return second
    if (second.isEmpty()) return first

    val merged = first.toMutableList()
    second.forEach { incoming ->
        val existingIndex = merged.indexOfFirst { sameBoteIdentity(it, incoming) }
        if (existingIndex >= 0) {
            val existing = merged[existingIndex]
            merged[existingIndex] = incoming.copy(
                zona = incoming.zona ?: existing.zona,
                nombre = incoming.nombre ?: existing.nombre,
                buzo = incoming.buzo ?: existing.buzo,
                densTipo = incoming.densTipo ?: existing.densTipo,
                submareal = incoming.submareal ?: existing.submareal,
                boteMaestroId = incoming.boteMaestroId ?: existing.boteMaestroId,
                lpMuestras = if (!incoming.lpMuestras.isNullOrEmpty()) incoming.lpMuestras else existing.lpMuestras,
                transectos = if (!incoming.transectos.isNullOrEmpty()) incoming.transectos else existing.transectos,
            )
        } else {
            merged.add(incoming)
        }
    }
    return merged
}

private fun mergeOperacionForUi(
    primary: OperacionDto,
    secondary: OperacionDto,
): OperacionDto {
    return primary.copy(
        sector = primary.sector.takeIf { it.isNotBlank() } ?: secondary.sector,
        region = primary.region ?: secondary.region,
        sectorAmerbId = primary.sectorAmerbId ?: secondary.sectorAmerbId,
        sectorAmerb = primary.sectorAmerb ?: secondary.sectorAmerb,
        tipoOrg = primary.tipoOrg ?: secondary.tipoOrg,
        opaId = primary.opaId ?: secondary.opaId,
        org = primary.org ?: secondary.org,
        numSeg = primary.numSeg ?: secondary.numSeg,
        fechaInicio = primary.fechaInicio ?: secondary.fechaInicio,
        fechaFin = primary.fechaFin ?: secondary.fechaFin,
        botes = mergeBotesForUi(primary.botes.orEmpty(), secondary.botes.orEmpty()),
    )
}

private fun operacionUiScore(op: OperacionDto): Int {
    return (op.botes?.size ?: 0) * 100 +
        (if (op.region != null) 1 else 0) +
        (if (!op.sectorAmerb.isNullOrBlank()) 1 else 0) +
        (if (!op.org.isNullOrBlank()) 1 else 0) +
        (if (op.numSeg != null) 1 else 0) +
        (if (!op.fechaInicio.isNullOrBlank()) 1 else 0)
}

private fun mergeOperacionItemForUi(
    current: OperacionItem,
    incoming: OperacionItem,
): OperacionItem {
    val primary = if (operacionUiScore(incoming.op) > operacionUiScore(current.op)) incoming else current
    val secondary = if (primary === incoming) current else incoming
    return OperacionItem(
        op = mergeOperacionForUi(primary.op, secondary.op),
        source = if (current.source == OperacionSource.BD || incoming.source == OperacionSource.BD) {
            OperacionSource.BD
        } else {
            primary.source
        },
    )
}

private fun normalizarTextoBusqueda(value: String?): String {
    return Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()
        .trim()
}

private fun buildFallbackBotesMaestros(): List<BoteMaestroDto> {
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

private val TIPOS_ORGANIZACION_DEFAULT = listOf("STI", "ASOC", "OTRO")

private fun operacionCoincideConBusqueda(op: OperacionDto, query: String): Boolean {
    if (query.isBlank()) return true
    val q = normalizarTextoBusqueda(query)
    val texto = buildString {
        append(op.id)
        append(' ')
        append(op.sector)
        append(' ')
        append(op.sectorAmerb.orEmpty())
        append(' ')
        append(op.org.orEmpty())
        append(' ')
        append(op.tipoOrg.orEmpty())
        append(' ')
        append(op.numSeg?.toString().orEmpty())
        append(' ')
        op.botes.orEmpty().forEach { bote ->
            append(bote.nombre.orEmpty())
            append(' ')
            append(bote.buzo.orEmpty())
            append(' ')
        }
    }
    return normalizarTextoBusqueda(texto).contains(q)
}

private fun operacionCoincideConMes(op: OperacionDto, mes: String): Boolean {
    if (mes.isBlank()) return true
    val fi = op.fechaInicio.orEmpty()
    val ff = op.fechaFin.orEmpty()
    return fi.startsWith(mes) || ff.startsWith(mes)
}

private fun prepararEstadoEdicionBote(
    bote: OperacionBoteDto,
    selectedSpeciesIds: MutableList<Int>,
    muestreoBySpeciesId: MutableMap<Int, Set<String>>,
    transectosList: MutableList<DensidadUnidadDto>,
) {
    selectedSpeciesIds.clear()
    muestreoBySpeciesId.clear()
    val densIds = bote.transectos
        ?.flatMap { it.counts?.keys ?: emptySet() }
        ?.mapNotNull { it.toIntOrNull() }
        ?.distinct()
        ?: emptyList()
    val lpIds = bote.lpMuestras
        ?.keys
        ?.mapNotNull { it.toIntOrNull() }
        ?.distinct()
        ?: emptyList()
    val existingSpecies = (densIds + lpIds).distinct()
    selectedSpeciesIds.addAll(existingSpecies)
    densIds.forEach { sid -> muestreoBySpeciesId[sid] = setOf("DENSIDAD", "L-P") }
    lpIds.forEach { sid -> muestreoBySpeciesId[sid] = (muestreoBySpeciesId[sid] ?: emptySet()) + "L-P" }
    transectosList.clear()
    transectosList.addAll(bote.transectos ?: emptyList())
}

private fun validarNuevaOperacion(
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

private fun todayIso(): String = LocalDate.now().toString()

private fun validarBotes(updatedBotesUi: List<OperacionBoteDto>): String? {
    return when {
        updatedBotesUi.isEmpty() -> "Debe agregar al menos un bote"
        updatedBotesUi.any { (it.submareal ?: 1) != 0 && it.nombre.isNullOrBlank() } ->
            "Todos los botes submareales deben tener un nombre (Bote Maestro)"
        updatedBotesUi.any { it.buzo.isNullOrBlank() } ->
            "Todos los botes deben tener asignado un buzo"
        else -> null
    }
}

private fun persistOperacionSnapshot(
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

private fun trySyncOperacionSnapshot(
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

private fun replaceMatchingBote(
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

private fun sameBoteIdentity(left: OperacionBoteDto, right: OperacionBoteDto): Boolean {
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

private fun totalLpSamples(bote: OperacionBoteDto): Int {
    return bote.lpMuestras
        ?.values
        ?.sumOf { buckets -> buckets.values.sumOf { samples -> samples.size } }
        ?: 0
}

private fun boteSelectionKey(bote: OperacionBoteDto): String {
    return listOfNotNull(bote.id?.takeIf { it.isNotBlank() }, bote.zona?.toString(), bote.nombre, bote.buzo)
        .joinToString("|")
}

private fun densTipoLabel(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", true)) "Cuadrante" else "Transecto"
}

private fun densTipoPluralLabel(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", true)) "Cuadrantes" else "Transectos"
}

private fun densityValueText(count: Int, area: Double?): String {
    val safeArea = area?.takeIf { it > 0.0 } ?: return "0.0000"
    return runCatching { String.format("%.4f", count.toDouble() / safeArea) }.getOrDefault("0.0000")
}

private fun inferOperacionRegionId(
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

@Composable
private fun GestionBotesDialog(
    show: Boolean,
    operacionId: String?,
    operationRegionRom: String?,
    operationCaleta: String?,
    botesList: SnapshotStateList<OperacionBoteDto>,
    botesMaestros: List<BoteMaestroDto>,
    validationError: String?,
    onValidationErrorChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    onAddRow: () -> Unit,
    onSave: () -> Unit,
) {
    if (!show) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = if (isSystemInDarkTheme()) Color(0xFF111B2B) else Color.White,
            tonalElevation = 12.dp
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompactLayout = maxWidth < 700.dp
                val fallbackBotesMaestros = remember { buildFallbackBotesMaestros() }
                val availableBotesMaestros = remember(botesMaestros) {
                    (botesMaestros + fallbackBotesMaestros).distinctBy { bote ->
                        listOf(
                            normalizarTextoBusqueda(bote.nombre),
                            normalizarTextoBusqueda(bote.caleta),
                            normalizarTextoBusqueda(bote.nrpa),
                        ).joinToString("|")
                    }
                }

                var activeSearchRowIndex by remember { mutableStateOf<Int?>(null) }
                var showBoatSearchDialog by remember { mutableStateOf(false) }
                var boatSearchTerm by remember { mutableStateOf("") }
                val operationCaletaKey = remember(operationCaleta) { normalizarTextoBusqueda(operationCaleta) }
                val operationRegionKey = remember(operationRegionRom) {
                    normalizarTextoBusqueda(operationRegionRom?.split("—")?.firstOrNull()?.trim())
                }
                val candidateBotes = remember(availableBotesMaestros, operationCaletaKey, operationRegionKey) {
                    val withRegion = if (operationRegionKey.isBlank()) {
                        availableBotesMaestros
                    } else {
                        availableBotesMaestros.filter { bote ->
                            val regionKey = normalizarTextoBusqueda(bote.region_rom ?: bote.region)
                            regionKey.contains(operationRegionKey)
                        }
                    }
                    val byCaleta = if (operationCaletaKey.isBlank()) {
                        withRegion
                    } else {
                        withRegion.filter { bote ->
                            val caletaKey = normalizarTextoBusqueda(bote.caleta)
                            caletaKey == operationCaletaKey ||
                                caletaKey.contains(operationCaletaKey) ||
                                operationCaletaKey.contains(caletaKey)
                        }
                    }
                    when {
                        byCaleta.isNotEmpty() -> byCaleta
                        withRegion.isNotEmpty() -> withRegion
                        else -> availableBotesMaestros
                    }
                }
                val filteredBotes = remember(candidateBotes, boatSearchTerm) {
                    val term = normalizarTextoBusqueda(boatSearchTerm)
                    candidateBotes
                        .filter { bote ->
                            if (term.isBlank()) return@filter true
                            listOf(bote.nombre, bote.nrpa, bote.nmatricula, bote.caleta)
                                .map { normalizarTextoBusqueda(it) }
                                .any { it.contains(term) }
                        }
                        .sortedBy { normalizarTextoBusqueda(it.nombre) }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF003366), Color(0xFF00509E))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Text(
                            "GESTIÓN DE BOTES — $operacionId",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                .background(Color(0xFFF1F3F5))
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#", Modifier.weight(0.3f), fontSize = if (isCompactLayout) 10.sp else 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                            Text("ZONA", Modifier.weight(0.75f), fontSize = if (isCompactLayout) 10.sp else 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                            Text("BOTE (MAESTRO)", Modifier.weight(1.55f), fontSize = if (isCompactLayout) 10.sp else 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                            Text("BUZO", Modifier.weight(1f), fontSize = if (isCompactLayout) 10.sp else 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                            Text("TIPO", Modifier.weight(0.95f), fontSize = if (isCompactLayout) 10.sp else 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                            Spacer(Modifier.width(36.dp))
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Color(0xFFF1F3F5), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(items = botesList.toList()) { index: Int, bote: OperacionBoteDto ->
                                    BoteRowItem(
                                        index = index + 1,
                                        bote = bote,
                                        isSearchActive = activeSearchRowIndex == index,
                                        onOpenSearch = {
                                            activeSearchRowIndex = index
                                            boatSearchTerm = bote.nombre.orEmpty()
                                            showBoatSearchDialog = true
                                        },
                                        onDelete = {
                                            botesList.removeAt(index)
                                            when {
                                                activeSearchRowIndex == index -> {
                                                    activeSearchRowIndex = null
                                                    boatSearchTerm = ""
                                                    showBoatSearchDialog = false
                                                }
                                                activeSearchRowIndex != null && activeSearchRowIndex!! > index -> {
                                                    activeSearchRowIndex = activeSearchRowIndex!! - 1
                                                }
                                            }
                                        },
                                        onUpdate = { updated -> botesList[index] = updated }
                                    )
                                    HorizontalDivider(color = Color(0xFFF1F3F5), modifier = Modifier.padding(horizontal = 12.dp))
                                }
                                if (botesList.isEmpty()) {
                                    item {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "No hay botes registrados. Agrega una fila para comenzar a cargar la operación.",
                                                color = Color.Gray,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                                if (validationError != null) {
                                    item {
                                        Text(
                                            text = validationError,
                                            color = Color.Red,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        if (activeSearchRowIndex != null && !showBoatSearchDialog) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isSystemInDarkTheme()) Color(0xFF0B1626) else Color(0xFFF8FAFD),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    if (isCompactLayout) {
                                        OutlinedTextField(
                                            value = boatSearchTerm,
                                            onValueChange = { boatSearchTerm = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = { Text("Buscar bote, RPA o matrícula...") },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(
                                                onSearch = {
                                                    if (filteredBotes.size == 1) {
                                                        val selected = filteredBotes.first()
                                                        val rowIndex = activeSearchRowIndex
                                                        if (rowIndex != null && rowIndex in botesList.indices) {
                                                            val current = botesList[rowIndex]
                                                            botesList[rowIndex] = current.copy(
                                                                nombre = selected.nombre,
                                                                boteMaestroId = selected.id,
                                                                submareal = 1
                                                            )
                                                        }
                                                        activeSearchRowIndex = null
                                                        boatSearchTerm = ""
                                                    }
                                                }
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val rowIndex = activeSearchRowIndex
                                                    val customBoatName = boatSearchTerm.trim()
                                                    if (rowIndex != null && rowIndex in botesList.indices && customBoatName.isNotBlank()) {
                                                        val current = botesList[rowIndex]
                                                        botesList[rowIndex] = current.copy(
                                                            nombre = customBoatName,
                                                            boteMaestroId = null,
                                                            submareal = 1
                                                        )
                                                        activeSearchRowIndex = null
                                                        boatSearchTerm = ""
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                enabled = boatSearchTerm.isNotBlank()
                                            ) {
                                                Text("Usar texto", maxLines = 1)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    activeSearchRowIndex = null
                                                    boatSearchTerm = ""
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Cerrar", maxLines = 1)
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = boatSearchTerm,
                                                onValueChange = { boatSearchTerm = it },
                                                modifier = Modifier.weight(1f),
                                                placeholder = { Text("Buscar bote, RPA o matrícula...") },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                                keyboardActions = KeyboardActions(
                                                    onSearch = {
                                                        if (filteredBotes.size == 1) {
                                                            val selected = filteredBotes.first()
                                                            val rowIndex = activeSearchRowIndex
                                                            if (rowIndex != null && rowIndex in botesList.indices) {
                                                                val current = botesList[rowIndex]
                                                                botesList[rowIndex] = current.copy(
                                                                    nombre = selected.nombre,
                                                                    boteMaestroId = selected.id,
                                                                    submareal = 1
                                                                )
                                                            }
                                                            activeSearchRowIndex = null
                                                            boatSearchTerm = ""
                                                        }
                                                    }
                                                )
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    val rowIndex = activeSearchRowIndex
                                                    val customBoatName = boatSearchTerm.trim()
                                                    if (rowIndex != null && rowIndex in botesList.indices && customBoatName.isNotBlank()) {
                                                        val current = botesList[rowIndex]
                                                        botesList[rowIndex] = current.copy(
                                                            nombre = customBoatName,
                                                            boteMaestroId = null,
                                                            submareal = 1
                                                        )
                                                        activeSearchRowIndex = null
                                                        boatSearchTerm = ""
                                                    }
                                                },
                                                enabled = boatSearchTerm.isNotBlank()
                                            ) {
                                                Text("Usar texto")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    activeSearchRowIndex = null
                                                    boatSearchTerm = ""
                                                }
                                            ) {
                                                Text("Cerrar")
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = buildString {
                                            append("Buscando para ")
                                            append(operationCaleta?.ifBlank { "la operación" } ?: "la operación")
                                            if (!operationRegionRom.isNullOrBlank()) {
                                                append(" · ")
                                                append(operationRegionRom)
                                            }
                                        },
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = if (isCompactLayout) 220.dp else 280.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSystemInDarkTheme()) Color(0xFF111B2B) else Color.White,
                                        border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                            if (filteredBotes.isEmpty()) {
                                                item {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(20.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "No se encontraron botes para \"${boatSearchTerm.ifBlank { "tu búsqueda" }}\". Se muestran maestros locales si no llegaron desde la nube.",
                                                            color = Color.Gray,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            } else {
                                                items(filteredBotes) { masterBoat ->
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                val rowIndex = activeSearchRowIndex
                                                                if (rowIndex != null && rowIndex in botesList.indices) {
                                                                    val current = botesList[rowIndex]
                                                                    botesList[rowIndex] = current.copy(
                                                                        nombre = masterBoat.nombre,
                                                                        boteMaestroId = masterBoat.id,
                                                                        submareal = 1
                                                                    )
                                                                }
                                                                activeSearchRowIndex = null
                                                                boatSearchTerm = ""
                                                            },
                                                        color = Color.Transparent
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = masterBoat.nombre.orEmpty(),
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 14.sp,
                                                                    color = if (isSystemInDarkTheme()) Color.White else Color(0xFF003366)
                                                                )
                                                                Text(
                                                                    text = "RPA ${masterBoat.nrpa.orEmpty()} · Caleta ${masterBoat.caleta.orEmpty()}",
                                                                    fontSize = 12.sp,
                                                                    color = Color.Gray
                                                                )
                                                            }
                                                            Surface(
                                                                shape = RoundedCornerShape(999.dp),
                                                                color = if (isSystemInDarkTheme()) Color(0xFF1A2740) else Color(0xFFEFF3F9)
                                                            ) {
                                                                Text(
                                                                    text = masterBoat.region_rom ?: masterBoat.region ?: "S/I",
                                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSystemInDarkTheme()) Color(0xFFBBDEFB) else Color(0xFF475569)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    HorizontalDivider(color = Color(0xFFF1F3F5))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showBoatSearchDialog && activeSearchRowIndex != null) {
                        BoteMaestroSearchDialog(
                            botes = candidateBotes.ifEmpty { availableBotesMaestros },
                            operationRegionRom = operationRegionRom,
                            operationCaleta = operationCaleta,
                            onSelect = { masterBoat ->
                                val rowIndex = activeSearchRowIndex
                                if (rowIndex != null && rowIndex in botesList.indices) {
                                    val current = botesList[rowIndex]
                                    botesList[rowIndex] = current.copy(
                                        nombre = masterBoat.nombre,
                                        boteMaestroId = masterBoat.id,
                                        submareal = 1
                                    )
                                }
                                activeSearchRowIndex = null
                                boatSearchTerm = ""
                                showBoatSearchDialog = false
                            },
                            onDismiss = {
                                activeSearchRowIndex = null
                                boatSearchTerm = ""
                                showBoatSearchDialog = false
                            }
                        )
                    }

                    if (isCompactLayout) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onAddRow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, Color(0xFF003366))
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color(0xFF003366))
                                Spacer(Modifier.width(8.dp))
                                Text("AGREGAR FILA", fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onValidationErrorChange(null)
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    border = BorderStroke(1.5.dp, Color.Gray)
                                ) {
                                    Text("CANCELAR", fontWeight = FontWeight.Bold, color = Color.Gray, maxLines = 1)
                                }

                                Button(
                                    onClick = onSave,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                                ) {
                                    Text("GUARDAR", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onAddRow,
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, Color(0xFF003366))
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color(0xFF003366))
                                Spacer(Modifier.width(8.dp))
                                Text("AGREGAR FILA", fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                            }

                            Spacer(Modifier.weight(1f))

                            OutlinedButton(
                                onClick = {
                                    onValidationErrorChange(null)
                                    onDismiss()
                                },
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                border = BorderStroke(1.5.dp, Color.Gray)
                            ) { Text("CANCELAR", fontWeight = FontWeight.Bold, color = Color.Gray) }

                            Button(
                                onClick = onSave,
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                            ) {
                                Text("GUARDAR CAMBIOS", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransectosDialog(
    show: Boolean,
    currentBote: OperacionBoteDto?,
    especiesMaestras: List<EspecieDto>,
    transectosList: SnapshotStateList<DensidadUnidadDto>,
    selectedSpeciesIds: List<Int>,
    muestreoBySpeciesId: Map<Int, Set<String>>,
    initialTab: String = "DENSIDAD",
    onDismiss: () -> Unit,
    onSaveDensity: (List<DensidadUnidadDto>) -> Unit,
    onUpdateLpSamples: (Int, List<LpSampleDto>) -> Unit,
    onRemoveLpSpecies: (Int) -> Unit,
) {
    if (!show) return

    val densityUnitTitle = densTipoPluralLabel(currentBote?.densTipo).uppercase()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = if (isSystemInDarkTheme()) Color(0xFF111B2B) else Color.White,
            tonalElevation = 8.dp
        ) {
            var subTab by remember(show, initialTab, currentBote?.id, currentBote?.zona, currentBote?.nombre) {
                mutableStateOf(initialTab)
            }
            var lpIngresoSpeciesId by remember { mutableStateOf<Int?>(null) }
            var pendingAreaFocusIndex by remember { mutableStateOf(if (transectosList.isNotEmpty()) 0 else null) }
            val especiesById = remember(especiesMaestras) { especiesMaestras.associateBy { it.id } }
            val transectListState = rememberLazyListState()

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF003366), Color(0xFF00509E))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        "EDITAR $densityUnitTitle — ${currentBote?.nombre?.uppercase()}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }

                Column(modifier = Modifier.padding(20.dp).weight(1f)) {
                    TabRow(selectedTabIndex = if (subTab == "DENSIDAD") 0 else 1) {
                        Tab(
                            selected = subTab == "DENSIDAD",
                            onClick = { subTab = "DENSIDAD" },
                            text = { Text("Densidad", fontWeight = FontWeight.Black) }
                        )
                        Tab(
                            selected = subTab == "LP",
                            onClick = { subTab = "LP" },
                            text = { Text("Peso-Longitud", fontWeight = FontWeight.Black) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (subTab == "LP") {
                        val lpMap = currentBote?.lpMuestras ?: emptyMap()
                        val selectedLpIds = remember(selectedSpeciesIds, muestreoBySpeciesId, especiesById) {
                            selectedSpeciesIds.filter { sid -> muestreoBySpeciesId[sid]?.contains("L-P") != false }
                                .distinct()
                                .sortedWith(compareBy({ especiesById[it]?.com ?: "ZZZ" }, { it }))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Especies (L-P)", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF003366))
                            Spacer(Modifier.weight(1f))
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = (selectedLpIds.sumOf { sid -> ((lpMap[sid.toString()] ?: emptyMap())["LP"] ?: emptyList()).size }).toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                                    Text(text = "Muestras", fontSize = 8.sp, color = Color(0xFF2E7D32))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.White,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF1F3F5))
                                        .padding(vertical = 10.dp, horizontal = 12.dp)
                                ) {
                                    Text("ESPECIE", modifier = Modifier.weight(1.6f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                    Text("MUESTRAS", modifier = Modifier.weight(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                    Text("TIPO", modifier = Modifier.weight(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Spacer(modifier = Modifier.width(88.dp))
                                }

                                if (selectedLpIds.isEmpty()) {
                                    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                                        Text("Selecciona especies en “Especies a muestrear” y marca L-P para ingresar Peso-Longitud.", color = Color.Gray, textAlign = TextAlign.Center)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(selectedLpIds) { sid ->
                                            val sp = especiesById[sid]
                                            val spName = sp?.com ?: "ID$sid"
                                            val buckets = lpMap[sid.toString()] ?: emptyMap()
                                            val lpList = buckets["LP"] ?: emptyList()
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1.6f)) {
                                                    Text(spName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                                                    val sci = sp?.sci
                                                    if (!sci.isNullOrBlank()) {
                                                        Text(sci, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                                    }
                                                }
                                                Text(lpList.size.toString(), modifier = Modifier.weight(0.6f), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF00897B), textAlign = TextAlign.Center)
                                                Text("L-P", modifier = Modifier.weight(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF00897B), textAlign = TextAlign.Center)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Button(
                                                    onClick = { lpIngresoSpeciesId = sid },
                                                    modifier = Modifier.width(88.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                                                ) { Text("Ingresar", fontWeight = FontWeight.Black, fontSize = 12.sp) }
                                            }
                                            HorizontalDivider(color = Color(0xFFF1F3F5))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val isQuadrantMode = currentBote?.densTipo.equals("Cuadrante", true)
                        val quadrants = remember(transectosList) {
                            transectosList.filter { it.tipo.equals("cuadrante", true) }.sortedBy { it.num ?: 0 }
                        }
                        var quadrantCountText by remember(show, currentBote?.id) { mutableStateOf("30") }
                        var quadrantAreaText by remember(show, currentBote?.id) { mutableStateOf("0.25") }
                        var quadrantSustrato by remember(show, currentBote?.id) { mutableStateOf("") }
                        var quadrantSpeciesId by remember(show, currentBote?.id) { mutableStateOf<Int?>(null) }
                        var showQuadrantSpeciesPicker by remember(show, currentBote?.id) { mutableStateOf(false) }

                        if (isQuadrantMode) {
                            val selectedQuadrantSpecies = quadrantSpeciesId?.let { especiesById[it] }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD).copy(alpha = 0.8f)),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, Color(0xFFBBDEFB))
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = Color(0xFF1565C0), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        "Crea cuadrantes indicando cantidad, area, sustrato y especie, igual que en la web.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF1565C0),
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFF8F9FA),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("CANTIDAD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                            BasicTextField(
                                                value = quadrantCountText,
                                                onValueChange = { quadrantCountText = it.filter { ch -> ch.isDigit() } },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 6.dp)
                                                    .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                    .background(Color.White, RoundedCornerShape(10.dp))
                                                    .padding(10.dp),
                                                textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("AREA CUADRANTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                            Box {
                                                var expandedArea by remember { mutableStateOf(false) }
                                                OutlinedButton(
                                                    onClick = { expandedArea = true },
                                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("$quadrantAreaText m²", color = Color(0xFF003366), modifier = Modifier.weight(1f))
                                                    Icon(Icons.Default.ArrowDropDown, null)
                                                }
                                                DropdownMenu(expanded = expandedArea, onDismissRequest = { expandedArea = false }) {
                                                    listOf("1", "0.25", "0.0625").forEach { option ->
                                                        DropdownMenuItem(
                                                            text = { Text("$option m²") },
                                                            onClick = {
                                                                quadrantAreaText = option
                                                                expandedArea = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("TIPO SUSTRATO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                            BasicTextField(
                                                value = quadrantSustrato,
                                                onValueChange = { quadrantSustrato = it },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 6.dp)
                                                    .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                    .background(Color.White, RoundedCornerShape(10.dp))
                                                    .padding(10.dp),
                                                textStyle = TextStyle(fontSize = 13.sp),
                                                singleLine = true
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("ESPECIE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                            OutlinedButton(
                                                onClick = { showQuadrantSpeciesPicker = true },
                                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(
                                                    selectedQuadrantSpecies?.let { "${it.com} - ${it.sci ?: ""}".trim() } ?: "Seleccionar especie",
                                                    color = Color(0xFF003366),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = onDismiss,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Cancelar")
                                        }
                                        Button(
                                            onClick = {
                                                val quantity = quadrantCountText.toIntOrNull()?.coerceAtLeast(1) ?: return@Button
                                                val speciesId = quadrantSpeciesId ?: return@Button
                                                val nextNum = (transectosList.mapNotNull { it.num }.maxOrNull() ?: 0) + 1
                                                repeat(quantity) { offset ->
                                                    transectosList.add(
                                                        DensidadUnidadDto(
                                                            num = nextNum + offset,
                                                            tipo = "cuadrante",
                                                            area = quadrantAreaText.toDoubleOrNull() ?: 0.25,
                                                            sustrato = quadrantSustrato.ifBlank { null },
                                                            especieId = speciesId,
                                                            counts = mapOf(speciesId.toString() to 0)
                                                        )
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = quadrantSpeciesId != null && (quadrantCountText.toIntOrNull() ?: 0) > 0,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                                        ) {
                                            Text("Crear", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Surface(
                                modifier = Modifier.weight(1f),
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                            ) {
                                if (quadrants.isEmpty()) {
                                    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                                        Text("Aun no hay cuadrantes registrados.", color = Color.Gray)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        itemsIndexed(quadrants, key = { _, q -> q.num ?: 0 }) { _, q ->
                                            val speciesId = q.especieId ?: q.counts?.keys?.firstOrNull()?.toIntOrNull()
                                            val speciesName = speciesId?.let { especiesById[it]?.com } ?: "Sin especie"
                                            val countKey = speciesId?.toString()
                                            val countValue = countKey?.let { q.counts?.get(it) } ?: 0
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                                border = BorderStroke(1.dp, Color(0xFFE8EAED)),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Cuadrante ${q.num ?: 0}", fontWeight = FontWeight.Black, color = Color(0xFF003366), modifier = Modifier.weight(1f))
                                                        Text(speciesName, fontSize = 12.sp, color = Color(0xFF00897B), fontWeight = FontWeight.Bold)
                                                        Spacer(Modifier.width(8.dp))
                                                        IconButton(
                                                            onClick = {
                                                                val idx = transectosList.indexOfFirst { it.num == q.num && it.tipo.equals("cuadrante", true) }
                                                                if (idx >= 0) transectosList.removeAt(idx)
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("CANTIDAD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                            BasicTextField(
                                                                value = countValue.toString(),
                                                                onValueChange = { value ->
                                                                    val idx = transectosList.indexOfFirst { it.num == q.num && it.tipo.equals("cuadrante", true) }
                                                                    if (idx >= 0 && countKey != null) {
                                                                        val newCounts = (transectosList[idx].counts ?: emptyMap()).toMutableMap()
                                                                        value.toIntOrNull()?.let { newCounts[countKey] = it } ?: newCounts.remove(countKey)
                                                                        transectosList[idx] = transectosList[idx].copy(counts = newCounts)
                                                                    }
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(top = 6.dp)
                                                                    .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                                    .background(Color.White, RoundedCornerShape(10.dp))
                                                                    .padding(10.dp),
                                                                textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Black, color = Color(0xFF00897B)),
                                                                singleLine = true,
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("DENSIDAD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                            Surface(
                                                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                                color = Color(0xFFF1F3F5),
                                                                shape = RoundedCornerShape(10.dp)
                                                            ) {
                                                                Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                                    Text(densityValueText(countValue, q.area), fontWeight = FontWeight.Black, color = Color.DarkGray)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("AREA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                            BasicTextField(
                                                                value = (q.area ?: 0.25).toString(),
                                                                onValueChange = { value ->
                                                                    val idx = transectosList.indexOfFirst { it.num == q.num && it.tipo.equals("cuadrante", true) }
                                                                    if (idx >= 0) transectosList[idx] = transectosList[idx].copy(area = value.toDoubleOrNull())
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(top = 6.dp)
                                                                    .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                                    .background(Color.White, RoundedCornerShape(10.dp))
                                                                    .padding(10.dp),
                                                                textStyle = TextStyle(fontSize = 13.sp),
                                                                singleLine = true,
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("SUSTRATO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                            BasicTextField(
                                                                value = q.sustrato.orEmpty(),
                                                                onValueChange = { value ->
                                                                    val idx = transectosList.indexOfFirst { it.num == q.num && it.tipo.equals("cuadrante", true) }
                                                                    if (idx >= 0) transectosList[idx] = transectosList[idx].copy(sustrato = value)
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(top = 6.dp)
                                                                    .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                                    .background(Color.White, RoundedCornerShape(10.dp))
                                                                    .padding(10.dp),
                                                                textStyle = TextStyle(fontSize = 13.sp),
                                                                singleLine = true
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (showQuadrantSpeciesPicker) {
                                SpeciesPickerDialog(
                                    species = especiesMaestras.sortedBy { it.com },
                                    currentSelectedIds = quadrantSpeciesId?.let { setOf(it) } ?: emptySet(),
                                    onDismiss = { showQuadrantSpeciesPicker = false },
                                    onApply = { ids ->
                                        quadrantSpeciesId = ids.firstOrNull()
                                        showQuadrantSpeciesPicker = false
                                    }
                                )
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD).copy(alpha = 0.8f)),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, Color(0xFFBBDEFB))
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = Color(0xFF1565C0), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        "Completa el primer transecto y usa \"Replicar\" para copiar la configuración al resto.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF1565C0),
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (transectosList.isNotEmpty()) {
                                            val first = transectosList[0]
                                            for (i in 1 until transectosList.size) {
                                                transectosList[i] = transectosList[i].copy(
                                                    area = first.area,
                                                    sustrato = first.sustrato,
                                                    cubierta = first.cubierta
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(26.dp),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    border = BorderStroke(1.5.dp, Color(0xFF003366))
                                ) {
                                    Text("REPLICAR FILA 1", fontSize = 13.sp, color = Color(0xFF003366), fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val nextIndex = transectosList.size
                                        transectosList.add(
                                            DensidadUnidadDto(
                                                num = transectosList.size + 1,
                                                tipo = currentBote?.densTipo ?: "Transecto",
                                                area = 120.0
                                            )
                                        )
                                        pendingAreaFocusIndex = nextIndex
                                    },
                                    shape = RoundedCornerShape(26.dp),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366))
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("AGREGAR FILA", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            LaunchedEffect(pendingAreaFocusIndex) {
                                pendingAreaFocusIndex?.let { index ->
                                    if (index < transectosList.size) {
                                        transectListState.animateScrollToItem(index)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, Color(0xFFF1F3F5), RoundedCornerShape(12.dp))
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = transectListState
                                ) {
                                    itemsIndexed(items = transectosList.toList()) { index: Int, t: DensidadUnidadDto ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFF1F3F5)),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            val focusManager = LocalFocusManager.current
                                            val areaFocusRequester = remember(index) { FocusRequester() }
                                            val sustratoFocusRequester = remember(index) { FocusRequester() }
                                            val densidadSpeciesIds = remember(selectedSpeciesIds, muestreoBySpeciesId) {
                                                selectedSpeciesIds.filter { sid -> muestreoBySpeciesId[sid]?.contains("DENSIDAD") != false }
                                            }
                                            val speciesFocusRequesters = remember(index, densidadSpeciesIds) {
                                                List(densidadSpeciesIds.size) { FocusRequester() }
                                            }

                                            LaunchedEffect(pendingAreaFocusIndex, subTab, index) {
                                                if (subTab == "DENSIDAD" && pendingAreaFocusIndex == index) {
                                                    areaFocusRequester.requestFocus()
                                                    pendingAreaFocusIndex = null
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "${densTipoLabel(t.tipo)} ${t.num ?: (index + 1)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF003366),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = { transectosList.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            Spacer(Modifier.height(12.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("ÁREA (M²)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                    BasicTextField(
                                                        value = (t.area ?: 120.0).toString(),
                                                        onValueChange = { transectosList[index] = t.copy(area = it.toDoubleOrNull()) },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .focusRequester(areaFocusRequester)
                                                            .padding(top = 6.dp)
                                                            .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                            .background(Color.White, RoundedCornerShape(10.dp))
                                                            .padding(10.dp),
                                                        textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Black),
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Number,
                                                            imeAction = ImeAction.Next
                                                        ),
                                                        keyboardActions = KeyboardActions(
                                                            onNext = { sustratoFocusRequester.requestFocus() }
                                                        )
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1.4f)) {
                                                    Text("SUSTRATO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                    BasicTextField(
                                                        value = t.sustrato.orEmpty(),
                                                        onValueChange = { transectosList[index] = t.copy(sustrato = it) },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .focusRequester(sustratoFocusRequester)
                                                            .padding(top = 6.dp)
                                                            .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                            .background(Color.White, RoundedCornerShape(10.dp))
                                                            .padding(10.dp),
                                                        textStyle = TextStyle(fontSize = 13.sp),
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                                        keyboardActions = KeyboardActions(
                                                            onNext = {
                                                                if (speciesFocusRequesters.isNotEmpty()) {
                                                                    speciesFocusRequesters.first().requestFocus()
                                                                } else {
                                                                    val nextIndex = index + 1
                                                                    if (nextIndex < transectosList.size) {
                                                                        pendingAreaFocusIndex = nextIndex
                                                                    } else {
                                                                        focusManager.clearFocus()
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(12.dp))

                                            Text("FAUNA (FILAS)", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                                            Spacer(Modifier.height(8.dp))

                                            val currentCounts = t.counts ?: emptyMap<String, Int>()
                                            if (densidadSpeciesIds.isEmpty()) {
                                                Text("No hay fauna seleccionada para densidad. Vuelve al paso de especies y activa DENSIDAD.", fontSize = 12.sp, color = Color.Gray)
                                            } else {
                                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Spacer(Modifier.weight(1f))
                                                    Text("N° IND", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.width(88.dp), textAlign = TextAlign.Center)
                                                    Spacer(Modifier.width(10.dp))
                                                    Text("DENS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
                                                }
                                                densidadSpeciesIds.forEachIndexed { speciesIndex, sid ->
                                                    val esp = especiesMaestras.find { it.id == sid }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            (esp?.com ?: "ID $sid").uppercase(),
                                                            modifier = Modifier.weight(1f),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF003366),
                                                            maxLines = 1
                                                        )
                                                        val area = (t.area ?: 120.0).takeIf { it > 0.0 } ?: 120.0
                                                        val countVal = currentCounts[sid.toString()] ?: 0
                                                        BasicTextField(
                                                            value = currentCounts[sid.toString()]?.toString().orEmpty(),
                                                            onValueChange = { newVal ->
                                                                val newCounts = currentCounts.toMutableMap()
                                                                newVal.toIntOrNull()?.let { newCounts[sid.toString()] = it } ?: newCounts.remove(sid.toString())
                                                                transectosList[index] = t.copy(counts = newCounts)
                                                            },
                                                            modifier = Modifier
                                                                .width(88.dp)
                                                                .focusRequester(speciesFocusRequesters[speciesIndex])
                                                                .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                                .background(Color.White, RoundedCornerShape(10.dp))
                                                                .padding(10.dp),
                                                            textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Black, color = Color(0xFF00897B)),
                                                            singleLine = true,
                                                            keyboardOptions = KeyboardOptions(
                                                                keyboardType = KeyboardType.Number,
                                                                imeAction = if (speciesIndex == speciesFocusRequesters.lastIndex) ImeAction.Done else ImeAction.Next
                                                            ),
                                                            keyboardActions = KeyboardActions(
                                                                onNext = {
                                                                    speciesFocusRequesters.getOrNull(speciesIndex + 1)?.requestFocus()
                                                                        ?: run {
                                                                            val nextIndex = index + 1
                                                                            if (nextIndex < transectosList.size) {
                                                                                pendingAreaFocusIndex = nextIndex
                                                                            } else {
                                                                                focusManager.clearFocus()
                                                                            }
                                                                        }
                                                                },
                                                                onDone = {
                                                                    val nextIndex = index + 1
                                                                    if (nextIndex < transectosList.size) {
                                                                        pendingAreaFocusIndex = nextIndex
                                                                    } else {
                                                                        focusManager.clearFocus()
                                                                    }
                                                                }
                                                            )
                                                        )
                                                        Spacer(Modifier.width(10.dp))
                                                        val densText = runCatching { String.format("%.4f", countVal.toDouble() / area) }.getOrNull() ?: "0.0000"
                                                        Surface(color = Color(0xFFF1F3F5), shape = RoundedCornerShape(10.dp)) {
                                                            Box(modifier = Modifier.width(72.dp).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                                Text(densText, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                                            }
                                                        }
                                                    }
                                                    Spacer(Modifier.height(6.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.5.dp, Color.Gray)
                    ) {
                        Text("CANCELAR", fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            if (subTab == "LP") {
                                onDismiss()
                            } else {
                                onSaveDensity(transectosList.map { t -> t.copy(area = t.area ?: 120.0) })
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                    ) {
                        Text(if (subTab == "LP") "CERRAR" else "GUARDAR DATOS", fontWeight = FontWeight.Bold)
                    }
                }
            }

            val sid = lpIngresoSpeciesId
            if (sid != null && currentBote != null) {
                LpIngresoDialog(
                    speciesId = sid,
                    speciesName = (especiesById[sid]?.com ?: "ID$sid"),
                    currentSamples = ((currentBote.lpMuestras ?: emptyMap())[sid.toString()] ?: emptyMap())["LP"] ?: emptyList(),
                    onDismiss = { lpIngresoSpeciesId = null },
                    onUpdateSamples = { nextSamples ->
                        onUpdateLpSamples(sid, nextSamples)
                    },
                    onRemoveSpecies = {
                        onRemoveLpSpecies(sid)
                        lpIngresoSpeciesId = null
                    }
                )
            }
        }
    }
}

@Composable
private fun NuevaOperacionDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    regiones: List<RegionDto>,
    regionLabelById: Map<Int, String>,
    selectedRegionId: Int?,
    onRegionSelected: (Int?) -> Unit,
    sectoresAmerbApi: List<SectorAmerbDto>,
    sectorAmerbInput: String,
    onSectorAmerbInputChange: (String) -> Unit,
    selectedSectorAmerb: SectorAmerb?,
    onSectorAmerbSelected: (SectorAmerb?) -> Unit,
    numSeguimiento: String,
    onNumSeguimientoChange: (String) -> Unit,
    fechaInicio: String,
    onFechaInicioClick: () -> Unit,
    fechaFin: String,
    onFechaFinClick: () -> Unit,
    caletasApi: List<CaletaDto>,
    caletaInput: String,
    onCaletaInputChange: (String) -> Unit,
    onCaletaSelected: (String) -> Unit,
    tipoOrg: String,
    onTipoOrgChange: (String) -> Unit,
    opasApi: List<OpaDto>,
    opaInput: String,
    onOpaInputChange: (String) -> Unit,
    onOpaSelected: (Opa?) -> Unit,
    validationError: String?,
    onCreateClick: () -> Unit,
) {
    if (!show) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = if (isSystemInDarkTheme()) Color(0xFF111B2B) else Color.White,
            tonalElevation = 12.dp
        ) {
            val isDark = isSystemInDarkTheme()
            val comboBg = if (isDark) Color(0xFF0D47A1) else Color.White
            val comboText = if (isDark) Color.White else Color.Black
            val comboBorder = if (isDark) Color(0xFF1976D2) else Color(0xFFF1F3F5)
            val labelColor = if (isDark) Color(0xFFBBDEFB) else Color.Gray
            val accentColor = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366)
            var expandedReg by remember { mutableStateOf(false) }
            var expandedTipo by remember { mutableStateOf(false) }

            val filteredSectores = sectoresAmerbApi.filter { it.region == selectedRegionId }
            val filteredCaletas = remember(caletasApi, selectedRegionId) {
                if (caletasApi.isEmpty()) {
                    emptyList()
                } else {
                    val allCaletas = caletasApi
                        .filter { it.nombre.isNotBlank() }
                        .distinctBy { normalizarTextoBusqueda(it.nombre) }
                        .sortedBy { normalizarTextoBusqueda(it.nombre) }

                    val byRegion = if (selectedRegionId != null) {
                        allCaletas.filter { caleta ->
                            caleta.region == selectedRegionId
                        }
                    } else {
                        allCaletas
                    }

                    when {
                        byRegion.isNotEmpty() -> byRegion
                        else -> allCaletas
                    }
                }
            }
            val filteredOpas = opasApi.filter { it.region == selectedRegionId }
            val hasMasterCatalogs = regiones.isNotEmpty() &&
                sectoresAmerbApi.isNotEmpty() &&
                caletasApi.isNotEmpty() &&
                opasApi.isNotEmpty()
            val selectedSectorLabel = selectedSectorAmerb?.nombre?.takeIf { it.isNotBlank() } ?: sectorAmerbInput.trim()
            val selectedCaletaLabel = caletaInput.trim()
            val selectedOpaLabel = opaInput.trim()

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF003366), Color(0xFF00509E))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        "NUEVA OPERACIÓN",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text("UBICACIÓN Y SECTOR", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
                    Spacer(Modifier.height(12.dp))

                    if (!hasMasterCatalogs) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFCC80))
                        ) {
                            Text(
                                text = "Los datos maestros aun no estan completos en el dispositivo. Sincroniza primero para trabajar con regiones, sectores, caletas y organizaciones desde Room.",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 12.sp,
                                color = Color(0xFF8A4B00)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Text("REGIÓN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                    Box {
                        OutlinedButton(
                            onClick = { expandedReg = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, comboBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = comboBg)
                        ) {
                            Text(
                                selectedRegionId?.let { regionLabelById[it] } ?: "Seleccionar Región",
                                color = comboText,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start
                            )
                            Icon(Icons.Default.ArrowDropDown, null, tint = labelColor)
                        }
                        DropdownMenu(
                            expanded = expandedReg,
                            onDismissRequest = { expandedReg = false },
                            modifier = Modifier.background(comboBg)
                        ) {
                            regiones.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(listOfNotNull(r.rom, r.nom).joinToString(" — "), color = comboText) },
                                    onClick = { onRegionSelected(r.id); expandedReg = false }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("SECTOR AMERB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                    SearchableDropdown(
                        value = sectorAmerbInput,
                        onValueChange = onSectorAmerbInputChange,
                        placeholder = if (filteredSectores.isEmpty()) "Sin sectores en esta región" else "Buscar sector...",
                        items = filteredSectores,
                        itemLabel = { it.nombre },
                        onItemSelected = { onSectorAmerbSelected(SectorAmerb(it.id, it.nombre, it.region ?: 0)) },
                        showAddNew = true,
                        onAddNewValue = {
                            onSectorAmerbInputChange(it)
                            onSectorAmerbSelected(null)
                        }
                    )

                    if (selectedSectorLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sector seleccionado: $selectedSectorLabel",
                            fontSize = 11.sp,
                            color = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("CALETA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                    SearchableDropdown(
                        value = caletaInput,
                        onValueChange = onCaletaInputChange,
                        placeholder = if (filteredCaletas.isEmpty()) "Sin caletas disponibles" else "Buscar caleta...",
                        items = filteredCaletas,
                        itemLabel = { it.nombre },
                        onItemSelected = { onCaletaSelected(it.nombre) },
                        showAddNew = true,
                        onAddNewValue = { onCaletaSelected(it) }
                    )

                    if (selectedCaletaLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Caleta seleccionada: $selectedCaletaLabel",
                            fontSize = 11.sp,
                            color = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = comboBorder)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("IDENTIFICACIÓN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("N° SEGUIMIENTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                            OutlinedTextField(
                                value = numSeguimiento,
                                onValueChange = {
                                    if (it.all { char -> char.isDigit() }) onNumSeguimientoChange(it)
                                },
                                modifier = Modifier.fillMaxWidth().background(comboBg, RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { defaultKeyboardAction(ImeAction.Done) }
                                ),
                                textStyle = TextStyle(color = comboText, fontSize = 14.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = comboBorder,
                                    focusedBorderColor = accentColor,
                                    unfocusedTextColor = comboText,
                                    focusedTextColor = comboText,
                                    unfocusedContainerColor = comboBg,
                                    focusedContainerColor = comboBg
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("FECHA INICIO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                            OutlinedTextField(
                                value = fechaInicio,
                                onValueChange = { },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().background(comboBg, RoundedCornerShape(12.dp)).clickable { onFechaInicioClick() },
                                shape = RoundedCornerShape(12.dp),
                                textStyle = TextStyle(color = comboText, fontSize = 14.sp),
                                trailingIcon = { Icon(Icons.Default.CalendarToday, null, tint = accentColor) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = comboBorder,
                                    focusedBorderColor = accentColor,
                                    disabledBorderColor = comboBorder,
                                    unfocusedTextColor = comboText,
                                    focusedTextColor = comboText,
                                    disabledTextColor = comboText,
                                    unfocusedContainerColor = comboBg,
                                    focusedContainerColor = comboBg,
                                    disabledContainerColor = comboBg
                                ),
                                interactionSource = remember { MutableInteractionSource() }
                                    .also { interactionSource ->
                                        LaunchedEffect(interactionSource) {
                                            interactionSource.interactions.collect {
                                                if (it is PressInteraction.Release) onFechaInicioClick()
                                            }
                                        }
                                    }
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("FECHA TÉRMINO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                            OutlinedTextField(
                                value = fechaFin,
                                onValueChange = { },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().background(comboBg, RoundedCornerShape(12.dp)).clickable { onFechaFinClick() },
                                shape = RoundedCornerShape(12.dp),
                                textStyle = TextStyle(color = comboText, fontSize = 14.sp),
                                trailingIcon = { Icon(Icons.Default.CalendarToday, null, tint = accentColor) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = comboBorder,
                                    focusedBorderColor = accentColor,
                                    disabledBorderColor = comboBorder,
                                    unfocusedTextColor = comboText,
                                    focusedTextColor = comboText,
                                    disabledTextColor = comboText,
                                    unfocusedContainerColor = comboBg,
                                    focusedContainerColor = comboBg,
                                    disabledContainerColor = comboBg
                                ),
                                interactionSource = remember { MutableInteractionSource() }
                                    .also { interactionSource ->
                                        LaunchedEffect(interactionSource) {
                                            interactionSource.interactions.collect {
                                                if (it is PressInteraction.Release) onFechaFinClick()
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("ORGANIZACIÓN (OPA)", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
                    Spacer(Modifier.height(12.dp))

                    Text("TIPO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                    Box {
                        OutlinedButton(
                            onClick = { expandedTipo = true },
                            modifier = Modifier.fillMaxWidth().background(comboBg, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, comboBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = comboBg)
                        ) {
                            Text(tipoOrg, color = comboText, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            Icon(Icons.Default.ArrowDropDown, null, tint = labelColor)
                        }
                        DropdownMenu(
                            expanded = expandedTipo,
                            onDismissRequest = { expandedTipo = false },
                            modifier = Modifier.background(comboBg)
                        ) {
                            TIPOS_ORGANIZACION_DEFAULT.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t, color = comboText) },
                                    onClick = { onTipoOrgChange(t); expandedTipo = false }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("NOMBRE OPA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                    SearchableDropdown(
                        value = opaInput,
                        onValueChange = onOpaInputChange,
                        placeholder = if (filteredOpas.isEmpty()) "Sin OPAs en esta región" else "Buscar OPA...",
                        items = filteredOpas,
                        itemLabel = { it.nombre },
                        onItemSelected = { onOpaSelected(Opa(it.id, it.nombre, it.nombre, it.region ?: 0)) },
                        showAddNew = true,
                        onAddNewValue = {
                            onOpaInputChange(it)
                            onOpaSelected(null)
                        }
                    )

                    if (selectedOpaLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Organización seleccionada: $selectedOpaLabel",
                            fontSize = 11.sp,
                            color = accentColor
                        )
                    }

                    if (validationError != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = validationError,
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.5.dp, Color(0.6f, 0.6f, 0.6f))
                    ) {
                        Text("CANCELAR", fontWeight = FontWeight.Bold, color = if (isDark) Color.LightGray else Color.Gray)
                    }

                    Button(
                        onClick = onCreateClick,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                    ) { Text("CREAR", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OperacionesScreen(navController: NavController, userId: Int) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var expandedOpId by remember { mutableStateOf<String?>(null) }
    var lastExpandClickAt by remember { mutableLongStateOf(0L) }
    var showOperacionDetalleDialog by remember { mutableStateOf(false) }
    var currentOperacionDetalle by remember { mutableStateOf<OperacionDto?>(null) }
    var currentOperacionDetalleBoteKey by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showBotesDialog by remember { mutableStateOf(false) }
    var showBoteResumenDialog by remember { mutableStateOf(false) }
    var showSpeciesDialog by remember { mutableStateOf(false) }
    var showTransectDialog by remember { mutableStateOf(false) }   
    var currentOpForBotes by remember { mutableStateOf<OperacionDto?>(null) }
    var currentBoteForData by remember { mutableStateOf<OperacionBoteDto?>(null) }
    var pendingDataTab by remember { mutableStateOf("DENSIDAD") }
    val botesList = remember { mutableStateListOf<OperacionBoteDto>() }
    val selectedSpeciesIds = remember { mutableStateListOf<Int>() }
    val muestreoBySpeciesId = remember { mutableStateMapOf<Int, Set<String>>() }
    val transectosList = remember { mutableStateListOf<DensidadUnidadDto>() }
    var especiesMaestras by remember { mutableStateOf(DataManager.especiesMaestras.toList()) }
    var botesMaestros by remember { mutableStateOf(DataManager.botesMaestros.toList()) }
    var regiones by remember { mutableStateOf(DataManager.regiones.toList()) }
    var regionLabelById by remember(regiones) { mutableStateOf(regiones.associate { r -> r.id to listOfNotNull(r.rom, r.nom).joinToString(" — ").ifBlank { "Región ${r.id}" } }) }
    // Listas dinámicas desde API
    var sectoresAmerbApi by remember { mutableStateOf(DataManager.sectoresAmerb.toList()) }
    var caletasApi by remember { mutableStateOf(DataManager.caletas.toList()) }
    var opasApi by remember { mutableStateOf(DataManager.opas.toList()) }
    var selectedRegionId by remember { mutableStateOf<Int?>(1) }
    var numSeguimiento by remember { mutableStateOf("") }
    var sectorAmerbInput by remember { mutableStateOf("") }
    var selectedSectorAmerb by remember { mutableStateOf<SectorAmerb?>(null) }
    var caletaInput by remember { mutableStateOf("") }
    var selectedCaleta by remember { mutableStateOf<String?>(null) }
    var tipoOrg by remember { mutableStateOf("STI") }
    var opaInput by remember { mutableStateOf("") }
    var selectedOpa by remember { mutableStateOf<Opa?>(null) }
    var fechaInicio by remember { mutableStateOf("") }
    var fechaFin by remember { mutableStateOf("") }
    var showInicioDatePicker by remember { mutableStateOf(false) }
    var showFinDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var textoBusqueda by remember { mutableStateOf("") }
    var filtroSector by remember { mutableStateOf("") }
    var filtroMes by remember { mutableStateOf("") }

    val nomenclatura = remember(selectedRegionId, numSeguimiento) {
        val year = LocalDate.now().year
        "OP-$year-${numSeguimiento.ifEmpty { "XXX" }}"
    }
    val sectorAmerbNombreActual = selectedSectorAmerb?.nombre?.takeIf { it.isNotBlank() } ?: sectorAmerbInput.trim()
    val caletaActual = selectedCaleta?.takeIf { it.isNotBlank() } ?: caletaInput.trim()
    val organizacionActual = selectedOpa?.nombre?.takeIf { it.isNotBlank() } ?: opaInput.trim()

    val operacionesUi by remember {
        derivedStateOf {
            val merged = linkedMapOf<String, OperacionItem>()
            (DataManager.operacionesBd.map { OperacionItem(it, OperacionSource.BD) } +
                DataManager.operacionesLc.map { OperacionItem(it, OperacionSource.LC) })
                .forEach { item ->
                    merged[item.op.id] = merged[item.op.id]
                        ?.let { existing -> mergeOperacionItemForUi(existing, item) }
                        ?: item
                }
            merged.values.toList()
        }
    }
    val sectoresDisponibles by remember(operacionesUi) {
        derivedStateOf {
            operacionesUi.mapNotNull { item ->
                item.op.sectorAmerb?.takeIf { it.isNotBlank() } ?: item.op.sector.takeIf { it.isNotBlank() }
            }.distinct().sorted()
        }
    }
    val mesesDisponibles by remember(operacionesUi) {
        derivedStateOf {
            operacionesUi.flatMap { item ->
                listOfNotNull(item.op.fechaInicio, item.op.fechaFin)
                    .mapNotNull { fecha -> fecha.takeIf { it.length >= 7 && it[4] == '-' }?.substring(0, 7) }
            }.distinct().sortedDescending()
        }
    }
    val operacionesFiltradas by remember(operacionesUi, textoBusqueda, filtroSector, filtroMes) {
        derivedStateOf {
            operacionesUi.filter { item ->
                val op = item.op
                val sectorOp = op.sectorAmerb?.takeIf { it.isNotBlank() } ?: op.sector
                val coincideSector = filtroSector.isBlank() || sectorOp == filtroSector
                coincideSector &&
                    operacionCoincideConMes(op, filtroMes) &&
                    operacionCoincideConBusqueda(op, textoBusqueda)
            }
        }
    }
    val aplicarCatalogoActual: () -> Unit = {
        val catalogo = DataManager.getCatalogSnapshot()
        regiones = catalogo.regiones
        botesMaestros = catalogo.botesMaestros
        especiesMaestras = catalogo.especiesMaestras
        sectoresAmerbApi = catalogo.sectoresAmerb
        caletasApi = catalogo.caletas
        opasApi = catalogo.opas
        regionLabelById = regiones.associate { r -> r.id to listOfNotNull(r.rom, r.nom).joinToString(" — ").ifBlank { "Región ${r.id}" } }
        if (selectedRegionId == null) {
            selectedRegionId = regiones.firstOrNull()?.id
        }
    }
    val syncAndRefreshCatalogs: suspend () -> Unit = syncRefresh@{
        if (!AppState.isEffectivelyOnline()) return@syncRefresh
        isLoading = true
        DataManager.syncAllFromServer(ctx)
        aplicarCatalogoActual()
        isLoading = false
    }

    // Carga inicial de datos
    LaunchedEffect(isLoading, userId) {
        if (!isLoading) return@LaunchedEffect
        if (AppState.forceOffline || AppState.authToken.isNullOrBlank()) {
            aplicarCatalogoActual()
            isLoading = false
            return@LaunchedEffect
        }

        runCatching {
            DataManager.syncAllFromServer(ctx)
        }

        aplicarCatalogoActual()
        isLoading = false
    }

    if (showBoteResumenDialog) {
        BoteDataResumenDialog(
            bote = currentBoteForData,
            onDismiss = { showBoteResumenDialog = false },
            onOpenDensity = {
                showBoteResumenDialog = false
                pendingDataTab = "DENSIDAD"
                val hasDensitySpecies = selectedSpeciesIds.any { sid ->
                    muestreoBySpeciesId[sid]?.contains("DENSIDAD") != false
                }
                if (!hasDensitySpecies) {
                    showSpeciesDialog = true
                } else {
                    if (transectosList.isEmpty()) {
                        transectosList.add(
                            DensidadUnidadDto(
                                num = 1,
                                tipo = currentBoteForData?.densTipo ?: "Transecto",
                                area = 120.0
                            )
                        )
                    }
                    showTransectDialog = true
                }
            },
            onOpenLp = {
                showBoteResumenDialog = false
                pendingDataTab = "LP"
                val hasLpSpecies = selectedSpeciesIds.any { sid ->
                    muestreoBySpeciesId[sid]?.contains("L-P") != false
                }
                if (!hasLpSpecies) {
                    showSpeciesDialog = true
                } else {
                    showTransectDialog = true
                }
            },
            onOpenSpecies = {
                showBoteResumenDialog = false
                showSpeciesDialog = true
            }
        )
    }

    // --- DIALOGO DE SELECCIÓN DE ESPECIES (L-P) ---
    if (showSpeciesDialog) {
        Dialog(
            onDismissRequest = { showSpeciesDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (isSystemInDarkTheme()) Color(0xFF111B2B) else Color.White,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header con degradado estilo web
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF003366), Color(0xFF00509E))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Text(
                            "ESPECIES A MUESTREAR",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        IconButton(
                            onClick = { showSpeciesDialog = false },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }

                    Column(modifier = Modifier.padding(20.dp).weight(1f)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD).copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFBBDEFB))
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFF1565C0), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "Selecciona las especies para el bote ${currentBoteForData?.nombre?.uppercase()}. Para algas, el ingreso será por diámetro del disco.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1565C0),
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Grid de especies
                        Box(modifier = Modifier.weight(1f)) {
                            val chunkedEspecies = especiesMaestras.chunked(3)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(chunkedEspecies) { rowEspecies ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        rowEspecies.forEach { esp ->
                                            SpeciesGridItem(
                                                especie = esp,
                                                isSelected = selectedSpeciesIds.contains(esp.id),
                                                onClick = { 
                                                    if (selectedSpeciesIds.contains(esp.id)) {
                                                        selectedSpeciesIds.remove(esp.id)
                                                        muestreoBySpeciesId.remove(esp.id)
                                                    } else {
                                                        selectedSpeciesIds.add(esp.id)
                                                        if (!muestreoBySpeciesId.containsKey(esp.id)) {
                                                            muestreoBySpeciesId[esp.id] = setOf("L-P", "DENSIDAD")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - rowEspecies.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("TIPOS DE MUESTREO POR ESPECIE", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Lista de seleccionadas estilizada
                        Surface(
                            modifier = Modifier
                                .heightIn(max = 140.dp)
                                .fillMaxWidth(),
                            color = Color(0xFFF8F9FA),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                        ) {
                            LazyColumn(modifier = Modifier.padding(12.dp)) {
                                items(selectedSpeciesIds) { id ->
                                    val esp = especiesMaestras.find { it.id == id } ?: return@items
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(esp.com, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                                            Text(esp.sci ?: "", fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color.Gray)
                                        }
                                        val sel = muestreoBySpeciesId[id] ?: setOf("L-P", "DENSIDAD")
                                        FlowRow(
                                            modifier = Modifier.padding(start = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            maxItemsInEachRow = 2
                                        ) {
                                            FilterChip(
                                                selected = sel.contains("L-P"),
                                                onClick = {
                                                    val next = if (sel.contains("L-P")) sel - "L-P" else sel + "L-P"
                                                    muestreoBySpeciesId[id] = next
                                                },
                                                label = { Text("L-P", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                                            )
                                            FilterChip(
                                                selected = sel.contains("DENSIDAD"),
                                                onClick = {
                                                    val next = if (sel.contains("DENSIDAD")) sel - "DENSIDAD" else sel + "DENSIDAD"
                                                    muestreoBySpeciesId[id] = next
                                                },
                                                label = { Text("DENSIDAD", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                                            )
                                        }
                                    }
                                }
                                if (selectedSpeciesIds.isEmpty()) {
                                    item { Text("No hay especies seleccionadas. Marca al menos una especie para continuar con el muestreo.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                                }
                            }
                        }
                    }

                    // Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSpeciesDialog = false },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(1.5.dp, Color.Gray)
                        ) { 
                            Text("CANCELAR", fontWeight = FontWeight.Bold, color = Color.Gray) 
                        }
                        Button(
                            onClick = { 
                                val currentBote = currentBoteForData
                                val currentOp = currentOpForBotes
                                if (currentBote != null && currentOp != null) {
                                    val lpIds = selectedSpeciesIds.filter { sid -> muestreoBySpeciesId[sid]?.contains("L-P") != false }
                                    if (lpIds.isNotEmpty()) {
                                        val lpM = (currentBote.lpMuestras ?: emptyMap()).toMutableMap()
                                        lpIds.forEach { sid ->
                                            lpM.putIfAbsent(sid.toString(), emptyMap())
                                        }
                                        val updatedBote = currentBote.copy(lpMuestras = lpM.toMap())
                                        currentBoteForData = updatedBote

                                        val updatedBotes = (currentOp.botes ?: emptyList()).toMutableList()
                                        val idx = updatedBotes.indexOfFirst { sameBoteIdentity(it, currentBote) }
                                        if (idx >= 0) updatedBotes[idx] = updatedBote else updatedBotes.add(updatedBote)
                                        val updatedOp = currentOp.copy(botes = updatedBotes)
                                        currentOpForBotes = updatedOp
                                        persistOperacionSnapshot(ctx, updatedOp)
                                        trySyncOperacionSnapshot(scope, ctx, updatedOp) { syncedOp ->
                                            currentOpForBotes = syncedOp
                                        }
                                    }
                                }

                                showSpeciesDialog = false
                                if (pendingDataTab == "DENSIDAD" && transectosList.isEmpty()) {
                                    transectosList.add(DensidadUnidadDto(num = 1, tipo = currentBoteForData?.densTipo ?: "Transecto", area = 120.0))
                                }
                                showTransectDialog = true
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366)),
                            enabled = selectedSpeciesIds.isNotEmpty()
                        ) { 
                            Text("CONTINUAR", fontWeight = FontWeight.Bold) 
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGO DE AGREGAR TRANSECTOS ---
    TransectosDialog(
        show = showTransectDialog,
        currentBote = currentBoteForData,
        especiesMaestras = especiesMaestras,
        transectosList = transectosList,
        selectedSpeciesIds = selectedSpeciesIds.toList(),
        muestreoBySpeciesId = muestreoBySpeciesId.toMap(),
        initialTab = pendingDataTab,
        onDismiss = { showTransectDialog = false },
        onSaveDensity = saveDensity@{ normalizedTransectos ->
            val currentBote = currentBoteForData ?: return@saveDensity
            val currentOp = currentOpForBotes ?: return@saveDensity

            val updatedBote = currentBote.copy(transectos = normalizedTransectos)
            val bIndex = botesList.indexOfFirst { sameBoteIdentity(it, currentBote) }
            if (bIndex >= 0) {
                botesList[bIndex] = updatedBote
            }

            val updatedBotes = (currentOp.botes ?: emptyList()).toMutableList()
            val opBoteIdx = updatedBotes.indexOfFirst { sameBoteIdentity(it, currentBote) }
            if (opBoteIdx >= 0) {
                updatedBotes[opBoteIdx] = updatedBote
            } else {
                updatedBotes.add(updatedBote)
            }

            val updatedBotesApi = updatedBotes.map { b ->
                val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                b.copy(
                    densTipo = dens,
                    transectos = b.transectos?.map { t ->
                        t.copy(tipo = if (dens == "cuadrante") "cuadrante" else "transecto")
                    }
                )
            }

            val updatedOp = currentOp.copy(botes = updatedBotesApi)
            currentOpForBotes = updatedOp
            currentBoteForData = updatedBote
            persistOperacionSnapshot(ctx, updatedOp)
            showTransectDialog = false

            trySyncOperacionSnapshot(scope, ctx, updatedOp) { syncedOp ->
                currentOpForBotes = syncedOp
            }
        },
        onUpdateLpSamples = updateLpSamples@{ speciesId, nextSamples ->
            val op = currentOpForBotes ?: return@updateLpSamples
            val curr = currentBoteForData ?: return@updateLpSamples
            val nextBotes = replaceMatchingBote(op.botes ?: emptyList(), curr) { b ->
                    val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                    val buckets = (lpM[speciesId.toString()] ?: emptyMap()).toMutableMap()
                    buckets["LP"] = nextSamples
                    lpM[speciesId.toString()] = buckets.toMap()
                    b.copy(lpMuestras = lpM.toMap())
            }
            val nextOp = op.copy(botes = nextBotes)
            currentOpForBotes = nextOp
            persistOperacionSnapshot(ctx, nextOp)
            trySyncOperacionSnapshot(scope, ctx, nextOp) { syncedOp ->
                currentOpForBotes = syncedOp
            }
            currentBoteForData = nextBotes.firstOrNull { sameBoteIdentity(it, curr) } ?: currentBoteForData
        },
        onRemoveLpSpecies = removeLpSpecies@{ speciesId ->
            val op = currentOpForBotes ?: return@removeLpSpecies
            val curr = currentBoteForData ?: return@removeLpSpecies
            val nextBotes = replaceMatchingBote(op.botes ?: emptyList(), curr) { b ->
                    val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                    lpM.remove(speciesId.toString())
                    b.copy(lpMuestras = lpM.toMap())
            }
            val nextOp = op.copy(botes = nextBotes)
            currentOpForBotes = nextOp
            persistOperacionSnapshot(ctx, nextOp)
            trySyncOperacionSnapshot(scope, ctx, nextOp) { syncedOp ->
                currentOpForBotes = syncedOp
            }
            currentBoteForData = nextBotes.firstOrNull { sameBoteIdentity(it, curr) } ?: currentBoteForData
        }
    )

    // --- DIALOGO DE NUEVA OPERACIÓN ---
    NuevaOperacionDialog(
        show = showAddDialog,
        onDismiss = {
            showAddDialog = false
            validationError = null
        },
        regiones = regiones,
        regionLabelById = regionLabelById,
        selectedRegionId = selectedRegionId,
        onRegionSelected = {
            selectedRegionId = it
            sectorAmerbInput = ""
            selectedSectorAmerb = null
            caletaInput = ""
            selectedCaleta = null
            opaInput = ""
            selectedOpa = null
            validationError = null
        },
        sectoresAmerbApi = sectoresAmerbApi,
        sectorAmerbInput = sectorAmerbInput,
        onSectorAmerbInputChange = {
            sectorAmerbInput = it
            if (!it.equals(selectedSectorAmerb?.nombre.orEmpty(), ignoreCase = true)) {
                selectedSectorAmerb = null
            }
        },
        selectedSectorAmerb = selectedSectorAmerb,
        onSectorAmerbSelected = {
            selectedSectorAmerb = it
            if (it != null) {
                sectorAmerbInput = it.nombre
            }
        },
        numSeguimiento = numSeguimiento,
        onNumSeguimientoChange = { numSeguimiento = it },
        fechaInicio = fechaInicio,
        onFechaInicioClick = { showInicioDatePicker = true },
        fechaFin = fechaFin,
        onFechaFinClick = { showFinDatePicker = true },
        caletasApi = caletasApi,
        caletaInput = caletaInput,
        onCaletaInputChange = {
            caletaInput = it
            if (!it.equals(selectedCaleta.orEmpty(), ignoreCase = true)) {
                selectedCaleta = null
            }
        },
        onCaletaSelected = {
            selectedCaleta = it
            caletaInput = it
        },
        tipoOrg = tipoOrg,
        onTipoOrgChange = { tipoOrg = it },
        opasApi = opasApi,
        opaInput = opaInput,
        onOpaInputChange = {
            opaInput = it
            if (!it.equals(selectedOpa?.nombre.orEmpty(), ignoreCase = true)) {
                selectedOpa = null
            }
        },
        onOpaSelected = {
            selectedOpa = it
            if (it != null) {
                opaInput = it.nombre
            }
        },
        validationError = validationError,
        onCreateClick = {
            val error = validarNuevaOperacion(
                selectedRegionId = selectedRegionId,
                sectorAmerb = sectorAmerbNombreActual,
                caleta = caletaActual,
                tipoOrg = tipoOrg,
                organizacion = organizacionActual,
                numSeguimiento = numSeguimiento,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin
            )
            if (error != null) {
                validationError = error
            } else {
                validationError = null
                showConfirmDialog = true
            }
        }
    )

    // --- DIALOGO DE CONFIRMACIÓN ---
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirmar creación") },
            text = { Text("¿Está seguro que quiere crear esta \"$nomenclatura\"?\nSi es así, presione \"SI\", de lo contrario \"NO\".") },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    val req = OperacionUpsertRequest(
                        id = nomenclatura,
                        region = selectedRegionId,
                        sector = caletaActual,
                        sectorAmerbId = selectedSectorAmerb?.id,
                        sectorAmerb = sectorAmerbNombreActual,
                        tipoOrg = tipoOrg,
                        opaId = selectedOpa?.id,
                        org = organizacionActual,
                        numSeg = numSeguimiento.toIntOrNull(),
                        fechaInicio = fechaInicio.ifBlank { null },
                        fechaFin = fechaFin.ifBlank { null }
                    )

                    scope.launch {
                        val finalOp = DataManager.createOperacion(ctx, req)

                        showAddDialog = false
                        // Reset de campos para la próxima creación
                        selectedRegionId = regiones.firstOrNull()?.id
                        sectorAmerbInput = ""
                        selectedSectorAmerb = null
                        numSeguimiento = ""
                        fechaInicio = todayIso()
                        fechaFin = todayIso()
                        caletaInput = ""
                        selectedCaleta = null
                        tipoOrg = "STI"
                        opaInput = ""
                        selectedOpa = null

                        currentOpForBotes = finalOp
                        botesList.clear()
                        botesList.add(OperacionBoteDto(zona = 1, densTipo = "Transecto", submareal = 1))
                        showBotesDialog = true
                    }
                }) { Text("SI") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("NO") }
            }
        )
    }

    // --- DIALOGO DE BOTES ---
    GestionBotesDialog(
        show = showBotesDialog,
        operacionId = currentOpForBotes?.id,
        operationRegionRom = currentOpForBotes
            ?.let { inferOperacionRegionId(it, sectoresAmerbApi, caletasApi, opasApi) }
            ?.let { rid -> regiones.find { it.id == rid }?.rom },
        operationCaleta = currentOpForBotes?.sector,
        botesList = botesList,
        botesMaestros = botesMaestros,
        validationError = validationError,
        onValidationErrorChange = { validationError = it },
        onDismiss = { showBotesDialog = false },
        onAddRow = { botesList.add(OperacionBoteDto(zona = botesList.size + 1, densTipo = "Transecto", submareal = 1)) },
        onSave = {
            val op = currentOpForBotes ?: return@GestionBotesDialog
            val updatedBotesUi = botesList.toList()
            val error = validarBotes(updatedBotesUi)
            if (error != null) {
                validationError = error
                return@GestionBotesDialog
            }

            validationError = null
            val updatedBotesApi = updatedBotesUi.map { b ->
                val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                val isIntermareal = b.submareal == 0 || b.nombre?.equals("Intermareal", ignoreCase = true) == true
                b.copy(
                    densTipo = dens,
                    submareal = if (isIntermareal) 0 else 1,
                    boteMaestroId = if (isIntermareal) null else b.boteMaestroId,
                    nombre = if (isIntermareal) "Intermareal" else b.nombre,
                    transectos = b.transectos?.map { t ->
                        t.copy(tipo = if (dens == "cuadrante") "cuadrante" else "transecto")
                    }
                )
            }

            val localOp = op.copy(botes = updatedBotesApi)
            val firstBoteToEdit = updatedBotesApi
                .sortedWith(compareBy({ it.zona ?: Int.MAX_VALUE }, { it.nombre ?: "" }))
                .firstOrNull()
            currentOpForBotes = localOp
            persistOperacionSnapshot(ctx, localOp)
            showBotesDialog = false
            currentOperacionDetalle = localOp
            currentOperacionDetalleBoteKey = firstBoteToEdit?.let { boteSelectionKey(it) }
            showOperacionDetalleDialog = firstBoteToEdit != null

            trySyncOperacionSnapshot(scope, ctx, localOp) { syncedOp ->
                currentOpForBotes = syncedOp
                if (showOperacionDetalleDialog && currentOperacionDetalle?.id == syncedOp.id) {
                    currentOperacionDetalle = syncedOp
                    currentOperacionDetalleBoteKey = firstBoteToEdit
                        ?.let { savedBote ->
                            syncedOp.botes
                                ?.firstOrNull { sameBoteIdentity(it, savedBote) }
                                ?.let { boteSelectionKey(it) }
                        } ?: currentOperacionDetalleBoteKey
                }
            }
        }
    )

    if (showInicioDatePicker) {
        MyDatePickerDialog(onDateSelected = { fechaInicio = it; showInicioDatePicker = false }, onDismiss = { showInicioDatePicker = false })
    }
    if (showFinDatePicker) {
        MyDatePickerDialog(onDateSelected = { fechaFin = it; showFinDatePicker = false }, onDismiss = { showFinDatePicker = false })
    }

    if (showOperacionDetalleDialog && currentOperacionDetalle != null) {
        OperacionDataDialog(
            opInitial = currentOperacionDetalle!!,
            regionLabel = currentOperacionDetalle
                ?.let { inferOperacionRegionId(it, sectoresAmerbApi, caletasApi, opasApi) }
                ?.let { regionLabelById[it] },
            especiesMaestras = especiesMaestras,
            initialSelectedBoteKey = currentOperacionDetalleBoteKey,
            onDismiss = {
                showOperacionDetalleDialog = false
                currentOperacionDetalleBoteKey = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Operaciones", color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        val effectiveOnline = AppState.isEffectivelyOnline()
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (effectiveOnline) Color(0xFF4CAF50) else Color.Gray, androidx.compose.foundation.shape.CircleShape)
                                .border(1.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { isLoading = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                syncAndRefreshCatalogs()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sincronizar todo", tint = Color.White)
                    }
                    IconButton(onClick = {
                        if (selectedRegionId == null) {
                            selectedRegionId = regiones.firstOrNull()?.id
                        }
                        if (fechaInicio.isBlank()) fechaInicio = todayIso()
                        if (fechaFin.isBlank()) fechaFin = todayIso()
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Nueva", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val grouped = remember(operacionesFiltradas, sectoresAmerbApi, caletasApi, opasApi) {
                operacionesFiltradas
                    .groupBy { inferOperacionRegionId(it.op, sectoresAmerbApi, caletasApi, opasApi) }
                    .toList()
                    .sortedWith(compareBy({ it.first ?: Int.MAX_VALUE }, { it.first ?: Int.MAX_VALUE }))
            }
            val regionExpanded = remember { mutableStateMapOf<String, Boolean>() }
            LaunchedEffect(grouped.size) {
                grouped.forEach { pair ->
                    val regionKey = pair.first?.toString() ?: "null"
                    if (!regionExpanded.containsKey(regionKey)) {
                        regionExpanded[regionKey] = false
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                item {
                    Text("Operaciones", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Cada operación agrupa botes con sus datos técnicos", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OperacionesFiltersHeader(
                        textoBusqueda = textoBusqueda,
                        onTextoBusquedaChange = { textoBusqueda = it },
                        filtroSector = filtroSector,
                        onFiltroSectorChange = { filtroSector = it },
                        sectoresDisponibles = sectoresDisponibles,
                        filtroMes = filtroMes,
                        onFiltroMesChange = { filtroMes = it },
                        mesesDisponibles = mesesDisponibles,
                        totalOperaciones = operacionesFiltradas.size,
                        canSync = AppState.isEffectivelyOnline(),
                        onSyncClick = { scope.launch { syncAndRefreshCatalogs() } },
                    )
                }
                if (grouped.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Sin resultados", fontWeight = FontWeight.Bold)
                                Text(
                                    "No hay operaciones que coincidan con los filtros actuales.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                grouped.forEach { (regionId, ops) ->
                    val regionKey = regionId?.toString() ?: "null"
                    item {
                        OperacionesRegionHeader(
                            titulo = regionId?.let { regionLabelById[it] } ?: "Sin región",
                            totalOperaciones = ops.size,
                            isExpanded = regionExpanded[regionKey] ?: false,
                            onToggle = { regionExpanded[regionKey] = !(regionExpanded[regionKey] ?: false) },
                        )
                    }

                    if (regionExpanded[regionKey] == true) {
                        items(ops, key = { "${it.source}-${it.op.id}" }) { item ->
                            val itemActual = operacionesUi.firstOrNull { it.op.id == item.op.id } ?: item
                            OperacionCard(
                                item = itemActual,
                                isExpanded = expandedOpId == itemActual.op.id,
                                onExpandClick = {
                                    if (expandedOpId == itemActual.op.id) {
                                        expandedOpId = null
                                    } else {
                                        lastExpandClickAt = System.currentTimeMillis()
                                        expandedOpId = itemActual.op.id
                                        if (itemActual.op.botes.isNullOrEmpty() && AppState.isEffectivelyOnline()) scope.launch {
                                            val refreshed = runCatching {
                                                DataManager.refreshOperacionDetail(ctx, itemActual.op.id)
                                            }.getOrNull()
                                            if (refreshed != null) {
                                                currentOpForBotes = if (currentOpForBotes?.id == refreshed.id) refreshed else currentOpForBotes
                                            }
                                        }
                                    }
                                },
                                onEditBotesClick = {
                                    currentOpForBotes = itemActual.op
                                    botesList.clear()
                                    botesList.addAll(itemActual.op.botes ?: emptyList())
                                    showBotesDialog = true
                                },
                                onEditDataClick = { bote ->
                                    if (System.currentTimeMillis() - lastExpandClickAt < 350L) {
                                        return@OperacionCard
                                    }
                                    currentOperacionDetalle = itemActual.op
                                    currentOperacionDetalleBoteKey = boteSelectionKey(bote)
                                    showOperacionDetalleDialog = true
                                },
                                onDeleteClick = {
                                    scope.launch {
                                        var success = true
                                        if (itemActual.source == OperacionSource.BD && AppState.isEffectivelyOnline()) {
                                            try {
                                                val res = RetrofitClient.apiService.eliminarOperacion(itemActual.op.id)
                                                success = res.isSuccessful && res.body()?.ok == true
                                            } catch (_: Exception) { success = false }
                                        }
                                        
                                        if (success) {
                                            DataManager.removeOperacion(itemActual.op.id, itemActual.source)
                                            DataManager.persistCache(ctx)
                                        }
                                    }
                                },
                                onUploadLocalClick = {
                                    if (itemActual.source != OperacionSource.LC) return@OperacionCard
                                    if (!AppState.isEffectivelyOnline()) return@OperacionCard
                                    scope.launch {
                                        val ok = DataManager.tryUploadOperacion(ctx, itemActual.op)
                                        if (!ok) {
                                            DataManager.markOperacionDirty(itemActual.op.id)
                                            DataManager.persistCache(ctx)
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperacionesFiltersHeader(
    textoBusqueda: String,
    onTextoBusquedaChange: (String) -> Unit,
    filtroSector: String,
    onFiltroSectorChange: (String) -> Unit,
    sectoresDisponibles: List<String>,
    filtroMes: String,
    onFiltroMesChange: (String) -> Unit,
    mesesDisponibles: List<String>,
    totalOperaciones: Int,
    canSync: Boolean,
    onSyncClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var expandedSector by remember { mutableStateOf(false) }
    var expandedMes by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = textoBusqueda,
        onValueChange = onTextoBusquedaChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (textoBusqueda.isNotBlank()) {
                IconButton(onClick = { onTextoBusquedaChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                }
            }
        },
        label = { Text("Buscar por operación, sector, OPA, bote o buzo") },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.bitecmaTeal,
            focusedLabelColor = colors.bitecmaTeal,
            unfocusedBorderColor = colors.bitecmaBorder,
            unfocusedLabelColor = colors.bitecmaMutedText,
            unfocusedContainerColor = colors.bitecmaSoftBackground,
            focusedContainerColor = colors.surface,
        )
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expandedSector = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, colors.bitecmaBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.bitecmaCardBackground,
                    contentColor = colors.bitecmaSubtleText,
                )
            ) {
                Text(
                    text = if (filtroSector.isBlank()) "Todos los sectores" else filtroSector,
                    maxLines = 1
                )
            }
            DropdownMenu(expanded = expandedSector, onDismissRequest = { expandedSector = false }) {
                DropdownMenuItem(
                    text = { Text("Todos los sectores") },
                    onClick = {
                        onFiltroSectorChange("")
                        expandedSector = false
                    }
                )
                sectoresDisponibles.forEach { sector ->
                    DropdownMenuItem(
                        text = { Text(sector) },
                        onClick = {
                            onFiltroSectorChange(sector)
                            expandedSector = false
                        }
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(0.8f)) {
            OutlinedButton(
                onClick = { expandedMes = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, colors.bitecmaBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.bitecmaCardBackground,
                    contentColor = colors.bitecmaSubtleText,
                )
            ) {
                Text(
                    text = if (filtroMes.isBlank()) "Todos los meses" else filtroMes,
                    maxLines = 1
                )
            }
            DropdownMenu(expanded = expandedMes, onDismissRequest = { expandedMes = false }) {
                DropdownMenuItem(
                    text = { Text("Todos los meses") },
                    onClick = {
                        onFiltroMesChange("")
                        expandedMes = false
                    }
                )
                mesesDisponibles.forEach { mes ->
                    DropdownMenuItem(
                        text = { Text(mes) },
                        onClick = {
                            onFiltroMesChange(mes)
                            expandedMes = false
                        }
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = "$totalOperaciones operación(es) visibles",
        fontSize = 12.sp,
        color = colors.bitecmaMutedText
    )
    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = onSyncClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        enabled = canSync,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.bitecmaTeal,
            contentColor = Color.White,
        )
    ) {
        Icon(Icons.Default.Sync, null)
        Spacer(Modifier.width(10.dp))
        Text("SINCRONIZAR TODO", fontWeight = FontWeight.Black)
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun OperacionesRegionHeader(
    titulo: String,
    totalOperaciones: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp)
            .clickable(onClick = onToggle),
        color = colors.bitecmaSoftBackgroundAlt,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.bitecmaBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    titulo,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.bitecmaNavy
                )
                Text(
                    "$totalOperaciones operación(es)",
                    fontSize = 11.sp,
                    color = colors.bitecmaMutedText
                )
            }
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.bitecmaNavy
            )
        }
    }
}

@Composable
private fun BoteDataResumenDialog(
    bote: OperacionBoteDto?,
    onDismiss: () -> Unit,
    onOpenDensity: () -> Unit,
    onOpenLp: () -> Unit,
    onOpenSpecies: () -> Unit,
) {
    if (bote == null) return

    val colors = MaterialTheme.colorScheme
    val densityUnits = bote.transectos?.size ?: 0
    val lpSamples = totalLpSamples(bote)
    val isIntermareal = bote.submareal == 0 || bote.nombre?.equals("Intermareal", ignoreCase = true) == true

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isIntermareal) Icons.Default.DirectionsWalk else Icons.Default.DirectionsBoat,
                        contentDescription = null,
                        tint = colors.bitecmaNavy,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bote.nombre?.uppercase() ?: "BOTE SIN NOMBRE",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = colors.bitecmaNavyStrong
                        )
                        Text(
                            text = "Zona ${bote.zona ?: 0} · ${bote.buzo?.uppercase() ?: "SIN BUZO"}",
                            fontSize = 12.sp,
                            color = colors.bitecmaMutedText
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = colors.bitecmaSuccessBg,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$densityUnits", fontWeight = FontWeight.Black, fontSize = 18.sp, color = colors.bitecmaTeal)
                            Text(densTipoPluralLabel(bote.densTipo), fontSize = 10.sp, color = colors.bitecmaTeal)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = colors.bitecmaAmberBg,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$lpSamples", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFFD97706))
                            Text("Muestras P-L", fontSize = 10.sp, color = Color(0xFFD97706))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (densityUnits == 0 && lpSamples == 0) {
                        "Este bote aun no tiene datos cargados. Puedes agregar especies o comenzar con densidad/peso-longitud."
                    } else {
                        "Revisa los datos ya cargados o agrega mas informacion a este bote."
                    },
                    fontSize = 12.sp,
                    color = colors.bitecmaMutedText
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onOpenDensity,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.bitecmaTeal)
                ) {
                    Text(if (densityUnits > 0) "VER / EDITAR DENSIDAD" else "AGREGAR DENSIDAD", fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onOpenLp,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text(if (lpSamples > 0) "VER / EDITAR PESO-LONGITUD" else "AGREGAR PESO-LONGITUD", fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onOpenSpecies,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("AGREGAR O CAMBIAR ESPECIES", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun OperacionCard(
    item: OperacionItem,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onEditBotesClick: () -> Unit,
    onEditDataClick: (OperacionBoteDto) -> Unit,
    onDeleteClick: () -> Unit,
    onUploadLocalClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val op = item.op
    val syncInfo = DataManager.getEstadoSyncOperacion(op.id, item.source)
    val tagBg = if (item.source == OperacionSource.BD) colors.bitecmaSuccessBg else colors.bitecmaDangerBg
    val tagFg = if (item.source == OperacionSource.BD) Color(0xFF059669) else colors.error
    val syncBadge = when (syncInfo.estado) {
        DataManager.EstadoSyncOperacion.SOLO_LOCAL -> Triple("SOLO LOCAL", colors.bitecmaBlueBg, Color(0xFF1D6FA4))
        DataManager.EstadoSyncOperacion.PENDIENTE -> Triple("PENDIENTE", colors.bitecmaAmberBg, Color(0xFFD97706))
        DataManager.EstadoSyncOperacion.SINCRONIZANDO -> Triple("SINCRONIZANDO", colors.bitecmaTealContainer, colors.bitecmaTeal)
        DataManager.EstadoSyncOperacion.ERROR -> Triple("ERROR", colors.bitecmaDangerBg, colors.error)
        DataManager.EstadoSyncOperacion.SINCRONIZADO -> Triple("SINCRONIZADA", colors.bitecmaSuccessBg, Color(0xFF059669))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.bitecmaCardBackground),
        border = BorderStroke(1.dp, colors.bitecmaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandClick() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = tagBg, shape = RoundedCornerShape(6.dp)) {
                            Box(modifier = Modifier.width(34.dp).height(22.dp), contentAlignment = Alignment.Center) {
                                Text(text = if (item.source == OperacionSource.BD) "BD" else "LC", color = tagFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        val titulo = listOfNotNull(
                            (op.sectorAmerb?.takeIf { it.isNotBlank() && it != "0000-00-00" } ?: op.sector.takeIf { it.isNotBlank() && it != "0000-00-00" }),
                        ).joinToString(" · ")
                        Text(titulo, fontWeight = FontWeight.ExtraBold, color = colors.bitecmaNavy, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Formato: N° Seguimiento · Fecha Inicio [· Fecha Fin si es distinta]
                    val subtitulo = buildString {
                        append(op.id) // El ID autogenerado tipo OP-2026-22
                        
                        val nSeg = op.numSeg?.toString()
                        if (!nSeg.isNullOrBlank() && nSeg != "0") {
                            append(" · ")
                            append(nSeg)
                        }

                        val fInicio = op.fechaInicio
                        if (!fInicio.isNullOrBlank() && fInicio != "0000-00-00") {
                            append(" · ")
                            append(fInicio)
                        }

                        val fFin = op.fechaFin
                        if (!fFin.isNullOrBlank() && fFin != "0000-00-00" && fFin != fInicio) {
                            append(" - ")
                            append(fFin)
                        }
                    }
                    Text(subtitulo, fontSize = 12.sp, color = colors.bitecmaSubtleText)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = syncBadge.second, shape = RoundedCornerShape(999.dp)) {
                            Text(
                                text = syncBadge.first,
                                color = syncBadge.third,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (!syncInfo.ultimoError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = syncInfo.ultimoError.orEmpty(),
                                fontSize = 10.sp,
                                color = colors.error,
                                maxLines = 1
                            )
                        }
                    }
                }
                Row {
                    val canUploadLocal = item.source == OperacionSource.LC &&
                        AppState.isEffectivelyOnline() &&
                        DataManager.operacionesBd.none { it.id == op.id }
                    if (canUploadLocal) {
                        IconButton(onClick = onUploadLocalClick) { Icon(Icons.Default.CloudUpload, null, tint = colors.bitecmaTeal) }
                    }
                    IconButton(onClick = onEditBotesClick) { Icon(Icons.Default.Edit, null, tint = colors.bitecmaSubtleText) }
                    IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Delete, null, tint = colors.error.copy(alpha = 0.8f)) }
                    IconButton(onClick = onExpandClick) { Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Expandir") }
                }
            }
            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colors.bitecmaBorder)
                Text("Botes registrados:", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                
                val botesAMostrar = op.botes ?: emptyList()
                if (botesAMostrar.isEmpty()) {
                    Text("No hay botes registrados en esta operación.", fontSize = 12.sp, color = colors.bitecmaMutedText, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    botesAMostrar.forEach { bote -> 
                        BoteItem(bote = bote, onClick = { onEditDataClick(bote) })
                        Spacer(modifier = Modifier.height(10.dp)) 
                    }
                }
            }
        }
    }
}

@Composable
fun BoteItem(bote: OperacionBoteDto, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val transectCount = bote.transectos?.size ?: 0
    val lpSampleCount = totalLpSamples(bote)
    val isIntermareal = bote.submareal == 0 || bote.nombre?.equals("Intermareal", ignoreCase = true) == true
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.bitecmaSoftBackground),
        border = BorderStroke(1.dp, colors.bitecmaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isIntermareal) Icons.Default.DirectionsWalk else Icons.Default.DirectionsBoat,
                contentDescription = null,
                tint = colors.bitecmaNavy,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = bote.nombre?.uppercase() ?: "BOTE SIN NOMBRE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = colors.bitecmaNavyStrong)
                Text(text = "Zona ${bote.zona ?: 0} · ${bote.buzo?.uppercase() ?: "SIN BUZO"}", fontSize = 12.sp, color = colors.bitecmaMutedText)
                Text(text = "Tipo: ${bote.densTipo?.lowercase() ?: "transecto"}", fontSize = 12.sp, color = colors.bitecmaMutedText)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (transectCount > 0) {
                        Surface(color = colors.bitecmaSuccessBg, shape = RoundedCornerShape(999.dp)) {
                            Text(
                                text = "$transectCount ${if (transectCount == 1) densTipoLabel(bote.densTipo).lowercase() else densTipoPluralLabel(bote.densTipo).lowercase()}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.bitecmaTeal
                            )
                        }
                    }
                    if (lpSampleCount > 0) {
                        Surface(color = colors.bitecmaAmberBg, shape = RoundedCornerShape(999.dp)) {
                            Text(
                                text = "$lpSampleCount ${if (lpSampleCount == 1) "muestra L-P" else "muestras L-P"}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                }
            }
            if (transectCount > 0 || lpSampleCount > 0) {
                Surface(
                    color = colors.bitecmaSuccessBg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${transectCount + lpSampleCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.bitecmaTeal)
                        Text(text = "Registros", fontSize = 8.sp, color = colors.bitecmaTeal)
                    }
                }
            }
        }
    }
}

@Composable
fun SpeciesGridItem(especie: EspecieDto, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.clickable { onClick() }.height(92.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colors.bitecmaTeal else colors.bitecmaBorder
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colors.bitecmaTealContainer else colors.bitecmaSoftBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    especie.com, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 13.sp,
                    maxLines = 2, 
                    textAlign = TextAlign.Center,
                    color = if (isSelected) colors.bitecmaTeal else colors.bitecmaNavyStrong
                )
                Text(
                    especie.sci ?: "", 
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, 
                    fontSize = 9.sp,
                    color = colors.bitecmaMutedText, 
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle, 
                    null, 
                    tint = colors.bitecmaTeal, 
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableDropdown(
    value: String, 
    onValueChange: (String) -> Unit, 
    placeholder: String, 
    items: List<T>, 
    itemLabel: (T) -> String, 
    onItemSelected: (T) -> Unit, 
    showAddNew: Boolean = false,
    onAddNewValue: ((String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredItems = items.filter { itemLabel(it).contains(value, ignoreCase = true) }
    val isDark = isSystemInDarkTheme()
    // Colores personalizados para mejor visibilidad y contraste (Azul profundo con texto blanco)
    val bgColor = if (isDark) Color(0xFF0D47A1) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val borderColor = if (isDark) Color(0xFF1976D2) else Color(0xFFF1F3F5)
    val focusedColor = if (isDark) Color(0xFFBBDEFB) else Color(0xFF003366)

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, 
            onValueChange = { 
                onValueChange(it)
                expanded = true 
            }, 
            placeholder = { Text(placeholder, fontSize = 13.sp, color = if (isDark) Color.LightGray else Color.Gray) }, 
            modifier = Modifier.fillMaxWidth().background(bgColor, RoundedCornerShape(12.dp)), 
            shape = RoundedCornerShape(12.dp), 
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    when {
                        filteredItems.size == 1 -> {
                            onItemSelected(filteredItems.first())
                            expanded = false
                        }
                        else -> {
                            expanded = filteredItems.isNotEmpty() || showAddNew
                        }
                    }
                }
            ),
            textStyle = TextStyle(color = textColor, fontSize = 14.sp),
            trailingIcon = { 
                IconButton(onClick = { expanded = !expanded }) { 
                    Icon(if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, tint = focusedColor) 
                } 
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = borderColor,
                focusedBorderColor = focusedColor,
                unfocusedTextColor = textColor,
                focusedTextColor = textColor,
                unfocusedContainerColor = bgColor,
                focusedContainerColor = bgColor
            )
        )

        if (expanded && (filteredItems.isNotEmpty() || showAddNew)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, borderColor)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (showAddNew && value.isNotBlank() && filteredItems.none { itemLabel(it).equals(value, true) }) {
                        item {
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, null, tint = Color(0xFF00897B), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Agregar '$value'...", color = Color(0xFF00897B), fontWeight = FontWeight.Bold) 
                                    }
                                }, 
                                onClick = { 
                                    onAddNewValue?.invoke(value.trim())
                                    expanded = false 
                                }
                            )
                            HorizontalDivider(color = borderColor)
                        }
                    }
                    items(filteredItems) { item ->
                        DropdownMenuItem(
                            text = { Text(itemLabel(item), fontSize = 14.sp, color = textColor) }, 
                            onClick = { 
                                onItemSelected(item)
                                expanded = false 
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoteRowItem(
    index: Int,
    bote: OperacionBoteDto,
    isSearchActive: Boolean,
    onOpenSearch: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (OperacionBoteDto) -> Unit
) {
    var showUnitWarning by remember { mutableStateOf(false) }
    var nextUnitType by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val zonaFocusRequester = remember { FocusRequester() }
    val buzoFocusRequester = remember { FocusRequester() }

    val isDark = isSystemInDarkTheme()
    val comboBg = if (isDark) Color(0xFF0D47A1) else Color.White
    val comboText = if (isDark) Color.White else Color.Black
    val comboBorder = if (isDark) Color(0xFF1976D2) else Color(0xFFF1F3F5)
    val labelColor = if (isDark) Color(0xFFBBDEFB) else Color.Gray
    val isIntermareal = bote.submareal == 0 || bote.nombre?.equals("Intermareal", ignoreCase = true) == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(index.toString(), Modifier.weight(0.3f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.LightGray else Color.Gray)
        
        // Zona Muestreo
        BasicTextField(
            value = bote.zona?.toString() ?: "",
            onValueChange = { onUpdate(bote.copy(zona = it.toIntOrNull())) },
            modifier = Modifier
                .weight(0.8f)
                .focusRequester(zonaFocusRequester)
                .padding(horizontal = 4.dp)
                .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                .background(comboBg, RoundedCornerShape(8.dp))
                .padding(10.dp),
            textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = comboText),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { buzoFocusRequester.requestFocus() }
            )
        )

        // Bote con buscador estilizado
        Box(Modifier.weight(1.8f).padding(horizontal = 4.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isIntermareal) { onOpenSearch() }
                    .border(1.5.dp, if (isSearchActive) Color(0xFF00897B) else comboBorder, RoundedCornerShape(8.dp))
                    .background(comboBg, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                color = Color.Transparent
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconToggleButton(
                        checked = isIntermareal,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onUpdate(bote.copy(submareal = 0, nombre = "Intermareal", boteMaestroId = null))
                            } else {
                                onUpdate(bote.copy(submareal = 1, nombre = null, boteMaestroId = null))
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        val iconTint = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366)
                        Icon(
                            if (isIntermareal) Icons.Default.DirectionsWalk else Icons.Default.DirectionsBoat,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        when {
                            isIntermareal -> "Intermareal (a pie)"
                            bote.nombre.isNullOrBlank() -> "Submareal (en bote) — seleccionar bote"
                            else -> "Submareal (en bote) — ${bote.nombre}"
                        },
                        fontSize = 13.sp, 
                        fontWeight = if (isIntermareal || bote.nombre != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (isIntermareal || bote.nombre != null) comboText else labelColor,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isIntermareal) {
                        Icon(Icons.Default.Search, null, tint = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Buzo
        BasicTextField(
            value = bote.buzo ?: "",
            onValueChange = { onUpdate(bote.copy(buzo = it)) },
            modifier = Modifier
                .weight(1.2f)
                .focusRequester(buzoFocusRequester)
                .padding(horizontal = 4.dp)
                .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                .background(comboBg, RoundedCornerShape(8.dp))
                .padding(10.dp),
            textStyle = TextStyle(fontSize = 13.sp, color = comboText),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (bote.buzo.isNullOrBlank()) {
                        Text("Buzo...", color = labelColor, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            }
        )

        // Tipo de unidad
        var expandedUni by remember { mutableStateOf(false) }
        Box(Modifier.weight(1.2f).padding(horizontal = 4.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedUni = true }
                    .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                    .background(comboBg, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                color = Color.Transparent
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(densTipoLabel(bote.densTipo), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = comboText, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null, tint = labelColor, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(
                expanded = expandedUni, 
                onDismissRequest = { expandedUni = false },
                modifier = Modifier.background(comboBg)
            ) {
                listOf("Transecto", "Cuadrante").forEach { u ->
                    DropdownMenuItem(
                        text = { Text(u, fontSize = 13.sp, color = comboText) }, 
                        onClick = { 
                            if (bote.densTipo != u && !bote.transectos.isNullOrEmpty()) {
                                nextUnitType = u
                                showUnitWarning = true
                            } else {
                                onUpdate(bote.copy(densTipo = u))
                            }
                            expandedUni = false 
                        }
                    )
                }
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }

    if (showUnitWarning) {
        AlertDialog(
            onDismissRequest = { showUnitWarning = false },
            title = { Text("BITECMA Dice:", fontWeight = FontWeight.Black, color = Color(0xFF003366)) },
            text = { Text("Al cambiar la unidad de muestreo, solo se perderán los datos de densidad. ¿Desea continuar?", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { 
                        onUpdate(bote.copy(densTipo = nextUnitType, transectos = emptyList()))
                        showUnitWarning = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("SÍ, CAMBIAR") }
            },
            dismissButton = {
                TextButton(onClick = { showUnitWarning = false }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }
}

@Composable
fun BoteMaestroSearchDialog(
    botes: List<BoteMaestroDto>,
    operationRegionRom: String?,
    operationCaleta: String?,
    onSelect: (BoteMaestroDto) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    
    // Estado para menús abatibles (jerarquía)
    var expandedRegionRom by remember(operationRegionRom, operationCaleta) { mutableStateOf<String?>(null) }
    var expandedCaleta by remember(operationCaleta) { mutableStateOf(operationCaleta) }

    fun normalizeRom(value: String?): String? {
        val s = value?.trim()
        if (s.isNullOrBlank()) return null
        val left = s.split("—").firstOrNull()?.trim().orEmpty()
        val token = left.split(" ").firstOrNull()?.trim().orEmpty()
        return token.takeIf { it.isNotBlank() }
    }

    val opCaleta = operationCaleta?.trim().takeIf { !it.isNullOrBlank() }
    val opRom = normalizeRom(operationRegionRom)
    fun normalizeKey(value: String?): String? {
        val raw = value?.trim()
        if (raw.isNullOrBlank()) return null
        val noParen = raw.replace(Regex("\\(.*?\\)"), " ")
        val noMarks = Normalizer.normalize(noParen, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        val cleaned = noMarks
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return cleaned.takeIf { it.isNotBlank() }
    }
    val opCaletaKey = normalizeKey(opCaleta)

    val baseBotes = remember(botes, opCaletaKey, opRom) {
        var filtered = botes
        if (opCaletaKey != null) {
            filtered = filtered.filter { b ->
                val bKey = normalizeKey(b.caleta)
                bKey != null && (bKey == opCaletaKey || bKey.contains(opCaletaKey) || opCaletaKey.contains(bKey))
            }
        }
        if (opRom != null) {
            filtered = filtered.filter { b ->
                val bRom = normalizeRom(b.region_rom ?: b.region)
                bRom == null || bRom.equals(opRom, ignoreCase = true)
            }
        }
        filtered
    }

    val regionKeys = remember(baseBotes) { baseBotes.mapNotNull { it.region_rom ?: it.region }.distinct().sorted() }
    LaunchedEffect(regionKeys, opRom, operationRegionRom, opCaletaKey) {
        if (expandedRegionRom == null) {
            val byExact = operationRegionRom?.let { desired -> regionKeys.firstOrNull { it.equals(desired, ignoreCase = true) } }
            val byRom = opRom?.let { desiredRom -> regionKeys.firstOrNull { normalizeRom(it)?.equals(desiredRom, ignoreCase = true) == true } }
            expandedRegionRom = byExact ?: byRom ?: regionKeys.firstOrNull()
        }
        if (!opCaleta.isNullOrBlank()) {
            expandedCaleta = opCaleta
        }
    }

    val queryFilteredBotes = baseBotes.filter { b ->
        (b.nombre ?: "").contains(query, true) ||
            (b.nrpa ?: "").contains(query, true) ||
            (b.nmatricula ?: "").contains(query, true)
    }

    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111B2B) else Color.White
    val headerBg = if (isDark) Color(0xFF0D47A1) else Color(0xFFF8F9FA)
    val textColor = if (isDark) Color.White else Color.Black
    val comboBg = if (isDark) Color(0xFF0D47A1) else Color.White
    val comboBorder = if (isDark) Color(0xFF1976D2) else Color(0xFFF1F3F5)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
            color = bgColor
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(headerBg).padding(16.dp)
                ) {
                    Text("SELECCIONAR BOTE MAESTRO", fontWeight = FontWeight.Black, fontSize = 16.sp, color = if (isDark) Color.White else Color(0xFF003366))
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = if (isDark) Color.LightGray else Color.Gray)
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar por nombre, RPA o matrícula...", color = if (isDark) Color.LightGray else Color.Gray) },
                        modifier = Modifier.fillMaxWidth().background(comboBg, RoundedCornerShape(12.dp)),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366)) },
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = comboBorder,
                            focusedBorderColor = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366),
                            unfocusedContainerColor = comboBg,
                            focusedContainerColor = comboBg
                        )
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    Text("JERARQUÍA: REGIÓN > CALETA > BOTE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                    if (!opCaleta.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Filtrado por caleta: ${opCaleta.uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFBBDEFB) else Color(0xFF003366))
                    }
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (baseBotes.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (!opCaleta.isNullOrBlank()) "No hay botes para la caleta seleccionada" else "No hay botes disponibles",
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        if (query.isEmpty()) {
                            // Mostrar Jerarquía: Región > Caleta > Bote
                            val regions = regionKeys
                            
                            items(regions) { rom ->
                                val isRegionExpanded = expandedRegionRom == rom
                                HierarchicalItem(
                                    label = "REGIÓN $rom",
                                    isExpanded = isRegionExpanded,
                                    onClick = { 
                                        expandedRegionRom = if (isRegionExpanded) null else rom
                                        expandedCaleta = null
                                    },
                                    level = 0
                                )
                                
                                if (isRegionExpanded) {
                                    val caletas = baseBotes.filter { (it.region_rom ?: it.region) == rom }
                                            .mapNotNull { it.caleta }
                                            .distinct()
                                            .sorted()
                                    
                                    caletas.forEach { caleta ->
                                        val isCaletaExpanded = expandedCaleta?.equals(caleta, ignoreCase = true) == true
                                        HierarchicalItem(
                                            label = caleta.uppercase(),
                                            isExpanded = isCaletaExpanded,
                                            onClick = { expandedCaleta = if (isCaletaExpanded) null else caleta },
                                            level = 1
                                        )
                                        
                                        if (isCaletaExpanded) {
                                            val botesInCaleta = baseBotes.filter { (it.region_rom ?: it.region) == rom && it.caleta?.equals(caleta, true) == true }
                                            
                                            botesInCaleta.forEach { b ->
                                                BoteFinalItem(b, onSelect)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Mostrar Resultados de Búsqueda Directa
                            items(queryFilteredBotes) { b ->
                                BoteFinalItem(b, onSelect)
                            }
                            if (queryFilteredBotes.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("No se encontraron botes con '$query'", color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HierarchicalItem(label: String, isExpanded: Boolean, onClick: () -> Unit, level: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = (level * 8).dp)
            .clickable { onClick() },
        color = if (isExpanded) Color(0xFFE3F2FD).copy(alpha = 0.5f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = if (isExpanded) Color(0xFF003366) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                fontSize = (14 - level).sp,
                fontWeight = if (isExpanded) FontWeight.Black else FontWeight.Bold,
                color = if (isExpanded) Color(0xFF003366) else Color.DarkGray
            )
        }
    }
}

@Composable
fun BoteFinalItem(b: BoteMaestroDto, onSelect: (BoteMaestroDto) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 24.dp)
            .clickable { onSelect(b) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F3F5)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsBoat, null, tint = Color(0xFF003366), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(b.nombre ?: "S/N", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("RPA: ${b.nrpa ?: "—"} · MAT: ${b.nmatricula ?: "—"}", fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00897B), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OperacionDataDialog(
    opInitial: OperacionDto,
    regionLabel: String?,
    especiesMaestras: List<EspecieDto>,
    initialSelectedBoteKey: String? = null,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var op by remember(opInitial.id) { mutableStateOf(opInitial) }
    var tab by remember { mutableStateOf("DENSIDAD") }
    var selectedBoteKey by remember(opInitial.id, initialSelectedBoteKey) { mutableStateOf(initialSelectedBoteKey) }
    var unidadTipo by remember { mutableStateOf("transecto") }

    LaunchedEffect(opInitial.id) {
        if (AppState.forceOffline || AppState.authToken.isNullOrBlank()) return@LaunchedEffect
        try {
            val fresh = DataManager.refreshOperacionDetail(ctx, opInitial.id)
            if (fresh != null) {
                op = fresh
            }
        } catch (_: Exception) {
        }
    }

    val botes = op.botes ?: emptyList()
    val selectedBote = remember(op, selectedBoteKey) {
        if (botes.isEmpty()) return@remember null
        val key = selectedBoteKey
        val found = if (key != null) botes.firstOrNull { boteSelectionKey(it) == key } else null
        found ?: botes.first()
    }
    LaunchedEffect(op.id, botes.size) {
        if (selectedBoteKey == null && botes.isNotEmpty()) {
            selectedBoteKey = boteSelectionKey(botes.first())
        }
        if (unidadTipo.isBlank()) {
            unidadTipo = "transecto"
        }
    }

    val especiesById = remember(especiesMaestras) { especiesMaestras.associateBy { it.id } }

    fun updateOperacion(next: OperacionDto) {
        op = next
        DataManager.upsertOperacionInMemory(next)
        if (!AppState.isEffectivelyOnline()) {
            DataManager.markOperacionDirty(next.id)
        }
        DataManager.persistCache(ctx)
    }

    fun updateSelectedBote(transform: (OperacionBoteDto) -> OperacionBoteDto) {
        val curr = selectedBote ?: return
        val nextBotes = botes.map { b ->
            if (boteSelectionKey(b) == boteSelectionKey(curr)) transform(b) else b
        }
        updateOperacion(op.copy(botes = nextBotes))
    }

    fun syncOperacionIfOnline() {
        if (!AppState.isEffectivelyOnline()) return
        val snapshot = op
        scope.launch {
            val ok = DataManager.tryUploadOperacion(ctx, snapshot)
            if (!ok) {
                DataManager.markOperacionDirty(snapshot.id)
                DataManager.persistCache(ctx)
            }
        }
    }

    var lpIngresoSpeciesId by remember { mutableStateOf<Int?>(null) }
    var showDensitySpeciesPicker by remember { mutableStateOf(false) }
    var showDensityEditor by remember { mutableStateOf(false) }
    val densitySelectedSpeciesIds = remember { mutableStateListOf<Int>() }
    val densityMuestreoBySpeciesId = remember { mutableStateMapOf<Int, Set<String>>() }
    val densityTransectosList = remember { mutableStateListOf<DensidadUnidadDto>() }

    LaunchedEffect(selectedBoteKey, op.id) {
        val bote = selectedBote
        if (bote == null) {
            densitySelectedSpeciesIds.clear()
            densityMuestreoBySpeciesId.clear()
            densityTransectosList.clear()
            return@LaunchedEffect
        }
        prepararEstadoEdicionBote(
            bote = bote,
            selectedSpeciesIds = densitySelectedSpeciesIds,
            muestreoBySpeciesId = densityMuestreoBySpeciesId,
            transectosList = densityTransectosList,
        )
    }

    fun ensureDensityEditorReady() {
        val bote = selectedBote ?: return
        if (unidadTipo == "cuadrante" || bote.densTipo.equals("Cuadrante", true)) {
            showDensityEditor = true
            return
        }
        val hasDensitySpecies = densitySelectedSpeciesIds.any { sid ->
            densityMuestreoBySpeciesId[sid]?.contains("DENSIDAD") != false
        }
        if (!hasDensitySpecies) {
            showDensitySpeciesPicker = true
            return
        }
        if (densityTransectosList.isEmpty()) {
            densityTransectosList.add(
                DensidadUnidadDto(
                    num = 1,
                    tipo = bote.densTipo ?: "Transecto",
                    area = 120.0,
                ),
            )
        }
        showDensityEditor = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 12.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(colors = listOf(Color(0xFF003366), Color(0xFF00509E))))
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                        Text(
                            "DATOS DE OPERACIÓN",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            op.id,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }

                Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF8F9FA),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(regionLabel ?: "Sin región", fontWeight = FontWeight.Black, color = Color(0xFF003366), fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${op.sector} · ${op.fechaInicio.orEmpty()}${if (!op.fechaFin.isNullOrBlank()) " → ${op.fechaFin}" else ""}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            if (!op.org.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(op.org.orEmpty(), fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tab == "DENSIDAD",
                            onClick = { tab = "DENSIDAD" },
                            label = { Text("Densidad") }
                        )
                        FilterChip(
                            selected = tab == "LP",
                            onClick = { tab = "LP" },
                            label = { Text("Peso-Longitud") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (botes.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFFF8F9FA),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Sin datos", fontWeight = FontWeight.Black, color = Color(0xFF003366), fontSize = 16.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Esta operación no tiene botes registrados.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        val scrollX = rememberScrollState()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollX),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            botes.sortedBy { it.zona ?: 0 }.forEach { b ->
                                val isSel = selectedBoteKey == boteSelectionKey(b)
                                AssistChip(
                                    onClick = {
                                        selectedBoteKey = boteSelectionKey(b)
                                        val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                                        unidadTipo = dens
                                    },
                                    label = {
                                        Text(
                                            "Zona ${b.zona ?: 0} · ${(b.nombre ?: "S/N").take(18)}",
                                            fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isSel) Color(0xFFE3F2FD) else Color(0xFFF8F9FA),
                                        labelColor = Color(0xFF003366)
                                    ),
                                    border = BorderStroke(1.dp, if (isSel) Color(0xFF003366) else Color(0xFFF1F3F5))
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (tab == "DENSIDAD") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = unidadTipo == "transecto",
                                    onClick = { unidadTipo = "transecto" },
                                    label = { Text("Transecto") }
                                )
                                FilterChip(
                                    selected = unidadTipo == "cuadrante",
                                    onClick = { unidadTipo = "cuadrante" },
                                    label = { Text("Cuadrante") }
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            val densitySpeciesIds = remember(densitySelectedSpeciesIds, densityMuestreoBySpeciesId) {
                                densitySelectedSpeciesIds.filter { sid ->
                                    densityMuestreoBySpeciesId[sid]?.contains("DENSIDAD") != false
                                }.distinct().sortedWith(compareBy({ especiesById[it]?.com ?: "ZZZ" }, { it }))
                            }
                            val densityUnitLabel = if (unidadTipo == "cuadrante") "cuadrantes" else "transectos"
                            val isQuadrantOverview = unidadTipo == "cuadrante"

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD).copy(alpha = 0.8f)),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, Color(0xFFBBDEFB))
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = Color(0xFF1565C0), modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        if (isQuadrantOverview) {
                                            "Crea cuadrantes indicando cantidad, area, sustrato y especie. Luego completa los conteos."
                                        } else {
                                            "Paso 1: toca \"Especies\" y marca DENSIDAD. Paso 2: crea los $densityUnitLabel. Paso 3: completa area, sustrato y conteos."
                                        },
                                        fontSize = 12.sp,
                                        color = Color(0xFF1565C0),
                                        lineHeight = 17.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isQuadrantOverview) {
                                    OutlinedButton(
                                        onClick = { showDensitySpeciesPicker = true },
                                        shape = RoundedCornerShape(26.dp),
                                        border = BorderStroke(1.5.dp, Color(0xFF003366))
                                    ) {
                                        Text("1. Especies", fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                                    }
                                }
                                Button(
                                    onClick = { ensureDensityEditorReady() },
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366))
                                ) {
                                    Text(
                                        when {
                                            isQuadrantOverview && selectedBote?.transectos?.any { it.tipo.equals("cuadrante", true) } == true -> "Editar cuadrantes"
                                            isQuadrantOverview -> "Crear cuadrantes"
                                            densitySpeciesIds.isEmpty() -> "2. Crear $densityUnitLabel"
                                            selectedBote?.transectos.isNullOrEmpty() -> "2. Crear $densityUnitLabel"
                                            else -> "Editar $densityUnitLabel"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (isQuadrantOverview) {
                                        "${selectedBote?.transectos?.count { it.tipo.equals("cuadrante", true) } ?: 0} cuadrante(s)"
                                    } else {
                                        "${densitySpeciesIds.size} especie(s)"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            val unitsAll = selectedBote?.transectos ?: emptyList()
                            val units = unitsAll.filter { (it.tipo ?: "transecto").equals(unidadTipo, true) }
                            val speciesIds = remember(unitsAll, unidadTipo, especiesMaestras.size) {
                                units.flatMap { it.counts?.keys?.mapNotNull { k -> k.toIntOrNull() } ?: emptyList() }
                                    .distinct()
                                    .sortedWith(compareBy({ especiesById[it]?.com ?: "ZZZ" }, { it }))
                            }

                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                            ) {
                                if (units.isEmpty()) {
                                    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "Aun no hay $densityUnitLabel registrados",
                                                color = Color.Gray,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                if (isQuadrantOverview) {
                                                    "Abre el editor y crea los cuadrantes con cantidad, area, sustrato y especie."
                                                } else {
                                                    "Primero selecciona especies para DENSIDAD y luego crea los $densityUnitLabel."
                                                },
                                                color = Color.Gray,
                                                textAlign = TextAlign.Center,
                                                fontSize = 12.sp
                                            )
                                            Spacer(Modifier.height(14.dp))
                                            if (!isQuadrantOverview) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    OutlinedButton(
                                                        onClick = { showDensitySpeciesPicker = true },
                                                        shape = RoundedCornerShape(22.dp),
                                                        border = BorderStroke(1.dp, Color(0xFF003366))
                                                    ) {
                                                        Text("Especies", color = Color(0xFF003366), fontWeight = FontWeight.Bold)
                                                    }
                                                    Button(
                                                        onClick = { ensureDensityEditorReady() },
                                                        shape = RoundedCornerShape(22.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366))
                                                    ) {
                                                        Text("Crear $densityUnitLabel", fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val tableScroll = rememberScrollState()
                                    val tableWidth = (40 + 70 + 100 + (speciesIds.size * 82) + 24).dp
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .horizontalScroll(tableScroll)
                                    ) {
                                        Column(modifier = Modifier.width(tableWidth)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFF1F3F5))
                                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("#", modifier = Modifier.width(40.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                                Text("ÁREA", modifier = Modifier.width(70.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                                Text("SUSTRATO", modifier = Modifier.width(100.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                                speciesIds.forEach { sid ->
                                                    val name = especiesById[sid]?.com ?: "ID$sid"
                                                    Text(
                                                        name.take(8).uppercase(),
                                                        modifier = Modifier.width(82.dp),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color.DarkGray,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }

                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                itemsIndexed(units) { idx, u ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text((u.num ?: (idx + 1)).toString(), modifier = Modifier.width(40.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text((u.area ?: 0.0).toString(), modifier = Modifier.width(70.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                                                        Text(u.sustrato.orEmpty(), modifier = Modifier.width(100.dp), fontSize = 12.sp, maxLines = 1)
                                                        val counts = u.counts ?: emptyMap()
                                                        speciesIds.forEach { sid ->
                                                            val v = counts[sid.toString()] ?: 0
                                                            Text(
                                                                v.toString(),
                                                                modifier = Modifier.width(82.dp),
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = Color(0xFF00897B),
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                    HorizontalDivider(color = Color(0xFFF1F3F5))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val lpMap = selectedBote?.lpMuestras ?: emptyMap()
                            val selectedLpIds = remember(lpMap) {
                                lpMap.keys.mapNotNull { it.toIntOrNull() }.distinct().sortedBy { especiesById[it]?.com ?: "" }
                            }
                            val totalLpSamples = remember(lpMap, selectedLpIds) {
                                selectedLpIds.sumOf { sid -> ((lpMap[sid.toString()] ?: emptyMap())["LP"] ?: emptyList()).size }
                            }

                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${selectedLpIds.size} especie(s)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            color = Color(0xFFE8F5E9),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(totalLpSamples.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                                                Text("Muestras", fontSize = 8.sp, color = Color(0xFF2E7D32))
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF1F3F5))
                                            .padding(vertical = 10.dp, horizontal = 12.dp)
                                    ) {
                                        Text("ESPECIE", modifier = Modifier.weight(1.6f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                        Text("MUESTRAS", modifier = Modifier.weight(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                        Text("TIPO", modifier = Modifier.weight(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Spacer(modifier = Modifier.width(88.dp))
                                    }

                                    if (selectedLpIds.isEmpty()) {
                                        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                "No hay especies habilitadas para Peso-Longitud en este bote.",
                                                color = Color.Gray,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(selectedLpIds) { sid ->
                                                val sp = especiesById[sid]
                                                val spName = sp?.com ?: "ID$sid"
                                                val buckets = lpMap[sid.toString()] ?: emptyMap()
                                                val lpList = buckets["LP"] ?: emptyList()
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1.6f)) {
                                                        Text(spName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                                                        val sci = sp?.sci
                                                        if (!sci.isNullOrBlank()) {
                                                            Text(sci, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                                        }
                                                    }
                                                    Text(lpList.size.toString(), modifier = Modifier.weight(0.6f), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF00897B), textAlign = TextAlign.Center)
                                                    Text("L-P", modifier = Modifier.weight(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF00897B), textAlign = TextAlign.Center)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Button(
                                                        onClick = { lpIngresoSpeciesId = sid },
                                                        modifier = Modifier.width(88.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                                                    ) { Text("Ingresar", fontWeight = FontWeight.Black, fontSize = 12.sp) }
                                                }
                                                HorizontalDivider(color = Color(0xFFF1F3F5))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.5.dp, Color.Gray)
                ) { Text("CERRAR", fontWeight = FontWeight.Bold, color = Color.Gray) }
            }
        }
    }

    val sid = lpIngresoSpeciesId
    if (sid != null && selectedBote != null) {
        LpIngresoDialog(
            speciesId = sid,
            speciesName = (especiesById[sid]?.com ?: "ID$sid"),
            currentSamples = ((selectedBote.lpMuestras ?: emptyMap())[sid.toString()] ?: emptyMap())["LP"] ?: emptyList(),
            onDismiss = { lpIngresoSpeciesId = null },
            onUpdateSamples = { nextSamples ->
                updateSelectedBote { b ->
                    val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                    val buckets = (lpM[sid.toString()] ?: emptyMap()).toMutableMap()
                    buckets["LP"] = nextSamples
                    lpM[sid.toString()] = buckets.toMap()
                    b.copy(lpMuestras = lpM.toMap())
                }
                syncOperacionIfOnline()
            },
            onRemoveSpecies = {
                updateSelectedBote { b ->
                    val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                    lpM.remove(sid.toString())
                    b.copy(lpMuestras = lpM.toMap())
                }
                syncOperacionIfOnline()
                lpIngresoSpeciesId = null
            }
        )
    }

    if (showDensitySpeciesPicker && selectedBote != null) {
        Dialog(
            onDismissRequest = { showDensitySpeciesPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(20.dp),
                color = if (isSystemInDarkTheme()) Color(0xFF111B2B) else Color.White,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(colors = listOf(Color(0xFF003366), Color(0xFF00509E))))
                            .padding(20.dp)
                    ) {
                        Text(
                            "ESPECIES A MUESTREAR",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        IconButton(
                            onClick = { showDensitySpeciesPicker = false },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }

                    Column(modifier = Modifier.padding(20.dp).weight(1f)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD).copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFBBDEFB))
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFF1565C0), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "Selecciona las especies para el bote ${selectedBote.nombre?.uppercase()}. Marca DENSIDAD y/o L-P segun corresponda.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1565C0),
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            val chunkedEspecies = especiesMaestras.sortedBy { it.com }.chunked(3)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(chunkedEspecies) { rowEspecies ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowEspecies.forEach { especie ->
                                            val sidLocal = especie.id
                                            val selected = densitySelectedSpeciesIds.contains(sidLocal)
                                            SpeciesGridItem(
                                                especie = especie,
                                                isSelected = selected,
                                                onClick = {
                                                    if (selected) {
                                                        densitySelectedSpeciesIds.remove(sidLocal)
                                                        densityMuestreoBySpeciesId.remove(sidLocal)
                                                    } else {
                                                        densitySelectedSpeciesIds.add(sidLocal)
                                                        densityMuestreoBySpeciesId[sidLocal] = setOf("L-P", "DENSIDAD")
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - rowEspecies.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            "TIPOS DE MUESTREO POR ESPECIE",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            modifier = Modifier
                                .heightIn(max = 170.dp)
                                .fillMaxWidth(),
                            color = Color(0xFFF8F9FA),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                        ) {
                            LazyColumn(modifier = Modifier.padding(12.dp)) {
                                items(densitySelectedSpeciesIds.sortedWith(compareBy({ especiesById[it]?.com ?: "ZZZ" }, { it }))) { sidLocal ->
                                    val especie = especiesById[sidLocal] ?: return@items
                                    val muestreos = densityMuestreoBySpeciesId[sidLocal] ?: setOf("L-P", "DENSIDAD")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(especie.com, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                                            if (!especie.sci.isNullOrBlank()) {
                                                Text(especie.sci, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                                            }
                                        }
                                        FlowRow(
                                            modifier = Modifier.padding(start = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            maxItemsInEachRow = 2
                                        ) {
                                            FilterChip(
                                                selected = muestreos.contains("L-P"),
                                                onClick = {
                                                    val next = if (muestreos.contains("L-P")) muestreos - "L-P" else muestreos + "L-P"
                                                    densityMuestreoBySpeciesId[sidLocal] = next
                                                },
                                                label = { Text("L-P", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                                            )
                                            FilterChip(
                                                selected = muestreos.contains("DENSIDAD"),
                                                onClick = {
                                                    val next = if (muestreos.contains("DENSIDAD")) muestreos - "DENSIDAD" else muestreos + "DENSIDAD"
                                                    densityMuestreoBySpeciesId[sidLocal] = next
                                                },
                                                label = { Text("DENSIDAD", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                                            )
                                        }
                                    }
                                }
                                if (densitySelectedSpeciesIds.isEmpty()) {
                                    item {
                                        Text(
                                            "No hay especies seleccionadas.",
                                            fontSize = 13.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDensitySpeciesPicker = false },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(1.5.dp, Color.Gray)
                        ) {
                            Text("CANCELAR", fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                val lpIds = densitySelectedSpeciesIds.filter { sidLocal ->
                                    densityMuestreoBySpeciesId[sidLocal]?.contains("L-P") != false
                                }
                                updateSelectedBote { b ->
                                    val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                                    lpIds.forEach { sidLocal ->
                                        lpM.putIfAbsent(sidLocal.toString(), emptyMap())
                                    }
                                    lpM.keys.toList().forEach { key ->
                                        val keyId = key.toIntOrNull()
                                        if (keyId != null && !lpIds.contains(keyId)) {
                                            lpM.remove(key)
                                        }
                                    }
                                    b.copy(lpMuestras = lpM.toMap())
                                }
                                syncOperacionIfOnline()
                                showDensitySpeciesPicker = false
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366)),
                        ) {
                            Text("CONTINUAR", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    TransectosDialog(
        show = showDensityEditor,
        currentBote = selectedBote,
        especiesMaestras = especiesMaestras,
        transectosList = densityTransectosList,
        selectedSpeciesIds = densitySelectedSpeciesIds.toList(),
        muestreoBySpeciesId = densityMuestreoBySpeciesId.toMap(),
        initialTab = "DENSIDAD",
        onDismiss = { showDensityEditor = false },
        onSaveDensity = { normalizedTransectos ->
            updateSelectedBote { b ->
                val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                b.copy(
                    transectos = normalizedTransectos.map { t ->
                        t.copy(tipo = if (dens == "cuadrante") "cuadrante" else "transecto")
                    }
                )
            }
            syncOperacionIfOnline()
            showDensityEditor = false
        },
        onUpdateLpSamples = { speciesId, nextSamples ->
            updateSelectedBote { b ->
                val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                val buckets = (lpM[speciesId.toString()] ?: emptyMap()).toMutableMap()
                buckets["LP"] = nextSamples
                lpM[speciesId.toString()] = buckets.toMap()
                b.copy(lpMuestras = lpM.toMap())
            }
            syncOperacionIfOnline()
        },
        onRemoveLpSpecies = { speciesId ->
            updateSelectedBote { b ->
                val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                lpM.remove(speciesId.toString())
                b.copy(lpMuestras = lpM.toMap())
            }
            syncOperacionIfOnline()
        },
    )
}

@Composable
private fun SpeciesPickerDialog(
    species: List<EspecieDto>,
    currentSelectedIds: Set<Int>,
    onDismiss: () -> Unit,
    onApply: (Set<Int>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val selected = remember(currentSelectedIds) { mutableStateListOf<Int>().apply { addAll(currentSelectedIds) } }
    val filtered = remember(species, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) species
        else species.filter {
            it.com.lowercase().contains(q) ||
                        (it.sci ?: "").lowercase().contains(q)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Seleccionar especies", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF003366))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar por nombre…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(filtered) { sp ->
                        val id = sp.id
                        val isSel = selected.contains(id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selected.contains(id)) selected.remove(id) else selected.add(id)
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSel,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selected.contains(id)) selected.add(id)
                                    } else {
                                        selected.remove(id)
                                    }
                                }
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sp.com, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF003366))
                                val sci = sp.sci
                                if (!sci.isNullOrBlank()) {
                                    Text(sci, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF1F3F5))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp)
                    ) { Text("Cancelar") }
                    Button(
                        onClick = { onApply(selected.toSet()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                    ) { Text("Aplicar", fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
private fun LpIngresoDialog(
    speciesId: Int,
    speciesName: String,
    currentSamples: List<LpSampleDto>,
    onDismiss: () -> Unit,
    onUpdateSamples: (List<LpSampleDto>) -> Unit,
    onRemoveSpecies: () -> Unit
) {
    var lText by remember(speciesId) { mutableStateOf("") }
    var pText by remember(speciesId) { mutableStateOf("") }
    var samples by remember(speciesId, currentSamples) { mutableStateOf(currentSamples) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var editL by remember { mutableStateOf("") }
    var editP by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val longitudFocusRequester = remember(speciesId) { FocusRequester() }
    val pesoFocusRequester = remember(speciesId) { FocusRequester() }
    val editLongitudFocusRequester = remember(editIndex) { FocusRequester() }
    val editPesoFocusRequester = remember(editIndex) { FocusRequester() }

    fun parseNumber(s: String): Double? {
        val t = s.trim().replace(",", ".")
        return t.toDoubleOrNull()
    }

    fun applySamples(next: List<LpSampleDto>) {
        samples = next
        onUpdateSamples(next)
    }

    fun submitSample() {
        val l = parseNumber(lText)
        val p = parseNumber(pText)
        when {
            l == null -> longitudFocusRequester.requestFocus()
            p == null -> pesoFocusRequester.requestFocus()
            else -> {
                applySamples(listOf(LpSampleDto(l = l, p = p)) + samples)
                lText = ""
                pText = ""
                longitudFocusRequester.requestFocus()
            }
        }
    }

    fun saveEditedSample(index: Int) {
        val l = parseNumber(editL)
        val p = parseNumber(editP)
        when {
            l == null -> editLongitudFocusRequester.requestFocus()
            p == null -> editPesoFocusRequester.requestFocus()
            else -> {
                val next = samples.toMutableList()
                next[index] = LpSampleDto(l = l, p = p)
                applySamples(next)
                editIndex = null
                focusManager.clearFocus()
            }
        }
    }

    LaunchedEffect(speciesId) {
        longitudFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(18.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(speciesName, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF003366))
                        Text("Peso-Longitud · ${samples.size} muestra(s)", fontSize = 12.sp, color = Color.Gray)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFF2FBF8),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE0F2F1))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = lText,
                            onValueChange = { lText = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(longitudFocusRequester),
                            label = { Text("Longitud (mm)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { pesoFocusRequester.requestFocus() }
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = pText,
                            onValueChange = { pText = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(pesoFocusRequester),
                            label = { Text("Peso (g)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitSample() }
                            ),
                            singleLine = true
                        )
                        Button(
                            onClick = { submitSample() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                        ) { Text("Agregar", fontWeight = FontWeight.Black) }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F3F5))
                        .padding(vertical = 10.dp, horizontal = 12.dp)
                ) {
                    Text("#", modifier = Modifier.width(44.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                    Text("L (mm)", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                    Text("P (g)", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                    Spacer(modifier = Modifier.width(140.dp))
                }

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(samples) { idx, s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text((samples.size - idx).toString(), modifier = Modifier.width(44.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text((s.l ?: 0.0).toString(), modifier = Modifier.weight(1f), fontSize = 12.sp)
                            Text((s.p ?: 0.0).toString(), modifier = Modifier.weight(1f), fontSize = 12.sp)
                            Spacer(Modifier.weight(0.1f))
                            OutlinedButton(
                                onClick = {
                                    editIndex = idx
                                    editL = (s.l ?: "").toString()
                                    editP = (s.p ?: "").toString()
                                },
                                modifier = Modifier.width(66.dp).height(34.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Editar", fontSize = 11.sp) }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { applySamples(samples.toMutableList().also { it.removeAt(idx) }) },
                                modifier = Modifier.width(74.dp).height(34.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                            ) { Text("Eliminar", fontSize = 11.sp) }
                        }
                        HorizontalDivider(color = Color(0xFFF1F3F5))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(999.dp)
                    ) { Text("Cerrar") }
                    OutlinedButton(
                        onClick = onRemoveSpecies,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                    ) { Text("Quitar especie") }
                }
            }
        }
    }

    val idx = editIndex
    if (idx != null && idx >= 0 && idx < samples.size) {
        LaunchedEffect(idx) {
            editLongitudFocusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { editIndex = null },
            title = { Text("Editar muestra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editL,
                        onValueChange = { editL = it },
                        modifier = Modifier.focusRequester(editLongitudFocusRequester),
                        label = { Text("Longitud (mm)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { editPesoFocusRequester.requestFocus() }
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editP,
                        onValueChange = { editP = it },
                        modifier = Modifier.focusRequester(editPesoFocusRequester),
                        label = { Text("Peso (g)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { saveEditedSample(idx) }
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { saveEditedSample(idx) }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { editIndex = null }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDatePickerDialog(onDateSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate(); val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd"); onDateSelected(date.format(fmt)) }; onDismiss() }) { Text("OK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }) { DatePicker(state = datePickerState) }
}
