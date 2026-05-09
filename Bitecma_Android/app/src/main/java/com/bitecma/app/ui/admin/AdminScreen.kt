package com.bitecma.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun AdminScreen(navController: NavController, userId: Int) {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Usuarios", "Nuevas Solicitudes")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administración", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF003366))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        selectedContentColor = Color(0xFF003366),
                        unselectedContentColor = Color.Gray
                    )
                }
            }

            when (tabIndex) {
                0 -> ListaUsuarios()
                1 -> ListaSolicitudes()
            }
        }
    }
}

@Composable
fun ListaUsuarios() {
    var users by remember { mutableStateOf(PerfilesData.perfiles.toList()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(users) { user ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(user.nombre, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = {
                            PerfilesData.perfiles.remove(user)
                            users = PerfilesData.perfiles.toList()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
                        }
                    }
                    Text(user.correo, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        var selectedRole by remember { mutableStateOf(user.rol) }
                        
                        Box {
                            Text(
                                text = "Rol: $selectedRole",
                                modifier = Modifier
                                    .clickable { expanded = true }
                                    .background(Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                color = Color.Black
                            )
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
                            Text("Activo")
                            var activo by remember { mutableStateOf(user.activo) }
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

@Composable
fun ListaSolicitudes() {
    var solicitudes by remember { mutableStateOf(SolicitudesData.solicitudes.toList()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (solicitudes.isEmpty()) {
            item {
                Text("No hay solicitudes pendientes.", color = Color.Gray, modifier = Modifier.padding(16.dp))
            }
        }
        items(solicitudes) { sol ->
            if (sol.estado == "PENDIENTE") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${sol.nombres} ${sol.apellidos}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("RUT: ${sol.rut}", color = Color.Gray)
                        Text("Teléfono: ${sol.numero}", color = Color.Gray)
                        Text("CV Adjunto: ${sol.nombreArchivoCv} (${sol.formatoCv})", color = Color(0xFF003366), fontWeight = FontWeight.Medium)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    val primerNombre = sol.nombres.trim().split(" ").firstOrNull() ?: ""
                                    val segundoApellido = sol.apellidos.trim().split(" ").getOrNull(1) ?: sol.apellidos.trim().split(" ").firstOrNull() ?: ""
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
                                    solicitudes = SolicitudesData.solicitudes.toList()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006600))
                            ) {
                                Text("Aprobar", color = Color.White)
                            }
                            
                            Button(
                                onClick = {
                                    sol.estado = "RECHAZADO"
                                    solicitudes = SolicitudesData.solicitudes.toList()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Text("Rechazar", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
