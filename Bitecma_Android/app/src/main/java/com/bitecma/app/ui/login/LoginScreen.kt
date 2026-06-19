package com.bitecma.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
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
    val isDark = isSystemInDarkTheme()
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
                        if (isDark) Color(0xFF07101B) else Color(0xFFD8F3FF),
                        if (isDark) Color(0xFF10233C) else Color(0xFFBFE6FF),
                        if (isDark) Color(0xFF0A5F67) else colors.bitecmaTeal,
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
                                val result = DataManager.loginUsuario(ctx, e, p)
                                if (result.success) {
                                    successMessage = result.successMessage ?: ""
                                    navController.navigate(AppRoutes.dashboard(AppState.dashboardUserId())) {
                                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                                    }
                                    return@launch
                                }
                                errorMessage = result.errorMessage ?: "No se pudo iniciar sesión"
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "El primer ingreso requiere internet. Despues podras seguir usando la app en este dispositivo.",
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
