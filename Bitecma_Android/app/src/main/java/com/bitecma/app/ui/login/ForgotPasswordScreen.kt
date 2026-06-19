package com.bitecma.app.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bitecma.app.ui.bitecmaBlueBg
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaDangerBg
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaNavyStrong
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(navController: NavController, initialEmail: String) {
    val colors = MaterialTheme.colorScheme
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    var email by remember { mutableStateOf(initialEmail) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cambiar Contraseña", color = androidx.compose.ui.graphics.Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = androidx.compose.ui.graphics.Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bitecmaNavyStrong)
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.bitecmaBlueBg,
                border = BorderStroke(1.dp, colors.bitecmaBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = colors.bitecmaNavy)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Este flujo solo orienta al usuario. El cambio real de contraseña debe hacerse en el sistema conectado a la base de datos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.bitecmaNavy
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.bitecmaCardBackground),
                border = BorderStroke(1.dp, colors.bitecmaBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Restablecer acceso",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.bitecmaNavy
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "La recuperación de contraseña debe hacerse contra la base de datos del servidor.",
                        fontSize = 12.sp,
                        color = colors.bitecmaSubtleText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Si no recuerdas tu clave, conéctate a internet y solicita el cambio desde el sistema central o con un administrador.",
                        fontSize = 12.sp,
                        color = colors.bitecmaSubtleText
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo electrónico") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(emailFocusRequester),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { passwordFocusRequester.requestFocus() }
                        ),
                        singleLine = true,
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
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Nueva contraseña") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocusRequester),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { confirmFocusRequester.requestFocus() }
                        ),
                        singleLine = true,
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
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirmar contraseña") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(confirmFocusRequester),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.bitecmaTeal,
                            focusedLabelColor = colors.bitecmaTeal,
                            unfocusedBorderColor = colors.bitecmaBorder,
                            unfocusedLabelColor = colors.bitecmaMutedText,
                            unfocusedContainerColor = colors.bitecmaSoftBackground,
                            focusedContainerColor = colors.bitecmaCardBackground
                        )
                    )

                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = colors.bitecmaDangerBg,
                            border = BorderStroke(1.dp, colors.bitecmaBorder)
                        ) {
                            Text(errorMessage, color = colors.error, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                        }
                    }
                    if (successMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = colors.bitecmaBlueBg,
                            border = BorderStroke(1.dp, colors.bitecmaBorder)
                        ) {
                            Text(successMessage, color = colors.bitecmaNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            errorMessage = ""
                            successMessage = ""
                            val e = email.trim().lowercase()

                            if (e.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                                errorMessage = "Todos los campos son obligatorios"
                                return@Button
                            }

                            if (newPassword.length < 4) {
                                errorMessage = "La contraseña debe tener al menos 4 caracteres"
                                return@Button
                            }

                            if (newPassword != confirmPassword) {
                                errorMessage = "Las contraseñas no coinciden"
                                return@Button
                            }

                            successMessage = "Solicitud preparada. Debes cambiar la contraseña en el sistema conectado a la base de datos."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.bitecmaTeal)
                    ) {
                        Text("Actualizar Contraseña", color = colors.onSecondary)
                    }
                }
            }
        }
    }
}
