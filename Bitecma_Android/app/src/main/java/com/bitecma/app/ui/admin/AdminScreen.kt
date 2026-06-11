package com.bitecma.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bitecma.app.data.NuevoUsuarioRequest
import com.bitecma.app.data.PerfilesData
import com.bitecma.app.data.SolicitudesData
import com.bitecma.app.data.Usuario
import com.bitecma.app.ui.bitecmaAmberBg
import com.bitecma.app.ui.bitecmaBlueBg
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaDangerBg
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaSuccessBg
import com.bitecma.app.ui.bitecmaTeal
import com.bitecma.app.ui.bitecmaTealContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun AdminScreen(navController: NavController, userId: Int) {
    val colors = MaterialTheme.colorScheme
    var tabIndex by remember { mutableStateOf(0) }
    var users by remember { mutableStateOf(PerfilesData.perfiles.toList()) }
    var solicitudes by remember { mutableStateOf(SolicitudesData.solicitudes.toList()) }
    val pendingSolicitudes = solicitudes.filter { it.estado == "PENDIENTE" }
    val activeUsers = users.count { it.activo }
    val tabs = listOf("Usuarios", "Solicitudes")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administración", color = colors.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = colors.onPrimary)
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
                .background(colors.background),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.bitecmaBlueBg,
                    border = BorderStroke(1.dp, colors.bitecmaBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Panel local de administración", fontWeight = FontWeight.Bold, color = colors.bitecmaNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Este módulo trabaja con datos locales de la app para pruebas y gestión interna. Revisa usuarios y solicitudes antes de sincronizar o pasar a producción.",
                            color = colors.bitecmaSubtleText,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AdminSummaryCard(
                        title = "Usuarios",
                        value = users.size.toString(),
                        subtitle = "Registros locales",
                        icon = Icons.Default.AdminPanelSettings,
                        containerColor = colors.bitecmaTealContainer,
                        accentColor = colors.bitecmaTeal,
                        modifier = Modifier.weight(1f)
                    )
                    AdminSummaryCard(
                        title = "Activos",
                        value = activeUsers.toString(),
                        subtitle = "Con acceso habilitado",
                        icon = Icons.Default.Badge,
                        containerColor = colors.bitecmaSuccessBg,
                        accentColor = colors.bitecmaNavy,
                        modifier = Modifier.weight(1f)
                    )
                    AdminSummaryCard(
                        title = "Solicitudes",
                        value = pendingSolicitudes.size.toString(),
                        subtitle = "Pendientes de revision",
                        icon = Icons.Default.PersonAddAlt1,
                        containerColor = colors.bitecmaAmberBg,
                        accentColor = colors.bitecmaNavy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.bitecmaCardBackground,
                    border = BorderStroke(1.dp, colors.bitecmaBorder)
                ) {
                    Column {
                        TabRow(
                            selectedTabIndex = tabIndex,
                            containerColor = colors.bitecmaCardBackground,
                            contentColor = colors.bitecmaNavy
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    text = { Text(title) },
                                    selected = tabIndex == index,
                                    onClick = { tabIndex = index },
                                    selectedContentColor = colors.bitecmaTeal,
                                    unselectedContentColor = colors.bitecmaMutedText
                                )
                            }
                        }

                        when (tabIndex) {
                            0 -> ListaUsuarios(
                                users = users,
                                onUsersChange = { users = it }
                            )
                            1 -> ListaSolicitudes(
                                solicitudes = pendingSolicitudes,
                                onSolicitudesChange = { updated ->
                                    solicitudes = updated
                                    users = PerfilesData.perfiles.toList()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListaUsuarios(
    users: List<Usuario>,
    onUsersChange: (List<Usuario>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    if (users.isEmpty()) {
        EmptyAdminState(
            title = "Sin usuarios locales",
            detail = "Todavía no hay usuarios creados o aprobados en este dispositivo."
        )
        return
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        users.forEach { user ->
            var expanded by remember(user.id) { mutableStateOf(false) }
            var selectedRole by remember(user.id) { mutableStateOf(user.rol) }
            var activo by remember(user.id) { mutableStateOf(user.activo) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.bitecmaSoftBackground),
                border = BorderStroke(1.dp, colors.bitecmaBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.nombre, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.bitecmaNavy)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(user.correo, color = colors.bitecmaSubtleText, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            PerfilesData.perfiles.remove(user)
                            onUsersChange(PerfilesData.perfiles.toList())
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = colors.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdminInfoPill("Rol", selectedRole)
                        AdminInfoPill("Estado", if (activo) "Activo" else "Inactivo")
                        if (user.numero.isNotBlank()) {
                            AdminInfoPill("Contacto", user.numero)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.bitecmaBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                border = BorderStroke(1.dp, colors.bitecmaBorder)
                            ) {
                                Text("Rol: $selectedRole", color = colors.bitecmaNavy)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("Admin", "Biólogo", "Visualizador").forEach { rol ->
                                    DropdownMenuItem(
                                        text = { Text(rol) },
                                        onClick = {
                                            selectedRole = rol
                                            user.rol = rol
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Acceso", color = colors.bitecmaSubtleText, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = activo,
                                onCheckedChange = {
                                    activo = it
                                    user.activo = it
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListaSolicitudes(
    solicitudes: List<NuevoUsuarioRequest>,
    onSolicitudesChange: (List<NuevoUsuarioRequest>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    if (solicitudes.isEmpty()) {
        EmptyAdminState(
            title = "Sin solicitudes pendientes",
            detail = "No hay nuevas solicitudes por revisar en este momento."
        )
        return
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        solicitudes.forEach { sol ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.bitecmaCardBackground),
                border = BorderStroke(1.dp, colors.bitecmaBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.bitecmaAmberBg
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = colors.bitecmaNavy,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${sol.nombres} ${sol.apellidos}", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.bitecmaNavy)
                            Text("RUT: ${sol.rut}", color = colors.bitecmaSubtleText, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdminInfoPill("Teléfono", sol.numero)
                        AdminInfoPill("CV", "${sol.nombreArchivoCv} (${sol.formatoCv})")
                        AdminInfoPill("Estado", sol.estado)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val primerNombre = sol.nombres.trim().split(" ").firstOrNull() ?: ""
                                val segundoApellido = sol.apellidos.trim().split(" ").getOrNull(1)
                                    ?: sol.apellidos.trim().split(" ").firstOrNull().orEmpty()
                                val rutLimpio = sol.rut.replace("-", "").replace(".", "").take(4)
                                val correo = "${primerNombre.lowercase()}.${segundoApellido.lowercase()}${rutLimpio}@bitecma.com"

                                val newUser = Usuario(
                                    id = PerfilesData.perfiles.maxOf { it.id } + 1,
                                    nombre = "${sol.nombres} ${sol.apellidos}",
                                    correo = correo,
                                    numero = sol.numero,
                                    contrasena = "12345678",
                                    rol = "Visualizador"
                                )
                                PerfilesData.perfiles.add(newUser)
                                sol.estado = "APROBADO"
                                onSolicitudesChange(SolicitudesData.solicitudes.toList())
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.bitecmaTeal)
                        ) {
                            Text("Aprobar")
                        }

                        OutlinedButton(
                            onClick = {
                                sol.estado = "RECHAZADO"
                                onSolicitudesChange(SolicitudesData.solicitudes.toList())
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, colors.error)
                        ) {
                            Text("Rechazar", color = colors.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSummaryCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, colors.bitecmaBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = accentColor)
            Spacer(modifier = Modifier.height(10.dp))
            Text(title.uppercase(), color = colors.bitecmaMutedText, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(value, color = colors.bitecmaNavy, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = colors.bitecmaSubtleText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AdminInfoPill(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.bitecmaSoftBackground,
        border = BorderStroke(1.dp, colors.bitecmaBorder)
    ) {
        Text(
            "$label: $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            color = colors.bitecmaNavy
        )
    }
}

@Composable
private fun EmptyAdminState(title: String, detail: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.bitecmaSoftBackground,
            border = BorderStroke(1.dp, colors.bitecmaBorder)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = colors.bitecmaNavy, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(detail, color = colors.bitecmaSubtleText, fontSize = 12.sp)
            }
        }
    }
}
