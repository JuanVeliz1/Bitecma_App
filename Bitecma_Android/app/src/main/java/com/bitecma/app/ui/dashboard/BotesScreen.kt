package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bitecma.app.data.DataManager
import com.bitecma.app.data.BoteMaestro
import com.bitecma.app.data.AppState
import com.bitecma.app.ui.bitecmaAmberBg
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaNavyStrong
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSoftBackgroundAlt
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaTeal
import com.bitecma.app.ui.bitecmaTealContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun BotesScreen(navController: NavController, userId: Int) {
    val colors = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedRegionRom by remember { mutableStateOf<String?>(null) }
    var regionDropdownExpanded by remember { mutableStateOf(false) }

    var botesData by remember { mutableStateOf<List<BoteMaestro>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var romToLabel by remember { mutableStateOf(DataManager.getRegionLabelMap()) }
    var regionOptions by remember { mutableStateOf(romToLabel.entries.map { it.key to it.value }.sortedWith(compareBy({ it.first.length }, { it.first }, { it.second }))) }

    LaunchedEffect(isLoading) {
        if (!isLoading) return@LaunchedEffect
        botesData = DataManager.botes.toList()
        romToLabel = DataManager.getRegionLabelMap()
        regionOptions = romToLabel.entries
            .map { it.key to it.value }
            .sortedWith(compareBy({ it.first.length }, { it.first }, { it.second }))

        if (!AppState.forceOffline && !AppState.authToken.isNullOrBlank()) {
            runCatching { DataManager.refreshCatalogs(ctx, force = true) }
            botesData = DataManager.botes.toList()
            romToLabel = DataManager.getRegionLabelMap()
            regionOptions = romToLabel.entries
                .map { it.key to it.value }
                .sortedWith(compareBy({ it.first.length }, { it.first }, { it.second }))
        }
        isLoading = false
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

    val accessMessage = when {
        AppState.isGuestMode -> "Modo sin cuenta: usando el catalogo local de botes."
        AppState.forceOffline || AppState.authToken.isNullOrBlank() -> "Sin sesion online: mostrando datos locales disponibles."
        else -> "Catalogo conectado y listo para consulta."
    }

    val accessContainerColor = when {
        AppState.isGuestMode -> colors.bitecmaAmberBg
        AppState.forceOffline || AppState.authToken.isNullOrBlank() -> colors.bitecmaSoftBackgroundAlt
        else -> colors.bitecmaTealContainer
    }

    val accessContentColor = when {
        AppState.isGuestMode -> colors.bitecmaNavyStrong
        AppState.forceOffline || AppState.authToken.isNullOrBlank() -> colors.bitecmaSubtleText
        else -> colors.bitecmaTeal
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Botes", color = colors.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = colors.onPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        // Forzar refresco
                        isLoading = true
                        searchQuery = "" // Limpiar búsqueda al refrescar
                        selectedRegionRom = null
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = colors.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.primary)
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = accessContainerColor,
                border = BorderStroke(1.dp, colors.bitecmaBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = accessContentColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = accessMessage,
                        color = accessContentColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.bitecmaTeal,
                        focusedLabelColor = colors.bitecmaTeal,
                        unfocusedBorderColor = colors.bitecmaBorder,
                        unfocusedLabelColor = colors.bitecmaMutedText,
                        unfocusedContainerColor = colors.bitecmaCardBackground,
                        focusedContainerColor = colors.bitecmaCardBackground
                    )
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
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.bitecmaTeal,
                    focusedLabelColor = colors.bitecmaTeal,
                    unfocusedBorderColor = colors.bitecmaBorder,
                    unfocusedLabelColor = colors.bitecmaMutedText,
                    unfocusedContainerColor = colors.bitecmaSoftBackground,
                    focusedContainerColor = colors.bitecmaCardBackground
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.bitecmaCardBackground,
                    border = BorderStroke(1.dp, colors.bitecmaBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("BOTES", style = MaterialTheme.typography.labelMedium, color = colors.bitecmaMutedText)
                        Text(filtered.size.toString(), style = MaterialTheme.typography.titleLarge, color = colors.bitecmaNavy)
                        Text("Resultados visibles", style = MaterialTheme.typography.bodySmall, color = colors.bitecmaSubtleText)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.bitecmaCardBackground,
                    border = BorderStroke(1.dp, colors.bitecmaBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("REGION", style = MaterialTheme.typography.labelMedium, color = colors.bitecmaMutedText)
                        Text((selectedRegionRom ?: "TODAS"), style = MaterialTheme.typography.titleLarge, color = colors.bitecmaNavy)
                        Text("Filtro actual", style = MaterialTheme.typography.bodySmall, color = colors.bitecmaSubtleText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                color = colors.bitecmaCardBackground,
                border = BorderStroke(1.dp, colors.bitecmaBorder)
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
                                    Text("Sin resultados", color = colors.bitecmaSubtleText)
                                }
                            }
                        } else {
                            items(filtered) { b ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    color = colors.bitecmaSoftBackground,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, colors.bitecmaBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = colors.bitecmaTealContainer
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Sailing,
                                                contentDescription = null,
                                                tint = colors.bitecmaTeal,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = b.nombre,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.bitecmaNavy,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = listOf(
                                                    "RPA ${b.rpa}",
                                                    "Mat. ${b.matricula}"
                                                ).joinToString("  ·  "),
                                                color = colors.bitecmaSubtleText,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = colors.bitecmaSoftBackgroundAlt
                                        ) {
                                            Text(
                                                text = b.regionRom,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = colors.bitecmaNavy
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
    }
}
