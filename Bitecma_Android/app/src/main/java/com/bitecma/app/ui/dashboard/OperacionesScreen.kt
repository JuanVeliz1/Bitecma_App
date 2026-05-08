package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.OperacionUpsertRequest
import com.bitecma.app.network.RegionDto
import com.bitecma.app.network.OperacionBoteDto
import com.bitecma.app.network.RetrofitClient
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager
import kotlinx.coroutines.launch

private enum class OperacionSource { BD, LC }

private data class OperacionItem(
    val op: OperacionDto,
    val source: OperacionSource
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperacionesScreen(navController: NavController, userId: Int) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var expandedOpId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var regiones by remember { mutableStateOf<List<RegionDto>>(emptyList()) }
    var regionLabelById by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    // Campos para Nueva Operación
    var selectedRegionId by remember { mutableStateOf<Int?>(null) }
    var sectorInput by remember { mutableStateOf("") }
    var fechaInicio by remember { mutableStateOf("") }
    var fechaFin by remember { mutableStateOf("") }

    val operacionesUi by remember {
        derivedStateOf {
            DataManager.operacionesBd.map { OperacionItem(it, OperacionSource.BD) } +
                DataManager.operacionesLc.map { OperacionItem(it, OperacionSource.LC) }
        }
    }

    // Carga inicial de datos (Híbrido API/Local)
    LaunchedEffect(Unit) {
        try {
            val regionesRes = RetrofitClient.apiService.getRegiones()
            if (regionesRes.isSuccessful) {
                val body = regionesRes.body()
                if (body?.ok == true && body.data != null) {
                    regiones = body.data
                    regionLabelById = body.data.associate { r ->
                        val label = listOfNotNull(r.rom, r.nom).joinToString(" — ").ifBlank { "Región ${r.id}" }
                        r.id to label
                    }
                    if (selectedRegionId == null) {
                        selectedRegionId = body.data.firstOrNull()?.id
                    }
                }
            }
            val response = RetrofitClient.apiService.getOperaciones()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.ok == true) {
                    DataManager.operacionesBd.clear()
                    DataManager.operacionesBd.addAll(body.data ?: emptyList())
                    isLoading = false
                    return@LaunchedEffect
                }
            }
        } catch (_: Exception) {
        }

        isLoading = false
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nueva Operación") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Región", fontSize = 12.sp, color = Color.Gray)
                    var expandedRegion by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { expandedRegion = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedLabel = selectedRegionId?.let { regionLabelById[it] } ?: "Seleccionar"
                            Text(selectedLabel)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expandedRegion, onDismissRequest = { expandedRegion = false }) {
                            regiones.forEach { r ->
                                val label = listOfNotNull(r.rom, r.nom).joinToString(" — ").ifBlank { "Región ${r.id}" }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedRegionId = r.id
                                        expandedRegion = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sectorInput,
                        onValueChange = { sectorInput = it },
                        label = { Text("Sector / AMERB") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = fechaInicio,
                            onValueChange = { fechaInicio = it },
                            label = { Text("Inicio (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("2026-05-06") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = fechaFin,
                            onValueChange = { fechaFin = it },
                            label = { Text("Fin (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("2026-05-06") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newId = "OP-${(100..999).random()}"
                    val req = OperacionUpsertRequest(
                        id = newId,
                        region = selectedRegionId,
                        sector = sectorInput,
                        fechaInicio = fechaInicio.ifBlank { null },
                        fechaFin = fechaFin.ifBlank { null }
                    )

                    scope.launch {
                        if (AppState.isOnline && !AppState.authToken.isNullOrBlank()) {
                            try {
                                val res = RetrofitClient.apiService.crearOperacion(req)
                                if (res.isSuccessful) {
                                    val body = res.body()
                                    if (body?.ok == true && body.data != null) {
                                        DataManager.operacionesBd.add(0, body.data)
                                        showAddDialog = false
                                        return@launch
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }

                        DataManager.operacionesLc.add(
                            OperacionDto(
                            id = newId,
                            sector = sectorInput,
                            region = selectedRegionId,
                            fechaInicio = fechaInicio.ifBlank { null },
                            fechaFin = fechaFin.ifBlank { null }
                        )
                        )
                        showAddDialog = false
                    }
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Operaciones", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
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
                }

                items(operacionesUi) { item ->
                    OperacionCard(
                        item = item,
                        regionLabel = item.op.region?.let { regionLabelById[it] },
                        isExpanded = expandedOpId == item.op.id,
                        onExpandClick = { expandedOpId = if (expandedOpId == item.op.id) null else item.op.id },
                        onEvadirClick = { navController.navigate("evadir/${item.op.id}") },
                        onUploadLocalClick = {
                            if (item.source != OperacionSource.LC) return@OperacionCard
                            if (!AppState.isOnline || AppState.authToken.isNullOrBlank()) return@OperacionCard
                            scope.launch {
                                try {
                                    val res = RetrofitClient.apiService.crearOperacion(
                                        OperacionUpsertRequest(
                                            id = item.op.id,
                                            region = item.op.region,
                                            sector = item.op.sector,
                                            fechaInicio = item.op.fechaInicio,
                                            fechaFin = item.op.fechaFin
                                        )
                                    )
                                    if (res.isSuccessful) {
                                        val body = res.body()
                                        if (body?.ok == true && body.data != null) {
                                            DataManager.operacionesLc.remove(item.op)
                                            DataManager.operacionesBd.add(0, body.data)
                                        }
                                    }
                                } catch (_: Exception) {
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

@Composable
private fun OperacionCard(
    item: OperacionItem,
    regionLabel: String?,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onEvadirClick: () -> Unit,
    onUploadLocalClick: () -> Unit
) {
    val op = item.op
    val tagBg = if (item.source == OperacionSource.BD) Color(0xFFDFF3E7) else Color(0xFFF8D7DA)
    val tagFg = if (item.source == OperacionSource.BD) Color(0xFF1B5E20) else Color(0xFF8A1F2D)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = tagBg, shape = RoundedCornerShape(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (item.source == OperacionSource.BD) "BD" else "LC",
                                    color = tagFg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(regionLabel ?: "Sin región", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${op.id} · ${op.sector} · ${op.fechaInicio ?: ""}", fontSize = 12.sp, color = Color.Gray)
                }

                if (item.source == OperacionSource.LC && AppState.isOnline && !AppState.authToken.isNullOrBlank()) {
                    TextButton(onClick = onUploadLocalClick) { Text("Subir") }
                }
                IconButton(onClick = onExpandClick) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expandir"
                    )
                }
            }

            if (isExpanded) {
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Text("Botes registrados:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                ;(op.botes ?: emptyList()).forEach { bote ->
                    BoteItem(bote)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onEvadirClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                ) {
                    Text("Previsualizar EVADIR", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun BoteItem(bote: OperacionBoteDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F9FA), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.DirectionsBoat,
            contentDescription = null,
            tint = Color(0xFF003366),
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(bote.nombre ?: "Bote", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Zona ${bote.zona ?: 0} · ${bote.buzo ?: ""}", fontSize = 12.sp, color = Color.Gray)
            Text("Tipo: ${bote.densTipo ?: ""}", fontSize = 12.sp, color = Color.Gray)
        }
    }
}
