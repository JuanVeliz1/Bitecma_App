package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import com.bitecma.app.data.DataManager
import com.bitecma.app.data.AppState
import com.bitecma.app.data.EspecieMaestra
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.RetrofitClient
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

private fun countLpMuestras(ops: List<OperacionDto>): Int {
    var total = 0
    for (op in ops) {
        for (b in op.botes ?: emptyList()) {
            for (bucket in (b.lpMuestras ?: emptyMap()).values) {
                for (arr in bucket.values) total += arr.size
            }
        }
    }
    return total
}

private fun countDensidadUnidades(ops: List<OperacionDto>): Int {
    var total = 0
    for (op in ops) {
        for (b in op.botes ?: emptyList()) total += (b.transectos ?: emptyList()).size
    }
    return total
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController, 
    userId: Int,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var docExpanded by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    
    val opsBd = DataManager.operacionesBd
    val opsLc = DataManager.operacionesLc
    val opsAll by remember { derivedStateOf { opsBd.toList() + opsLc.toList() } }

    val totalOps = opsAll.size
    val totalMuestras = countLpMuestras(opsAll)
    val unidadesDensidad = countDensidadUnidades(opsAll)

    LaunchedEffect(Unit) {
        try {
            val especiesRes = RetrofitClient.apiService.getEspecies()
            if (especiesRes.isSuccessful) {
                AppState.isOnline = true
                val body = especiesRes.body()
                if (body?.ok == true && body.data != null) {
                    DataManager.especies.clear()
                    DataManager.especies.addAll(
                        body.data.map { e ->
                            EspecieMaestra(e.id, e.com, e.sci ?: "")
                        }
                    )
                }
            } else {
                AppState.isOnline = false
            }

            val opsRes = RetrofitClient.apiService.getOperaciones()
            if (opsRes.isSuccessful) {
                AppState.isOnline = true
                val body = opsRes.body()
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
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(280.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "BITECMA", 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Bold, 
                    modifier = Modifier.padding(16.dp), 
                    color = if (isDarkMode) Color.White else Color(0xFF003366)
                )
                HorizontalDivider()
                
                NavigationDrawerItem(
                    label = { Text("Dashboard") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Operaciones") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; navController.navigate("operaciones/$userId") },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Especies") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; navController.navigate("especies/$userId") },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Botes") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; navController.navigate("botes/$userId") },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))
                
                // Indicador de Estado de Conexión (Online/Offline)
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    color = if (AppState.isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (AppState.isOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (AppState.isOnline) "MODO ONLINE" else "MODO OFFLINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (AppState.isOnline) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }

                // Botón de Modo Oscuro Estático en la parte inferior
                NavigationDrawerItem(
                    label = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isDarkMode) "Modo Claro" else "Modo Oscuro")
                        }
                    },
                    selected = false,
                    onClick = {
                        onDarkModeChange(!isDarkMode)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Cerrar sesión", color = Color.Red) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        AppState.clearSession(ctx)
                        navController.navigate("login") {
                            popUpTo(0)
                        }
                    },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dashboard", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF003366))
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                item {
                    Text("Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Resumen operacional", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Summary Cards (Replica Foto 3)
                item {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompact = maxWidth < 700.dp
                        if (isCompact) {
                            Column {
                                SummaryCard(
                                    title = "OPERACIONES",
                                    value = totalOps.toString(),
                                    subtitle = "Total registradas",
                                    color = Color(0xFF00897B),
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                SummaryCard(
                                    title = "MUESTRAS L-P",
                                    value = totalMuestras.toString(),
                                    subtitle = "Registros L/LP/D",
                                    color = Color(0xFF003366),
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                SummaryCard(
                                    title = "DENSIDAD",
                                    value = unidadesDensidad.toString(),
                                    subtitle = "Unidades registradas",
                                    color = Color(0xFF7E57C2),
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SummaryCard(
                                    title = "OPERACIONES",
                                    value = totalOps.toString(),
                                    subtitle = "Total registradas",
                                    color = Color(0xFF00897B),
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SummaryCard(
                                    title = "MUESTRAS L-P",
                                    value = totalMuestras.toString(),
                                    subtitle = "Registros L/LP/D",
                                    color = Color(0xFF003366),
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SummaryCard(
                                    title = "DENSIDAD",
                                    value = unidadesDensidad.toString(),
                                    subtitle = "Unidades registradas",
                                    color = Color(0xFF7E57C2),
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Recent Operations y Gráfico (Replica Foto 3)
                item {
                    val speciesNameById = DataManager.especies.associate { it.id to it.nombreComun }
                    val totals = mutableMapOf<Int, Int>()
                    for (op in opsAll) {
                        for (b in op.botes ?: emptyList()) {
                            for (t in b.transectos ?: emptyList()) {
                                for ((k, v) in (t.counts ?: emptyMap())) {
                                    val id = k.toIntOrNull() ?: continue
                                    totals[id] = (totals[id] ?: 0) + v
                                }
                            }
                        }
                    }
                    val top = totals.entries.sortedByDescending { it.value }.take(5)
                    val maxVal = (top.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompact = maxWidth < 700.dp
                        if (isCompact) {
                            Column {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("OPERACIONES RECIENTES", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
                                            TextButton(onClick = { navController.navigate("operaciones/$userId") }) {
                                                Text("Ver todas", fontSize = 12.sp)
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Text("ID", modifier = Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text("SECTOR", modifier = Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text("FECHA", modifier = Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text("BOTES", modifier = Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        opsAll.take(5).forEach { op ->
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                                Text(op.id, modifier = Modifier.weight(1.5f), fontSize = 11.sp)
                                                Text(op.sector, modifier = Modifier.weight(1.5f), fontSize = 11.sp)
                                                Text(op.fechaInicio ?: "", modifier = Modifier.weight(1.5f), fontSize = 11.sp)
                                                Text((op.botes?.size ?: 0).toString(), modifier = Modifier.weight(0.8f), fontSize = 11.sp)
                                            }
                                        }
                                        if (opsAll.isEmpty()) {
                                            Text(
                                                "Sin operaciones",
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                textAlign = TextAlign.Center,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("COMPOSICIÓN POR ESPECIE", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        if (top.isEmpty()) {
                                            Text("Sin datos", color = Color.Gray, fontSize = 12.sp)
                                        } else {
                                            top.forEach { (id, value) ->
                                                val ratio = value.toFloat() / maxVal.toFloat()
                                                val name = speciesNameById[id] ?: "ID $id"
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(vertical = 6.dp)
                                                ) {
                                                    Text(name, modifier = Modifier.width(70.dp), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                                    LinearProgressIndicator(
                                                        progress = { ratio },
                                                        modifier = Modifier.fillMaxWidth().height(12.dp),
                                                        color = Color(0xFF2D6A4F),
                                                        trackColor = Color(0xFFEEEEEE)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Card(
                                    modifier = Modifier.weight(1.5f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("OPERACIONES RECIENTES", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
                                            TextButton(onClick = { navController.navigate("operaciones/$userId") }) {
                                                Text("Ver todas", fontSize = 12.sp)
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Text("ID", modifier = Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text("SECTOR", modifier = Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text("FECHA", modifier = Modifier.weight(1.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text("BOTES", modifier = Modifier.weight(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        opsAll.take(5).forEach { op ->
                                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                                Text(op.id, modifier = Modifier.weight(1.5f), fontSize = 11.sp)
                                                Text(op.sector, modifier = Modifier.weight(1.5f), fontSize = 11.sp)
                                                Text(op.fechaInicio ?: "", modifier = Modifier.weight(1.5f), fontSize = 11.sp)
                                                Text((op.botes?.size ?: 0).toString(), modifier = Modifier.weight(0.8f), fontSize = 11.sp)
                                            }
                                        }
                                        if (opsAll.isEmpty()) {
                                            Text(
                                                "Sin operaciones",
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                textAlign = TextAlign.Center,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("COMPOSICIÓN POR ESPECIE", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        if (top.isEmpty()) {
                                            Text("Sin datos", color = Color.Gray, fontSize = 12.sp)
                                        } else {
                                            top.forEach { (id, value) ->
                                                val ratio = value.toFloat() / maxVal.toFloat()
                                                val name = speciesNameById[id] ?: "ID $id"
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(vertical = 6.dp)
                                                ) {
                                                    Text(name, modifier = Modifier.width(70.dp), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                                    LinearProgressIndicator(
                                                        progress = { ratio },
                                                        modifier = Modifier.fillMaxWidth().height(12.dp),
                                                        color = Color(0xFF2D6A4F),
                                                        trackColor = Color(0xFFEEEEEE)
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
    }
}

@Composable
fun SummaryCard(title: String, value: String, subtitle: String, color: Color, isDarkMode: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.Gray, fontSize = 10.sp)
        }
    }
}
