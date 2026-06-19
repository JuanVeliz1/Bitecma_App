package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitecma.app.network.EspecieDto

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExtractedOperacionSpeciesDialog(
    show: Boolean,
    boteNombre: String?,
    especiesMaestras: List<EspecieDto>,
    selectedSpeciesIds: SnapshotStateList<Int>,
    muestreoBySpeciesId: SnapshotStateMap<Int, Set<String>>,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
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
            tonalElevation = 8.dp
        ) {
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
                        "Especies a muestrear",
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

                Column(modifier = Modifier.padding(20.dp).weight(1f)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = colors.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Selecciona las especies para el bote ${boteNombre?.uppercase()}. Para algas, el ingreso sera por diametro del disco.",
                                fontSize = 13.sp,
                                color = colors.primary,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

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
                    Text("Tipos de muestreo por especie", fontWeight = FontWeight.Black, fontSize = 11.sp, color = colors.onSurfaceVariant, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier
                            .heightIn(max = 140.dp)
                            .fillMaxWidth(),
                        color = colors.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.2f))
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
                                        Text(esp.com, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                        Text(
                                            esp.sci ?: "",
                                            fontSize = 10.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = colors.onSurfaceVariant
                                        )
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
                                            label = { Text("Densidad", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                                        )
                                    }
                                }
                            }
                            if (selectedSpeciesIds.isEmpty()) {
                                item {
                                    Text(
                                        "No hay especies seleccionadas. Marca al menos una especie para continuar con el muestreo.",
                                        fontSize = 13.sp,
                                        color = colors.onSurfaceVariant,
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
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.5.dp, colors.outline)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                    }
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        enabled = selectedSpeciesIds.isNotEmpty()
                    ) {
                        Text("Continuar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
