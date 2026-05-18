package com.bitecma.app.ui.login

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import com.bitecma.app.network.RetrofitClient
import com.bitecma.app.network.AuthLoginRequest
import kotlinx.coroutines.launch
import com.bitecma.app.data.PerfilesData
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager

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
    
    // Check if the user is a known user or has a last login record
    val trimmedEmail = email.trim()
    val foundUser = PerfilesData.perfiles.find { it.correo.equals(trimmedEmail, ignoreCase = true) }
    val lastLoginText = remember(trimmedEmail) {
        if (trimmedEmail.isEmpty()) null else AppState.getLastLoginText(ctx, trimmedEmail)
    }
    
    // Show "Bienvenido de nuevo" if it's a known domain or we have a last login record
    val isKnownDomain = trimmedEmail.contains("@bitecma.cl", ignoreCase = true) || 
                        trimmedEmail.contains("@usuario.cl", ignoreCase = true)
    val shouldShowWelcome = foundUser != null || (lastLoginText != null && isKnownDomain)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "BITECMA",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = "Sistema AMERB - V1.1.0", fontSize = 12.sp, color = Color.Gray)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(text = "Iniciar sesión", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (shouldShowWelcome) {
                    val displayName = foundUser?.nombre ?: trimmedEmail.substringBefore("@")
                    Text(
                        text = "Bienvenido de nuevo, $displayName",
                        color = Color(0xFF006600),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Última vez conectado: ${lastLoginText ?: "Primera vez"}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    placeholder = { Text("bitecma@bitecma.cl") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    enabled = !isLoading
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
                    }
                )

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage, color = Color.Red, fontSize = 12.sp)
                }
                
                if (successMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = successMessage, color = Color(0xFF006600), fontSize = 12.sp)
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
                                // 1. INTENTO DE LOGIN ONLINE (CPANEL)
                                val response = RetrofitClient.apiService.login(AuthLoginRequest(correo = e, password = p))
                                
                                if (response.isSuccessful && response.body() != null) {
                                    val body = response.body()!!
                                    if (body.ok == true && !body.token.isNullOrBlank()) {
                                        AppState.isOnline = true
                                        AppState.authToken = body.token
                                        AppState.currentUserEmail = e
                                        AppState.forceOffline = false
                                        val userApi = body.user
                                        AppState.currentUserId = userApi?.uid
                                        AppState.currentUserName = userApi?.nombre
                                        AppState.currentUserRole = userApi?.rol
                                        AppState.persistSession(ctx)
                                        AppState.saveLastLoginNow(ctx, e)
                                        successMessage = "Bienvenido ${userApi?.nombre ?: ""} (Online)"
                                        runCatching { DataManager.syncAllFromServer(ctx) }
                                        navController.navigate("dashboard/${userApi?.uid ?: 0}") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                        return@launch
                                    } else {
                                        // Servidor respondió pero con error (ej: contraseña incorrecta en API)
                                        errorMessage = body.error ?: "Credenciales de API inválidas"
                                    }
                                } else {
                                    // Error de servidor (404, 500, etc)
                                    errorMessage = "Error en servidor Bitecma (${response.code()})"
                                }
                            } catch (ex: Exception) {
                                // Error de red (Sin internet, URL mal, etc) - NO CIERRA LA APP
                                errorMessage = "Sin conexión a Bitecma. Intentando local..."
                            }

                            // 2. RESPALDO: SI FALLÓ LA API O NO HAY INTERNET, PROBAMOS LOCAL
                            val localUser = PerfilesData.perfiles.find { it.correo.lowercase() == e }
                            if (localUser != null && localUser.contrasena == p) {
                                AppState.isOnline = false
                                AppState.authToken = null
                                AppState.currentUserEmail = e
                                AppState.currentUserId = localUser.id
                                AppState.currentUserName = localUser.nombre
                                AppState.currentUserRole = localUser.rol
                                AppState.persistSession(ctx)
                                AppState.saveLastLoginNow(ctx, e)
                                successMessage = "Bienvenido (Modo Offline)"
                                navController.navigate("dashboard/${localUser.id}") {
                                    popUpTo("login") { inclusive = true }
                                }
                            } else {
                                if (errorMessage.isEmpty() || errorMessage.contains("Intentando local")) {
                                    errorMessage = "Correo o contraseña incorrectos localmente"
                                }
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Ingresar", color = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(onClick = { 
                    val currentEmail = email.trim()
                    navController.navigate("forgot_password/${if(currentEmail.isEmpty()) " " else currentEmail}") 
                }) {
                    Text(
                        text = "¿Olvidaste tu contraseña?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}
