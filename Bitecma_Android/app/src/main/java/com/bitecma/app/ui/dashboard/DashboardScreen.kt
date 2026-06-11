package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
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
import com.bitecma.app.AppRoutes
import com.bitecma.app.data.DataManager
import com.bitecma.app.data.AppState
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.ui.bitecmaAmberBg
import com.bitecma.app.ui.bitecmaBlueBg
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaDangerBg
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSoftBackgroundAlt
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaSuccessBg
import com.bitecma.app.ui.bitecmaTeal
import com.bitecma.app.ui.bitecmaTealContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

private data class ConnectionStatusUi(
    val title: String,
    val detail: String,
    val accentColor: Color,
    val containerColor: Color,
)

@Composable
private fun rememberStableNetworkFlag(): Boolean {
    var displayHasNetwork by remember { mutableStateOf(AppState.hasNetwork) }

    LaunchedEffect(AppState.hasNetwork) {
        if (AppState.hasNetwork) {
            displayHasNetwork = true
            return@LaunchedEffect
        }

        delay(2000)
        if (!AppState.hasNetwork) {
            displayHasNetwork = false
        }
    }

    return displayHasNetwork
}

@Composable
private fun rememberConnectionStatusUi(
    esInvitado: Boolean,
    isConnecting: Boolean,
    displayHasNetwork: Boolean,
): ConnectionStatusUi {
    val colors = MaterialTheme.colorScheme
    return remember(
        esInvitado,
        isConnecting,
        AppState.forceOffline,
        displayHasNetwork,
        AppState.hasAuthenticatedSession(),
        AppState.lastSyncError,
    ) {
        when {
            !displayHasNetwork -> ConnectionStatusUi(
                title = "SIN RED",
                detail = "La app sigue trabajando con cache local",
                accentColor = colors.error,
                containerColor = colors.bitecmaDangerBg,
            )
            esInvitado -> ConnectionStatusUi(
                title = "RED DISPONIBLE",
                detail = "Hay internet, pero estas trabajando sin cuenta",
                accentColor = colors.bitecmaNavy,
                containerColor = colors.bitecmaAmberBg,
            )
            isConnecting -> ConnectionStatusUi(
                title = "RED DISPONIBLE",
                detail = "Internet activo. Preparando la sincronizacion",
                accentColor = colors.bitecmaNavy,
                containerColor = colors.bitecmaBlueBg,
            )
            AppState.forceOffline -> ConnectionStatusUi(
                title = "RED DISPONIBLE",
                detail = "Internet activo, pero la sincronizacion esta pausada manualmente",
                accentColor = colors.bitecmaNavy,
                containerColor = colors.bitecmaBlueBg,
            )
            AppState.hasAuthenticatedSession() -> ConnectionStatusUi(
                title = "RED DISPONIBLE",
                detail = "Internet activo y sincronizacion habilitada",
                accentColor = colors.bitecmaTeal,
                containerColor = colors.bitecmaSuccessBg,
            )
            else -> ConnectionStatusUi(
                title = "RED DISPONIBLE",
                detail = "Internet activo, pero falta conectar una cuenta para sincronizar",
                accentColor = colors.bitecmaNavy,
                containerColor = colors.bitecmaBlueBg,
            )
        }
    }
}

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
    val colors = MaterialTheme.colorScheme
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var isConnecting by remember { mutableStateOf(false) }
    val esInvitado = AppState.isGuestMode
    val displayHasNetwork = rememberStableNetworkFlag()
    val connectionStatus = rememberConnectionStatusUi(
        esInvitado = esInvitado,
        isConnecting = isConnecting,
        displayHasNetwork = displayHasNetwork,
    )
    
    val opsAll by remember { derivedStateOf { DataManager.operacionesBd.toList() + DataManager.operacionesLc.toList() } }
    val totalOps = opsAll.size
    val totalMuestras = remember(opsAll) { countLpMuestras(opsAll) }
    val unidadesDensidad = remember(opsAll) { countDensidadUnidades(opsAll) }
    val operacionesPendientes by remember {
        derivedStateOf {
            DataManager.estadosSyncOperacion.values.count {
                it.estado != DataManager.EstadoSyncOperacion.SINCRONIZADO
            }
        }
    }
    val operacionesConError by remember {
        derivedStateOf {
            DataManager.estadosSyncOperacion.values.count {
                it.estado == DataManager.EstadoSyncOperacion.ERROR ||
                    (it.estado == DataManager.EstadoSyncOperacion.SINCRONIZANDO && !it.ultimoError.isNullOrBlank())
            }
        }
    }
    val archivosPendientes by remember { derivedStateOf { DataManager.pendingTextFiles.size } }
    val archivosConError by remember {
        derivedStateOf {
            DataManager.pendingTextFiles.count { it.estado == DataManager.EstadoSyncArchivo.ERROR }
        }
    }

    val speciesNameById by remember {
        derivedStateOf { DataManager.especies.associate { it.id to it.nombreComun } }
    }

    val topSpecies by remember {
        derivedStateOf {
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
            val top = totals.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }
            val maxVal = (top.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
            top to maxVal
        }
    }

    LaunchedEffect(AppState.forceOffline, AppState.authToken) {
        DataManager.reconcileBackgroundSync(ctx)
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
                    color = colors.bitecmaNavy
                )
                HorizontalDivider(color = colors.bitecmaBorder)
                
                NavigationDrawerItem(
                    label = { Text("Dashboard") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Operaciones") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; navController.navigate(AppRoutes.operaciones(userId)) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Especies") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; navController.navigate(AppRoutes.especies(userId)) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Botes") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; navController.navigate(AppRoutes.botes(userId)) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                
                if (AppState.hasAuthenticatedSession()) {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        color = colors.bitecmaSoftBackground,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sincronizacion", fontSize = 11.sp, fontWeight = FontWeight.Black, color = colors.bitecmaNavy)
                                Text(
                                    if (AppState.forceOffline) "Pausada manualmente" else "Habilitada con la nube",
                                    fontSize = 10.sp,
                                    color = colors.bitecmaMutedText
                                )
                            }
                            Switch(
                                checked = !AppState.forceOffline,
                                enabled = !isConnecting,
                                onCheckedChange = { checked ->
                                    if (isConnecting) return@Switch
                                    AppState.forceOffline = !checked
                                    if (AppState.forceOffline) {
                                        AppState.isOnline = false
                                        AppState.persistSession(ctx)
                                        DataManager.reconcileBackgroundSync(ctx)
                                        return@Switch
                                    }
                                    AppState.persistSession(ctx)
                                    scope.launch {
                                        isConnecting = true
                                        val ok = runCatching { DataManager.ensureAuthenticatedOnlineSession(ctx) }.getOrNull() == true
                                        if (ok) {
                                            DataManager.reconcileBackgroundSync(ctx)
                                            runCatching { DataManager.syncAllFromServer(ctx) }
                                        } else {
                                            AppState.isOnline = false
                                            AppState.persistSession(ctx)
                                            DataManager.reconcileBackgroundSync(ctx)
                                        }
                                        isConnecting = false
                                    }
                                }
                            )
                        }
                    }
                }

                if (esInvitado) {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        color = colors.bitecmaAmberBg,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Modo sin cuenta", fontSize = 12.sp, fontWeight = FontWeight.Black, color = colors.bitecmaNavy)
                            Text(
                                "Puedes trabajar normal, pero nada se sincroniza con la base hasta que conectes una cuenta.",
                                fontSize = 10.sp,
                                color = colors.bitecmaSubtleText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(AppRoutes.LOGIN)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Conectar cuenta")
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    color = connectionStatus.containerColor,
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
                                    connectionStatus.accentColor,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = connectionStatus.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = connectionStatus.accentColor
                            )
                            Text(
                                text = connectionStatus.detail,
                                fontSize = 10.sp,
                                color = colors.bitecmaMutedText
                            )
                            if (operacionesPendientes > 0 || archivosPendientes > 0) {
                                Text(
                                    text = "Pendientes: $operacionesPendientes operaciones, $archivosPendientes archivos",
                                    fontSize = 10.sp,
                                    color = colors.bitecmaMutedText,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (operacionesConError > 0) {
                                Text(
                                    text = "Con error de sincronizacion: $operacionesConError",
                                    fontSize = 10.sp,
                                    color = colors.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (archivosConError > 0) {
                                Text(
                                    text = "Archivos con error: $archivosConError",
                                    fontSize = 10.sp,
                                    color = colors.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (esInvitado && (operacionesPendientes > 0 || archivosPendientes > 0)) {
                                Text(
                                    text = "Conecta una cuenta cuando tengas internet para subir estos pendientes a la base.",
                                    fontSize = 10.sp,
                                    color = colors.bitecmaNavy,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
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
                HorizontalDivider(color = colors.bitecmaBorder)
                if (esInvitado) {
                    NavigationDrawerItem(
                        label = { Text("Conectar cuenta") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(AppRoutes.LOGIN)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                NavigationDrawerItem(
                    label = { Text(if (esInvitado) "Salir del modo sin cuenta" else "Cerrar sesión", color = colors.error) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        AppState.clearSession(ctx)
                        navController.navigate(AppRoutes.LOGIN) {
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
                    title = { Text("Dashboard", color = colors.onPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú", tint = colors.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.primary)
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
                    Text("Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.bitecmaNavy)
                    Text("Resumen operacional", color = colors.bitecmaSubtleText, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    DashboardStatusCard(
                        esInvitado = esInvitado,
                        isConnecting = isConnecting,
                        displayHasNetwork = displayHasNetwork,
                        connectionStatus = connectionStatus,
                        operacionesPendientes = operacionesPendientes,
                        archivosPendientes = archivosPendientes,
                        operacionesConError = operacionesConError,
                        archivosConError = archivosConError,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    DashboardQuickActions(
                        onOperaciones = { navController.navigate(AppRoutes.operaciones(userId)) },
                        onEspecies = { navController.navigate(AppRoutes.especies(userId)) },
                        onBotes = { navController.navigate(AppRoutes.botes(userId)) },
                    )
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
                                    color = colors.bitecmaTeal,
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                SummaryCard(
                                    title = "MUESTRAS L-P",
                                    value = totalMuestras.toString(),
                                    subtitle = "Registros L/LP/D",
                                    color = colors.bitecmaNavy,
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                SummaryCard(
                                    title = "DENSIDAD",
                                    value = unidadesDensidad.toString(),
                                    subtitle = "Unidades registradas",
                                    color = colors.primary,
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
                                    color = colors.bitecmaTeal,
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SummaryCard(
                                    title = "MUESTRAS L-P",
                                    value = totalMuestras.toString(),
                                    subtitle = "Registros L/LP/D",
                                    color = colors.bitecmaNavy,
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SummaryCard(
                                    title = "DENSIDAD",
                                    value = unidadesDensidad.toString(),
                                    subtitle = "Unidades registradas",
                                    color = colors.primary,
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
                    val top = topSpecies.first
                    val maxVal = topSpecies.second

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompact = maxWidth < 700.dp
                        if (isCompact) {
                            Column {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = colors.bitecmaCardBackground),
                                    border = BorderStroke(1.dp, colors.bitecmaBorder),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("OPERACIONES RECIENTES", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.bitecmaMutedText)
                                            TextButton(onClick = { navController.navigate(AppRoutes.operaciones(userId)) }) {
                                                Text("Ver todas", fontSize = 12.sp)
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.bitecmaBorder)
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
                                                color = colors.bitecmaMutedText
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = colors.bitecmaCardBackground),
                                    border = BorderStroke(1.dp, colors.bitecmaBorder),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("COMPOSICIÓN POR ESPECIE", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.bitecmaMutedText)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.bitecmaBorder)
                                        if (top.isEmpty()) {
                                            Text("Sin datos", color = colors.bitecmaMutedText, fontSize = 12.sp)
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
                                                        color = colors.bitecmaTeal,
                                                        trackColor = colors.bitecmaSoftBackgroundAlt
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
                                    colors = CardDefaults.cardColors(containerColor = colors.bitecmaCardBackground),
                                    border = BorderStroke(1.dp, colors.bitecmaBorder),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("OPERACIONES RECIENTES", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.bitecmaMutedText)
                                            TextButton(onClick = { navController.navigate(AppRoutes.operaciones(userId)) }) {
                                                Text("Ver todas", fontSize = 12.sp)
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.bitecmaBorder)
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
                                                color = colors.bitecmaMutedText
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = colors.bitecmaCardBackground),
                                    border = BorderStroke(1.dp, colors.bitecmaBorder),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("COMPOSICIÓN POR ESPECIE", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.bitecmaMutedText)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.bitecmaBorder)
                                        if (top.isEmpty()) {
                                            Text("Sin datos", color = colors.bitecmaMutedText, fontSize = 12.sp)
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
                                                        color = colors.bitecmaTeal,
                                                        trackColor = colors.bitecmaSoftBackgroundAlt
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
private fun DashboardStatusCard(
    esInvitado: Boolean,
    isConnecting: Boolean,
    displayHasNetwork: Boolean,
    connectionStatus: ConnectionStatusUi,
    operacionesPendientes: Int,
    archivosPendientes: Int,
    operacionesConError: Int,
    archivosConError: Int,
) {
    val colors = MaterialTheme.colorScheme
    val syncEnabled = displayHasNetwork && AppState.hasAuthenticatedSession() && !AppState.forceOffline
    val title = when {
        esInvitado -> "Trabajo local sin cuenta"
        isConnecting -> "Conectando y sincronizando"
        syncEnabled -> "Sincronizacion habilitada"
        displayHasNetwork -> "Trabajo local con red disponible"
        else -> "Trabajo local sin red"
    }
    val detail = when {
        esInvitado -> "Puedes registrar operaciones y archivos, pero quedaran pendientes hasta conectar una cuenta."
        isConnecting -> "La app esta validando sesion y preparando la sincronizacion."
        syncEnabled -> "La sincronizacion esta habilitada para operaciones y documentos."
        displayHasNetwork -> "Hay internet, pero la app sigue en trabajo local hasta habilitar sincronizacion."
        else -> "La app sigue trabajando con cache local y cola de pendientes para sincronizar despues."
    }
    val containerColor = when {
        esInvitado -> colors.bitecmaAmberBg
        isConnecting -> colors.bitecmaBlueBg
        syncEnabled -> colors.bitecmaSuccessBg
        else -> colors.bitecmaSoftBackgroundAlt
    }
    val accentColor = when {
        esInvitado -> colors.bitecmaNavy
        isConnecting -> colors.bitecmaNavy
        syncEnabled -> colors.bitecmaTeal
        else -> colors.bitecmaNavy
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, colors.bitecmaBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = accentColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.bitecmaSubtleText)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusPill("Ops pendientes", operacionesPendientes.toString())
                StatusPill("Archivos", archivosPendientes.toString())
                if (operacionesConError + archivosConError > 0) {
                    StatusPill("Errores", (operacionesConError + archivosConError).toString(), isError = true)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, value: String, isError: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isError) colors.bitecmaDangerBg else colors.bitecmaCardBackground,
        border = BorderStroke(1.dp, colors.bitecmaBorder)
    ) {
        Text(
            text = "$label: $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (isError) colors.error else colors.bitecmaNavy,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun DashboardQuickActions(
    onOperaciones: () -> Unit,
    onEspecies: () -> Unit,
    onBotes: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text("Accesos rapidos", style = MaterialTheme.typography.titleMedium, color = colors.bitecmaNavy)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("Operaciones", Icons.Default.EditNote, colors.bitecmaTealContainer, colors.bitecmaTeal, Modifier.weight(1f), onOperaciones)
            QuickActionCard("Especies", Icons.Default.SetMeal, colors.bitecmaSoftBackgroundAlt, colors.bitecmaNavy, Modifier.weight(1f), onEspecies)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("Botes", Icons.Default.DirectionsBoat, colors.bitecmaCardBackground, colors.bitecmaNavy, Modifier.weight(1f), onBotes)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, colors.bitecmaBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconColor)
            Spacer(modifier = Modifier.width(10.dp))
            Text(title, color = colors.bitecmaNavy, fontWeight = FontWeight.Bold)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun SummaryCard(title: String, value: String, subtitle: String, color: Color, isDarkMode: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = colors.bitecmaCardBackground),
        border = BorderStroke(1.dp, colors.bitecmaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bitecmaCardBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(color)
                    .align(Alignment.TopCenter)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(title.uppercase(), color = colors.bitecmaMutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(value, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = colors.bitecmaNavy)
                Text(subtitle, color = colors.bitecmaMutedText, fontSize = 11.sp)
            }
        }
    }
}
