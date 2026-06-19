@file:Suppress("DEPRECATION")

package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager
import com.bitecma.app.data.OperacionSource
import com.bitecma.app.network.OperacionBoteDto
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.ui.bitecmaAmberBg
import com.bitecma.app.ui.bitecmaBlueBg
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaDangerBg
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaNavyStrong
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSoftBackgroundAlt
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaSuccessBg
import com.bitecma.app.ui.bitecmaTeal
import com.bitecma.app.ui.bitecmaTealContainer

internal data class OperacionItem(
    val op: OperacionDto,
    val source: OperacionSource
)

internal fun mergeBotesForUi(
    first: List<OperacionBoteDto>,
    second: List<OperacionBoteDto>,
): List<OperacionBoteDto> {
    if (first.isEmpty()) return second
    if (second.isEmpty()) return first

    val merged = first.map { preferred ->
        val fallback = second.firstOrNull { sameBoteIdentity(it, preferred) }
        if (fallback == null) {
            preferred
        } else {
            preferred.copy(
                zona = preferred.zona ?: fallback.zona,
                nombre = preferred.nombre ?: fallback.nombre,
                buzo = preferred.buzo ?: fallback.buzo,
                densTipo = preferred.densTipo ?: fallback.densTipo,
                submareal = preferred.submareal ?: fallback.submareal,
                boteMaestroId = preferred.boteMaestroId ?: fallback.boteMaestroId,
                lpMuestras = preferred.lpMuestras ?: fallback.lpMuestras,
                transectos = preferred.transectos ?: fallback.transectos,
            )
        }
    }

    val additional = second.filterNot { candidate ->
        merged.any { existing -> sameBoteIdentity(existing, candidate) }
    }

    return (merged + additional)
        .sortedWith(compareBy({ it.zona ?: Int.MAX_VALUE }, { normalizarTextoBusqueda(it.nombre) }, { normalizarTextoBusqueda(it.buzo) }))
}

internal fun mergeOperacionForUi(
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
        botes = primary.botes?.let { mergeBotesForUi(it, secondary.botes.orEmpty()) } ?: secondary.botes,
    )
}

internal fun operacionUiScore(op: OperacionDto): Int {
    return (op.botes?.size ?: 0) * 100 +
        (if (op.region != null) 1 else 0) +
        (if (!op.sectorAmerb.isNullOrBlank()) 1 else 0) +
        (if (!op.org.isNullOrBlank()) 1 else 0) +
        (if (op.numSeg != null) 1 else 0) +
        (if (!op.fechaInicio.isNullOrBlank()) 1 else 0)
}

internal fun mergeOperacionItemForUi(
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

@Composable
internal fun OperacionesFiltersHeader(
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
        Text("Sincronizar", fontWeight = FontWeight.Black)
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
internal fun OperacionesRegionHeader(
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
internal fun BoteDataResumenDialog(
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
                        "Este bote aun no tiene informacion registrada."
                    } else {
                        "Revisa la informacion registrada o agrega nuevos datos."
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
                    Text(if (densityUnits > 0) "Ver densidad" else "Agregar densidad", fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onOpenLp,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text(if (lpSamples > 0) "Ver peso-longitud" else "Agregar peso-longitud", fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onOpenSpecies,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Gestionar especies", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
internal fun OperacionCard(
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
        DataManager.EstadoSyncOperacion.SOLO_LOCAL -> Triple("Solo local", colors.bitecmaBlueBg, Color(0xFF1D6FA4))
        DataManager.EstadoSyncOperacion.PENDIENTE -> Triple("Pendiente", colors.bitecmaAmberBg, Color(0xFFD97706))
        DataManager.EstadoSyncOperacion.SINCRONIZANDO -> Triple("Sincronizando", colors.bitecmaTealContainer, colors.bitecmaTeal)
        DataManager.EstadoSyncOperacion.ERROR -> Triple("Error", colors.bitecmaDangerBg, colors.error)
        DataManager.EstadoSyncOperacion.SINCRONIZADO -> Triple("Sincronizada", colors.bitecmaSuccessBg, Color(0xFF059669))
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
                    val subtitulo = buildString {
                        append(op.id)

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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
internal fun BoteItem(bote: OperacionBoteDto, onClick: () -> Unit) {
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
