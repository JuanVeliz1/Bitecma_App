package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitecma.app.network.DensidadUnidadDto
import com.bitecma.app.network.EspecieDto
import com.bitecma.app.network.LpSampleDto
import com.bitecma.app.network.OperacionBoteDto

private fun extractedDensTipoLabel(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", true)) "Cuadrante" else "Transecto"
}

private fun extractedDensTipoPluralLabel(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", true)) "Cuadrantes" else "Transectos"
}

private fun extractedNormalizeDensTipo(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", true)) "cuadrante" else "transecto"
}

private fun extractedSpeciesIdsFromCounts(counts: Map<String, Int>?): List<Int> {
    return counts.orEmpty().keys.mapNotNull { it.toIntOrNull() }.distinct().sorted()
}

private fun extractedMergeCountsForSpecies(
    previousCounts: Map<String, Int>?,
    selectedSpeciesIds: Collection<Int>,
): Map<String, Int> {
    val previous = previousCounts.orEmpty()
    return selectedSpeciesIds
        .distinct()
        .sorted()
        .associate { sid -> sid.toString() to (previous[sid.toString()] ?: 0) }
}

private fun extractedDensityValueText(count: Int, area: Double?): String {
    val safeArea = area?.takeIf { it > 0.0 } ?: return "0.0000"
    return runCatching { String.format("%.4f", count.toDouble() / safeArea) }.getOrDefault("0.0000")
}

private fun extractedBoteDisplayName(bote: OperacionBoteDto?): String {
    val isIntermareal = bote?.submareal == 0 || bote?.nombre?.equals("Intermareal", ignoreCase = true) == true
    return if (isIntermareal) bote?.buzo?.takeIf { it.isNotBlank() } ?: "SIN BUZO" else bote?.nombre?.uppercase() ?: "S/N"
}

private fun extractedCountInputText(count: Int?): String {
    val safeCount = count ?: 0
    return if (safeCount == 0) "" else safeCount.toString()
}

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun ExtractedTransectosDialog(
    show: Boolean,
    currentBote: OperacionBoteDto?,
    especiesMaestras: List<EspecieDto>,
    transectosList: SnapshotStateList<DensidadUnidadDto>,
    initialTab: String = "DENSIDAD",
    onDismiss: () -> Unit,
    onSaveDensity: (List<DensidadUnidadDto>) -> Unit,
    onSaveLp: () -> Unit,
    onUpdateLpSamples: (Int, String, List<LpSampleDto>) -> Unit,
    onRemoveLpSpecies: (Int) -> Unit,
) {
    if (!show) return
    val colors = MaterialTheme.colorScheme

    val densityUnitTitle = extractedDensTipoPluralLabel(currentBote?.densTipo).uppercase()

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
            color = colors.surface,
            tonalElevation = 8.dp
        ) {
            var subTab by remember(show, currentBote?.id, currentBote?.zona, currentBote?.nombre) {
                mutableStateOf("DENSIDAD")
            }
            var pendingAreaFocusIndex by remember { mutableStateOf(if (transectosList.isNotEmpty()) 0 else null) }
            var pendingQuadrantCountFocusIndex by remember { mutableStateOf<Int?>(null) }
            var pendingSpeciesFocusIndex by remember { mutableStateOf<Int?>(null) }
            var selectedTransectSpeciesNum by remember(show, currentBote?.id) { mutableStateOf<Int?>(null) }
            val especiesById = remember(especiesMaestras) { especiesMaestras.associateBy { it.id } }
            val transectListState = rememberLazyListState()
            val quadrantListState = rememberLazyListState()

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
                        "Agregar $densityUnitTitle - ${extractedBoteDisplayName(currentBote)}",
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = 44.dp),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        maxLines = 1
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
                        .imePadding()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    run {
                        val isQuadrantMode = currentBote?.densTipo.equals("Cuadrante", true)
                        val quadrants = transectosList
                            .filter { it.tipo.equals("cuadrante", true) }
                            .sortedBy { it.num ?: 0 }
                        var quadrantCountText by remember(show, currentBote?.id) { mutableStateOf("30") }
                        var quadrantAreaText by remember(show, currentBote?.id) { mutableStateOf("0.25") }
                        var quadrantSustrato by remember(show, currentBote?.id) { mutableStateOf("") }
                        var quadrantSpeciesId by remember(show, currentBote?.id) { mutableStateOf<Int?>(null) }
                        var showQuadrantSpeciesPicker by remember(show, currentBote?.id) { mutableStateOf(false) }

                        if (isQuadrantMode) {
                            val selectedQuadrantSpecies = quadrantSpeciesId?.let { especiesById[it] }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Cantidad", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                            BasicTextField(
                                                value = quadrantCountText,
                                                onValueChange = { quadrantCountText = it.filter { ch -> ch.isDigit() } },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 6.dp)
                                                    .border(1.5.dp, colors.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                                    .background(colors.surface, RoundedCornerShape(10.dp))
                                                    .padding(10.dp),
                                                textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.onSurface),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Area cuadrante", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                            Box {
                                                var expandedArea by remember { mutableStateOf(false) }
                                                OutlinedButton(
                                                    onClick = { expandedArea = true },
                                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("$quadrantAreaText m2", color = colors.primary, modifier = Modifier.weight(1f))
                                                    Icon(Icons.Default.ArrowDropDown, null)
                                                }
                                                DropdownMenu(expanded = expandedArea, onDismissRequest = { expandedArea = false }) {
                                                    listOf("1", "0.25", "0.0625").forEach { option ->
                                                        DropdownMenuItem(
                                                            text = { Text("$option m2") },
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
                                            Text("Tipo sustrato", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                            BasicTextField(
                                                value = quadrantSustrato,
                                                onValueChange = { quadrantSustrato = it },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 6.dp)
                                                    .border(1.5.dp, colors.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                                    .background(colors.surface, RoundedCornerShape(10.dp))
                                                    .padding(10.dp),
                                                textStyle = TextStyle(fontSize = 13.sp, color = colors.onSurface),
                                                singleLine = true
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Especie", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                            OutlinedButton(
                                                onClick = { showQuadrantSpeciesPicker = true },
                                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(
                                                    selectedQuadrantSpecies?.let { "${it.com} - ${it.sci ?: ""}".trim() } ?: "Elegir especie",
                                                    color = colors.primary,
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
                                                val nextFocusIndex = quadrants.size
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
                                                pendingQuadrantCountFocusIndex = nextFocusIndex
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = quadrantSpeciesId != null && (quadrantCountText.toIntOrNull() ?: 0) > 0,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary)
                                        ) {
                                            Text("Agregar", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Surface(
                                modifier = Modifier.weight(1f),
                                color = colors.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.45f))
                            ) {
                                if (quadrants.isEmpty()) {
                                    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                                        Text("Aun no hay cuadrantes registrados.", color = colors.onSurfaceVariant)
                                    }
                                } else {
                                    LaunchedEffect(pendingQuadrantCountFocusIndex) {
                                        pendingQuadrantCountFocusIndex?.let { index ->
                                            if (index < quadrants.size) {
                                                quadrantListState.animateScrollToItem(index)
                                            }
                                        }
                                    }
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        state = quadrantListState,
                                        contentPadding = PaddingValues(bottom = 160.dp)
                                    ) {
                                        itemsIndexed(quadrants, key = { _, q -> q.num ?: 0 }) { qIndex, q ->
                                            val speciesId = q.especieId ?: q.counts?.keys?.firstOrNull()?.toIntOrNull()
                                            val speciesName = speciesId?.let { especiesById[it]?.com } ?: "Sin especie"
                                            val countKey = speciesId?.toString()
                                            val countValue = countKey?.let { q.counts?.get(it) } ?: 0
                                            val focusManager = LocalFocusManager.current
                                            val countFocusRequester = remember(q.num) { FocusRequester() }
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                                border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.45f)),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                LaunchedEffect(pendingQuadrantCountFocusIndex, qIndex) {
                                                    if (pendingQuadrantCountFocusIndex == qIndex) {
                                                        countFocusRequester.requestFocus()
                                                        pendingQuadrantCountFocusIndex = null
                                                    }
                                                }
                                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Cuadrante ${q.num ?: 0}", fontWeight = FontWeight.Black, color = colors.onSurface, modifier = Modifier.weight(1f))
                                                        Text(speciesName, fontSize = 12.sp, color = colors.secondary, fontWeight = FontWeight.Bold)
                                                        Spacer(Modifier.width(8.dp))
                                                        IconButton(
                                                            onClick = {
                                                                val idx = transectosList.indexOfFirst { it.num == q.num && it.tipo.equals("cuadrante", true) }
                                                                if (idx >= 0) transectosList.removeAt(idx)
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Cantidad", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                                            BasicTextField(
                                                                value = extractedCountInputText(countValue),
                                                                onValueChange = { value ->
                                                                    val idx = transectosList.indexOfFirst { it.num == q.num && it.tipo.equals("cuadrante", true) }
                                                                    if (idx >= 0 && countKey != null) {
                                                                        val newCounts = (transectosList[idx].counts ?: emptyMap()).toMutableMap()
                                                                        newCounts[countKey] = value.toIntOrNull() ?: 0
                                                                        transectosList[idx] = transectosList[idx].copy(counts = newCounts)
                                                                    }
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .focusRequester(countFocusRequester)
                                                                    .padding(top = 6.dp)
                                                                    .border(1.5.dp, colors.outline.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                                                    .background(colors.surface, RoundedCornerShape(10.dp))
                                                                    .padding(10.dp),
                                                                textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Black, color = colors.secondary),
                                                                singleLine = true,
                                                                keyboardOptions = KeyboardOptions(
                                                                    keyboardType = KeyboardType.Number,
                                                                    imeAction = if (qIndex == quadrants.lastIndex) ImeAction.Done else ImeAction.Next
                                                                ),
                                                                keyboardActions = KeyboardActions(
                                                                    onNext = {
                                                                        val nextIndex = qIndex + 1
                                                                        if (nextIndex < quadrants.size) {
                                                                            pendingQuadrantCountFocusIndex = nextIndex
                                                                        } else {
                                                                            focusManager.clearFocus()
                                                                        }
                                                                    },
                                                                    onDone = { focusManager.clearFocus() }
                                                                )
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Densidad", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                                            Surface(
                                                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                                color = colors.surfaceVariant,
                                                                shape = RoundedCornerShape(10.dp)
                                                            ) {
                                                                Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                                    Text(extractedDensityValueText(countValue, q.area), fontWeight = FontWeight.Black, color = colors.onSurface)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Area", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                                            BasicTextField(
                                                                value = (q.area ?: 0.25).toString(),
                                                                onValueChange = { value ->
                                                                    val idx = transectosList.indexOfFirst { it.num == q.num && it.tipo.equals("cuadrante", true) }
                                                                    if (idx >= 0) transectosList[idx] = transectosList[idx].copy(area = value.toDoubleOrNull())
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(top = 6.dp)
                                                                    .border(1.5.dp, colors.outline.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                                                    .background(colors.surface, RoundedCornerShape(10.dp))
                                                                    .padding(10.dp),
                                                                textStyle = TextStyle(fontSize = 13.sp, color = colors.onSurface),
                                                                singleLine = true,
                                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Sustrato", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                                            BasicTextField(
                                                                value = q.sustrato.orEmpty(),
                                                                onValueChange = { value ->
                                                                    val idx = transectosList.indexOfFirst { it.num == q.num && it.tipo.equals("cuadrante", true) }
                                                                    if (idx >= 0) transectosList[idx] = transectosList[idx].copy(sustrato = value)
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(top = 6.dp)
                                                                    .border(1.5.dp, colors.outline.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                                                    .background(colors.surface, RoundedCornerShape(10.dp))
                                                                    .padding(10.dp),
                                                                textStyle = TextStyle(fontSize = 13.sp, color = colors.onSurface),
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
                                ExtractedSpeciesPickerDialog(
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
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (transectosList.isNotEmpty()) {
                                            val first = transectosList[0]
                                            for (i in 1 until transectosList.size) {
                                                transectosList[i] = transectosList[i].copy(
                                                    area = first.area,
                                                    sustrato = first.sustrato,
                                                    cubierta = first.cubierta,
                                                    counts = extractedMergeCountsForSpecies(
                                                        transectosList[i].counts,
                                                        extractedSpeciesIdsFromCounts(first.counts)
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(26.dp),
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    border = BorderStroke(1.5.dp, colors.primary)
                                ) {
                                    Text(
                                        "Replicar primera fila",
                                        fontSize = 12.sp,
                                        color = colors.primary,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 14.sp,
                                        maxLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Button(
                                    onClick = {
                                        val nextIndex = transectosList.size
                                        val nextNum = (transectosList.maxOfOrNull { it.num ?: 0 } ?: 0) + 1
                                        transectosList.add(
                                            DensidadUnidadDto(
                                                num = nextNum,
                                                tipo = "transecto",
                                                area = 120.0,
                                                counts = densityCountsForDraft(
                                                    bote = currentBote,
                                                    transectos = transectosList.toList(),
                                                ),
                                            )
                                        )
                                        pendingAreaFocusIndex = nextIndex
                                    },
                                    shape = RoundedCornerShape(26.dp),
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("+ Agregar", fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    }
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
                                    .border(1.dp, colors.outline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
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
                                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                                            border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.45f)),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                val focusManager = LocalFocusManager.current
                                                val areaFocusRequester = remember(index) { FocusRequester() }
                                                val sustratoFocusRequester = remember(index) { FocusRequester() }
                                                val transectSpeciesIds = remember(t.counts) {
                                                    extractedSpeciesIdsFromCounts(t.counts)
                                                }
                                                val speciesFocusRequesters = remember(index, transectSpeciesIds) {
                                                    List(transectSpeciesIds.size) { FocusRequester() }
                                                }

                                                LaunchedEffect(pendingAreaFocusIndex, subTab, index) {
                                                    if (subTab == "DENSIDAD" && pendingAreaFocusIndex == index) {
                                                        areaFocusRequester.requestFocus()
                                                        pendingAreaFocusIndex = null
                                                    }
                                                }
                                                LaunchedEffect(pendingSpeciesFocusIndex, subTab, index, speciesFocusRequesters.size) {
                                                    if (subTab == "DENSIDAD" && pendingSpeciesFocusIndex == index) {
                                                        if (speciesFocusRequesters.isNotEmpty()) {
                                                            speciesFocusRequesters.first().requestFocus()
                                                        } else {
                                                            areaFocusRequester.requestFocus()
                                                        }
                                                        pendingSpeciesFocusIndex = null
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "${extractedDensTipoLabel(t.tipo)} ${t.num ?: (index + 1)}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = colors.onSurface,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(onClick = { transectosList.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Delete, null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
                                                    }
                                                }

                                                Spacer(Modifier.height(12.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { selectedTransectSpeciesNum = t.num ?: (index + 1) },
                                                        shape = RoundedCornerShape(10.dp),
                                                        border = BorderStroke(1.dp, colors.primary)
                                                    ) {
                                                        val selectedCount = extractedSpeciesIdsFromCounts(t.counts).size
                                                        Text(
                                                            if (selectedCount > 0) "$selectedCount especies" else "Seleccionar especies",
                                                            color = colors.primary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    Text(
                                                        "Detalles del transecto",
                                                        fontSize = 11.sp,
                                                        color = colors.onSurface,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }

                                                Spacer(Modifier.height(12.dp))

                                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("Area (m2)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                                        BasicTextField(
                                                            value = (t.area ?: 120.0).toString(),
                                                            onValueChange = { transectosList[index] = t.copy(area = it.toDoubleOrNull()) },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .focusRequester(areaFocusRequester)
                                                                .padding(top = 6.dp)
                                                                .border(1.5.dp, colors.outline.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                                                .background(colors.surface, RoundedCornerShape(10.dp))
                                                                .padding(10.dp),
                                                            textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Black, color = colors.onSurface),
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
                                                        Text("Sustrato", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                                        BasicTextField(
                                                            value = t.sustrato.orEmpty(),
                                                            onValueChange = { transectosList[index] = t.copy(sustrato = it) },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .focusRequester(sustratoFocusRequester)
                                                                .padding(top = 6.dp)
                                                                .border(1.5.dp, colors.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                                                .background(colors.surface, RoundedCornerShape(10.dp))
                                                                .padding(10.dp),
                                                            textStyle = TextStyle(fontSize = 13.sp, color = colors.onSurface),
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

                                                Column {
                                                    Text("Cubierta biologica", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                                    BasicTextField(
                                                        value = t.cubierta.orEmpty(),
                                                        onValueChange = { transectosList[index] = t.copy(cubierta = it) },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 6.dp)
                                                            .border(1.5.dp, colors.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                                            .background(colors.surface, RoundedCornerShape(10.dp))
                                                            .padding(10.dp),
                                                        textStyle = TextStyle(fontSize = 13.sp, color = colors.onSurface),
                                                        singleLine = true
                                                    )
                                                }

                                                Spacer(Modifier.height(12.dp))

                                                Text("Fauna (filas)", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.onSurface, letterSpacing = 1.sp)
                                                Spacer(Modifier.height(8.dp))

                                                val currentCounts = t.counts ?: emptyMap<String, Int>()
                                                val densidadSpeciesIds = transectSpeciesIds
                                                if (densidadSpeciesIds.isEmpty()) {
                                                    Text("Selecciona especies para este transecto antes de ingresar conteos.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                                                } else {
                                                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Spacer(Modifier.weight(1f))
                                                        Text("N° ind", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.onSurface, modifier = Modifier.width(88.dp), textAlign = TextAlign.Center)
                                                        Spacer(Modifier.width(10.dp))
                                                        Text("Dens.", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.onSurface, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
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
                                                                color = colors.primary,
                                                                maxLines = 1
                                                            )
                                                            val area = (t.area ?: 120.0).takeIf { it > 0.0 } ?: 120.0
                                                            val countVal = currentCounts[sid.toString()] ?: 0
                                                            BasicTextField(
                                                                value = extractedCountInputText(currentCounts[sid.toString()]),
                                                                onValueChange = { newVal ->
                                                                    val newCounts = currentCounts.toMutableMap()
                                                                    newCounts[sid.toString()] = newVal.toIntOrNull() ?: 0
                                                                    transectosList[index] = t.copy(counts = newCounts)
                                                                },
                                                                modifier = Modifier
                                                                    .width(88.dp)
                                                                    .focusRequester(speciesFocusRequesters[speciesIndex])
                                                                    .border(1.5.dp, colors.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                                                    .background(colors.surface, RoundedCornerShape(10.dp))
                                                                    .padding(10.dp),
                                                                textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Black, color = colors.tertiary),
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
                                                                                    pendingSpeciesFocusIndex = nextIndex
                                                                                } else {
                                                                                    focusManager.clearFocus()
                                                                                }
                                                                            }
                                                                    },
                                                                    onDone = {
                                                                        val nextIndex = index + 1
                                                                        if (nextIndex < transectosList.size) {
                                                                            pendingSpeciesFocusIndex = nextIndex
                                                                        } else {
                                                                            focusManager.clearFocus()
                                                                        }
                                                                    }
                                                                )
                                                            )
                                                            Spacer(Modifier.width(10.dp))
                                                            val densText = runCatching { String.format("%.4f", countVal.toDouble() / area) }.getOrNull() ?: "0.0000"
                                                            Surface(color = colors.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                                                                Box(modifier = Modifier.width(72.dp).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                                    Text(densText, fontSize = 12.sp, fontWeight = FontWeight.Black, color = colors.onSurface, textAlign = TextAlign.Center)
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
                        border = BorderStroke(1.5.dp, colors.outline)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Bold, color = colors.onSurface)
                    }
                    Button(
                        onClick = {
                            onSaveDensity(
                                transectosList
                                    .map { t ->
                                        val isQuadrant = extractedNormalizeDensTipo(t.tipo) == "cuadrante"
                                        t.copy(
                                            tipo = if (isQuadrant) "cuadrante" else "transecto",
                                            area = t.area ?: if (isQuadrant) 0.25 else 120.0,
                                        )
                                    }
                                    .sortedBy { it.num ?: 0 }
                            )
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary)
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }

            val transectSpeciesNum = selectedTransectSpeciesNum
            val selectedTransect = transectSpeciesNum?.let { targetNum ->
                transectosList.firstOrNull { (it.num ?: 0) == targetNum && extractedNormalizeDensTipo(it.tipo) == "transecto" }
            }
            if (transectSpeciesNum != null && selectedTransect != null) {
                ExtractedSpeciesPickerDialog(
                    title = "Especies del transecto $transectSpeciesNum",
                    species = especiesMaestras.sortedBy { it.com },
                    currentSelectedIds = extractedSpeciesIdsFromCounts(selectedTransect.counts).toSet(),
                    onDismiss = { selectedTransectSpeciesNum = null },
                    onApply = { ids ->
                        val idx = transectosList.indexOfFirst {
                            (it.num ?: 0) == transectSpeciesNum && extractedNormalizeDensTipo(it.tipo) == "transecto"
                        }
                        if (idx >= 0) {
                            val current = transectosList[idx]
                            transectosList[idx] = current.copy(
                                counts = extractedMergeCountsForSpecies(current.counts, ids)
                            )
                        }
                        selectedTransectSpeciesNum = null
                    }
                )
            }
        }
    }
}
