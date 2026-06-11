package com.bitecma.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.bitecma.app.AppRoutes
import com.bitecma.app.network.RetrofitClient
import com.bitecma.app.network.AuthLoginRequest
import com.bitecma.app.sync.SyncScheduler
import kotlinx.coroutines.launch
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaTeal
import com.bitecma.app.ui.bitecmaTealContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(email, password) {
        if (errorMessage.isNotEmpty() || successMessage.isNotEmpty()) {
            errorMessage = ""
            successMessage = ""
        }
    }
    
    val trimmedEmail = email.trim()
    val colors = MaterialTheme.colorScheme
    val lastLoginText = remember(trimmedEmail) {
        if (trimmedEmail.isEmpty()) null else AppState.getLastLoginText(ctx, trimmedEmail)
    }
    val shouldShowWelcome = !lastLoginText.isNullOrBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFD8F3FF),
                        Color(0xFFBFE6FF),
                        colors.bitecmaTeal,
                    ),
                ),
            )
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.bitecmaCardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .border(1.dp, colors.bitecmaTeal.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "BITECMA",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.bitecmaNavy
                    )
                    Text(
                        text = "Sistema AMERB - V1.1.0",
                        fontSize = 11.sp,
                        color = colors.bitecmaTeal,
                        letterSpacing = 1.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(text = "Iniciar sesión", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.bitecmaNavy)
                    Text(
                        text = "Sistema de gestión de operaciones bentónicas y generación de documentos.",
                        fontSize = 13.sp,
                        color = colors.bitecmaSubtleText
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (shouldShowWelcome) {
                        val displayName = trimmedEmail.substringBefore("@").ifBlank { "usuario" }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.bitecmaTealContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Bienvenido de nuevo, $displayName",
                                    color = colors.bitecmaTeal,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Última vez conectado: ${lastLoginText ?: "Primera vez"}",
                                    color = colors.bitecmaSubtleText,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo electrónico") },
                        placeholder = { Text("tu_correo@dominio.cl") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        enabled = !isLoading,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.bitecmaTeal,
                            focusedLabelColor = colors.bitecmaTeal,
                            unfocusedBorderColor = colors.bitecmaBorder,
                            unfocusedLabelColor = colors.bitecmaMutedText,
                            unfocusedContainerColor = colors.bitecmaSoftBackground,
                            focusedContainerColor = colors.surface,
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        placeholder = { Text("********") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !isLoading,
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña")
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.bitecmaTeal,
                            focusedLabelColor = colors.bitecmaTeal,
                            unfocusedBorderColor = colors.bitecmaBorder,
                            unfocusedLabelColor = colors.bitecmaMutedText,
                            unfocusedContainerColor = colors.bitecmaSoftBackground,
                            focusedContainerColor = colors.surface,
                        )
                    )

                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = errorMessage, color = colors.error, fontSize = 12.sp)
                    }
                    
                    if (successMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = successMessage, color = colors.bitecmaTeal, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            errorMessage = ""
                            successMessage = ""
                            val e = email.trim().lowercase()
                            val p = password.trim()
                            
                            if (e.isEmpty() || p.isEmpty()) {
                                errorMessage = "Completa correo y contraseña"
                                return@Button
                            }

                            scope.launch {
                                isLoading = true
                                errorMessage = ""
                                try {
                                    val response = RetrofitClient.apiService.login(AuthLoginRequest(correo = e, password = p))

                                    if (response.isSuccessful && response.body() != null) {
                                        val body = response.body()!!
                                        if (body.ok == true && !body.token.isNullOrBlank()) {
                                            AppState.isOnline = true
                                            AppState.authToken = body.token
                                            AppState.currentUserEmail = e
                                            AppState.forceOffline = false
                                            AppState.hasVerifiedSession = true
                                            val userApi = body.user
                                            AppState.currentUserId = userApi?.uid ?: userApi?.id
                                            AppState.currentUserName = userApi?.nombre
                                            AppState.currentUserRole = userApi?.rol
                                            AppState.persistSession(ctx)
                                            AppState.hasNetwork = true
                                            AppState.saveLastLoginNow(ctx, e)
                                            successMessage = "Bienvenido ${userApi?.nombre ?: ""} (Online)"
                                            DataManager.reconcileBackgroundSync(ctx)
                                            SyncScheduler.scheduleImmediate(ctx)
                                            runCatching { DataManager.syncAllFromServer(ctx) }
                                            navController.navigate(AppRoutes.dashboard(AppState.dashboardUserId())) {
                                                popUpTo(AppRoutes.LOGIN) { inclusive = true }
                                            }
                                            return@launch
                                        } else {
                                            errorMessage = body.error ?: "Correo o contraseña incorrectos"
                                            isLoading = false
                                            return@launch
                                        }
                                    } else {
                                        val code = response.code()
                                        if (code == 401 || code == 403) {
                                            errorMessage = "Correo o contraseña incorrectos"
                                            isLoading = false
                                            return@launch
                                        }
                                        errorMessage = "No se pudo conectar con el servidor ($code)"
                                    }
                                } catch (ex: Exception) {
                                    errorMessage = "Necesitas internet para iniciar sesión con tu cuenta registrada"
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.bitecmaTeal),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("Ingresar", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            AppState.enterGuestMode(ctx)
                            successMessage = "Entraste sin cuenta. Todo quedará local hasta que conectes una cuenta."
                            navController.navigate(AppRoutes.dashboard(AppState.dashboardUserId())) {
                                popUpTo(AppRoutes.LOGIN) { inclusive = true }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        border = BorderStroke(1.5.dp, colors.bitecmaBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.bitecmaCardBackground,
                            contentColor = colors.bitecmaSubtleText,
                        )
                    ) {
                        Text("Usar sin cuenta")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Sin cuenta puedes trabajar localmente. Para sincronizar con la base de datos deberás iniciar sesión más tarde.",
                        fontSize = 11.sp,
                        color = colors.bitecmaMutedText
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    TextButton(onClick = { 
                        val currentEmail = email.trim()
                        navController.navigate(AppRoutes.forgotPassword(currentEmail))
                    }) {
                        Text(
                            text = "¿Olvidaste tu contraseña?",
                            fontSize = 12.sp,
                            color = colors.bitecmaTeal,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Text(
                        text = "Bitecma Ltda. © 1995",
                        fontSize = 12.sp,
                        color = colors.bitecmaMutedText
                    )
                }
            }            
        }
    }
}
