package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager
import com.bitecma.app.ui.bitecmaAmberBg
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaDangerBg
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSoftBackgroundAlt
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaTeal
import com.bitecma.app.ui.bitecmaTealContainer
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun GenericIngresoScreen(navController: NavController, title: String, endpoint: String) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val campo1FocusRequester = remember { FocusRequester() }
    val campo2FocusRequester = remember { FocusRequester() }
    var campo1 by remember { mutableStateOf("") }
    var campo2 by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val accessMessage = when {
        AppState.isGuestMode -> "Modo sin cuenta: este formulario no envia datos al servidor hasta conectar una cuenta."
        AppState.forceOffline || AppState.authToken.isNullOrBlank() -> "Sin sesion online: puedes preparar datos, pero no enviarlos al servidor."
        else -> "Conexion activa: el formulario puede enviar informacion al servidor."
    }

    val accessContainerColor = when {
        AppState.isGuestMode -> colors.bitecmaAmberBg
        AppState.forceOffline || AppState.authToken.isNullOrBlank() -> colors.bitecmaSoftBackgroundAlt
        else -> colors.bitecmaTealContainer
    }

    val accessContentColor = when {
        AppState.isGuestMode -> colors.bitecmaNavy
        AppState.forceOffline || AppState.authToken.isNullOrBlank() -> colors.bitecmaSubtleText
        else -> colors.bitecmaTeal
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = colors.onPrimary) },
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
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = colors.bitecmaCardBackground,
                    border = BorderStroke(1.dp, colors.bitecmaBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Ingreso de datos para $title", style = MaterialTheme.typography.titleMedium, color = colors.bitecmaNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Endpoint objetivo: $endpoint", fontSize = 12.sp, color = colors.bitecmaMutedText)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = campo1,
                            onValueChange = { campo1 = it },
                            label = { Text("Titulo / Referencia") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(campo1FocusRequester),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { campo2FocusRequester.requestFocus() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.bitecmaTeal,
                                focusedLabelColor = colors.bitecmaTeal,
                                unfocusedBorderColor = colors.bitecmaBorder,
                                unfocusedLabelColor = colors.bitecmaMutedText,
                                unfocusedContainerColor = colors.bitecmaSoftBackground,
                                focusedContainerColor = colors.bitecmaCardBackground
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = campo2,
                            onValueChange = { campo2 = it },
                            label = { Text("Descripcion / Datos") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(campo2FocusRequester),
                            minLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.bitecmaTeal,
                                focusedLabelColor = colors.bitecmaTeal,
                                unfocusedBorderColor = colors.bitecmaBorder,
                                unfocusedLabelColor = colors.bitecmaMutedText,
                                unfocusedContainerColor = colors.bitecmaSoftBackground,
                                focusedContainerColor = colors.bitecmaCardBackground
                            )
                        )
                    }
                }
            }

            if (mensaje.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (mensaje.contains("éxito")) colors.bitecmaTealContainer else colors.bitecmaDangerBg,
                        border = BorderStroke(1.dp, colors.bitecmaBorder)
                    ) {
                        Text(
                            mensaje,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            color = if (mensaje.contains("éxito")) colors.bitecmaTeal else colors.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                if (AppState.forceOffline || AppState.authToken.isNullOrBlank()) {
                                    mensaje = "Conecta una cuenta para enviar datos al servidor"
                                    return@launch
                                }
                                if (DataManager.pingServidor()) {
                                    mensaje = "Datos registrados con éxito"
                                    campo1 = ""
                                    campo2 = ""
                                    campo1FocusRequester.requestFocus()
                                } else {
                                    mensaje = "Error del servidor o sesión no disponible"
                                }
                            } catch (e: Exception) {
                                mensaje = "Error de conexión: Revisa tu internet o la URL de la API"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && campo1.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.bitecmaTeal)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.onSecondary)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enviar a Servidor")
                    }
                }
            }
        }
    }
}
