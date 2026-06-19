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
                        "ESPECIES A MUESTREAR",
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
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD).copy(alpha = 0.8f)),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFFBBDEFB))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFF1565C0), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Selecciona las especies para el bote ${boteNombre?.uppercase()}. Para algas, el ingreso sera por diametro del disco.",
                                fontSize = 13.sp,
                                color = Color(0xFF1565C0),
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
                    Text("TIPOS DE MUESTREO POR ESPECIE", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))

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
                                        Text(
                                            esp.sci ?: "",
                                            fontSize = 10.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = Color.Gray
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
                                            label = { Text("DENSIDAD", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                                        )
                                    }
                                }
                            }
                            if (selectedSpeciesIds.isEmpty()) {
                                item {
                                    Text(
                                        "No hay especies seleccionadas. Marca al menos una especie para continuar con el muestreo.",
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
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.5.dp, Color.Gray)
                    ) {
                        Text("CANCELAR", fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                    Button(
                        onClick = onContinue,
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
