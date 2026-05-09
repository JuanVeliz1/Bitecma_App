package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.bitecma.app.data.EspecieMaestra
import com.bitecma.app.network.RetrofitClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun EspeciesScreen(navController: NavController, userId: Int) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedEspecie by remember { mutableStateOf<EspecieMaestra?>(null) }

    var especiesData by remember { mutableStateOf<List<EspecieMaestra>>(emptyList()) }

    LaunchedEffect(Unit) {
        especiesData = DataManager.especies
        try {
            val res = RetrofitClient.apiService.getEspecies()
            if (res.isSuccessful && res.body()?.ok == true) {
                val items = res.body()?.data ?: emptyList()
                especiesData = items.map { e ->
                    EspecieMaestra(
                        id = e.id,
                        nombreComun = e.com,
                        nombreCientifico = e.sci ?: ""
                    )
                }
            }
        } catch (_: Exception) {
        }
    }
    
    val especies = especiesData.filter { 
        it.nombreComun.contains(searchQuery, ignoreCase = true) || 
        it.nombreCientifico.contains(searchQuery, ignoreCase = true)
    }

    if (selectedEspecie != null) {
        AlertDialog(
            onDismissRequest = { selectedEspecie = null },
            title = { Text(selectedEspecie!!.nombreComun, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Nombre Científico:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                    Text(selectedEspecie!!.nombreCientifico, fontSize = 16.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Código:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                    Text("ESP-${selectedEspecie!!.id.toString().padStart(3, '0')}", fontSize = 14.sp)
                }
            },
            confirmButton = {
                Button(onClick = { selectedEspecie = null }) {
                    Text("Entendido")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maestro de Especies", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filtrar por nombre común o científico...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Cabecera Tabla Especies
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F3F4), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(12.dp)
            ) {
                Text("#", modifier = Modifier.weight(0.3f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("NOMBRE COMÚN", modifier = Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("NOMBRE CIENTÍFICO", modifier = Modifier.weight(2f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(especies) { esp ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEspecie = esp }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(esp.id.toString(), modifier = Modifier.weight(0.3f), fontSize = 11.sp)
                            Text(esp.nombreComun, modifier = Modifier.weight(1.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(esp.nombreCientifico, modifier = Modifier.weight(2f), fontSize = 11.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                    }
                }
            }
        }
    }
}
