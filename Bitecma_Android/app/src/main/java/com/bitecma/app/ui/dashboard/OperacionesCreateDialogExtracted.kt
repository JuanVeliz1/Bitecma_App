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
import com.bitecma.app.network.RegionDto

private val EXTRACTED_TIPOS_ORGANIZACION_DEFAULT = listOf("STI", "ASOC", "OTRO")

@Composable
internal fun ExtractedNuevaOperacionDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    regiones: List<RegionDto>,
    regionLabelById: Map<Int, String>,
    selectedRegionId: Int?,
    onRegionSelected: (Int?) -> Unit,
    sectorAmerbInput: String,
    onSectorAmerbInputChange: (String) -> Unit,
    numSeguimiento: String,
    onNumSeguimientoChange: (String) -> Unit,
    fechaInicio: String,
    onFechaInicioClick: () -> Unit,
    fechaFin: String,
    onFechaFinClick: () -> Unit,
    caletaInput: String,
    onCaletaInputChange: (String) -> Unit,
    onCaletaSelected: (String) -> Unit,
    tipoOrg: String,
    onTipoOrgChange: (String) -> Unit,
    opaInput: String,
    onOpaInputChange: (String) -> Unit,
    validationError: String?,
    onCreateClick: () -> Unit,
) {
    if (!show) return
    val colors = MaterialTheme.colorScheme

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
            tonalElevation = 12.dp
        ) {
            val comboBg = colors.surface
            val comboText = colors.onSurface
            val comboBorder = colors.outline.copy(alpha = 0.35f)
            val labelColor = colors.onSurfaceVariant
            val accentColor = colors.primary
            var expandedReg by remember { mutableStateOf(false) }
            var expandedTipo by remember { mutableStateOf(false) }

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
                        "Nueva operacion",
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = 44.dp),
                        color = Color.White,
                        fontSize = 18.sp,
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
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text("Ubicacion y sector", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
                    Spacer(Modifier.height(12.dp))

                    Text("Region", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                    Box {
                        OutlinedButton(
                            onClick = { expandedReg = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, comboBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = comboBg)
                        ) {
                            Text(
                                selectedRegionId?.let { regionLabelById[it] } ?: "Seleccionar region",
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

                    Text("Sector AMERB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                    OutlinedTextField(
                        value = sectorAmerbInput,
                        onValueChange = onSectorAmerbInputChange,
                        placeholder = { Text("Ingresar sector AMERB...", color = labelColor) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Caleta", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                    OutlinedTextField(
                        value = caletaInput,
                        onValueChange = {
                            onCaletaInputChange(it)
                            onCaletaSelected(it)
                        },
                        placeholder = { Text("Ingresar caleta...", color = labelColor) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
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

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = comboBorder)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Identificacion", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("N° seguimiento", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
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
                            Text("Fecha inicio", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
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
                            Text("Fecha termino", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
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

                    Text("Organizacion (OPA)", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
                    Spacer(Modifier.height(12.dp))

                    Text("Tipo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
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

                    Text("Nombre OPA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                    OutlinedTextField(
                        value = opaInput,
                        onValueChange = onOpaInputChange,
                        placeholder = { Text("Ingresar organizacion...", color = labelColor) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
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
                        border = BorderStroke(1.5.dp, colors.outline)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                    }

                    Button(
                        onClick = onCreateClick,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary)
                    ) { Text("Crear", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
