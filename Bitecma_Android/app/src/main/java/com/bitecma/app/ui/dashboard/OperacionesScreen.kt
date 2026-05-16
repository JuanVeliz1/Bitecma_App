package com.bitecma.app.ui.dashboard
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.compose.ui.text.style.TextAlign
import com.bitecma.app.network.*
import com.bitecma.app.data.*
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate

private enum class OperacionSource { BD, LC }
private data class OperacionItem(
    val op: OperacionDto,
    val source: OperacionSource
)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val muestreoBySpeciesId = remember { mutableStateMapOf<Int, Set<String>>() }
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
    var validationError by remember { mutableStateOf<String?>(null) }

    val nomenclatura = remember(selectedRegionId, numSeguimiento) {
        val year = LocalDate.now().year
        "OP-$year-${numSeguimiento.ifEmpty { "XXX" }}"
    }

    val operacionesUi by remember {
        derivedStateOf {
            DataManager.operacionesBd.map { OperacionItem(it, OperacionSource.BD) } +
                DataManager.operacionesLc.map { OperacionItem(it, OperacionSource.LC) }
        }
    }

    // Carga inicial de datos
    LaunchedEffect(isLoading, userId) {
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
            }

            val especiesRes = RetrofitClient.apiService.getEspecies()
            if (especiesRes.isSuccessful) {
                AppState.isOnline = true
                especiesMaestras = especiesRes.body()?.data ?: emptyList()
            }

            // Cargar Sectores, Caletas y OPAs desde API de forma explícita
            try {
                val secRes = RetrofitClient.apiService.getSectoresAmerb()
                if (secRes.isSuccessful) {
                    sectoresAmerbApi = secRes.body()?.data ?: emptyList()
                    println("Sectores cargados: ${sectoresAmerbApi.size}")
                }
                
                val calRes = RetrofitClient.apiService.getCaletas()
                if (calRes.isSuccessful) {
                    caletasApi = calRes.body()?.data ?: emptyList()
                    println("Caletas cargadas: ${caletasApi.size}")
                }
                
                val opaRes = RetrofitClient.apiService.getOpas()
                if (opaRes.isSuccessful) {
                    opasApi = opaRes.body()?.data ?: emptyList()
                    println("OPAs cargadas: ${opasApi.size}")
                }

                // Fallback: Si no hay caletas de la API, extraer de botes maestros
                if (caletasApi.isEmpty() && botesMaestros.isNotEmpty()) {
                    caletasApi = botesMaestros.mapNotNull { it.caleta }.distinct().map { 
                        // Intentar inferir la región del bote para la caleta
                        val bote = botesMaestros.find { b -> b.caleta == it }
                        val regId = bote?.region_rom?.let { rom -> 
                            regiones.find { r -> r.rom == rom }?.id 
                        }
                        CaletaDto(nombre = it, region = regId)
                    }
                    println("Caletas extraídas de botes: ${caletasApi.size}")
                }
            } catch (e: Exception) {
                println("Error cargando dropdowns: ${e.message}")
            }

            val response = RetrofitClient.apiService.getOperaciones()
            if (response.isSuccessful) {
                AppState.isOnline = true
                val body = response.body()
                if (body?.ok == true) {
                    DataManager.operacionesBd.clear()
                    DataManager.operacionesBd.addAll(body.data ?: emptyList())
                }
            }
        } catch (e: Exception) {
            AppState.isOnline = false
            println("Error en carga inicial: ${e.message}")
        }
        isLoading = false
    }

    // --- DIALOGO DE SELECCIÓN DE ESPECIES (L-P) ---
    if (showSpeciesDialog) {
        Dialog(
            onDismissRequest = { showSpeciesDialog = false },
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
                    // Header con degradado estilo web
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
                            onClick = { showSpeciesDialog = false },
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
                                    "Selecciona las especies para el bote ${currentBoteForData?.nombre?.uppercase()}. Para algas, el ingreso será por diámetro del disco.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1565C0),
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Grid de especies
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
                        
                        // Lista de seleccionadas estilizada
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
                                            Text(esp.sci ?: "", fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color.Gray)
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
                                    item { Text("No hay especies seleccionadas", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                                }
                            }
                        }
                    }

                    // Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSpeciesDialog = false },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(1.5.dp, Color.Gray)
                        ) { 
                            Text("CANCELAR", fontWeight = FontWeight.Bold, color = Color.Gray) 
                        }
                        Button(
                            onClick = { 
                                showSpeciesDialog = false
                                if (transectosList.isEmpty()) {
                                    transectosList.add(DensidadUnidadDto(num = 1, tipo = currentBoteForData?.densTipo ?: "Transecto", area = 120.0))
                                }
                                showTransectDialog = true
                            },
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

    // --- DIALOGO DE AGREGAR TRANSECTOS ---
    if (showTransectDialog) {
        Dialog(
            onDismissRequest = { showTransectDialog = false },
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
                    // Header con degradado estilo web
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
                            "AGREGAR TRANSECTOS — ${currentBoteForData?.nombre?.uppercase()}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        IconButton(
                            onClick = { showTransectDialog = false },
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
                                    "Completa el primer transecto y usa \"Replicar\" para copiar la configuración al resto.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1565C0),
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                shape = RoundedCornerShape(26.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                                border = BorderStroke(1.5.dp, Color(0xFF003366))
                            ) { 
                                Text("REPLICAR FILA 1", fontSize = 13.sp, color = Color(0xFF003366), fontWeight = FontWeight.Bold) 
                            }
                            
                            Button(
                                onClick = { 
                                    transectosList.add(DensidadUnidadDto(num = transectosList.size + 1, tipo = currentBoteForData?.densTipo ?: "Transecto", area = 120.0))
                                }, 
                                shape = RoundedCornerShape(26.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366))
                            ) { 
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("AGREGAR FILA", fontSize = 13.sp, fontWeight = FontWeight.Bold) 
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Color(0xFFF1F3F5), RoundedCornerShape(12.dp))
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(transectosList) { index, t ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFF1F3F5)),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "UNIDAD ${t.num ?: (index + 1)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF003366),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = { transectosList.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            Spacer(Modifier.height(12.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("ÁREA (M²)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                    BasicTextField(
                                                        value = (t.area ?: 120.0).toString(),
                                                        onValueChange = { transectosList[index] = t.copy(area = it.toDoubleOrNull()) },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 6.dp)
                                                            .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                            .background(Color.White, RoundedCornerShape(10.dp))
                                                            .padding(10.dp),
                                                        textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Black),
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1.4f)) {
                                                    Text("SUSTRATO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                    BasicTextField(
                                                        value = t.sustrato.orEmpty(),
                                                        onValueChange = { transectosList[index] = t.copy(sustrato = it) },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 6.dp)
                                                            .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                            .background(Color.White, RoundedCornerShape(10.dp))
                                                            .padding(10.dp),
                                                        textStyle = TextStyle(fontSize = 13.sp),
                                                        singleLine = true
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(12.dp))

                                            Text("FAUNA (FILAS)", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                                            Spacer(Modifier.height(8.dp))

                                            val currentCounts = t.counts ?: emptyMap()
                                            val densidadSpeciesIds = remember(selectedSpeciesIds.toList(), muestreoBySpeciesId.toMap()) {
                                                selectedSpeciesIds.filter { sid -> muestreoBySpeciesId[sid]?.contains("DENSIDAD") != false }
                                            }
                                            if (densidadSpeciesIds.isEmpty()) {
                                                Text("No hay fauna seleccionada.", fontSize = 12.sp, color = Color.Gray)
                                            } else {
                                                densidadSpeciesIds.forEach { sid ->
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
                                                            color = Color(0xFF003366),
                                                            maxLines = 1
                                                        )
                                                        BasicTextField(
                                                            value = currentCounts[sid.toString()]?.toString().orEmpty(),
                                                            onValueChange = { newVal ->
                                                                val newCounts = currentCounts.toMutableMap()
                                                                newVal.toIntOrNull()?.let { newCounts[sid.toString()] = it } ?: newCounts.remove(sid.toString())
                                                                transectosList[index] = t.copy(counts = newCounts)
                                                            },
                                                            modifier = Modifier
                                                                .width(88.dp)
                                                                .border(1.5.dp, Color(0xFFF1F3F5), RoundedCornerShape(10.dp))
                                                                .background(Color.White, RoundedCornerShape(10.dp))
                                                                .padding(10.dp),
                                                            textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Black, color = Color(0xFF00897B)),
                                                            singleLine = true,
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                        )
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

                    // Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showTransectDialog = false },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(1.5.dp, Color.Gray)
                        ) { 
                            Text("CANCELAR", fontWeight = FontWeight.Bold, color = Color.Gray) 
                        }
                        Button(
                            onClick = { 
                                val currentBote = currentBoteForData ?: return@Button
                                val currentOp = currentOpForBotes ?: return@Button
                                
                                val normalizedTransectos = transectosList.map { t ->
                                    t.copy(area = t.area ?: 120.0)
                                }
                                val updatedBote = currentBote.copy(
                                    transectos = normalizedTransectos
                                )
                                
                                val bIndex = botesList.indexOfFirst { it.zona == updatedBote.zona }
                                if (bIndex >= 0) { botesList[bIndex] = updatedBote }

                                val updatedBotes = (currentOp.botes ?: emptyList()).toMutableList()
                                val opBoteIdx = updatedBotes.indexOfFirst { it.zona == updatedBote.zona }
                                if (opBoteIdx >= 0) { updatedBotes[opBoteIdx] = updatedBote } else { updatedBotes.add(updatedBote) }
                                
                                val updatedOp = currentOp.copy(botes = updatedBotes)
                                val idxBd = DataManager.operacionesBd.indexOfFirst { it.id == updatedOp.id }
                                if (idxBd >= 0) { DataManager.operacionesBd[idxBd] = updatedOp } else {
                                    val idxLc = DataManager.operacionesLc.indexOfFirst { it.id == updatedOp.id }
                                    if (idxLc >= 0) { DataManager.operacionesLc[idxLc] = updatedOp }
                                }
                                
                                showTransectDialog = false 

                                scope.launch {
                                    if (AppState.isOnline && !AppState.authToken.isNullOrBlank()) {
                                        try {
                                            val updatedBotesApi = updatedBotes.map { b ->
                                                val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                                                b.copy(
                                                    densTipo = dens,
                                                    transectos = b.transectos?.map { t ->
                                                        t.copy(tipo = if (dens == "cuadrante") "cuadrante" else "transecto")
                                                    }
                                                )
                                            }
                                            val req = OperacionUpsertRequest(
                                                id = currentOp.id,
                                                region = currentOp.region,
                                                sector = currentOp.sector,
                                                sectorAmerbId = currentOp.sectorAmerbId,
                                                sectorAmerb = currentOp.sectorAmerb,
                                                tipoOrg = currentOp.tipoOrg,
                                                opaId = currentOp.opaId,
                                                org = currentOp.org,
                                                numSeg = currentOp.numSeg,
                                                fechaInicio = currentOp.fechaInicio,
                                                fechaFin = currentOp.fechaFin,
                                                botes = updatedBotesApi
                                            )
                                            val res = RetrofitClient.apiService.actualizarOperacion(currentOp.id, req)
                                            if (res.isSuccessful && res.body()?.ok == true) {
                                                val savedOp = res.body()!!.data!!
                                                val finalSavedOp = if (savedOp.botes.isNullOrEmpty() && updatedBotes.isNotEmpty()) {
                                                    savedOp.copy(botes = updatedBotes)
                                                } else {
                                                    savedOp
                                                }
                                                val idx = DataManager.operacionesBd.indexOfFirst { it.id == currentOp.id }
                                                if (idx >= 0) DataManager.operacionesBd[idx] = finalSavedOp else DataManager.operacionesBd.add(0, finalSavedOp)
                                                currentOpForBotes = finalSavedOp
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }, 
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                        ) { 
                            Text("GUARDAR DATOS", fontWeight = FontWeight.Bold) 
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGO DE NUEVA OPERACIÓN ---
    if (showAddDialog) {
        Dialog(
            onDismissRequest = { showAddDialog = false },
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
                // Colores para el ComboBox (un poco más oscuro que celeste pastel, con alto contraste)
                val comboBg = if (isDark) Color(0xFF0D47A1) else Color.White
                val comboText = if (isDark) Color.White else Color.Black
                val comboBorder = if (isDark) Color(0xFF1976D2) else Color(0xFFF1F3F5)
                val labelColor = if (isDark) Color(0xFFBBDEFB) else Color.Gray
                val accentColor = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366)

                Column(modifier = Modifier.fillMaxSize()) {
                    // Header con estilo web moderno
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
                            "NUEVA OPERACIÓN",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        IconButton(
                            onClick = { showAddDialog = false },
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
                        // Sección: Ubicación
                        Text("UBICACIÓN Y SECTOR", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
                        Spacer(Modifier.height(12.dp))
                        
                        Text("REGIÓN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                        var expandedReg by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { expandedReg = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, comboBorder),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = comboBg)
                            ) {
                                Text(selectedRegionId?.let { regionLabelById[it] } ?: "Seleccionar Región", color = comboText, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                                Icon(Icons.Default.ArrowDropDown, null, tint = labelColor)
                            }
                            DropdownMenu(
                                expanded = expandedReg, 
                                onDismissRequest = { expandedReg = false },
                                modifier = Modifier.background(comboBg)
                            ) {
                                regiones.forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text(listOfNotNull(r.rom, r.nom).joinToString(" — "), color = comboText) },
                                        onClick = { selectedRegionId = r.id; expandedReg = false }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("SECTOR AMERB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                        val filteredSectores = if (sectoresAmerbApi.isNotEmpty()) {
                            sectoresAmerbApi.filter { it.region == selectedRegionId }
                        } else {
                            OperacionData.sectoresAmerb.filter { it.region == selectedRegionId }.map { SectorAmerbDto(it.id, it.nombre, it.region) }
                        }
                        SearchableDropdown(
                            value = sectorAmerbInput,
                            onValueChange = { sectorAmerbInput = it },
                            placeholder = if (filteredSectores.isEmpty()) "Sin sectores en esta región" else "Buscar sector...",
                            items = filteredSectores,
                            itemLabel = { it.nombre },
                            onItemSelected = { selectedSectorAmerb = SectorAmerb(it.id, it.nombre, it.region ?: 0); sectorAmerbInput = it.nombre }
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = comboBorder)
                        Spacer(modifier = Modifier.height(24.dp))

                        // Sección: Identificación
                        Text("IDENTIFICACIÓN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
                        Spacer(Modifier.height(12.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("N° SEGUIMIENTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                                OutlinedTextField(
                                    value = numSeguimiento,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) numSeguimiento = it },
                                    modifier = Modifier.fillMaxWidth().background(comboBg, RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                    modifier = Modifier.fillMaxWidth().background(comboBg, RoundedCornerShape(12.dp)).clickable { showInicioDatePicker = true },
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
                                                    if (it is PressInteraction.Release) {
                                                        showInicioDatePicker = true
                                                    }
                                                }
                                            }
                                        }
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text("FECHA TÉRMINO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                                OutlinedTextField(
                                    value = fechaFin,
                                    onValueChange = { },
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().background(comboBg, RoundedCornerShape(12.dp)).clickable { showFinDatePicker = true },
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
                                                    if (it is PressInteraction.Release) {
                                                        showFinDatePicker = true
                                                    }
                                                }
                                            }
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("CALETA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                        val filteredCaletas = remember(caletasApi, selectedRegionId, selectedSectorAmerb) {
                            // 1. Intentar filtrar por Sector AMERB si hay uno seleccionado y la API tiene datos
                            var result = if (selectedSectorAmerb != null && caletasApi.isNotEmpty()) {
                                caletasApi.filter { it.region == selectedRegionId && it.sectorAmerbId == selectedSectorAmerb?.id }
                            } else {
                                emptyList()
                            }

                            // 2. Si no hay resultados por sector o no hay sector seleccionado, filtrar por Región (API)
                            if (result.isEmpty() && caletasApi.isNotEmpty()) {
                                result = caletasApi.filter { it.region == selectedRegionId }
                            }

                            // 3. Si la API sigue sin dar resultados para la región, usar los datos locales (fallback)
                            if (result.isEmpty()) {
                                (OperacionData.caletasByRegion[selectedRegionId] ?: emptyList()).map { 
                                    CaletaDto(id = 0, nombre = it, region = selectedRegionId ?: 0) 
                                }
                            } else {
                                result
                            }
                        }
                        SearchableDropdown(
                            value = caletaInput,
                            onValueChange = { caletaInput = it },
                            placeholder = if (filteredCaletas.isEmpty()) "Sin caletas disponibles" else "Buscar caleta...",
                            items = filteredCaletas,
                            itemLabel = { it.nombre },
                            onItemSelected = { selectedCaleta = it.nombre; caletaInput = it.nombre },
                            showAddNew = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Actualización dinámica de botes según caleta
                        LaunchedEffect(selectedCaleta) {
                            if (!selectedCaleta.isNullOrBlank()) {
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = comboBorder)
                        Spacer(modifier = Modifier.height(24.dp))

                        // Sección: Organización
                        Text("ORGANIZACIÓN (OPA)", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accentColor)
                        Spacer(Modifier.height(12.dp))

                        Text("TIPO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                        var expandedTipo by remember { mutableStateOf(false) }
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
                                OperacionData.tiposOrganizacion.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t, color = comboText) }, 
                                        onClick = { tipoOrg = t; expandedTipo = false }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("NOMBRE OPA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                        val filteredOpas = if (opasApi.isNotEmpty()) {
                            opasApi.filter { it.region == selectedRegionId }
                        } else {
                            OperacionData.opas.filter { it.region == selectedRegionId }.map { OpaDto(it.id, it.nombre, region = it.region) }
                        }
                        SearchableDropdown(
                            value = opaInput,
                            onValueChange = { opaInput = it },
                            placeholder = if (filteredOpas.isEmpty()) "Sin OPAs en esta región" else "Buscar OPA...",
                            items = filteredOpas,
                            itemLabel = { it.nombre },
                            onItemSelected = { selectedOpa = Opa(it.id, it.nombre, it.nombre, it.region ?: 0); opaInput = it.nombre },
                            showAddNew = true
                        )

                        if (validationError != null) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = validationError!!,
                                color = Color.Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Footer con botones robustos
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddDialog = false; validationError = null },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(1.5.dp, Color(0.6f, 0.6f, 0.6f))
                        ) { Text("CANCELAR", fontWeight = FontWeight.Bold, color = if (isDark) Color.LightGray else Color.Gray) }
                        
                        Button(
                            onClick = {
                                val error = when {
                                    selectedRegionId == null -> "Debe seleccionar una región"
                                    numSeguimiento.isBlank() -> "Debe ingresar el N° de seguimiento"
                                    fechaInicio.isBlank() -> "Debe ingresar la fecha de inicio"
                                    fechaFin.isBlank() -> "Debe ingresar la fecha de término"
                                    else -> {
                                        try {
                                            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                                            val inicio = LocalDate.parse(fechaInicio, fmt)
                                            val fin = LocalDate.parse(fechaFin, fmt)
                                            if (fin.isBefore(inicio)) "La fecha de término no puede ser anterior a la de inicio"
                                            else null
                                        } catch (_: Exception) {
                                            "Formato de fecha inválido"
                                        }
                                    }
                                }

                                if (error != null) {
                                    validationError = error
                                } else {
                                    validationError = null
                                    showConfirmDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                        ) { Text("CREAR", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
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
                        // Reset de campos para la próxima creación
                        selectedRegionId = null
                        sectorAmerbInput = ""
                        selectedSectorAmerb = null
                        numSeguimiento = ""
                        fechaInicio = ""
                        fechaFin = ""
                        caletaInput = ""
                        selectedCaleta = ""
                        tipoOrg = "STI"
                        opaInput = ""
                        selectedOpa = null

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
        Dialog(
            onDismissRequest = { showBotesDialog = false },
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
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header con degradado
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
                            "GESTIÓN DE BOTES — ${currentOpForBotes?.id}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        IconButton(
                            onClick = { showBotesDialog = false },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }

                    Column(modifier = Modifier.padding(20.dp).weight(1f)) {
                        // Header de la tabla estilizado
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                .background(Color(0xFFF1F3F5))
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#", Modifier.weight(0.3f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                            Text("ZONA", Modifier.weight(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                            Text("BOTE (MAESTRO)", Modifier.weight(1.8f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                            Text("BUZO", Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                            Text("UNIDAD", Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                            Spacer(Modifier.width(36.dp))
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Color(0xFFF1F3F5), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(botesList) { index, bote ->
                                    val opRegionRom = currentOpForBotes?.region?.let { rid -> regiones.find { it.id == rid }?.rom }
                                    BoteRowItem(
                                        index = index + 1,
                                        bote = bote,
                                        botesMaestros = botesMaestros,
                                        operationRegionRom = opRegionRom,
                                        operationCaleta = currentOpForBotes?.sector,
                                        onDelete = { botesList.removeAt(index) },
                                        onUpdate = { updated -> botesList[index] = updated }
                                    )
                                    HorizontalDivider(color = Color(0xFFF1F3F5), modifier = Modifier.padding(horizontal = 12.dp))
                                }
                                if (botesList.isEmpty()) {
                                    item {
                                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                            Text("No hay botes registrados", color = Color.Gray)
                                        }
                                    }
                                }
                                if (validationError != null) {
                                    item {
                                        Text(
                                            text = validationError!!,
                                            color = Color.Red,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { botesList.add(OperacionBoteDto(zona = botesList.size + 1, densTipo = "Transecto")) },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF003366))
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color(0xFF003366))
                            Spacer(Modifier.width(8.dp))
                            Text("AGREGAR FILA", fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                        }
                        
                        Spacer(Modifier.weight(1f))

                        OutlinedButton(
                            onClick = { showBotesDialog = false; validationError = null },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(1.5.dp, Color.Gray)
                        ) { Text("CANCELAR", fontWeight = FontWeight.Bold, color = Color.Gray) }

                        Button(
                            onClick = { 
                                val op = currentOpForBotes ?: return@Button
                                val updatedBotesUi = botesList.toList()
                                
                                // Validación de botes
                                if (updatedBotesUi.isEmpty()) {
                                    validationError = "Debe agregar al menos un bote"
                                    return@Button
                                }
                                if (updatedBotesUi.any { it.nombre.isNullOrBlank() }) {
                                    validationError = "Todos los botes deben tener un nombre (Bote Maestro)"
                                    return@Button
                                }
                                if (updatedBotesUi.any { it.buzo.isNullOrBlank() }) {
                                    validationError = "Todos los botes deben tener asignado un buzo"
                                    return@Button
                                }
                                
                                validationError = null
                                val localOp = op.copy(botes = updatedBotesUi)
                                currentOpForBotes = localOp
                                val idxBd = DataManager.operacionesBd.indexOfFirst { it.id == op.id }
                                if (idxBd >= 0) {
                                    DataManager.operacionesBd[idxBd] = localOp
                                } else {
                                    val idxLc = DataManager.operacionesLc.indexOfFirst { it.id == op.id }
                                    if (idxLc >= 0) DataManager.operacionesLc[idxLc] = localOp
                                    else DataManager.operacionesLc.add(localOp)
                                }
                                showBotesDialog = false
                                
                                scope.launch {
                                    if (AppState.isOnline && !AppState.authToken.isNullOrBlank()) {
                                        try {
                                            val updatedBotesApi = updatedBotesUi.map { b ->
                                                val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                                                b.copy(
                                                    densTipo = dens,
                                                    transectos = b.transectos?.map { t ->
                                                        t.copy(tipo = if (dens == "cuadrante") "cuadrante" else "transecto")
                                                    }
                                                )
                                            }
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
                                                botes = updatedBotesApi
                                            )
                                            val res = RetrofitClient.apiService.actualizarOperacion(op.id, req)
                                            if (res.isSuccessful && res.body()?.ok == true) {
                                                val savedOp = res.body()!!.data!!
                                                val finalSavedOp = if (savedOp.botes.isNullOrEmpty() && updatedBotesUi.isNotEmpty()) {
                                                    savedOp.copy(botes = updatedBotesUi)
                                                } else {
                                                    savedOp
                                                }
                                                
                                                val idx = DataManager.operacionesBd.indexOfFirst { it.id == op.id }
                                                if (idx >= 0) DataManager.operacionesBd[idx] = finalSavedOp else DataManager.operacionesBd.add(0, finalSavedOp)
                                                currentOpForBotes = finalSavedOp
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
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

    if (showInicioDatePicker) {
        MyDatePickerDialog(onDateSelected = { fechaInicio = it; showInicioDatePicker = false }, onDismiss = { showInicioDatePicker = false })
    }
    if (showFinDatePicker) {
        MyDatePickerDialog(onDateSelected = { fechaFin = it; showFinDatePicker = false }, onDismiss = { showFinDatePicker = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Operaciones", color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (AppState.isOnline) Color(0xFF4CAF50) else Color.Gray, androidx.compose.foundation.shape.CircleShape)
                                .border(1.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
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
                val grouped = operacionesUi
                    .groupBy { it.op.region }
                    .toList()
                    .sortedWith(compareBy({ it.first ?: Int.MAX_VALUE }, { it.first ?: Int.MAX_VALUE }))

                grouped.forEach { (regionId, ops) ->
                    item {
                        Text(
                            regionId?.let { regionLabelById[it] } ?: "Sin región",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF003366),
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                        )
                    }
                    items(ops) { item ->
                        OperacionCard(
                            item = item,
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
                                
                                selectedSpeciesIds.clear()
                                val existingSpecies = bote.transectos?.flatMap { it.counts?.keys ?: emptySet() }?.mapNotNull { it.toIntOrNull() }?.distinct() ?: emptyList()
                                selectedSpeciesIds.addAll(existingSpecies)
                                
                                transectosList.clear()
                                transectosList.addAll(bote.transectos ?: emptyList())
                                
                                showSpeciesDialog = true
                            },
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
                                        if (item.source == OperacionSource.BD) DataManager.operacionesBd.remove(item.op)
                                        else DataManager.operacionesLc.remove(item.op)
                                    }
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
}

@Composable
private fun OperacionCard(
    item: OperacionItem,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onEditBotesClick: () -> Unit,
    onEditDataClick: (OperacionBoteDto) -> Unit,
    onDeleteClick: () -> Unit,
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
                        val titulo = listOfNotNull(
                            (op.sectorAmerb?.takeIf { it.isNotBlank() && it != "0000-00-00" } ?: op.sector.takeIf { it.isNotBlank() && it != "0000-00-00" }),
                        ).joinToString(" · ")
                        Text(titulo, fontWeight = FontWeight.ExtraBold, color = Color(0xFF003366), fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Formato: N° Seguimiento · Fecha Inicio [· Fecha Fin si es distinta]
                    val subtitulo = buildString {
                        append(op.id) // El ID autogenerado tipo OP-2026-22
                        
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
                    Text(subtitulo, fontSize = 12.sp, color = Color.Gray)
                }
                Row {
                    if (item.source == OperacionSource.LC && AppState.isOnline && !AppState.authToken.isNullOrBlank()) {
                        IconButton(onClick = onUploadLocalClick) { Icon(Icons.Default.CloudUpload, null, tint = Color(0xFF1B5E20)) }
                    }
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
        modifier = modifier.clickable { onClick() }.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF003366) else Color(0xFFF1F3F5)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD).copy(alpha = 0.5f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(8.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    especie.com, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 12.sp, 
                    maxLines = 2, 
                    textAlign = TextAlign.Center,
                    color = if (isSelected) Color(0xFF003366) else Color.Black
                )
                Text(
                    especie.sci ?: "", 
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, 
                    fontSize = 9.sp, 
                    color = Color.Gray, 
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle, 
                    null, 
                    tint = Color(0xFF003366), 
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableDropdown(
    value: String, 
    onValueChange: (String) -> Unit, 
    placeholder: String, 
    items: List<T>, 
    itemLabel: (T) -> String, 
    onItemSelected: (T) -> Unit, 
    showAddNew: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredItems = items.filter { itemLabel(it).contains(value, ignoreCase = true) }
    val isDark = isSystemInDarkTheme()
    // Colores personalizados para mejor visibilidad y contraste (Azul profundo con texto blanco)
    val bgColor = if (isDark) Color(0xFF0D47A1) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val borderColor = if (isDark) Color(0xFF1976D2) else Color(0xFFF1F3F5)
    val focusedColor = if (isDark) Color(0xFFBBDEFB) else Color(0xFF003366)

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, 
            onValueChange = { 
                onValueChange(it)
                expanded = true 
            }, 
            placeholder = { Text(placeholder, fontSize = 13.sp, color = if (isDark) Color.LightGray else Color.Gray) }, 
            modifier = Modifier.fillMaxWidth().background(bgColor, RoundedCornerShape(12.dp)), 
            shape = RoundedCornerShape(12.dp), 
            singleLine = true,
            textStyle = TextStyle(color = textColor, fontSize = 14.sp),
            trailingIcon = { 
                IconButton(onClick = { expanded = !expanded }) { 
                    Icon(if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, tint = focusedColor) 
                } 
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = borderColor,
                focusedBorderColor = focusedColor,
                unfocusedTextColor = textColor,
                focusedTextColor = textColor,
                unfocusedContainerColor = bgColor,
                focusedContainerColor = bgColor
            )
        )

        if (expanded && (filteredItems.isNotEmpty() || showAddNew)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, borderColor)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (showAddNew && value.isNotBlank() && filteredItems.none { itemLabel(it).equals(value, true) }) {
                        item {
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, null, tint = Color(0xFF00897B), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Agregar '$value'...", color = Color(0xFF00897B), fontWeight = FontWeight.Bold) 
                                    }
                                }, 
                                onClick = { 
                                    expanded = false 
                                }
                            )
                            HorizontalDivider(color = borderColor)
                        }
                    }
                    items(filteredItems) { item ->
                        DropdownMenuItem(
                            text = { Text(itemLabel(item), fontSize = 14.sp, color = textColor) }, 
                            onClick = { 
                                onItemSelected(item)
                                expanded = false 
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoteRowItem(
    index: Int,
    bote: OperacionBoteDto,
    botesMaestros: List<BoteMaestroDto>,
    operationRegionRom: String?,
    operationCaleta: String?,
    onDelete: () -> Unit,
    onUpdate: (OperacionBoteDto) -> Unit
) {
    var showUnitWarning by remember { mutableStateOf(false) }
    var nextUnitType by remember { mutableStateOf("") }
    var showBoteSearch by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val comboBg = if (isDark) Color(0xFF0D47A1) else Color.White
    val comboText = if (isDark) Color.White else Color.Black
    val comboBorder = if (isDark) Color(0xFF1976D2) else Color(0xFFF1F3F5)
    val labelColor = if (isDark) Color(0xFFBBDEFB) else Color.Gray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(index.toString(), Modifier.weight(0.3f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.LightGray else Color.Gray)
        
        // Zona Muestreo
        BasicTextField(
            value = bote.zona?.toString() ?: "",
            onValueChange = { onUpdate(bote.copy(zona = it.toIntOrNull())) },
            modifier = Modifier
                .weight(0.8f)
                .padding(horizontal = 4.dp)
                .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                .background(comboBg, RoundedCornerShape(8.dp))
                .padding(10.dp),
            textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = comboText),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        // Bote con buscador estilizado
        Box(Modifier.weight(1.8f).padding(horizontal = 4.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBoteSearch = true }
                    .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                    .background(comboBg, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                color = Color.Transparent
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        bote.nombre ?: "Seleccionar Bote", 
                        fontSize = 13.sp, 
                        fontWeight = if (bote.nombre != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (bote.nombre != null) comboText else labelColor,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.Search, null, tint = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366), modifier = Modifier.size(16.dp))
                }
            }
            if (showBoteSearch) { 
                BoteMaestroSearchDialog(
                    botes = botesMaestros, 
                    operationRegionRom = operationRegionRom,
                    operationCaleta = operationCaleta,
                    onSelect = { m -> onUpdate(bote.copy(nombre = m.nombre, boteMaestroId = m.id)); showBoteSearch = false }, 
                    onDismiss = { showBoteSearch = false }
                ) 
            }
        }

        // Buzo
        BasicTextField(
            value = bote.buzo ?: "",
            onValueChange = { onUpdate(bote.copy(buzo = it)) },
            modifier = Modifier
                .weight(1.2f)
                .padding(horizontal = 4.dp)
                .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                .background(comboBg, RoundedCornerShape(8.dp))
                .padding(10.dp),
            textStyle = TextStyle(fontSize = 13.sp, color = comboText),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (bote.buzo.isNullOrBlank()) {
                        Text("Buzo...", color = labelColor, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            }
        )

        // Unidad
        var expandedUni by remember { mutableStateOf(false) }
        Box(Modifier.weight(1.2f).padding(horizontal = 4.dp)) {
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
                    Text(bote.densTipo ?: "Unidad", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = comboText, modifier = Modifier.weight(1f))
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

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
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
fun BoteMaestroSearchDialog(
    botes: List<BoteMaestroDto>,
    operationRegionRom: String?,
    operationCaleta: String?,
    onSelect: (BoteMaestroDto) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    
    // Estado para menús abatibles (jerarquía)
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
                Box(
                    modifier = Modifier.fillMaxWidth().background(headerBg).padding(16.dp)
                ) {
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
                            // Mostrar Jerarquía: Región > Caleta > Bote
                            val regions = regionKeys
                            
                            items(regions) { rom ->
                                val isRegionExpanded = expandedRegionRom == rom
                                HierarchicalItem(
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
                                        HierarchicalItem(
                                            label = caleta.uppercase(),
                                            isExpanded = isCaletaExpanded,
                                            onClick = { expandedCaleta = if (isCaletaExpanded) null else caleta },
                                            level = 1
                                        )
                                        
                                        if (isCaletaExpanded) {
                                            val botesInCaleta = baseBotes.filter { (it.region_rom ?: it.region) == rom && it.caleta?.equals(caleta, true) == true }
                                            
                                            botesInCaleta.forEach { b ->
                                                BoteFinalItem(b, onSelect)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Mostrar Resultados de Búsqueda Directa
                            items(queryFilteredBotes) { b ->
                                BoteFinalItem(b, onSelect)
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
fun HierarchicalItem(label: String, isExpanded: Boolean, onClick: () -> Unit, level: Int) {
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
fun BoteFinalItem(b: BoteMaestroDto, onSelect: (BoteMaestroDto) -> Unit) {
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

@Composable
private fun OperacionDataDialog(
    opInitial: OperacionDto,
    regionLabel: String?,
    especiesMaestras: List<EspecieDto>,
    onDismiss: () -> Unit
) {
    var op by remember(opInitial.id) { mutableStateOf(opInitial) }
    var tab by remember { mutableStateOf("DENSIDAD") }
    var selectedBoteKey by remember { mutableStateOf<String?>(null) }
    var unidadTipo by remember { mutableStateOf("transecto") }

    LaunchedEffect(opInitial.id) {
        try {
            val res = RetrofitClient.apiService.getOperacion(opInitial.id)
            if (res.isSuccessful && res.body()?.ok == true && res.body()?.data != null) {
                val fresh = res.body()!!.data!!
                op = fresh
                val idx = DataManager.operacionesBd.indexOfFirst { it.id == fresh.id }
                if (idx >= 0) DataManager.operacionesBd[idx] = fresh
            }
        } catch (_: Exception) {
        }
    }

    fun boteKey(b: OperacionBoteDto): String {
        return listOfNotNull(b.zona?.toString(), b.nombre, b.buzo).joinToString("|")
    }

    val botes = op.botes ?: emptyList()
    val selectedBote = remember(op, selectedBoteKey) {
        if (botes.isEmpty()) return@remember null
        val key = selectedBoteKey
        val found = if (key != null) botes.firstOrNull { boteKey(it) == key } else null
        found ?: botes.first()
    }
    LaunchedEffect(op.id, botes.size) {
        if (selectedBoteKey == null && botes.isNotEmpty()) {
            selectedBoteKey = boteKey(botes.first())
        }
        if (unidadTipo.isBlank()) {
            unidadTipo = "transecto"
        }
    }

    val especiesById = remember(especiesMaestras) { especiesMaestras.associateBy { it.id } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 12.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(colors = listOf(Color(0xFF003366), Color(0xFF00509E))))
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                        Text(
                            "DATOS DE OPERACIÓN",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            op.id,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }

                Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF8F9FA),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(regionLabel ?: "Sin región", fontWeight = FontWeight.Black, color = Color(0xFF003366), fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${op.sector} · ${op.fechaInicio.orEmpty()}${if (!op.fechaFin.isNullOrBlank()) " → ${op.fechaFin}" else ""}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            if (!op.org.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(op.org.orEmpty(), fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tab == "DENSIDAD",
                            onClick = { tab = "DENSIDAD" },
                            label = { Text("EVADIR") }
                        )
                        FilterChip(
                            selected = tab == "LP",
                            onClick = { tab = "LP" },
                            label = { Text("L-P") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (botes.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFFF8F9FA),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Sin datos", fontWeight = FontWeight.Black, color = Color(0xFF003366), fontSize = 16.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Esta operación no tiene botes registrados.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        val scrollX = rememberScrollState()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollX),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            botes.sortedBy { it.zona ?: 0 }.forEach { b ->
                                val isSel = selectedBoteKey == boteKey(b)
                                AssistChip(
                                    onClick = {
                                        selectedBoteKey = boteKey(b)
                                        val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                                        unidadTipo = dens
                                    },
                                    label = {
                                        Text(
                                            "Zona ${b.zona ?: 0} · ${(b.nombre ?: "S/N").take(18)}",
                                            fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isSel) Color(0xFFE3F2FD) else Color(0xFFF8F9FA),
                                        labelColor = Color(0xFF003366)
                                    ),
                                    border = BorderStroke(1.dp, if (isSel) Color(0xFF003366) else Color(0xFFF1F3F5))
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (tab == "DENSIDAD") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = unidadTipo == "transecto",
                                    onClick = { unidadTipo = "transecto" },
                                    label = { Text("Transecto") }
                                )
                                FilterChip(
                                    selected = unidadTipo == "cuadrante",
                                    onClick = { unidadTipo = "cuadrante" },
                                    label = { Text("Cuadrante") }
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            val unitsAll = selectedBote?.transectos ?: emptyList()
                            val units = unitsAll.filter { (it.tipo ?: "transecto").equals(unidadTipo, true) }
                            val speciesIds = remember(unitsAll, unidadTipo, especiesMaestras.size) {
                                units.flatMap { it.counts?.keys?.mapNotNull { k -> k.toIntOrNull() } ?: emptyList() }
                                    .distinct()
                                    .sortedWith(compareBy({ especiesById[it]?.com ?: "ZZZ" }, { it }))
                            }

                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                            ) {
                                if (units.isEmpty()) {
                                    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                                        Text("Sin unidades registradas", color = Color.Gray)
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFF1F3F5))
                                                .horizontalScroll(rememberScrollState())
                                                .padding(vertical = 10.dp, horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("#", modifier = Modifier.width(40.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                            Text("ÁREA", modifier = Modifier.width(70.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                            Text("SUSTRATO", modifier = Modifier.width(100.dp), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                            speciesIds.forEach { sid ->
                                                val name = especiesById[sid]?.com ?: "ID$sid"
                                                Text(
                                                    name.take(8).uppercase(),
                                                    modifier = Modifier.width(82.dp),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.DarkGray,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            itemsIndexed(units) { idx, u ->
                                                val rowScroll = rememberScrollState()
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rowScroll)
                                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text((u.num ?: (idx + 1)).toString(), modifier = Modifier.width(40.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text((u.area ?: 0.0).toString(), modifier = Modifier.width(70.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                                                    Text(u.sustrato.orEmpty(), modifier = Modifier.width(100.dp), fontSize = 12.sp, maxLines = 1)
                                                    val counts = u.counts ?: emptyMap()
                                                    speciesIds.forEach { sid ->
                                                        val v = counts[sid.toString()] ?: 0
                                                        Text(
                                                            v.toString(),
                                                            modifier = Modifier.width(82.dp),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = Color(0xFF00897B),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(color = Color(0xFFF1F3F5))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val lp = selectedBote?.lpMuestras ?: emptyMap()
                            val rows = remember(lp, especiesMaestras.size) {
                                val out = mutableListOf<Triple<String, String, String>>()
                                lp.forEach { (spId, buckets) ->
                                    val sid = spId.toIntOrNull()
                                    val spName = if (sid != null) (especiesById[sid]?.com ?: "ID$sid") else spId
                                    buckets.forEach { (kind, ms) ->
                                        ms.forEach { m ->
                                            val s = when (kind.uppercase()) {
                                                "LP" -> "L=${m.l ?: "—"} · P=${m.p ?: "—"}"
                                                "D" -> "D=${m.d ?: "—"}"
                                                else -> "L=${m.l ?: "—"}"
                                            }
                                            out.add(Triple(spName, kind.uppercase(), s))
                                        }
                                    }
                                }
                                out
                            }

                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                            ) {
                                if (rows.isEmpty()) {
                                    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                                        Text("Sin muestras L-P registradas", color = Color.Gray)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFF1F3F5))
                                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                                            ) {
                                                Text("ESPECIE", modifier = Modifier.weight(1.4f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                                Text("TIPO", modifier = Modifier.weight(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray, textAlign = TextAlign.Center)
                                                Text("VALORES", modifier = Modifier.weight(1.4f), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                                            }
                                        }
                                        items(rows) { (esp, kind, vals) ->
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp)) {
                                                Text(esp, modifier = Modifier.weight(1.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF003366))
                                                Text(kind, modifier = Modifier.weight(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF00897B), textAlign = TextAlign.Center)
                                                Text(vals, modifier = Modifier.weight(1.4f), fontSize = 12.sp, color = Color.Gray)
                                            }
                                            HorizontalDivider(color = Color(0xFFF1F3F5))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.5.dp, Color.Gray)
                ) { Text("CERRAR", fontWeight = FontWeight.Bold, color = Color.Gray) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDatePickerDialog(onDateSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate(); val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd"); onDateSelected(date.format(fmt)) }; onDismiss() }) { Text("OK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }) { DatePicker(state = datePickerState) }
}
