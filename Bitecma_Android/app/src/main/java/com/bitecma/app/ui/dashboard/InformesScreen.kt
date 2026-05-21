package com.bitecma.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.core.content.ContextCompat
import com.bitecma.app.data.DataManager
import com.bitecma.app.data.AppState
import com.bitecma.app.network.FileMetaDto
import com.bitecma.app.utils.ExcelExporter
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.RetrofitClient
import com.bitecma.app.network.UploadTextFileRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun DocumentosScreen(navController: NavController, userId: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedOpId by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<FileMetaDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val opIds = remember(DataManager.operacionesBd.size, DataManager.operacionesLc.size) {
        (DataManager.operacionesBd.mapNotNull { it.id } + DataManager.operacionesLc.mapNotNull { it.id }).distinct()
    }

    var pendingSaveName by remember { mutableStateOf<String?>(null) }
    var pendingSaveText by remember { mutableStateOf<String?>(null) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }

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

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val name = pendingSaveName
        if (uri != null && name != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    if (pendingSaveBytes != null) {
                        os.write(pendingSaveBytes)
                    } else if (pendingSaveText != null) {
                        os.write(pendingSaveText!!.toByteArray(Charsets.UTF_8))
                    }
                }
            } catch (_: Exception) {
            }
        }
        pendingSaveName = null
        pendingSaveText = null
        pendingSaveBytes = null
    }

    val saveXlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val name = pendingSaveName
        val bytes = pendingSaveBytes
        if (uri != null && name != null && bytes != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(bytes)
                }
            } catch (_: Exception) {
            }
        }
        pendingSaveName = null
        pendingSaveBytes = null
    }

    val pickTextLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isLoading = true
            error = null
            try {
                val name = run {
                    val c = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    c?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                } ?: "archivo.txt"

                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

                val req = UploadTextFileRequest(name = name, opId = selectedOpId, text = text)

                val canUpload = !AppState.forceOffline && !AppState.authToken.isNullOrBlank()
                val uploaded = if (canUpload) {
                    val up = RetrofitClient.apiService.uploadTextFile(req)
                    if (up.isSuccessful && up.body()?.ok == true) {
                        val refreshed = RetrofitClient.apiService.getFiles(selectedOpId)
                        if (refreshed.isSuccessful && refreshed.body()?.ok == true) {
                            files = refreshed.body()?.data ?: emptyList()
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }

                if (!uploaded) {
                    DataManager.enqueueTextFile(req)
                    DataManager.persistCache(ctx)
                    error = "Guardado local. Se subirá cuando vuelvas a estar online."
                }
            } catch (_: Exception) {
                error = "Guardado local. Se subirá cuando vuelvas a estar online."
                val name = "archivo.txt"
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                val req = UploadTextFileRequest(name = name, opId = selectedOpId, text = text)
                DataManager.enqueueTextFile(req)
                DataManager.persistCache(ctx)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedOpId) {
        isLoading = true
        error = null
        if (AppState.forceOffline || AppState.authToken.isNullOrBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        try {
            val res = RetrofitClient.apiService.getFiles(selectedOpId)
            if (res.isSuccessful) {
                val body = res.body()
                files = if (body?.ok == true) body.data ?: emptyList() else emptyList()
                if (body?.ok != true && body?.error != null) error = body.error
            } else {
                error = "Error del servidor (${res.code()})"
                files = emptyList()
            }
        } catch (_: Exception) {
            error = "Sin conexión"
            files = emptyList()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documentos", color = Color.White) },
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
                .padding(16.dp)
        ) {
            Text("Documentación", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Sube/descarga archivos de texto", color = Color.Gray, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(16.dp))

            var opMenuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { opMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedOpId ?: "General (sin operación)")
                }
                DropdownMenu(expanded = opMenuExpanded, onDismissRequest = { opMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("General (sin operación)") },
                        onClick = { selectedOpId = null; opMenuExpanded = false }
                    )
                    opIds.forEach { id ->
                        DropdownMenuItem(
                            text = { Text(id) },
                            onClick = { selectedOpId = id; opMenuExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val opId = selectedOpId ?: return@Button
                    val op = (DataManager.operacionesBd + DataManager.operacionesLc).find { it.id == opId }
                    if (op != null) {
                        scope.launch {
                            isLoading = true
                            try {
                                val bytes = ExcelExporter.generateOperacionExcel(op)
                                pendingSaveName = "Reporte_${op.id}.xlsx"
                                pendingSaveBytes = bytes
                                saveXlsxLauncher.launch(pendingSaveName!!)
                            } catch (e: Exception) {
                                error = "Error al generar Excel: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    } else {
                        error = "Operación no encontrada localmente"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && selectedOpId != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generar reporte .xlsx")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    pickTextLauncher.launch(arrayOf("text/plain"))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366))
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subir .txt")
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(error ?: "", color = Color.Red, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            if (!isLoading && files.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sin archivos", fontWeight = FontWeight.Bold)
                        Text("Puedes subir un archivo .txt relacionado.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(files) { f ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(f.name, fontWeight = FontWeight.Bold)
                                val meta = listOfNotNull(f.opId, f.createdAt, f.size?.let { "${it}B" }).joinToString(" · ")
                                if (meta.isNotBlank()) Text(meta, fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(
                                enabled = !isLoading,
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        error = null
                                        try {
                                            val res = RetrofitClient.apiService.getFile(f.id)
                                            if (!res.isSuccessful) {
                                                error = "Error del servidor (${res.code()})"
                                                return@launch
                                            }
                                            val body = res.body()
                                            if (body?.ok == true && body.data != null) {
                                                pendingSaveName = body.data.name
                                                pendingSaveText = body.data.text
                                                val doSave = { saveLauncher.launch(body.data.name) }
                                                if (Build.VERSION.SDK_INT <= 28) {
                                                    runWithPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) { doSave() }
                                                } else {
                                                    doSave()
                                                }
                                            } else {
                                                error = body?.error ?: "No se pudo descargar"
                                            }
                                        } catch (_: Exception) {
                                            error = "Sin conexión"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Descargar")
                            }
                        }
                    }
                }
            }
        }
    }
}
