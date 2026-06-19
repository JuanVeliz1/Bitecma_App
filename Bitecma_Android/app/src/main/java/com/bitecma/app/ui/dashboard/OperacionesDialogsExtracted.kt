package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitecma.app.network.EspecieDto
import com.bitecma.app.network.LpSampleDto

@Composable
internal fun ExtractedSpeciesPickerDialog(
    title: String = "Seleccionar especies",
    species: List<EspecieDto>,
    currentSelectedIds: Set<Int>,
    onDismiss: () -> Unit,
    onApply: (Set<Int>) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val selected = remember(currentSelectedIds) { mutableStateListOf<Int>().apply { addAll(currentSelectedIds) } }
    val uniqueSpecies = remember(species) {
        species.distinctBy { "${it.com.trim().lowercase()}|${it.sci?.trim()?.lowercase().orEmpty()}" }
    }
    val filtered = remember(uniqueSpecies, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) uniqueSpecies
        else uniqueSpecies.filter {
            it.com.lowercase().contains(q) ||
                (it.sci ?: "").lowercase().contains(q)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = colors.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp, color = colors.onSurface)
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
                                Text(sp.com, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.onSurface)
                                val sci = sp.sci
                                if (!sci.isNullOrBlank()) {
                                    Text(sci, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                        HorizontalDivider(color = colors.outline.copy(alpha = 0.45f))
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
                        colors = ButtonDefaults.buttonColors(containerColor = colors.secondary)
                    ) { Text("Aplicar", fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
internal fun ExtractedLpIngresoDialog(
    speciesId: Int,
    sampleKind: String,
    speciesName: String,
    currentSamples: List<LpSampleDto>,
    onDismiss: () -> Unit,
    onUpdateSamples: (List<LpSampleDto>) -> Unit,
    onRemoveSpecies: () -> Unit
) {
    val normalizedSampleKind = remember(sampleKind) { normalizeLpKind(sampleKind) }
    var lText by remember(speciesId, normalizedSampleKind) { mutableStateOf("") }
    var pText by remember(speciesId, normalizedSampleKind) { mutableStateOf("") }
    var dText by remember(speciesId, normalizedSampleKind) { mutableStateOf("") }
    var samples by remember(speciesId, currentSamples) { mutableStateOf(currentSamples) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var editL by remember { mutableStateOf("") }
    var editP by remember { mutableStateOf("") }
    var editD by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val longitudFocusRequester = remember(speciesId) { FocusRequester() }
    val pesoFocusRequester = remember(speciesId) { FocusRequester() }
    val diametroFocusRequester = remember(speciesId) { FocusRequester() }
    val editLongitudFocusRequester = remember(editIndex) { FocusRequester() }
    val editPesoFocusRequester = remember(editIndex) { FocusRequester() }
    val editDiametroFocusRequester = remember(editIndex) { FocusRequester() }

    fun parseNumber(s: String): Double? {
        val t = s.trim().replace(",", ".")
        return t.toDoubleOrNull()
    }

    fun applySamples(next: List<LpSampleDto>) {
        samples = next
        onUpdateSamples(next)
    }

    fun submitSample() {
        when (normalizedSampleKind) {
            "D" -> {
                val d = parseNumber(dText)
                if (d == null) {
                    diametroFocusRequester.requestFocus()
                } else {
                    applySamples(listOf(LpSampleDto(d = d)) + samples)
                    dText = ""
                    diametroFocusRequester.requestFocus()
                }
            }
            "L" -> {
                val l = parseNumber(lText)
                if (l == null) {
                    longitudFocusRequester.requestFocus()
                } else {
                    applySamples(listOf(LpSampleDto(l = l)) + samples)
                    lText = ""
                    longitudFocusRequester.requestFocus()
                }
            }
            else -> {
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
        }
    }

    fun saveEditedSample(index: Int) {
        when (normalizedSampleKind) {
            "D" -> {
                val d = parseNumber(editD)
                if (d == null) {
                    editDiametroFocusRequester.requestFocus()
                } else {
                    val next = samples.toMutableList()
                    next[index] = LpSampleDto(d = d)
                    applySamples(next)
                    editIndex = null
                    focusManager.clearFocus()
                }
            }
            "L" -> {
                val l = parseNumber(editL)
                if (l == null) {
                    editLongitudFocusRequester.requestFocus()
                } else {
                    val next = samples.toMutableList()
                    next[index] = LpSampleDto(l = l)
                    applySamples(next)
                    editIndex = null
                    focusManager.clearFocus()
                }
            }
            else -> {
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
        }
    }

    LaunchedEffect(speciesId, normalizedSampleKind) {
        when (normalizedSampleKind) {
            "D" -> diametroFocusRequester.requestFocus()
            else -> longitudFocusRequester.requestFocus()
        }
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
                        Text("${lpKindLabel(normalizedSampleKind)} · ${samples.size} muestra(s)", fontSize = 12.sp, color = Color.Gray)
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
                        if (normalizedSampleKind == "D") {
                            OutlinedTextField(
                                value = dText,
                                onValueChange = { dText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(diametroFocusRequester),
                                label = { Text("Diametro disco (cm)") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submitSample() }
                                ),
                                singleLine = true
                            )
                        } else {
                            OutlinedTextField(
                                value = lText,
                                onValueChange = { lText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(longitudFocusRequester),
                                label = { Text("Longitud (mm)") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = if (normalizedSampleKind == "LP") ImeAction.Next else ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { pesoFocusRequester.requestFocus() },
                                    onDone = { submitSample() }
                                ),
                                singleLine = true
                            )
                            if (normalizedSampleKind == "LP") {
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
                            }
                        }
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
                    Text(
                        if (normalizedSampleKind == "D") "D (cm)" else "L (mm)",
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.DarkGray
                    )
                    if (normalizedSampleKind == "LP") {
                        Text("P (g)", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                    }
                    Spacer(modifier = Modifier.width(140.dp))
                }

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(samples) { idx, s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text((samples.size - idx).toString(), modifier = Modifier.width(44.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                when (normalizedSampleKind) {
                                    "D" -> (s.d ?: 0.0).toString()
                                    else -> (s.l ?: 0.0).toString()
                                },
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp
                            )
                            if (normalizedSampleKind == "LP") {
                                Text((s.p ?: 0.0).toString(), modifier = Modifier.weight(1f), fontSize = 12.sp)
                            }
                            Spacer(Modifier.weight(0.1f))
                            OutlinedButton(
                                onClick = {
                                    editIndex = idx
                                    editL = (s.l ?: "").toString()
                                    editP = (s.p ?: "").toString()
                                    editD = (s.d ?: "").toString()
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
            when (normalizedSampleKind) {
                "D" -> editDiametroFocusRequester.requestFocus()
                else -> editLongitudFocusRequester.requestFocus()
            }
        }
        AlertDialog(
            onDismissRequest = { editIndex = null },
            title = { Text("Editar muestra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (normalizedSampleKind == "D") {
                        OutlinedTextField(
                            value = editD,
                            onValueChange = { editD = it },
                            modifier = Modifier.focusRequester(editDiametroFocusRequester),
                            label = { Text("Diametro disco (cm)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { saveEditedSample(idx) }
                            ),
                            singleLine = true
                        )
                    } else {
                        OutlinedTextField(
                            value = editL,
                            onValueChange = { editL = it },
                            modifier = Modifier.focusRequester(editLongitudFocusRequester),
                            label = { Text("Longitud (mm)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = if (normalizedSampleKind == "LP") ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { editPesoFocusRequester.requestFocus() },
                                onDone = { saveEditedSample(idx) }
                            ),
                            singleLine = true
                        )
                        if (normalizedSampleKind == "LP") {
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
                    }
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
