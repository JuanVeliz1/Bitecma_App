package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bitecma.app.data.DataManager
import com.bitecma.app.data.BoteMaestro
import com.bitecma.app.data.AppState
import com.bitecma.app.network.RetrofitClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun BotesScreen(navController: NavController, userId: Int) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedRegionRom by remember { mutableStateOf<String?>(null) }
    var regionDropdownExpanded by remember { mutableStateOf(false) }

    var botesData by remember { mutableStateOf<List<BoteMaestro>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var romToLabel by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var regionOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(isLoading) {
        if (!isLoading) return@LaunchedEffect
        
        // Cargar datos locales primero
        botesData = DataManager.botes
        if (AppState.forceOffline) {
            isLoading = false
            return@LaunchedEffect
        }
        
        try {
            // 1. Obtener Regiones para mapeo dinámico
            val regRes = RetrofitClient.apiService.getRegiones()
            val fetchedRomToLabel = if (regRes.isSuccessful && regRes.body()?.ok == true) {
                (regRes.body()?.data ?: emptyList()).associate { r ->
                    val label = listOfNotNull(r.rom, r.nom).joinToString(" — ").ifBlank { r.rom ?: "Región ${r.id}" }
                    (r.rom ?: "") to label
                }
            } else {
                emptyMap()
            }
            romToLabel = fetchedRomToLabel.filterKeys { it.isNotBlank() }
            regionOptions = romToLabel.entries
                .map { it.key to it.value }
                .sortedWith(compareBy({ it.first.length }, { it.first }, { it.second }))

            // 2. Obtener Botes desde la API/Base de Datos
            val bRes = RetrofitClient.apiService.getBotes()
            if (bRes.isSuccessful && bRes.body()?.ok == true) {
                val items = bRes.body()?.data ?: emptyList()
                val mapped = items.mapNotNull { b ->
                    val rom = b.region_rom ?: b.region
                    val regionLabel = if (rom != null) fetchedRomToLabel[rom] ?: rom else ""
                    
                    val nombre = b.nombre ?: return@mapNotNull null
                    BoteMaestro(
                        nombre = nombre,
                        caleta = b.caleta ?: "S/I",
                        rpa = b.nrpa ?: "S/I",
                        matricula = b.nmatricula ?: "S/I",
                        regionId = regionLabel
                    )
                }
                // Combinar locales únicos con los de la API (evitar duplicados por nombre)
                val combined = (mapped + DataManager.botes).distinctBy { it.nombre.uppercase() }
                botesData = combined
                
                // Actualizar DataManager para que OperacionesScreen también vea los nuevos botes
                DataManager.botes.clear()
                DataManager.botes.addAll(combined)
            }
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    data class BoteUi(
        val regionRom: String,
        val regionLabel: String,
        val nombre: String,
        val rpa: String,
        val matricula: String
    )

    fun inferRegionRom(label: String): String {
        val first = label.split("—").firstOrNull()?.trim().orEmpty()
        return if (first.isNotBlank()) first else label.trim().ifBlank { "?" }
    }

    val botesUi by remember(botesData) {
        mutableStateOf(
            botesData.map { b ->
                val regionLabel = b.regionId.ifBlank { "?" }
                val rom = inferRegionRom(regionLabel).uppercase()
                BoteUi(
                    regionRom = rom,
                    regionLabel = regionLabel,
                    nombre = b.nombre,
                    rpa = b.rpa,
                    matricula = b.matricula
                )
            }
        )
    }

    val filtered = remember(botesUi, searchQuery, selectedRegionRom) {
        val q = searchQuery.trim().lowercase()
        botesUi
            .asSequence()
            .filter { b -> selectedRegionRom == null || b.regionRom.equals(selectedRegionRom, true) }
            .filter { b ->
                if (q.isBlank()) true
                else {
                b.nombre.lowercase().contains(q) ||
                    b.rpa.lowercase().contains(q) ||
                    b.matricula.lowercase().contains(q)
                }
            }
            .sortedBy { it.nombre.lowercase() }
            .toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Botes", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        // Forzar refresco
                        isLoading = true
                        searchQuery = "" // Limpiar búsqueda al refrescar
                        selectedRegionRom = null
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = regionDropdownExpanded,
                onExpandedChange = { regionDropdownExpanded = !regionDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedRegionRom?.let { romToLabel[it] ?: it } ?: "Todas las regiones",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    label = { Text("Región") },
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = regionDropdownExpanded,
                    onDismissRequest = { regionDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Todas las regiones") },
                        onClick = {
                            selectedRegionRom = null
                            regionDropdownExpanded = false
                        }
                    )
                    regionOptions.forEach { (rom, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedRegionRom = rom
                                regionDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar por nombre, RPA o matrícula...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFF1F3F5))
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    if (isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        if (filtered.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("Sin resultados", color = Color.Gray)
                                }
                            }
                        } else {
                            items(filtered) { b ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    color = Color.White,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFF1F3F5))
                                ) {
                                    Text(
                                        text = b.nombre,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF003366),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
