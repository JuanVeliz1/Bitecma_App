package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bitecma.app.data.AppState
import com.bitecma.app.ui.bitecmaAmberBg
import com.bitecma.app.ui.bitecmaBlueBg
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSoftBackgroundAlt
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaTeal
import com.bitecma.app.ui.bitecmaTealContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun IngresosScreen(navController: NavController, userId: Int) {
    val colors = MaterialTheme.colorScheme
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Capturas", "Muestras", "Densidad")

    val accessMessage = when {
        AppState.isGuestMode -> "Modo sin cuenta: puedes capturar datos localmente antes de sincronizar con una cuenta."
        AppState.forceOffline || AppState.authToken.isNullOrBlank() -> "Sin sesion online: usa estos formularios como apoyo local de terreno."
        else -> "Conexion activa: formularios listos para registrar informacion."
    }

    val accessContainerColor = when {
        AppState.isGuestMode -> colors.bitecmaAmberBg
        AppState.forceOffline || AppState.authToken.isNullOrBlank() -> colors.bitecmaSoftBackgroundAlt
        else -> colors.bitecmaBlueBg
    }

    val accessContentColor = when {
        AppState.isGuestMode -> colors.bitecmaNavy
        AppState.forceOffline || AppState.authToken.isNullOrBlank() -> colors.bitecmaSubtleText
        else -> colors.bitecmaNavy
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ingreso de Datos", color = colors.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = colors.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = colors.bitecmaCardBackground,
                contentColor = colors.bitecmaNavy
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        selectedContentColor = colors.bitecmaTeal,
                        unselectedContentColor = colors.bitecmaMutedText
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> {
                        IngresoInfoCard(
                            title = "Capturas",
                            description = "Registra cantidad por bote y especie para uso rapido en terreno.",
                            example = "Ej: Bote=5MENTARIO · Especie=Loco · Cantidad=150",
                            icon = Icons.Default.EditNote
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FormCaptura(onSave = { })
                    }
                    1 -> {
                        IngresoInfoCard(
                            title = "Muestras",
                            description = "Registra mediciones biologicas como longitud y peso por especie.",
                            example = "Ej: Sector=Chan-chan · Talla=95 mm · Peso=180 g",
                            icon = Icons.Default.SetMeal
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FormMuestra(onSave = { })
                    }
                    2 -> {
                        IngresoInfoCard(
                            title = "Densidad",
                            description = "Registra area y conteo de individuos en una unidad de muestreo.",
                            example = "Ej: Área=10 · Conteo=12",
                            icon = Icons.Default.Scale
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FormDensidad(onSave = { })
                    }
                }
            }
        }
    }
}

@Composable
fun FormCaptura(onSave: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val especieFocusRequester = remember { FocusRequester() }
    val cantidadFocusRequester = remember { FocusRequester() }
    var bote by remember { mutableStateOf("") }
    var especie by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.bitecmaCardBackground,
        border = BorderStroke(1.dp, colors.bitecmaBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
        Text("Nueva Captura", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.bitecmaNavy)
        Spacer(modifier = Modifier.height(16.dp))
        IngresoField(
            value = bote,
            onValueChange = { bote = it },
            label = "Nombre del Bote",
            modifier = Modifier.fillMaxWidth(),
            imeAction = ImeAction.Next,
            onNext = { especieFocusRequester.requestFocus() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        IngresoField(
            value = especie,
            onValueChange = { especie = it },
            label = "Especie",
            modifier = Modifier.fillMaxWidth().focusRequester(especieFocusRequester),
            imeAction = ImeAction.Next,
            onNext = { cantidadFocusRequester.requestFocus() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        IngresoField(
            value = cantidad,
            onValueChange = { cantidad = it },
            label = "Cantidad (Kg/Unid)",
            modifier = Modifier.fillMaxWidth().focusRequester(cantidadFocusRequester),
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Number,
            onDone = { focusManager.clearFocus() }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.bitecmaTeal)
        ) {
            Text("Guardar Captura")
        }
        }
    }
}

@Composable
fun FormMuestra(onSave: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val tallaFocusRequester = remember { FocusRequester() }
    val pesoFocusRequester = remember { FocusRequester() }
    var sector by remember { mutableStateOf("") }
    var talla by remember { mutableStateOf("") }
    var peso by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.bitecmaCardBackground,
        border = BorderStroke(1.dp, colors.bitecmaBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
        Text("Nueva Muestra Biológica", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.bitecmaNavy)
        Spacer(modifier = Modifier.height(16.dp))
        IngresoField(
            value = sector,
            onValueChange = { sector = it },
            label = "Sector",
            modifier = Modifier.fillMaxWidth(),
            imeAction = ImeAction.Next,
            onNext = { tallaFocusRequester.requestFocus() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        IngresoField(
            value = talla,
            onValueChange = { talla = it },
            label = "Talla (mm)",
            modifier = Modifier.fillMaxWidth().focusRequester(tallaFocusRequester),
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Number,
            onNext = { pesoFocusRequester.requestFocus() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        IngresoField(
            value = peso,
            onValueChange = { peso = it },
            label = "Peso (g)",
            modifier = Modifier.fillMaxWidth().focusRequester(pesoFocusRequester),
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Number,
            onDone = { focusManager.clearFocus() }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.bitecmaTeal)
        ) {
            Text("Guardar Muestra")
        }
        }
    }
}

@Composable
fun FormDensidad(onSave: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val conteoFocusRequester = remember { FocusRequester() }
    var area by remember { mutableStateOf("") }
    var conteo by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.bitecmaCardBackground,
        border = BorderStroke(1.dp, colors.bitecmaBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
        Text("Registro de Densidad", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.bitecmaNavy)
        Spacer(modifier = Modifier.height(16.dp))
        IngresoField(
            value = area,
            onValueChange = { area = it },
            label = "Área (m²)",
            modifier = Modifier.fillMaxWidth(),
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Number,
            onNext = { conteoFocusRequester.requestFocus() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        IngresoField(
            value = conteo,
            onValueChange = { conteo = it },
            label = "Conteo de individuos",
            modifier = Modifier.fillMaxWidth().focusRequester(conteoFocusRequester),
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Number,
            onDone = { focusManager.clearFocus() }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.bitecmaTeal)
        ) {
            Text("Guardar Densidad")
        }
        }
    }
}

@Composable
private fun IngresoInfoCard(
    title: String,
    description: String,
    example: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.bitecmaSoftBackground,
        border = BorderStroke(1.dp, colors.bitecmaBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.bitecmaTealContainer
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.bitecmaTeal,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.bitecmaNavy)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, fontSize = 12.sp, color = colors.bitecmaSubtleText)
                Spacer(modifier = Modifier.height(2.dp))
                Text(example, fontSize = 11.sp, color = colors.bitecmaMutedText)
            }
        }
    }
}

@Composable
private fun IngresoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction,
    keyboardType: KeyboardType = KeyboardType.Text,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = imeAction,
            keyboardType = keyboardType
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() },
            onDone = { onDone() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.bitecmaTeal,
            focusedLabelColor = colors.bitecmaTeal,
            unfocusedBorderColor = colors.bitecmaBorder,
            unfocusedLabelColor = colors.bitecmaMutedText,
            unfocusedContainerColor = colors.bitecmaCardBackground,
            focusedContainerColor = colors.bitecmaCardBackground
        )
    )
}
