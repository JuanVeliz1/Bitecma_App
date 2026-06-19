package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitecma.app.data.Opa
import com.bitecma.app.data.SectorAmerb
import com.bitecma.app.network.CaletaDto
import com.bitecma.app.network.OpaDto
import com.bitecma.app.network.RegionDto
import com.bitecma.app.network.SectorAmerbDto

private val EXTRACTED_TIPOS_ORGANIZACION_DEFAULT = listOf("STI", "ASOC", "OTRO")

@Composable
internal fun ExtractedNuevaOperacionDialog(
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
                        .distinctBy { it.nombre.trim().lowercase() }
                        .sortedBy { it.nombre.trim().lowercase() }

                    val byRegion = if (selectedRegionId != null) {
                        allCaletas.filter { caleta -> caleta.region == selectedRegionId }
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
                        "NUEVA OPERACION",
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
                    Text("UBICACION Y SECTOR", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
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

                    Text("REGION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                    Box {
                        OutlinedButton(
                            onClick = { expandedReg = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, comboBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = comboBg)
                        ) {
                            Text(
                                selectedRegionId?.let { regionLabelById[it] } ?: "Seleccionar Region",
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
                                    text = { Text(listOfNotNull(r.rom, r.nom).joinToString(" - "), color = comboText) },
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
                        placeholder = if (filteredSectores.isEmpty()) "Sin sectores en esta region" else "Buscar sector...",
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

                    Text("IDENTIFICACION", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
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
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
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
                            Text("FECHA TERMINO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
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

                    Text("ORGANIZACION (OPA)", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
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
                            EXTRACTED_TIPOS_ORGANIZACION_DEFAULT.forEach { t ->
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
                        placeholder = if (filteredOpas.isEmpty()) "Sin OPAs en esta region" else "Buscar OPA...",
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
                            text = "Organizacion seleccionada: $selectedOpaLabel",
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
