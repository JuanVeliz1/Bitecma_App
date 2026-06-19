package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.FlowRow
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager
import com.bitecma.app.network.DensidadUnidadDto
import com.bitecma.app.network.EspecieDto
import com.bitecma.app.network.LpSampleDto
import com.bitecma.app.network.OperacionBoteDto
import com.bitecma.app.network.OperacionDto
import kotlinx.coroutines.launch

private fun extractedOpDataBoteSelectionKey(bote: OperacionBoteDto): String {
    return listOfNotNull(bote.id?.takeIf { it.isNotBlank() }, bote.zona?.toString(), bote.nombre, bote.buzo)
        .joinToString("|")
}

private data class ExtractedDataDialogLpIngresoTarget(
    val speciesId: Int,
    val sampleKind: String,
)

private fun extractedOpDataDensTipoPluralLabel(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", true)) "Cuadrantes" else "Transectos"
}

private fun extractedOpDataNormalizeDensTipo(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", true)) "cuadrante" else "transecto"
}

private fun extractedOpDataBoteDisplayName(bote: OperacionBoteDto): String {
    val isIntermareal = bote.submareal == 0 || bote.nombre?.equals("Intermareal", ignoreCase = true) == true
    return if (isIntermareal) bote.buzo?.takeIf { it.isNotBlank() } ?: "SIN BUZO" else bote.nombre ?: "S/N"
}

private fun extractedOpDataCreateDefaultDensityUnits(
    densTipo: String?,
    selectedSpeciesIds: List<Int> = emptyList(),
    count: Int = 6,
): List<DensidadUnidadDto> {
    return if (extractedOpDataNormalizeDensTipo(densTipo) == "cuadrante") {
        emptyList()
    } else {
        val defaultCounts = selectedSpeciesIds
            .distinct()
            .sorted()
            .takeIf { it.isNotEmpty() }
            ?.associate { sid -> sid.toString() to 0 }
        (1..count).map { num ->
            DensidadUnidadDto(
                num = num,
                tipo = "transecto",
                area = 120.0,
                counts = defaultCounts,
            )
        }
    }
}

private fun extractedOpDataSpeciesIdsFromCounts(counts: Map<String, Int>?): List<Int> {
    return counts.orEmpty().keys.mapNotNull { it.toIntOrNull() }.distinct().sorted()
}

private fun extractedOpDataPrepareBoteState(
    bote: OperacionBoteDto,
    selectedSpeciesIds: MutableList<Int>,
    muestreoBySpeciesId: MutableMap<Int, Set<String>>,
    transectosList: MutableList<DensidadUnidadDto>,
) {
    prepararEstadoEdicionBote(
        bote = bote,
        selectedSpeciesIds = selectedSpeciesIds,
        muestreoBySpeciesId = muestreoBySpeciesId,
        transectosList = transectosList,
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ExtractedOperacionDataDialog(
    opInitial: OperacionDto,
    regionLabel: String?,
    especiesMaestras: List<EspecieDto>,
    initialSelectedBoteKey: String? = null,
    onOperacionUpdated: (OperacionDto) -> Unit = {},
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
            if (fresh != null && op == opInitial) {
                op = fresh
                onOperacionUpdated(fresh)
            }
        } catch (_: Exception) {
        }
    }

    val botes = op.botes ?: emptyList()
    val selectedBote = remember(op, selectedBoteKey) {
        if (botes.isEmpty()) return@remember null
        val key = selectedBoteKey
        val found = if (key != null) botes.firstOrNull { extractedOpDataBoteSelectionKey(it) == key } else null
        found ?: botes.first()
    }
    LaunchedEffect(op.id, botes.size, selectedBoteKey, selectedBote?.densTipo) {
        if (selectedBoteKey == null && botes.isNotEmpty()) {
            selectedBoteKey = extractedOpDataBoteSelectionKey(botes.first())
        }
        selectedBote?.let { bote ->
            unidadTipo = extractedOpDataNormalizeDensTipo(bote.densTipo)
        }
    }

    val especiesById = remember(especiesMaestras) { especiesMaestras.associateBy { it.id } }

    fun updateOperacion(next: OperacionDto) {
        op = next
        onOperacionUpdated(next)
        DataManager.upsertOperacionInMemory(next)
        if (!AppState.isEffectivelyOnline()) {
            DataManager.markOperacionDirty(next.id)
        }
        DataManager.persistCache(ctx)
    }

    fun updateSelectedBote(transform: (OperacionBoteDto) -> OperacionBoteDto): OperacionDto? {
        val curr = selectedBote ?: return null
        val nextBotes = botes.map { b ->
            if (extractedOpDataBoteSelectionKey(b) == extractedOpDataBoteSelectionKey(curr)) transform(b) else b
        }
        val nextOp = op.copy(botes = nextBotes)
        updateOperacion(nextOp)
        return nextOp
    }

    fun syncOperacionIfOnline(snapshot: OperacionDto = op) {
        if (!AppState.isEffectivelyOnline()) return
        scope.launch {
            val ok = DataManager.tryUploadOperacion(ctx, snapshot)
            if (!ok) {
                DataManager.markOperacionDirty(snapshot.id)
                DataManager.persistCache(ctx)
            } else {
                op = DataManager.operacionesBd.firstOrNull { it.id == snapshot.id }
                    ?: DataManager.operacionesLc.firstOrNull { it.id == snapshot.id }
                    ?: snapshot
                onOperacionUpdated(op)
            }
        }
    }

    var lpIngresoTarget by remember { mutableStateOf<ExtractedDataDialogLpIngresoTarget?>(null) }
    var showLpSpeciesPicker by remember { mutableStateOf(false) }
    var showDensityEditor by remember { mutableStateOf(false) }
    val densitySelectedSpeciesIds = remember { mutableStateListOf<Int>() }
    val densityMuestreoBySpeciesId = remember { mutableStateMapOf<Int, Set<String>>() }
    val densityTransectosList = remember { mutableStateListOf<DensidadUnidadDto>() }

    LaunchedEffect(selectedBoteKey, op.id, selectedBote?.transectos, selectedBote?.lpMuestras) {
        val bote = selectedBote
        if (bote == null) {
            densitySelectedSpeciesIds.clear()
            densityMuestreoBySpeciesId.clear()
            densityTransectosList.clear()
            return@LaunchedEffect
        }
        extractedOpDataPrepareBoteState(
            bote = bote,
            selectedSpeciesIds = densitySelectedSpeciesIds,
            muestreoBySpeciesId = densityMuestreoBySpeciesId,
            transectosList = densityTransectosList,
        )
    }

    fun ensureDensityEditorReady() {
        val bote = selectedBote ?: return
        if (densityTransectosList.isEmpty()) {
            densityTransectosList.addAll(
                extractedOpDataCreateDefaultDensityUnits(
                    densTipo = bote.densTipo,
                    selectedSpeciesIds = densitySpeciesIdsForDraft(bote),
                )
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
                            "DATOS DE OPERACION",
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(regionLabel ?: "Sin región", fontWeight = FontWeight.Black, color = Color(0xFF003366), fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${op.sector} · ${op.fechaInicio.orEmpty()}${if (!op.fechaFin.isNullOrBlank()) " -> ${op.fechaFin}" else ""}",
                            fontSize = 12.sp,
                            color = Color.Gray
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
                                val isSel = selectedBoteKey == extractedOpDataBoteSelectionKey(b)
                                AssistChip(
                                    onClick = {
                                        selectedBoteKey = extractedOpDataBoteSelectionKey(b)
                                        val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                                        unidadTipo = dens
                                    },
                                    label = {
                                        Text(
                                            "Zona ${b.zona ?: 0} · ${extractedOpDataBoteDisplayName(b).take(18)}",
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

                        if (tab == "DENSIDAD") {
                            val densityUnitLabel = if (unidadTipo == "cuadrante") "cuadrantes" else "transectos"
                            val isQuadrantOverview = unidadTipo == "cuadrante"
                            val unitsAll = selectedBote?.transectos ?: emptyList()
                            val units = unitsAll.filter { extractedOpDataNormalizeDensTipo(it.tipo) == unidadTipo }
                            val speciesIds = remember(unitsAll, unidadTipo, especiesMaestras.size) {
                                units.flatMap { extractedOpDataSpeciesIdsFromCounts(it.counts) }
                                    .distinct()
                                    .sortedWith(compareBy({ especiesById[it]?.com ?: "ZZZ" }, { it }))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    extractedOpDataDensTipoPluralLabel(selectedBote?.densTipo),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF003366)
                                )
                                Button(
                                    onClick = { ensureDensityEditorReady() },
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366))
                                ) {
                                    Text(
                                        if (isQuadrantOverview) "Agregar Cuadrante" else "Agregar Transecto",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${units.size} ${if (isQuadrantOverview) "unidad(es)" else "transecto(s)"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }

                            Spacer(Modifier.height(10.dp))

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
                                                    "Abre el formulario y crea cuadrantes indicando cantidad, area, sustrato y especie."
                                                } else {
                                                    "Abre el formulario para crear transectos, replicar el primero y definir especies por cada transecto."
                                                },
                                                color = Color.Gray,
                                                textAlign = TextAlign.Center,
                                                fontSize = 12.sp
                                            )
                                            Spacer(Modifier.height(14.dp))
                                            Button(
                                                onClick = { ensureDensityEditorReady() },
                                                shape = RoundedCornerShape(22.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366))
                                            ) {
                                                Text(if (isQuadrantOverview) "Agregar cuadrante" else "Agregar transecto", fontWeight = FontWeight.Bold)
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
                                                Text("AREA", modifier = Modifier.width(70.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
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
                            val lpSpeciesIds = remember(lpMap, especiesMaestras.size) {
                                lpMap.keys
                                    .mapNotNull { it.toIntOrNull() }
                                    .distinct()
                                    .sortedWith(compareBy({ especiesById[it]?.com ?: "ZZZ" }, { it }))
                            }
                            val lpRows = remember(lpMap, especiesMaestras.size) {
                                lpSpeciesIds.flatMap { sid ->
                                    val species = especiesById[sid]
                                    val normalizedBuckets = normalizeLpBuckets(lpMap[sid.toString()])
                                    orderedLpKinds(lpMap[sid.toString()], species).map { kind ->
                                        Triple(sid, kind, normalizedBuckets[kind].orEmpty().size)
                                    }
                                }
                            }
                            val selectedLpIds = remember(lpMap) {
                                lpMap.keys.mapNotNull { it.toIntOrNull() }.distinct().sortedBy { especiesById[it]?.com ?: "" }
                            }
                            val totalLpSamples = remember(lpMap, selectedLpIds) {
                                selectedLpIds.sumOf { sid ->
                                    normalizeLpBuckets(lpMap[sid.toString()]).values.sumOf { samples -> samples.size }
                                }
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
                                        OutlinedButton(
                                            onClick = { showLpSpeciesPicker = true },
                                            shape = RoundedCornerShape(18.dp),
                                            border = BorderStroke(1.dp, Color(0xFF003366)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF003366))
                                        ) {
                                            Text("+ Especies", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Spacer(Modifier.width(8.dp))
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

                                    if (lpRows.isEmpty()) {
                                        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                "No hay especies habilitadas para Peso-Longitud en este bote.",
                                                color = Color.Gray,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(lpRows) { (sid, sampleKind, sampleCount) ->
                                                val sp = especiesById[sid]
                                                val spName = sp?.com ?: "ID$sid"
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
                                                    Text(sampleCount.toString(), modifier = Modifier.weight(0.6f), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF00897B), textAlign = TextAlign.Center)
                                                    Text(lpKindLabel(sampleKind), modifier = Modifier.weight(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF00897B), textAlign = TextAlign.Center)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Button(
                                                        onClick = {
                                                            lpIngresoTarget = ExtractedDataDialogLpIngresoTarget(
                                                                speciesId = sid,
                                                                sampleKind = sampleKind,
                                                            )
                                                        },
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

    val lpTarget = lpIngresoTarget
    if (lpTarget != null && selectedBote != null) {
        val sid = lpTarget.speciesId
        val sampleKind = normalizeLpKind(lpTarget.sampleKind)
        val currentBuckets = normalizeLpBuckets((selectedBote.lpMuestras ?: emptyMap())[sid.toString()])
        ExtractedLpIngresoDialog(
            speciesId = sid,
            sampleKind = sampleKind,
            speciesName = (especiesById[sid]?.com ?: "ID$sid"),
            currentSamples = currentBuckets[sampleKind].orEmpty(),
            onDismiss = { lpIngresoTarget = null },
            onUpdateSamples = { nextSamples ->
                val nextOp = updateSelectedBote { b ->
                    val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                    val buckets = (lpM[sid.toString()] ?: emptyMap()).toMutableMap()
                    buckets[sampleKind] = nextSamples
                    lpM[sid.toString()] = buckets.toMap()
                    b.copy(lpMuestras = lpM.toMap())
                } ?: return@ExtractedLpIngresoDialog
                syncOperacionIfOnline(nextOp)
            },
            onRemoveSpecies = {
                val nextOp = updateSelectedBote { b ->
                    val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                    lpM.remove(sid.toString())
                    b.copy(lpMuestras = lpM.toMap())
                } ?: return@ExtractedLpIngresoDialog
                syncOperacionIfOnline(nextOp)
                lpIngresoTarget = null
            }
        )
    }

    if (showLpSpeciesPicker && selectedBote != null) {
        ExtractedSpeciesPickerDialog(
            title = "Especies Peso-Longitud",
            species = especiesMaestras.sortedBy { it.com },
            currentSelectedIds = densitySelectedSpeciesIds.toSet(),
            onDismiss = { showLpSpeciesPicker = false },
            onApply = { ids ->
                densitySelectedSpeciesIds.clear()
                densitySelectedSpeciesIds.addAll(ids.sorted())
                densityMuestreoBySpeciesId.clear()
                ids.forEach { sidLocal ->
                    densityMuestreoBySpeciesId[sidLocal] = setOf("L-P")
                }
                val nextOp = updateSelectedBote { b ->
                    val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                    ids.forEach { sidLocal ->
                        val species = especiesById[sidLocal]
                        val defaultKinds = defaultLpKindsForSpecies(species)
                        val buckets = (lpM[sidLocal.toString()] ?: emptyMap()).toMutableMap()
                        defaultKinds.forEach { kind ->
                            buckets.putIfAbsent(kind, emptyList())
                        }
                        lpM[sidLocal.toString()] = buckets.toMap()
                    }
                    lpM.keys.toList().forEach { key ->
                        val keyId = key.toIntOrNull()
                        if (keyId != null && !ids.contains(keyId)) {
                            lpM.remove(key)
                        }
                    }
                    b.copy(lpMuestras = lpM.toMap())
                } ?: return@ExtractedSpeciesPickerDialog
                syncOperacionIfOnline(nextOp)
                showLpSpeciesPicker = false
            }
        )
    }

    ExtractedTransectosDialog(
        show = showDensityEditor,
        currentBote = selectedBote,
        especiesMaestras = especiesMaestras,
        transectosList = densityTransectosList,
        initialTab = "DENSIDAD",
        onDismiss = { showDensityEditor = false },
        onSaveDensity = { normalizedTransectos ->
            val nextOp = updateSelectedBote { b ->
                val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                syncLpSpeciesWithDensity(
                    bote = b.copy(
                        densTipo = dens,
                        transectos = normalizedTransectos.map { t ->
                            t.copy(tipo = if (dens == "cuadrante") "cuadrante" else "transecto")
                        }
                    ),
                    especiesById = especiesById,
                )
            } ?: return@ExtractedTransectosDialog
            syncOperacionIfOnline(nextOp)
            showDensityEditor = false
        },
        onSaveLp = {
            syncOperacionIfOnline()
            showDensityEditor = false
        },
        onUpdateLpSamples = { speciesId, sampleKind, nextSamples ->
            val nextOp = updateSelectedBote { b ->
                val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                val buckets = (lpM[speciesId.toString()] ?: emptyMap()).toMutableMap()
                buckets[normalizeLpKind(sampleKind)] = nextSamples
                lpM[speciesId.toString()] = buckets.toMap()
                b.copy(lpMuestras = lpM.toMap())
            } ?: return@ExtractedTransectosDialog
            syncOperacionIfOnline(nextOp)
        },
        onRemoveLpSpecies = { speciesId ->
            val nextOp = updateSelectedBote { b ->
                val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                lpM.remove(speciesId.toString())
                b.copy(lpMuestras = lpM.toMap())
            } ?: return@ExtractedTransectosDialog
            syncOperacionIfOnline(nextOp)
        },
    )
}
