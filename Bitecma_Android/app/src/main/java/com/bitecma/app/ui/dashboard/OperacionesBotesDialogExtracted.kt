@file:Suppress("DEPRECATION")

package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitecma.app.network.BoteMaestroDto
import com.bitecma.app.network.OperacionBoteDto
import java.text.Normalizer

private fun normalizeBoatText(value: String?): String {
    return Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()
        .trim()
}

@Composable
internal fun ExtractedGestionBotesDialog(
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
                val availableBotesMaestros = remember(botesMaestros) {
                    botesMaestros.distinctBy { bote ->
                        listOf(
                            normalizeBoatText(bote.nombre),
                            normalizeBoatText(bote.caleta),
                            normalizeBoatText(bote.nrpa),
                        ).joinToString("|")
                    }
                }

                var activeSearchRowIndex by remember { mutableStateOf<Int?>(null) }
                var showBoatSearchDialog by remember { mutableStateOf(false) }
                var boatSearchTerm by remember { mutableStateOf("") }
                val operationCaletaKey = remember(operationCaleta) { normalizeBoatText(operationCaleta) }
                val operationRegionKey = remember(operationRegionRom) {
                    normalizeBoatText(operationRegionRom?.split("—")?.firstOrNull()?.trim())
                }
                val candidateBotes = remember(availableBotesMaestros, operationCaletaKey, operationRegionKey) {
                    val withRegion = if (operationRegionKey.isBlank()) {
                        availableBotesMaestros
                    } else {
                        availableBotesMaestros.filter { bote ->
                            val regionKey = normalizeBoatText(bote.region_rom ?: bote.region)
                            regionKey.contains(operationRegionKey)
                        }
                    }
                    val byCaleta = if (operationCaletaKey.isBlank()) {
                        withRegion
                    } else {
                        withRegion.filter { bote ->
                            val caletaKey = normalizeBoatText(bote.caleta)
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
                    val term = normalizeBoatText(boatSearchTerm)
                    candidateBotes
                        .filter { bote ->
                            if (term.isBlank()) return@filter true
                            listOf(bote.nombre, bote.nrpa, bote.nmatricula, bote.caleta)
                                .map { normalizeBoatText(it) }
                                .any { it.contains(term) }
                        }
                        .sortedBy { normalizeBoatText(it.nombre) }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(colors = listOf(Color(0xFF003366), Color(0xFF00509E))))
                            .padding(20.dp)
                    ) {
                        Text(
                            "BOTES — $operacionId",
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
                        if (isCompactLayout) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFF8FAFD),
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                            ) {
                                Text(
                                    text = "Vista movil: cada bote se muestra en bloques para evitar textos cortados y opciones comprimidas.",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .background(Color(0xFFF1F3F5))
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#", Modifier.weight(0.3f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                Text("ZONA", Modifier.weight(0.75f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                Text("TIPO", Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                Text("BOTE", Modifier.weight(1.55f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                Text("BUZO", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                Text("UNIDAD", Modifier.weight(0.95f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                Spacer(Modifier.width(36.dp))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Color(0xFFF1F3F5), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(items = botesList.toList()) { index: Int, bote: OperacionBoteDto ->
                                    ExtractedBoteRowItem(
                                        index = index + 1,
                                        bote = bote,
                                        isCompactLayout = isCompactLayout,
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
                                                "No hay botes registrados. Se crean 4 filas por defecto, pero puedes agregar más si la operación lo necesita.",
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
                                    val applyBoatText = {
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
                                    }
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
                                                onClick = applyBoatText,
                                                modifier = Modifier.weight(1f),
                                                enabled = boatSearchTerm.isNotBlank()
                                            ) { Text("Usar texto", maxLines = 1) }
                                            OutlinedButton(
                                                onClick = {
                                                    activeSearchRowIndex = null
                                                    boatSearchTerm = ""
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Cerrar", maxLines = 1) }
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
                                                onClick = applyBoatText,
                                                enabled = boatSearchTerm.isNotBlank()
                                            ) { Text("Usar texto") }
                                            OutlinedButton(
                                                onClick = {
                                                    activeSearchRowIndex = null
                                                    boatSearchTerm = ""
                                                }
                                            ) { Text("Cerrar") }
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
                                                            text = "No se encontraron botes para \"${boatSearchTerm.ifBlank { "tu búsqueda" }}\". Puedes ingresarlo manualmente.",
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
                        ExtractedBoteMaestroSearchDialog(
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
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, Color(0xFF003366))
                            ) {
                                Text("+ Agregar", fontWeight = FontWeight.Bold, color = Color(0xFF003366))
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
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    border = BorderStroke(1.5.dp, Color.Gray)
                                ) {
                                    Text("CANCELAR", fontWeight = FontWeight.Bold, color = Color.Gray, maxLines = 1)
                                }

                                Button(
                                    onClick = onSave,
                                    modifier = Modifier.weight(1f).height(52.dp),
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
                                Text("+ Agregar", fontWeight = FontWeight.Bold, color = Color(0xFF003366))
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
private fun ExtractedBoteTipoToggleButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    icon: ImageVector,
    activeColor: Color,
    inactiveTextColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeColor else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) activeColor else borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (selected) Color.White else inactiveTextColor
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else inactiveTextColor,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun ExtractedBoteRowItem(
    index: Int,
    bote: OperacionBoteDto,
    isCompactLayout: Boolean,
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

    var expandedUni by remember { mutableStateOf(false) }

    val zonaField: @Composable (Modifier) -> Unit = { modifier ->
        BasicTextField(
            value = bote.zona?.toString() ?: "",
            onValueChange = { onUpdate(bote.copy(zona = it.toIntOrNull())) },
            modifier = modifier
                .focusRequester(zonaFocusRequester)
                .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                .background(comboBg, RoundedCornerShape(8.dp))
                .padding(10.dp),
            textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = comboText),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { buzoFocusRequester.requestFocus() })
        )
    }

    val tipoField: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            modifier = modifier
                .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                .background(comboBg, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExtractedBoteTipoToggleButton(
                    modifier = Modifier.weight(1f),
                    selected = !isIntermareal,
                    label = "Sub",
                    icon = Icons.Default.DirectionsBoat,
                    activeColor = Color(0xFF003366),
                    inactiveTextColor = comboText,
                    borderColor = comboBorder,
                    onClick = {
                        onUpdate(
                            bote.copy(
                                submareal = 1,
                                nombre = if (bote.nombre.equals("Intermareal", true)) null else bote.nombre,
                            )
                        )
                    }
                )
                ExtractedBoteTipoToggleButton(
                    modifier = Modifier.weight(1f),
                    selected = isIntermareal,
                    label = "Inter",
                    icon = Icons.Default.DirectionsWalk,
                    activeColor = Color(0xFF5E35B1),
                    inactiveTextColor = comboText,
                    borderColor = comboBorder,
                    onClick = {
                        onUpdate(
                            bote.copy(
                                submareal = 0,
                                nombre = "Intermareal",
                                boteMaestroId = null,
                            )
                        )
                    }
                )
            }
        }
    }

    val nombreField: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            modifier = modifier
                .border(1.5.dp, if (isSearchActive) Color(0xFF00897B) else comboBorder, RoundedCornerShape(8.dp))
                .background(comboBg, RoundedCornerShape(8.dp))
                .padding(10.dp),
            color = Color.Transparent
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isIntermareal) {
                    Text(
                        text = "Intermareal",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = comboText,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    BasicTextField(
                        value = bote.nombre.orEmpty(),
                        onValueChange = {
                            onUpdate(
                                bote.copy(
                                    nombre = it,
                                    boteMaestroId = null,
                                    submareal = 1,
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = comboText),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (bote.nombre.isNullOrBlank()) {
                                    Text("Nombre bote", color = labelColor, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                if (!isIntermareal) {
                    IconButton(onClick = onOpenSearch, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Buscar bote maestro",
                            tint = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.Waves,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFB39DDB) else Color(0xFF5E35B1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    val buzoField: @Composable (Modifier) -> Unit = { modifier ->
        BasicTextField(
            value = bote.buzo ?: "",
            onValueChange = { onUpdate(bote.copy(buzo = it)) },
            modifier = modifier
                .focusRequester(buzoFocusRequester)
                .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                .background(comboBg, RoundedCornerShape(8.dp))
                .padding(10.dp),
            textStyle = TextStyle(fontSize = 13.sp, color = comboText),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { innerTextField ->
                Box {
                    if (bote.buzo.isNullOrBlank()) {
                        Text("Nombre buzo", color = labelColor, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            }
        )
    }

    val unidadField: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
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
                    Text(boatDensityLabel(bote.densTipo), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = comboText, modifier = Modifier.weight(1f))
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
    }

    if (isCompactLayout) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            color = if (isDark) Color(0xFF132238) else Color(0xFFF8FAFD),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFF1F3F5))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bote $index",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDark) Color.White else Color(0xFF003366),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color.LightGray.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExtractedBoteFieldSection("Zona", Modifier.weight(0.8f)) {
                        zonaField(Modifier.fillMaxWidth())
                    }
                    ExtractedBoteFieldSection("Unidad", Modifier.weight(1.2f)) {
                        unidadField(Modifier.fillMaxWidth())
                    }
                }

                ExtractedBoteFieldSection("Tipo") {
                    tipoField(Modifier.fillMaxWidth())
                }

                ExtractedBoteFieldSection("Bote") {
                    nombreField(Modifier.fillMaxWidth())
                }

                ExtractedBoteFieldSection("Buzo") {
                    buzoField(Modifier.fillMaxWidth())
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(index.toString(), Modifier.weight(0.3f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.LightGray else Color.Gray)
            zonaField(Modifier.weight(0.75f).padding(horizontal = 4.dp))
            Box(Modifier.weight(1.2f).padding(horizontal = 4.dp)) {
                tipoField(Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1.55f).padding(horizontal = 4.dp)) {
                nombreField(Modifier.fillMaxWidth())
            }
            buzoField(Modifier.weight(1f).padding(horizontal = 4.dp))
            unidadField(Modifier.weight(0.95f).padding(horizontal = 4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
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
private fun ExtractedBoteFieldSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Color.Gray
        )
        content()
    }
}

private fun boatDensityLabel(densTipo: String?): String {
    return if (densTipo.equals("Cuadrante", ignoreCase = true)) "Cuadrante" else "Transecto"
}

@Composable
internal fun ExtractedBoteMaestroSearchDialog(
    botes: List<BoteMaestroDto>,
    operationRegionRom: String?,
    operationCaleta: String?,
    onSelect: (BoteMaestroDto) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
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
                Box(modifier = Modifier.fillMaxWidth().background(headerBg).padding(16.dp)) {
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
                            val regions = regionKeys
                            items(regions) { rom ->
                                val isRegionExpanded = expandedRegionRom == rom
                                ExtractedHierarchicalItem(
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
                                        ExtractedHierarchicalItem(
                                            label = caleta.uppercase(),
                                            isExpanded = isCaletaExpanded,
                                            onClick = { expandedCaleta = if (isCaletaExpanded) null else caleta },
                                            level = 1
                                        )

                                        if (isCaletaExpanded) {
                                            val botesInCaleta = baseBotes.filter { (it.region_rom ?: it.region) == rom && it.caleta?.equals(caleta, true) == true }
                                            botesInCaleta.forEach { b ->
                                                ExtractedBoteFinalItem(b, onSelect)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(queryFilteredBotes) { b ->
                                ExtractedBoteFinalItem(b, onSelect)
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
internal fun ExtractedHierarchicalItem(label: String, isExpanded: Boolean, onClick: () -> Unit, level: Int) {
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
internal fun ExtractedBoteFinalItem(b: BoteMaestroDto, onSelect: (BoteMaestroDto) -> Unit) {
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
