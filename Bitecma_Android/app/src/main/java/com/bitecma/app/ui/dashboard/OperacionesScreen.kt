package com.bitecma.app.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.text.style.TextAlign
import com.bitecma.app.network.*
import com.bitecma.app.data.*
import com.bitecma.app.utils.ExcelExporter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate

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
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showBotesDialog by remember { mutableStateOf(false) }
    var showSpeciesDialog by remember { mutableStateOf(false) }
    var showTransectDialog by remember { mutableStateOf(false) }   
    var currentOpForBotes by remember { mutableStateOf<OperacionDto?>(null) }
    var currentBoteForData by remember { mutableStateOf<OperacionBoteDto?>(null) }
    val botesList = remember { mutableStateListOf<OperacionBoteDto>() }
    val selectedSpeciesIds = remember { mutableStateListOf<Int>() }
    val transectosList = remember { mutableStateListOf<DensidadUnidadDto>() }
    var especiesMaestras by remember { mutableStateOf<List<EspecieDto>>(emptyList()) }
    var botesMaestros by remember { mutableStateOf<List<BoteMaestroDto>>(emptyList()) }
    var regiones by remember { mutableStateOf<List<RegionDto>>(emptyList()) }
    var regionLabelById by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    
    // Listas dinámicas desde API
    var sectoresAmerbApi by remember { mutableStateOf<List<SectorAmerbDto>>(emptyList()) }
    var caletasApi by remember { mutableStateOf<List<CaletaDto>>(emptyList()) }
    var opasApi by remember { mutableStateOf<List<OpaDto>>(emptyList()) }
    var selectedRegionId by remember { mutableStateOf<Int?>(1) }
    var numSeguimiento by remember { mutableStateOf("") }
    var sectorAmerbInput by remember { mutableStateOf("") }
    var selectedSectorAmerb by remember { mutableStateOf<SectorAmerb?>(null) }
    var caletaInput by remember { mutableStateOf("") }
    var selectedCaleta by remember { mutableStateOf<String?>(null) }
    var tipoOrg by remember { mutableStateOf("STI") }
    var opaInput by remember { mutableStateOf("") }
    var selectedOpa by remember { mutableStateOf<Opa?>(null) }
    var fechaInicio by remember { mutableStateOf("") }
    var fechaFin by remember { mutableStateOf("") }
    var showInicioDatePicker by remember { mutableStateOf(false) }
    var showFinDatePicker by remember { mutableStateOf(false) }

    val nomenclatura = remember(selectedRegionId, numSeguimiento) {
        val year = LocalDate.now().year
        "OP-$year-${numSeguimiento.ifBlank { "XXX" }}"
    }

    val operacionesUi by remember {
        derivedStateOf {
            DataManager.operacionesBd.map { OperacionItem(it, OperacionSource.BD) } +
                DataManager.operacionesLc.map { OperacionItem(it, OperacionSource.LC) }
        }
    }

    // Carga inicial de datos
    LaunchedEffect(isLoading) {
        if (!isLoading) return@LaunchedEffect
        try {
            val regionesRes = RetrofitClient.apiService.getRegiones()
            if (regionesRes.isSuccessful) {
                AppState.isOnline = true
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
            } else {
                AppState.isOnline = false
            }
            
            val botesRes = RetrofitClient.apiService.getBotes()
            if (botesRes.isSuccessful) {
                AppState.isOnline = true
                botesMaestros = botesRes.body()?.data ?: emptyList()
            } else {
                AppState.isOnline = false
            }

            val especiesRes = RetrofitClient.apiService.getEspecies()
            if (especiesRes.isSuccessful) {
                AppState.isOnline = true
                especiesMaestras = especiesRes.body()?.data ?: emptyList()
            } else {
                AppState.isOnline = false
            }

            // Cargar Sectores, Caletas y OPAs desde API
            try {
                val secRes = RetrofitClient.apiService.getSectoresAmerb()
                if (secRes.isSuccessful) {
                    AppState.isOnline = true
                    sectoresAmerbApi = secRes.body()?.data ?: emptyList()
                }
                
                val calRes = RetrofitClient.apiService.getCaletas()
                if (calRes.isSuccessful) {
                    AppState.isOnline = true
                    caletasApi = calRes.body()?.data ?: emptyList()
                }
                
                val opaRes = RetrofitClient.apiService.getOpas()
                if (opaRes.isSuccessful) {
                    AppState.isOnline = true
                    opasApi = opaRes.body()?.data ?: emptyList()
                }
            } catch (_: Exception) {}

            val response = RetrofitClient.apiService.getOperaciones()
            if (response.isSuccessful) {
                AppState.isOnline = true
                val body = response.body()
                if (body?.ok == true) {
                    DataManager.operacionesBd.clear()
                    DataManager.operacionesBd.addAll(body.data ?: emptyList())
                }
            } else {
                AppState.isOnline = false
            }
        } catch (_: Exception) {
            AppState.isOnline = false
        }
        isLoading = false
    }

    // --- DIALOGO DE SELECCIÓN DE ESPECIES (L-P) ---
    if (showSpeciesDialog) {
        AlertDialog(
            onDismissRequest = { showSpeciesDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.98f).padding(8.dp),
            content = {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Especies a muestrear (L-P) — Bote ${currentBoteForData?.nombre?.uppercase()}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                            IconButton(onClick = { showSpeciesDialog = false }, modifier = Modifier.border(1.dp, Color.Red, RoundedCornerShape(4.dp)).size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Selecciona las especies a muestrear en este bote. Para algas, el ingreso será por diámetro del disco.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1565C0)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(modifier = Modifier.height(300.dp)) {
                            val chunkedEspecies = especiesMaestras.chunked(3)
                            LazyColumn {
                                items(chunkedEspecies) { rowEspecies ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowEspecies.forEach { esp ->
                                            SpeciesGridItem(
                                                especie = esp,
                                                isSelected = selectedSpeciesIds.contains(esp.id),
                                                onClick = { 
                                                    if (selectedSpeciesIds.contains(esp.id)) {
                                                        selectedSpeciesIds.remove(esp.id)
                                                    } else {
                                                        selectedSpeciesIds.add(esp.id)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - rowEspecies.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Tipos de muestreo por especie", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Mostrar especies seleccionadas con su tipo de muestreo
                        LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                            items(selectedSpeciesIds) { id ->
                                val esp = especiesMaestras.find { it.id == id } ?: return@items
                                Row(
                                     modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                     verticalAlignment = Alignment.CenterVertically,
                                     horizontalArrangement = Arrangement.SpaceBetween
                                 ) {
                                     Text("${esp.com} (${esp.sci})", fontSize = 12.sp, modifier = Modifier.weight(1f))
                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                         Text("L-P", fontSize = 11.sp, color = Color.Gray)
                                        Checkbox(checked = true, onCheckedChange = { }, enabled = false)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { showSpeciesDialog = false }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                            Button(
                                onClick = { 
                                    showSpeciesDialog = false
                                    // Inicializar transectos si están vacíos
                                    if (transectosList.isEmpty()) {
                                        transectosList.add(DensidadUnidadDto(num = 1, tipo = currentBoteForData?.densTipo ?: "Transecto"))
                                    }
                                    showTransectDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                            ) { Text("Confirmar") }
                        }
                    }
                }
            }
        )
    }

    // --- DIALOGO DE AGREGAR TRANSECTOS ---
    if (showTransectDialog) {
        AlertDialog(
            onDismissRequest = { showTransectDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.98f).padding(8.dp),
            content = {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Agregar transectos — ${currentBoteForData?.nombre?.uppercase()}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                            IconButton(onClick = { showTransectDialog = false }, modifier = Modifier.border(1.dp, Color.Red, RoundedCornerShape(4.dp)).size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Completa el primer transecto y usa \"Replicar\" para copiar la configuración al resto.", fontSize = 11.sp, color = Color(0xFF1565C0))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { 
                                    if (transectosList.isNotEmpty()) {
                                        val first = transectosList[0]
                                        for (i in 1 until transectosList.size) {
                                            transectosList[i] = transectosList[i].copy(
                                                area = first.area,
                                                sustrato = first.sustrato,
                                                cubierta = first.cubierta
                                            )
                                        }
                                    }
                                }, 
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Replicar fila 1", fontSize = 12.sp) }
                            
                            OutlinedButton(
                                onClick = { 
                                    transectosList.add(DensidadUnidadDto(num = transectosList.size + 1, tipo = currentBoteForData?.densTipo ?: "Transecto"))
                                }, 
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Agregar transecto", fontSize = 12.sp) }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Header de la tabla
                        Row(Modifier.fillMaxWidth().background(Color(0xFFF1F3F5)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("#", Modifier.width(30.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text("ÁREA", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text("SUSTRATO", Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            selectedSpeciesIds.forEach { id ->
                                val esp = especiesMaestras.find { it.id == id }
                                Text(esp?.com?.take(4)?.uppercase() ?: "ESP", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.width(40.dp))
                        }

                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            itemsIndexed(transectosList) { index, t ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text((index + 1).toString(), Modifier.width(30.dp), fontSize = 12.sp)
                                    
                                    // Área
                                    OutlinedTextField(
                                        value = t.area?.toString() ?: "",
                                        onValueChange = { transectosList[index] = t.copy(area = it.toDoubleOrNull()) },
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(fontSize = 12.sp),
                                        singleLine = true
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    
                                    // Sustrato
                                    OutlinedTextField(
                                        value = t.sustrato ?: "",
                                        onValueChange = { transectosList[index] = t.copy(sustrato = it) },
                                        modifier = Modifier.weight(1.5f),
                                        textStyle = TextStyle(fontSize = 12.sp),
                                        singleLine = true
                                    )
                                    Spacer(Modifier.width(4.dp))

                                    // Counts por especie
                                    selectedSpeciesIds.forEach { id ->
                                        val currentCounts = t.counts ?: emptyMap()
                                        OutlinedTextField(
                                            value = currentCounts[id.toString()]?.toString() ?: "",
                                            onValueChange = { newVal ->
                                                val newCounts = currentCounts.toMutableMap()
                                                newVal.toIntOrNull()?.let { newCounts[id.toString()] = it } ?: newCounts.remove(id.toString())
                                                transectosList[index] = t.copy(counts = newCounts)
                                            },
                                            modifier = Modifier.weight(1f),
                                            textStyle = TextStyle(fontSize = 12.sp, textAlign = TextAlign.Center),
                                            singleLine = true
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }

                                    IconButton(onClick = { transectosList.removeAt(index) }, modifier = Modifier.width(40.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFEEEEEE))
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { showTransectDialog = false }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                            Button(
                                onClick = { 
                                    val currentBote = currentBoteForData ?: return@Button
                                    val currentOp = currentOpForBotes ?: return@Button
                                    
                                    val updatedBote = currentBote.copy(
                                        transectos = transectosList.toList()
                                    )
                                    
                                    // 1. Actualizar botesList (para cuando el diálogo de botes está abierto)
                                    val bIndex = botesList.indexOfFirst { it.zona == updatedBote.zona }
                                    if (bIndex >= 0) {
                                        botesList[bIndex] = updatedBote
                                    }

                                    // 2. Actualizar DataManager directamente
                                    val updatedBotes = (currentOp.botes ?: emptyList()).toMutableList()
                                    val opBoteIdx = updatedBotes.indexOfFirst { it.zona == updatedBote.zona }
                                    if (opBoteIdx >= 0) {
                                        updatedBotes[opBoteIdx] = updatedBote
                                    } else {
                                        updatedBotes.add(updatedBote)
                                    }
                                    
                                    val updatedOp = currentOp.copy(botes = updatedBotes)
                                    
                                    // Actualizar en la lista correspondiente
                                    val idxBd = DataManager.operacionesBd.indexOfFirst { it.id == updatedOp.id }
                                    if (idxBd >= 0) {
                                        DataManager.operacionesBd[idxBd] = updatedOp
                                    } else {
                                        val idxLc = DataManager.operacionesLc.indexOfFirst { it.id == updatedOp.id }
                                        if (idxLc >= 0) {
                                            DataManager.operacionesLc[idxLc] = updatedOp
                                        }
                                    }
                                    
                                    showTransectDialog = false 
                                }, 
                                modifier = Modifier.weight(1f), 
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                            ) { Text("Guardar") }
                        }
                    }
                }
            }
        )
    }

    // --- DIALOGO DE NUEVA OPERACIÓN ---
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp),
            content = {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Nueva operación", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                            IconButton(onClick = { showAddDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text("REGIÓN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                                var expandedRegion by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { expandedRegion = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(selectedRegionId?.let { regionLabelById[it] } ?: "Seleccionar", fontSize = 13.sp)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(expanded = expandedRegion, onDismissRequest = { expandedRegion = false }) {
                                        regiones.forEach { r ->
                                            DropdownMenuItem(
                                                text = { Text(listOfNotNull(r.rom, r.nom).joinToString(" — ")) },
                                                onClick = { selectedRegionId = r.id; expandedRegion = false }
                                            )
                                        }
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("N° SEGUIMIENTO / ESBA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                                OutlinedTextField(
                                    value = numSeguimiento,
                                    onValueChange = { numSeguimiento = it },
                                    placeholder = { Text("Ej: 16", fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("SECTOR AMERB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                        SearchableDropdown(
                            value = sectorAmerbInput,
                            onValueChange = { sectorAmerbInput = it },
                            placeholder = "Buscar sector AMERB...",
                            items = if (sectoresAmerbApi.isNotEmpty()) {
                                sectoresAmerbApi.filter { it.region == selectedRegionId }
                            } else {
                                OperacionData.sectoresAmerb.filter { it.region == selectedRegionId }.map { SectorAmerbDto(it.id, it.nombre, it.region) }
                            },
                            itemLabel = { it.nombre },
                            onItemSelected = { selectedSectorAmerb = SectorAmerb(it.id, it.nombre, it.region ?: 0); sectorAmerbInput = it.nombre }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("CALETA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                        SearchableDropdown(
                            value = caletaInput,
                            onValueChange = { caletaInput = it },
                            placeholder = "Buscar caleta...",
                            items = if (caletasApi.isNotEmpty()) {
                                caletasApi.filter { it.region == selectedRegionId }.map { it.nombre }
                            } else {
                                OperacionData.caletasByRegion[selectedRegionId] ?: emptyList()
                            },
                            itemLabel = { it },
                            onItemSelected = { selectedCaleta = it; caletaInput = it },
                            showAddNew = true,
                            addNewLabel = "Agregar Caleta..."
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("TIPO ORGANIZACIÓN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                                var expandedTipo by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { expandedTipo = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(tipoOrg, fontSize = 13.sp)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(expanded = expandedTipo, onDismissRequest = { expandedTipo = false }) {
                                        OperacionData.tiposOrganizacion.forEach { t ->
                                            DropdownMenuItem(text = { Text(t) }, onClick = { tipoOrg = t; expandedTipo = false })
                                        }
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text("ORGANIZACIÓN (OPA)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                                SearchableDropdown(
                                    value = opaInput,
                                    onValueChange = { opaInput = it },
                                    placeholder = "Buscar organización...",
                                    items = if (opasApi.isNotEmpty()) {
                                        opasApi.filter { it.region == selectedRegionId }
                                    } else {
                                        OperacionData.opas.filter { it.region == selectedRegionId }.map { OpaDto(it.id, it.nombre, it.region) }
                                    },
                                    itemLabel = { it.nombre },
                                    onItemSelected = { selectedOpa = Opa(it.id, it.nombre, it.nombre, it.region ?: 0); opaInput = it.nombre },
                                    showAddNew = true,
                                    addNewLabel = "Agregar Organización..."
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("FECHA INICIO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                                OutlinedTextField(
                                    value = fechaInicio,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().clickable { showInicioDatePicker = true },
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    enabled = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("FECHA FIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                                OutlinedTextField(
                                    value = fechaFin,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().clickable { showFinDatePicker = true },
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    enabled = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { showAddDialog = false }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                                Text("Cancelar")
                            }
                            Button(onClick = { showConfirmDialog = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))) {
                                Text("Crear")
                            }
                        }
                    }
                }
            }
        )
    }

    // --- DIALOGO DE CONFIRMACIÓN ---
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirmar creación") },
            text = { Text("¿Está seguro que quiere crear esta \"$nomenclatura\"?\nSi es así, presione \"SI\", de lo contrario \"NO\".") },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    val req = OperacionUpsertRequest(
                        id = nomenclatura,
                        region = selectedRegionId,
                        sector = caletaInput,
                        sectorAmerbId = selectedSectorAmerb?.id,
                        sectorAmerb = selectedSectorAmerb?.nombre,
                        tipoOrg = tipoOrg,
                        opaId = selectedOpa?.id,
                        org = selectedOpa?.nombre,
                        numSeg = numSeguimiento.toIntOrNull(),
                        fechaInicio = fechaInicio.ifBlank { null },
                        fechaFin = fechaFin.ifBlank { null }
                    )

                    scope.launch {
                        var finalOp: OperacionDto? = null
                        if (AppState.isOnline && !AppState.authToken.isNullOrBlank()) {
                            try {
                                val res = RetrofitClient.apiService.crearOperacion(req)
                                if (res.isSuccessful && res.body()?.ok == true) {
                                    finalOp = res.body()!!.data!!
                                    DataManager.operacionesBd.add(0, finalOp)
                                }
                            } catch (_: Exception) {}
                        }
                        
                        if (finalOp == null) {
                            finalOp = OperacionDto(
                                id = nomenclatura,
                                sector = caletaInput,
                                region = selectedRegionId,
                                numSeg = numSeguimiento.toIntOrNull(),
                                fechaInicio = fechaInicio.ifBlank { null },
                                fechaFin = fechaFin.ifBlank { null },
                                org = selectedOpa?.nombre,
                                sectorAmerb = selectedSectorAmerb?.nombre
                            )
                            DataManager.operacionesLc.add(finalOp)
                        }

                        showAddDialog = false
                        currentOpForBotes = finalOp
                        botesList.clear()
                        botesList.add(OperacionBoteDto(zona = 1, densTipo = "Transecto"))
                        showBotesDialog = true
                    }
                }) { Text("SI") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("NO") }
            }
        )
    }

    // --- DIALOGO DE BOTES ---
    if (showBotesDialog) {
        AlertDialog(
            onDismissRequest = { showBotesDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.98f).padding(8.dp),
            content = {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Botes — ${currentOpForBotes?.id}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                            IconButton(onClick = { showBotesDialog = false }, modifier = Modifier.border(1.dp, Color.Red, RoundedCornerShape(4.dp)).size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth().background(Color(0xFFF1F3F5)).padding(8.dp)) {
                            Text("#", Modifier.weight(0.3f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text("ZONA MUESTREO", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text("BOTE", Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text("BUZO", Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text("UNIDAD", Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(Modifier.weight(0.8f))
                        }
                        LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp)) {
                            itemsIndexed(botesList) { index, bote ->
                                BoteRowItem(
                                    index = index + 1,
                                    bote = bote,
                                    botesMaestros = botesMaestros,
                                    operationRegionId = currentOpForBotes?.region,
                                    onDelete = { botesList.removeAt(index) },
                                    onUpdate = { updated -> botesList[index] = updated }
                                )
                                HorizontalDivider(color = Color(0xFFEEEEEE))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton(onClick = { botesList.add(OperacionBoteDto(zona = botesList.size + 1, densTipo = "Transecto")) }) {
                                Text("Agregar fila")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showBotesDialog = false }) { Text("Cancelar") }
                                Button(
                                    onClick = { 
                                        val op = currentOpForBotes ?: return@Button
                                        val updatedBotes = botesList.toList()
                                        
                                        scope.launch {
                                            if (AppState.isOnline && !AppState.authToken.isNullOrBlank()) {
                                                try {
                                                    val req = OperacionUpsertRequest(
                                                        id = op.id,
                                                        region = op.region,
                                                        sector = op.sector,
                                                        sectorAmerbId = op.sectorAmerbId,
                                                        sectorAmerb = op.sectorAmerb,
                                                        tipoOrg = op.tipoOrg,
                                                        opaId = op.opaId,
                                                        org = op.org,
                                                        numSeg = op.numSeg,
                                                        fechaInicio = op.fechaInicio,
                                                        fechaFin = op.fechaFin,
                                                        botes = updatedBotes
                                                    )
                                                    val res = RetrofitClient.apiService.crearOperacion(req)
                                                    if (res.isSuccessful && res.body()?.ok == true) {
                                                        val savedOp = res.body()!!.data!!
                                                        // Forzar actualización en el DataManager con los datos que acabamos de enviar
                                                        // por si la API no los devuelve de inmediato en el GET
                                                        val finalSavedOp = if (savedOp.botes.isNullOrEmpty() && updatedBotes.isNotEmpty()) {
                                                            savedOp.copy(botes = updatedBotes)
                                                        } else {
                                                            savedOp
                                                        }
                                                        
                                                        val idx = DataManager.operacionesBd.indexOfFirst { it.id == op.id }
                                                        if (idx >= 0) DataManager.operacionesBd[idx] = finalSavedOp
                                                        else DataManager.operacionesBd.add(0, finalSavedOp)
                                                        DataManager.operacionesLc.removeAll { it.id == op.id }
                                                    }
                                                } catch (_: Exception) {}
                                            } else {
                                                val localOp = op.copy(botes = updatedBotes)
                                                // Buscar en ambas listas para actualizar localmente
                                                val idxBd = DataManager.operacionesBd.indexOfFirst { it.id == op.id }
                                                if (idxBd >= 0) {
                                                    DataManager.operacionesBd[idxBd] = localOp
                                                } else {
                                                    val idxLc = DataManager.operacionesLc.indexOfFirst { it.id == op.id }
                                                    if (idxLc >= 0) {
                                                        DataManager.operacionesLc[idxLc] = localOp
                                                    } else {
                                                        DataManager.operacionesLc.add(localOp)
                                                    }
                                                }
                                            }
                                            showBotesDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                                ) {
                                    Text("Guardar botes")
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (showInicioDatePicker) {
        MyDatePickerDialog(onDateSelected = { fechaInicio = it; showInicioDatePicker = false }, onDismiss = { showInicioDatePicker = false })
    }
    if (showFinDatePicker) {
        MyDatePickerDialog(onDateSelected = { fechaFin = it; showFinDatePicker = false }, onDismiss = { showFinDatePicker = false })
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
                    IconButton(onClick = { isLoading = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = Color.White)
                    }
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
                        onEditBotesClick = {
                            currentOpForBotes = item.op
                            botesList.clear()
                            botesList.addAll(item.op.botes ?: emptyList())
                            showBotesDialog = true
                        },
                        onEditDataClick = { bote ->
                            currentOpForBotes = item.op
                            currentBoteForData = bote
                            
                            // Inicializar datos para el diálogo
                            selectedSpeciesIds.clear()
                            // Extraer IDs de especies de los transectos existentes
                            val existingSpecies = bote.transectos?.flatMap { it.counts?.keys ?: emptySet() }?.mapNotNull { it.toIntOrNull() }?.distinct() ?: emptyList()
                            selectedSpeciesIds.addAll(existingSpecies)
                            
                            transectosList.clear()
                            transectosList.addAll(bote.transectos ?: emptyList())
                            
                            showSpeciesDialog = true
                        },
                        onDocClick = { navController.navigate("documentos/$userId") },
                        onDeleteClick = {
                            scope.launch {
                                var success = true
                                if (item.source == OperacionSource.BD && AppState.isOnline && !AppState.authToken.isNullOrBlank()) {
                                    try {
                                        val res = RetrofitClient.apiService.eliminarOperacion(item.op.id)
                                        success = res.isSuccessful && res.body()?.ok == true
                                    } catch (_: Exception) { success = false }
                                }
                                
                                if (success) {
                                    if (item.source == OperacionSource.BD) {
                                        DataManager.operacionesBd.remove(item.op)
                                    } else {
                                        DataManager.operacionesLc.remove(item.op)
                                    }
                                }
                            }
                        },
                        onExcelClick = {
                            scope.launch {
                                try {
                                    ExcelExporter.generateOperacionExcel(item.op)
                                    // Para descarga directa necesitamos un launcher en la UI, 
                                    // por ahora notificamos o usamos el de Documentos.
                                    // Mejor redirigir a documentos con la op seleccionada.
                                    navController.navigate("documentos/$userId")
                                } catch (_: Exception) {}
                            }
                        },
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
                                            fechaFin = item.op.fechaFin,
                                            botes = item.op.botes
                                        )
                                    )
                                    if (res.isSuccessful && res.body()?.ok == true) {
                                        DataManager.operacionesLc.remove(item.op)
                                        DataManager.operacionesBd.add(0, res.body()!!.data!!)
                                    }
                                } catch (_: Exception) {}
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
    onEditBotesClick: () -> Unit,
    onEditDataClick: (OperacionBoteDto) -> Unit,
    onDocClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onExcelClick: () -> Unit,
    onUploadLocalClick: () -> Unit
) {
    val op = item.op
    val tagBg = if (item.source == OperacionSource.BD) Color(0xFFDFF3E7) else Color(0xFFF8D7DA)
    val tagFg = if (item.source == OperacionSource.BD) Color(0xFF1B5E20) else Color(0xFF8A1F2D)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = tagBg, shape = RoundedCornerShape(6.dp)) {
                            Box(modifier = Modifier.width(34.dp).height(22.dp), contentAlignment = Alignment.Center) {
                                Text(text = if (item.source == OperacionSource.BD) "BD" else "LC", color = tagFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(regionLabel ?: "Sin región", fontWeight = FontWeight.ExtraBold, color = Color(0xFF003366), fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("${op.id} · ${op.sector?.uppercase() ?: ""} · ${op.fechaInicio ?: ""}", fontSize = 12.sp, color = Color.Gray)
                }
                Row {
                    if (item.source == OperacionSource.LC && AppState.isOnline && !AppState.authToken.isNullOrBlank()) {
                        IconButton(onClick = onUploadLocalClick) { Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF1B5E20)) }
                    }
                    IconButton(onClick = onExcelClick) { Icon(Icons.Default.FileDownload, null, tint = Color(0xFF00897B)) }
                    IconButton(onClick = onEditBotesClick) { Icon(Icons.Default.Edit, null, tint = Color.Gray) }
                    IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.7f)) }
                    IconButton(onClick = onExpandClick) { Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Expandir") }
                }
            }
            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.5f))
                Text("Botes registrados:", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                
                val botesAMostrar = op.botes ?: emptyList()
                if (botesAMostrar.isEmpty()) {
                    Text("No hay botes registrados en esta operación.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    botesAMostrar.forEach { bote -> 
                        BoteItem(bote = bote, onClick = { onEditDataClick(bote) })
                        Spacer(modifier = Modifier.height(10.dp)) 
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDocClick, 
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                ) { 
                    Text("Ir a Documentación (XLSX)", color = Color.White, fontWeight = FontWeight.Bold) 
                }
            }
        }
    }
}

@Composable
fun BoteItem(bote: OperacionBoteDto, onClick: () -> Unit) {
    val transectCount = bote.transectos?.size ?: 0
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsBoat, contentDescription = null, tint = Color(0xFF003366), modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = bote.nombre?.uppercase() ?: "BOTE SIN NOMBRE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.Black)
                Text(text = "Zona ${bote.zona ?: 0} · ${bote.buzo?.uppercase() ?: "SIN BUZO"}", fontSize = 12.sp, color = Color.Gray)
                Text(text = "Tipo: ${bote.densTipo?.lowercase() ?: "transecto"}", fontSize = 12.sp, color = Color.Gray)
            }
            if (transectCount > 0) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$transectCount", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                        Text(text = if (transectCount == 1) "Unidad" else "Unidades", fontSize = 8.sp, color = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}

@Composable
fun SpeciesGridItem(especie: EspecieDto, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(2.dp, Color(0xFF00897B)) else null,
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE8F5E9) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(especie.com ?: "Especie", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            Text(especie.sci ?: "", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 9.sp, color = Color.Gray, maxLines = 1)
            if (isSelected) { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00897B), modifier = Modifier.size(14.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableDropdown(value: String, onValueChange: (String) -> Unit, placeholder: String, items: List<T>, itemLabel: (T) -> String, onItemSelected: (T) -> Unit, showAddNew: Boolean = false, addNewLabel: String = "") {
    var expanded by remember { mutableStateOf(false) }
    val filteredItems = items.filter { itemLabel(it).contains(value, ignoreCase = true) }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(value = value, onValueChange = { onValueChange(it); expanded = true }, placeholder = { Text(placeholder, fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true, trailingIcon = { IconButton(onClick = { expanded = !expanded }) { Icon(Icons.Default.ArrowDropDown, null) } })
        DropdownMenu(expanded = expanded && (filteredItems.isNotEmpty() || showAddNew), onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.8f).heightIn(max = 250.dp)) {
            if (showAddNew) {
                DropdownMenuItem(text = { Text(addNewLabel, color = Color(0xFF00897B), fontWeight = FontWeight.Bold) }, onClick = { expanded = false })
                HorizontalDivider()
            }
            filteredItems.forEach { item -> DropdownMenuItem(text = { Text(itemLabel(item), fontSize = 13.sp) }, onClick = { onItemSelected(item); expanded = false }) }
        }
    }
}

@Composable
fun BoteRowItem(index: Int, bote: OperacionBoteDto, botesMaestros: List<BoteMaestroDto>, operationRegionId: Int?, onDelete: () -> Unit, onUpdate: (OperacionBoteDto) -> Unit) {
    var showUnitWarning by remember { mutableStateOf(false) }
    var nextUnitType by remember { mutableStateOf("") }
    var showBoteSearch by remember { mutableStateOf(false) }
    if (showUnitWarning) {
        AlertDialog(onDismissRequest = { showUnitWarning = false }, title = { Text("BITECMA Dice:") }, text = { Text("Al cambiar la unidad de muestreo, solo se perderán los datos de densidad (los datos de peso-longitud se mantendrán). ¿Continuar?") }, confirmButton = { Button(onClick = { onUpdate(bote.copy(densTipo = nextUnitType)); showUnitWarning = false }) { Text("Aceptar") } }, dismissButton = { TextButton(onClick = { showUnitWarning = false }) { Text("Cancelar") } })
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(index.toString(), Modifier.weight(0.3f), fontSize = 12.sp)
        OutlinedTextField(value = bote.zona?.toString() ?: "", onValueChange = { onUpdate(bote.copy(zona = it.toIntOrNull())) }, modifier = Modifier.weight(1f), textStyle = TextStyle(fontSize = 12.sp))
        Box(Modifier.weight(1.5f)) {
            OutlinedTextField(value = bote.nombre ?: "", onValueChange = {}, readOnly = true, placeholder = { Text("Nombre bote", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth().clickable { showBoteSearch = true }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = Color.LightGray, disabledTextColor = Color.Black))
            if (showBoteSearch) { BoteMaestroSearchDialog(botes = botesMaestros, operationRegionId = operationRegionId, onSelect = { m -> onUpdate(bote.copy(nombre = m.nombre, boteMaestroId = m.id)); showBoteSearch = false }, onDismiss = { showBoteSearch = false }) }
        }
        OutlinedTextField(value = bote.buzo ?: "", onValueChange = { onUpdate(bote.copy(buzo = it)) }, modifier = Modifier.weight(1.2f), placeholder = { Text("Nombre buzo", fontSize = 11.sp) }, textStyle = TextStyle(fontSize = 12.sp))
        var expandedUnit by remember { mutableStateOf(false) }
        Box(Modifier.weight(1.2f)) {
            OutlinedButton(onClick = { expandedUnit = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(2.dp), shape = RoundedCornerShape(4.dp)) {
                Text(bote.densTipo ?: "Transecto", fontSize = 10.sp)
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(expanded = expandedUnit, onDismissRequest = { expandedUnit = false }) {
                listOf("Transecto", "Cuadrante").forEach { type -> DropdownMenuItem(text = { Text(type, fontSize = 12.sp) }, onClick = { if (type == "Cuadrante" && bote.densTipo == "Transecto") { nextUnitType = type; showUnitWarning = true } else { onUpdate(bote.copy(densTipo = type)) }; expandedUnit = false }) }
            }
        }
        TextButton(onClick = onDelete, Modifier.weight(0.8f)) { Text("Eliminar", color = Color.Gray, fontSize = 10.sp) }
    }
}

@Composable
fun BoteMaestroSearchDialog(botes: List<BoteMaestroDto>, operationRegionId: Int?, onSelect: (BoteMaestroDto) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    
    // Filtrar primero por la región de la operación, si está disponible
    val filtered = botes.filter { b ->
        val matchesQuery = (b.nombre ?: "").contains(query, true) || 
                          (b.nrpa ?: "").contains(query, true) || 
                          (b.nmatricula ?: "").contains(query, true)
        
        // Si hay una región seleccionada en la operación, priorizar o filtrar por ella
        // (Opcional: puedes decidir si mostrar todos o solo los de la región)
        matchesQuery
    }.sortedByDescending { 
        // Priorizar los que coinciden con la región de la operación
        if (operationRegionId != null) {
            // Aquí asumo que b.region o b.region_rom se puede mapear al ID
            // Pero por simplicidad, solo mostramos el filtro de búsqueda
            true 
        } else true
    }

    AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, title = { 
        Column {
            Text("Buscar Bote Maestro", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, 
                onValueChange = { query = it }, 
                placeholder = { Text("Nombre, RPA o matrícula...") }, 
                modifier = Modifier.fillMaxWidth(), 
                trailingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }, text = {
        LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(filtered) { b ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(b) }.padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(b.nombre ?: "S/N", fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RPA: ${b.nrpa ?: "—"}", fontSize = 11.sp, color = Color.Gray)
                            Text("MAT: ${b.nmatricula ?: "—"}", fontSize = 11.sp, color = Color.Gray)
                        }
                        Text("CALETA: ${b.caleta ?: "—"} · REGIÓN: ${b.region_rom ?: b.region ?: "—"}", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text("No se encontraron botes", modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center, color = Color.Gray)
                }
            }
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDatePickerDialog(onDateSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate(); val fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy"); onDateSelected(date.format(fmt)) }; onDismiss() }) { Text("OK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }) { DatePicker(state = datePickerState) }
}
