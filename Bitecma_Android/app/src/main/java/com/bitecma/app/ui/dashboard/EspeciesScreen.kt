package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.text.KeyboardOptions
import com.bitecma.app.data.DataManager
import com.bitecma.app.data.EspecieMaestra
import com.bitecma.app.data.AppState
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSoftBackgroundAlt
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaTeal
import com.bitecma.app.ui.bitecmaTealContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun EspeciesScreen(navController: NavController, userId: Int) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedEspecie by remember { mutableStateOf<EspecieMaestra?>(null) }
    val dedupeKey: (EspecieMaestra) -> String = { especie ->
        "${especie.nombreComun.trim().lowercase()}|${especie.nombreCientifico.trim().lowercase()}"
    }
    val especiesData by remember {
        derivedStateOf { DataManager.especies.distinctBy(dedupeKey) }
    }

    LaunchedEffect(context) {
        runCatching { DataManager.refreshCatalogs(context) }
    }
    
    val especies = especiesData.filter { 
        it.nombreComun.contains(searchQuery, ignoreCase = true) || 
        it.nombreCientifico.contains(searchQuery, ignoreCase = true)
    }

    val accessMessage = when {
        AppState.hasAuthenticatedSession() && AppState.hasNetwork -> "Catalogo de especies sincronizado con la base."
        AppState.hasAuthenticatedSession() -> "Sin red: mostrando el ultimo catalogo de especies guardado."
        else -> "Debes iniciar sesion con internet para sincronizar especies."
    }

    val accessContainerColor = when {
        AppState.hasAuthenticatedSession() && AppState.hasNetwork -> colors.bitecmaTealContainer
        AppState.hasAuthenticatedSession() -> colors.bitecmaSoftBackgroundAlt
        else -> colors.bitecmaTealContainer
    }

    val accessContentColor = when {
        AppState.hasAuthenticatedSession() && AppState.hasNetwork -> colors.bitecmaTeal
        AppState.hasAuthenticatedSession() -> colors.bitecmaSubtleText
        else -> colors.bitecmaTeal
    }

    if (selectedEspecie != null) {
        AlertDialog(
            onDismissRequest = { selectedEspecie = null },
            containerColor = colors.bitecmaCardBackground,
            title = { Text(selectedEspecie!!.nombreComun, fontWeight = FontWeight.Bold, color = colors.bitecmaNavy) },
            text = {
                Column {
                    Text("Nombre Científico:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.bitecmaMutedText)
                    Text(selectedEspecie!!.nombreCientifico, fontSize = 16.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Código:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.bitecmaMutedText)
                    Text("ESP-${selectedEspecie!!.id.toString().padStart(3, '0')}", fontSize = 14.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedEspecie = null },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.bitecmaTeal)
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maestro de Especies", color = colors.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = colors.onPrimary)
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
                    Icon(Icons.Default.Info, contentDescription = null, tint = accessContentColor)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(accessMessage, color = accessContentColor, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filtrar por nombre común o científico...") },
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.bitecmaCardBackground,
                border = BorderStroke(1.dp, colors.bitecmaBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("ESPECIES", style = MaterialTheme.typography.labelMedium, color = colors.bitecmaMutedText)
                    Text(especies.size.toString(), style = MaterialTheme.typography.titleLarge, color = colors.bitecmaNavy)
                    Text("Resultados para la busqueda actual", style = MaterialTheme.typography.bodySmall, color = colors.bitecmaSubtleText)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(especies) { esp ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.bitecmaCardBackground,
                        border = BorderStroke(1.dp, colors.bitecmaBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEspecie = esp }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.bitecmaTealContainer
                            ) {
                                Text(
                                    esp.id.toString(),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    color = colors.bitecmaTeal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(esp.nombreComun, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.bitecmaNavy)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    esp.nombreCientifico.ifBlank { "Sin nombre cientifico" },
                                    fontSize = 12.sp,
                                    color = colors.bitecmaSubtleText,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Codigo ESP-${esp.id.toString().padStart(3, '0')}",
                                    fontSize = 11.sp,
                                    color = colors.bitecmaMutedText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
