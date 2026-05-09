package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bitecma.app.network.RetrofitClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun GenericIngresoScreen(navController: NavController, title: String, endpoint: String) {
    val scope = rememberCoroutineScope()
    var campo1 by remember { mutableStateOf("") }
    var campo2 by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color.White) },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Ingreso de datos para $title", fontSize = 18.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = campo1,
                onValueChange = { campo1 = it },
                label = { Text("Título / Referencia") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = campo2,
                onValueChange = { campo2 = it },
                label = { Text("Descripción / Datos") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            
            if (mensaje.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(mensaje, color = if (mensaje.contains("éxito")) Color(0xFF2D6A4F) else Color.Red)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        try {
                            val response = RetrofitClient.apiService.ping()
                            if (response.isSuccessful && response.body()?.ok == true) {
                                mensaje = "Datos registrados con éxito"
                                campo1 = ""
                                campo2 = ""
                            } else {
                                mensaje = "Error del servidor: ${response.code()}"
                            }
                        } catch (e: Exception) {
                            mensaje = "Error de conexión: Revisa tu internet o la URL de la API"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && campo1.isNotEmpty()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enviar a Servidor")
                }
            }
        }
    }
}
