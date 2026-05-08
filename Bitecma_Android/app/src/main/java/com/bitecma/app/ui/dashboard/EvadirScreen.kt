package com.bitecma.app.ui.dashboard
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.bitecma.app.data.DataManager
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.RetrofitClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvadirScreen(navController: NavController, opId: String) {
    val horizontalScrollState = rememberScrollState()

    val ctx = LocalContext.current
    var op by remember { mutableStateOf<OperacionDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastCsvToSave by remember { mutableStateOf<String?>(null) }

    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingPermissionAction?.invoke()
        pendingPermissionAction = null
    }

    fun runWithPermission(permission: String, action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            action()
            return
        }
        val granted = ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
        if (granted) action()
        else {
            pendingPermissionAction = action
            permissionLauncher.launch(permission)
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = lastCsvToSave ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(csv.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {
            }
        }
        lastCsvToSave = null
    }

    LaunchedEffect(opId) {
        isLoading = true
        error = null
        op = null
        try {
            val res = RetrofitClient.apiService.getOperacion(opId)
            if (res.isSuccessful) {
                val body = res.body()
                if (body?.ok == true && body.data != null) {
                    op = body.data
                } else {
                    error = body?.error ?: "No se pudo cargar la operación"
                }
            } else {
                error = "Error del servidor (${res.code()})"
            }
        } catch (_: Exception) {
            error = "Sin conexión"
        } finally {
            isLoading = false
        }
    }

    val especieNombreById = remember(DataManager.especies.size) {
        DataManager.especies.associate { it.id.toString() to it.nombreComun }
    }

    val evadirRows = remember(op, especieNombreById) {
        val out = mutableListOf<Map<String, String>>()
        val current = op ?: return@remember out
        var idx = 1
        for (b in current.botes ?: emptyList()) {
            for (t in b.transectos ?: emptyList()) {
                val fecha = t.fecha ?: current.fechaInicio ?: ""
                for ((spId, cnt) in (t.counts ?: emptyMap())) {
                    if (cnt <= 0) continue
                    out.add(
                        mapOf(
                            "ITEM" to idx.toString(),
                            "FECHA" to fecha,
                            "BOTE" to (b.nombre ?: ""),
                            "ESPECIE" to (especieNombreById[spId] ?: "ID $spId"),
                            "CANT" to cnt.toString(),
                            "UNID" to "N°"
                        )
                    )
                    idx++
                }
            }
        }
        out
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Previsualización EVADIR", 
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        enabled = !isLoading && op != null && evadirRows.isNotEmpty(),
                        onClick = {
                            val header = listOf("ITEM", "FECHA", "BOTE", "ESPECIE", "CANTIDAD", "UNIDAD")
                            val lines = mutableListOf(header.joinToString(","))
                            for (r in evadirRows) {
                                val row = listOf(
                                    r["ITEM"] ?: "",
                                    r["FECHA"] ?: "",
                                    r["BOTE"] ?: "",
                                    r["ESPECIE"] ?: "",
                                    r["CANT"] ?: "",
                                    r["UNID"] ?: ""
                                ).joinToString(",") { it.replace(",", " ") }
                                lines.add(row)
                            }
                            lastCsvToSave = lines.joinToString("\n")
                            val doSave = { createDocLauncher.launch("EVADIR_${opId}.csv") }
                            if (Build.VERSION.SDK_INT <= 28) {
                                runWithPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) { doSave() }
                            } else {
                                doSave()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Descargar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF00897B))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFE0F2F1),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Operación: $opId",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color(0xFF004D40)
                    )
                    Text(
                        text = "Resumen de capturas para exportación a plataforma SERNAPESCA",
                        color = Color(0xFF00796B),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00897B))
                }
                return@Column
            }

            if (error != null) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(error ?: "Error desconocido", color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                return@Column
            }

            if (op == null || evadirRows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin datos disponibles", color = Color.Gray, fontSize = 14.sp)
                }
                return@Column
            }

            // Table Content with Responsive Horizontal Scroll
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Row (Fixed)
                Box(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                    Row(
                        modifier = Modifier
                            .width(1200.dp) // Minimum width for the table to avoid squishing
                            .background(Color(0xFF003366))
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        TableCell("ITEM", weight = 0.5f, isHeader = true)
                        TableCell("BOTE", weight = 1.2f, isHeader = true)
                        TableCell("FECHA", weight = 1.0f, isHeader = true)
                        TableCell("ESPECIE", weight = 1.2f, isHeader = true)
                        TableCell("CANT", weight = 0.7f, isHeader = true)
                        TableCell("UNID", weight = 0.5f, isHeader = true)
                        Spacer(modifier = Modifier.weight(1.0f))
                    }
                }

                // Data Rows (Scrollable)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    items(evadirRows) { row ->
                        Row(
                            modifier = Modifier
                                .width(1200.dp) // Increased width for OP-2026-004 style
                                .background(Color.White)
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableCell(row["ITEM"] ?: "", weight = 0.5f)
                            TableCell(row["BOTE"] ?: "", weight = 1.2f, fontWeight = FontWeight.Bold)
                            TableCell(row["FECHA"] ?: "", weight = 1.0f)
                            TableCell(row["ESPECIE"] ?: "", weight = 1.2f, color = Color(0xFF0D47A1))
                            TableCell(row["CANT"] ?: "", weight = 0.7f, fontWeight = FontWeight.Bold)
                            TableCell(row["UNID"] ?: "", weight = 0.5f)
                            // Additional space for other columns if needed
                            Spacer(modifier = Modifier.weight(1.0f))
                        }
                        HorizontalDivider(modifier = Modifier.width(1200.dp), color = Color(0xFFEEEEEE), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String, 
    weight: Float, 
    isHeader: Boolean = false,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = if (isHeader) Color.White else Color.Black
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 4.dp),
        fontSize = if (isHeader) 12.sp else 13.sp,
        fontWeight = if (isHeader) FontWeight.Bold else fontWeight,
        color = color,
        maxLines = 1
    )
}
