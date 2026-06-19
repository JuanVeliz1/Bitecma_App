@file:Suppress("DEPRECATION")

package com.bitecma.app.ui.dashboard
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import com.bitecma.app.network.*
import com.bitecma.app.data.*
import com.bitecma.app.ui.bitecmaAmberBg
import com.bitecma.app.ui.bitecmaBlueBg
import com.bitecma.app.ui.bitecmaBorder
import com.bitecma.app.ui.bitecmaCardBackground
import com.bitecma.app.ui.bitecmaDangerBg
import com.bitecma.app.ui.bitecmaMutedText
import com.bitecma.app.ui.bitecmaNavy
import com.bitecma.app.ui.bitecmaNavyStrong
import com.bitecma.app.ui.bitecmaPurpleBg
import com.bitecma.app.ui.bitecmaSoftBackground
import com.bitecma.app.ui.bitecmaSoftBackgroundAlt
import com.bitecma.app.ui.bitecmaSubtleText
import com.bitecma.app.ui.bitecmaSuccessBg
import com.bitecma.app.ui.bitecmaTeal
import com.bitecma.app.ui.bitecmaTealContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate

private val TIPOS_ORGANIZACION_DEFAULT = listOf("STI", "ASOC", "OTRO")

private fun operacionCoincideConBusqueda(op: OperacionDto, query: String): Boolean {
    if (query.isBlank()) return true
    val q = normalizarTextoBusqueda(query)
    val texto = buildString {
        append(op.id)
        append(' ')
        append(op.sector)
        append(' ')
        append(op.sectorAmerb.orEmpty())
        append(' ')
        append(op.org.orEmpty())
        append(' ')
        append(op.tipoOrg.orEmpty())
        append(' ')
        append(op.numSeg?.toString().orEmpty())
        append(' ')
        op.botes.orEmpty().forEach { bote ->
            append(bote.nombre.orEmpty())
            append(' ')
            append(bote.buzo.orEmpty())
            append(' ')
        }
    }
    return normalizarTextoBusqueda(texto).contains(q)
}

private fun operacionCoincideConMes(op: OperacionDto, mes: String): Boolean {
    if (mes.isBlank()) return true
    val fi = op.fechaInicio.orEmpty()
    val ff = op.fechaFin.orEmpty()
    return fi.startsWith(mes) || ff.startsWith(mes)
}

@Composable
private fun rememberStableSyncEnabled(rawEnabled: Boolean): Boolean {
    var displayEnabled by remember { mutableStateOf(rawEnabled) }

    LaunchedEffect(rawEnabled) {
        if (rawEnabled) {
            displayEnabled = true
            return@LaunchedEffect
        }
        delay(1500)
        if (!rawEnabled) {
            displayEnabled = false
        }
    }

    return displayEnabled
}

@Composable
private fun GestionBotesDialog(
    show: Boolean,
    operacionId: String?,
    operationRegionRom: String?,
    operationCaleta: String?,
    botesList: SnapshotStateList<OperacionBoteDto>,
    botesMaestros: List<BoteMaestroDto>,
    validationError: String?,
    onValidationErrorChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    onAddRow: () -> Unit,
    onSave: () -> Unit,
) {
    ExtractedGestionBotesDialog(
        show = show,
        operacionId = operacionId,
        operationRegionRom = operationRegionRom,
        operationCaleta = operationCaleta,
        botesList = botesList,
        botesMaestros = botesMaestros,
        validationError = validationError,
        onValidationErrorChange = onValidationErrorChange,
        onDismiss = onDismiss,
        onAddRow = onAddRow,
        onSave = onSave,
    )
}


@Composable
private fun TransectosDialog(
    show: Boolean,
    currentBote: OperacionBoteDto?,
    especiesMaestras: List<EspecieDto>,
    transectosList: SnapshotStateList<DensidadUnidadDto>,
    initialTab: String = "DENSIDAD",
    onDismiss: () -> Unit,
    onSaveDensity: (List<DensidadUnidadDto>) -> Unit,
    onSaveLp: () -> Unit,
    onUpdateLpSamples: (Int, String, List<LpSampleDto>) -> Unit,
    onRemoveLpSpecies: (Int) -> Unit,
) {
    ExtractedTransectosDialog(
        show = show,
        currentBote = currentBote,
        especiesMaestras = especiesMaestras,
        transectosList = transectosList,
        initialTab = initialTab,
        onDismiss = onDismiss,
        onSaveDensity = onSaveDensity,
        onSaveLp = onSaveLp,
        onUpdateLpSamples = onUpdateLpSamples,
        onRemoveLpSpecies = onRemoveLpSpecies,
    )
}

@Composable
private fun NuevaOperacionDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    regiones: List<RegionDto>,
    regionLabelById: Map<Int, String>,
    selectedRegionId: Int?,
    onRegionSelected: (Int?) -> Unit,
    sectorAmerbInput: String,
    onSectorAmerbInputChange: (String) -> Unit,
    numSeguimiento: String,
    onNumSeguimientoChange: (String) -> Unit,
    fechaInicio: String,
    onFechaInicioClick: () -> Unit,
    fechaFin: String,
    onFechaFinClick: () -> Unit,
    caletaInput: String,
    onCaletaInputChange: (String) -> Unit,
    onCaletaSelected: (String) -> Unit,
    tipoOrg: String,
    onTipoOrgChange: (String) -> Unit,
    opaInput: String,
    onOpaInputChange: (String) -> Unit,
    validationError: String?,
    onCreateClick: () -> Unit,
) {
    ExtractedNuevaOperacionDialog(
        show = show,
        onDismiss = onDismiss,
        regiones = regiones,
        regionLabelById = regionLabelById,
        selectedRegionId = selectedRegionId,
        onRegionSelected = onRegionSelected,
        sectorAmerbInput = sectorAmerbInput,
        onSectorAmerbInputChange = onSectorAmerbInputChange,
        numSeguimiento = numSeguimiento,
        onNumSeguimientoChange = onNumSeguimientoChange,
        fechaInicio = fechaInicio,
        onFechaInicioClick = onFechaInicioClick,
        fechaFin = fechaFin,
        onFechaFinClick = onFechaFinClick,
        caletaInput = caletaInput,
        onCaletaInputChange = onCaletaInputChange,
        onCaletaSelected = onCaletaSelected,
        tipoOrg = tipoOrg,
        onTipoOrgChange = onTipoOrgChange,
        opaInput = opaInput,
        onOpaInputChange = onOpaInputChange,
        validationError = validationError,
        onCreateClick = onCreateClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperacionesScreen(navController: NavController, userId: Int) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var isSyncingAll by remember { mutableStateOf(false) }
    var expandedOpId by remember { mutableStateOf<String?>(null) }
    var lastExpandClickAt by remember { mutableLongStateOf(0L) }
    var showOperacionDetalleDialog by remember { mutableStateOf(false) }
    var currentOperacionDetalle by remember { mutableStateOf<OperacionDto?>(null) }
    var currentOperacionDetalleBoteKey by remember { mutableStateOf<String?>(null) }
    var pendingDeleteOperacion by remember { mutableStateOf<OperacionItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showBotesDialog by remember { mutableStateOf(false) }
    var showBoteResumenDialog by remember { mutableStateOf(false) }
    var showSpeciesDialog by remember { mutableStateOf(false) }
    var showTransectDialog by remember { mutableStateOf(false) }   
    var currentOpForBotes by remember { mutableStateOf<OperacionDto?>(null) }
    var currentBoteForData by remember { mutableStateOf<OperacionBoteDto?>(null) }
    var pendingDataTab by remember { mutableStateOf("DENSIDAD") }
    val botesList = remember { mutableStateListOf<OperacionBoteDto>() }
    val selectedSpeciesIds = remember { mutableStateListOf<Int>() }
    val muestreoBySpeciesId = remember { mutableStateMapOf<Int, Set<String>>() }
    val transectosList = remember { mutableStateListOf<DensidadUnidadDto>() }
    var especiesMaestras by remember { mutableStateOf(DataManager.especiesMaestras.toList()) }
    var botesMaestros by remember { mutableStateOf(DataManager.botesMaestros.toList()) }
    var regiones by remember { mutableStateOf(DataManager.regiones.toList()) }
    var regionLabelById by remember(regiones) { mutableStateOf(regiones.associate { r -> r.id to listOfNotNull(r.rom, r.nom).joinToString(" — ").ifBlank { "Región ${r.id}" } }) }
    // Listas dinámicas desde API
    var sectoresAmerbApi by remember { mutableStateOf(DataManager.sectoresAmerb.toList()) }
    var caletasApi by remember { mutableStateOf(DataManager.caletas.toList()) }
    var opasApi by remember { mutableStateOf(DataManager.opas.toList()) }
    var selectedRegionId by remember { mutableStateOf<Int?>(1) }
    var numSeguimiento by remember { mutableStateOf("") }
    var sectorAmerbInput by remember { mutableStateOf("") }
    var caletaInput by remember { mutableStateOf("") }
    var selectedCaleta by remember { mutableStateOf<String?>(null) }
    var tipoOrg by remember { mutableStateOf("STI") }
    var opaInput by remember { mutableStateOf("") }
    var fechaInicio by remember { mutableStateOf("") }
    var fechaFin by remember { mutableStateOf("") }
    var showInicioDatePicker by remember { mutableStateOf(false) }
    var showFinDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var textoBusqueda by remember { mutableStateOf("") }
    var filtroSector by remember { mutableStateOf("") }
    var filtroMes by remember { mutableStateOf("") }

    val nomenclatura = remember(selectedRegionId, numSeguimiento) {
        val year = LocalDate.now().year
        "OP-$year-${numSeguimiento.ifEmpty { "XXX" }}"
    }
    val sectorAmerbNombreActual = sectorAmerbInput.trim()
    val caletaActual = selectedCaleta?.takeIf { it.isNotBlank() } ?: caletaInput.trim()
    val organizacionActual = opaInput.trim()

    val operacionesUi by remember {
        derivedStateOf {
            val merged = linkedMapOf<String, OperacionItem>()
            (DataManager.operacionesBd.map { OperacionItem(it, OperacionSource.BD) } +
                DataManager.operacionesLc.map { OperacionItem(it, OperacionSource.LC) })
                .forEach { item ->
                    merged[item.op.id] = merged[item.op.id]
                        ?.let { existing -> mergeOperacionItemForUi(existing, item) }
                        ?: item
                }
            merged.values.toList()
        }
    }
    val sectoresDisponibles by remember(operacionesUi) {
        derivedStateOf {
            operacionesUi.mapNotNull { item ->
                item.op.sectorAmerb?.takeIf { it.isNotBlank() } ?: item.op.sector.takeIf { it.isNotBlank() }
            }.distinct().sorted()
        }
    }
    val mesesDisponibles by remember(operacionesUi) {
        derivedStateOf {
            operacionesUi.flatMap { item ->
                listOfNotNull(item.op.fechaInicio, item.op.fechaFin)
                    .mapNotNull { fecha -> fecha.takeIf { it.length >= 7 && it[4] == '-' }?.substring(0, 7) }
            }.distinct().sortedDescending()
        }
    }
    val operacionesFiltradas by remember(operacionesUi, textoBusqueda, filtroSector, filtroMes) {
        derivedStateOf {
            operacionesUi.filter { item ->
                val op = item.op
                val sectorOp = op.sectorAmerb?.takeIf { it.isNotBlank() } ?: op.sector
                val coincideSector = filtroSector.isBlank() || sectorOp == filtroSector
                coincideSector &&
                    operacionCoincideConMes(op, filtroMes) &&
                    operacionCoincideConBusqueda(op, textoBusqueda)
            }
        }
    }
    val canSyncUi = rememberStableSyncEnabled(AppState.isEffectivelyOnline())
    LaunchedEffect(currentBoteForData?.id, currentBoteForData?.zona, currentBoteForData?.lpMuestras, currentBoteForData?.transectos) {
        val bote = currentBoteForData ?: return@LaunchedEffect
        prepararEstadoEdicionBote(
            bote = bote,
            selectedSpeciesIds = selectedSpeciesIds,
            muestreoBySpeciesId = muestreoBySpeciesId,
            transectosList = transectosList,
        )
    }
    val aplicarCatalogoActual: () -> Unit = {
        val catalogo = DataManager.getCatalogSnapshot()
        regiones = catalogo.regiones
        botesMaestros = catalogo.botesMaestros
        especiesMaestras = catalogo.especiesMaestras
        sectoresAmerbApi = catalogo.sectoresAmerb
        caletasApi = catalogo.caletas
        opasApi = catalogo.opas
        regionLabelById = regiones.associate { r -> r.id to listOfNotNull(r.rom, r.nom).joinToString(" — ").ifBlank { "Región ${r.id}" } }
        if (selectedRegionId == null) {
            selectedRegionId = regiones.firstOrNull()?.id
        }
    }
    val syncAndRefreshCatalogs: suspend () -> Unit = syncRefresh@{
        if (isSyncingAll || !AppState.isEffectivelyOnline()) return@syncRefresh
        isSyncingAll = true
        try {
            DataManager.syncAllFromServer(ctx)
            aplicarCatalogoActual()
        } finally {
            isSyncingAll = false
        }
    }

    // Carga inicial de datos
    LaunchedEffect(isLoading, userId) {
        if (!isLoading) return@LaunchedEffect
        if (AppState.forceOffline || AppState.authToken.isNullOrBlank()) {
            aplicarCatalogoActual()
            isLoading = false
            return@LaunchedEffect
        }

        runCatching {
            DataManager.syncAllFromServer(ctx)
        }

        aplicarCatalogoActual()
        isLoading = false
    }

    if (showBoteResumenDialog) {
        BoteDataResumenDialog(
            bote = currentBoteForData,
            onDismiss = { showBoteResumenDialog = false },
            onOpenDensity = {
                showBoteResumenDialog = false
                pendingDataTab = "DENSIDAD"
                val hasDensitySpecies = selectedSpeciesIds.any { sid ->
                    muestreoBySpeciesId[sid]?.contains("DENSIDAD") != false
                }
                if (!hasDensitySpecies) {
                    showSpeciesDialog = true
                } else {
                    if (transectosList.isEmpty()) {
                        transectosList.add(
                            DensidadUnidadDto(
                                num = 1,
                                tipo = currentBoteForData?.densTipo ?: "Transecto",
                                area = 120.0,
                                counts = densityCountsForDraft(currentBoteForData),
                            )
                        )
                    }
                    showTransectDialog = true
                }
            },
            onOpenLp = {
                showBoteResumenDialog = false
                pendingDataTab = "LP"
                val hasLpSpecies = selectedSpeciesIds.any { sid ->
                    muestreoBySpeciesId[sid]?.contains("L-P") != false
                }
                if (!hasLpSpecies) {
                    showSpeciesDialog = true
                } else {
                    showTransectDialog = true
                }
            },
            onOpenSpecies = {
                showBoteResumenDialog = false
                showSpeciesDialog = true
            }
        )
    }

    ExtractedOperacionSpeciesDialog(
        show = showSpeciesDialog,
        boteNombre = currentBoteForData?.nombre,
        especiesMaestras = especiesMaestras,
        selectedSpeciesIds = selectedSpeciesIds,
        muestreoBySpeciesId = muestreoBySpeciesId,
        onDismiss = { showSpeciesDialog = false },
        onContinue = {
            val currentBote = currentBoteForData
            val currentOp = currentOpForBotes
            if (currentBote != null && currentOp != null) {
                val lpIds = selectedSpeciesIds.filter { sid -> muestreoBySpeciesId[sid]?.contains("L-P") != false }
                if (lpIds.isNotEmpty()) {
                    val lpM = (currentBote.lpMuestras ?: emptyMap()).toMutableMap()
                    lpIds.forEach { sid ->
                        lpM.putIfAbsent(sid.toString(), emptyMap())
                    }
                    val updatedBote = currentBote.copy(lpMuestras = lpM.toMap())
                    currentBoteForData = updatedBote

                    val updatedBotes = (currentOp.botes ?: emptyList()).toMutableList()
                    val idx = updatedBotes.indexOfFirst { sameBoteIdentity(it, currentBote) }
                    if (idx >= 0) updatedBotes[idx] = updatedBote else updatedBotes.add(updatedBote)
                    val updatedOp = currentOp.copy(botes = updatedBotes)
                    currentOpForBotes = updatedOp
                    persistOperacionSnapshot(ctx, updatedOp)
                    trySyncOperacionSnapshot(scope, ctx, updatedOp) { syncedOp ->
                        currentOpForBotes = syncedOp
                    }
                }
            }

            showSpeciesDialog = false
            if (pendingDataTab == "DENSIDAD" && transectosList.isEmpty()) {
                transectosList.add(
                    DensidadUnidadDto(
                        num = 1,
                        tipo = currentBoteForData?.densTipo ?: "Transecto",
                        area = 120.0,
                        counts = densityCountsForDraft(currentBoteForData),
                    )
                )
            }
            showTransectDialog = true
        },
    )

    // --- DIALOGO DE AGREGAR TRANSECTOS ---
    TransectosDialog(
        show = showTransectDialog,
        currentBote = currentBoteForData,
        especiesMaestras = especiesMaestras,
        transectosList = transectosList,
        initialTab = pendingDataTab,
        onDismiss = { showTransectDialog = false },
        onSaveDensity = saveDensity@{ normalizedTransectos ->
            val currentBote = currentBoteForData ?: return@saveDensity
            val currentOp = currentOpForBotes ?: return@saveDensity

            val updatedBote = syncLpSpeciesWithDensity(
                bote = currentBote.copy(transectos = normalizedTransectos),
                especiesById = especiesMaestras.associateBy { it.id },
            )
            val bIndex = botesList.indexOfFirst { sameBoteIdentity(it, currentBote) }
            if (bIndex >= 0) {
                botesList[bIndex] = updatedBote
            }

            val updatedBotes = (currentOp.botes ?: emptyList()).toMutableList()
            val opBoteIdx = updatedBotes.indexOfFirst { sameBoteIdentity(it, currentBote) }
            if (opBoteIdx >= 0) {
                updatedBotes[opBoteIdx] = updatedBote
            } else {
                updatedBotes.add(updatedBote)
            }

            val updatedBotesApi = updatedBotes.map { b ->
                val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                b.copy(
                    densTipo = dens,
                    transectos = b.transectos?.map { t ->
                        t.copy(tipo = if (dens == "cuadrante") "cuadrante" else "transecto")
                    }
                )
            }

            val updatedOp = currentOp.copy(botes = updatedBotesApi)
            currentOpForBotes = updatedOp
            currentBoteForData = updatedBote
            persistOperacionSnapshot(ctx, updatedOp)
            showTransectDialog = false

            trySyncOperacionSnapshot(scope, ctx, updatedOp) { syncedOp ->
                currentOpForBotes = syncedOp
            }
        },
        onSaveLp = saveLp@{
            val currentOp = currentOpForBotes ?: return@saveLp
            persistOperacionSnapshot(ctx, currentOp)
            showTransectDialog = false

            trySyncOperacionSnapshot(scope, ctx, currentOp) { syncedOp ->
                currentOpForBotes = syncedOp
            }
        },
        onUpdateLpSamples = updateLpSamples@{ speciesId, sampleKind, nextSamples ->
            val op = currentOpForBotes ?: return@updateLpSamples
            val curr = currentBoteForData ?: return@updateLpSamples
            val nextBotes = replaceMatchingBote(op.botes ?: emptyList(), curr) { b ->
                    val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                    val buckets = (lpM[speciesId.toString()] ?: emptyMap()).toMutableMap()
                    buckets[normalizeLpKind(sampleKind)] = nextSamples
                    lpM[speciesId.toString()] = buckets.toMap()
                    b.copy(lpMuestras = lpM.toMap())
            }
            val nextOp = op.copy(botes = nextBotes)
            currentOpForBotes = nextOp
            persistOperacionSnapshot(ctx, nextOp)
            trySyncOperacionSnapshot(scope, ctx, nextOp) { syncedOp ->
                currentOpForBotes = syncedOp
            }
            currentBoteForData = nextBotes.firstOrNull { sameBoteIdentity(it, curr) } ?: currentBoteForData
        },
        onRemoveLpSpecies = removeLpSpecies@{ speciesId ->
            val op = currentOpForBotes ?: return@removeLpSpecies
            val curr = currentBoteForData ?: return@removeLpSpecies
            val nextBotes = replaceMatchingBote(op.botes ?: emptyList(), curr) { b ->
                    val lpM = (b.lpMuestras ?: emptyMap()).toMutableMap()
                    lpM.remove(speciesId.toString())
                    b.copy(lpMuestras = lpM.toMap())
            }
            val nextOp = op.copy(botes = nextBotes)
            currentOpForBotes = nextOp
            persistOperacionSnapshot(ctx, nextOp)
            trySyncOperacionSnapshot(scope, ctx, nextOp) { syncedOp ->
                currentOpForBotes = syncedOp
            }
            currentBoteForData = nextBotes.firstOrNull { sameBoteIdentity(it, curr) } ?: currentBoteForData
        }
    )

    // --- DIALOGO DE NUEVA OPERACIÓN ---
    NuevaOperacionDialog(
        show = showAddDialog,
        onDismiss = {
            showAddDialog = false
            validationError = null
        },
        regiones = regiones,
        regionLabelById = regionLabelById,
        selectedRegionId = selectedRegionId,
        onRegionSelected = {
            selectedRegionId = it
            sectorAmerbInput = ""
            caletaInput = ""
            selectedCaleta = null
            opaInput = ""
            validationError = null
        },
        sectorAmerbInput = sectorAmerbInput,
        onSectorAmerbInputChange = { sectorAmerbInput = it },
        numSeguimiento = numSeguimiento,
        onNumSeguimientoChange = { numSeguimiento = it },
        fechaInicio = fechaInicio,
        onFechaInicioClick = { showInicioDatePicker = true },
        fechaFin = fechaFin,
        onFechaFinClick = { showFinDatePicker = true },
        caletaInput = caletaInput,
        onCaletaInputChange = {
            caletaInput = it
            if (!it.equals(selectedCaleta.orEmpty(), ignoreCase = true)) {
                selectedCaleta = null
            }
        },
        onCaletaSelected = {
            selectedCaleta = it
            caletaInput = it
        },
        tipoOrg = tipoOrg,
        onTipoOrgChange = { tipoOrg = it },
        opaInput = opaInput,
        onOpaInputChange = { opaInput = it },
        validationError = validationError,
        onCreateClick = {
            val error = validarNuevaOperacion(
                selectedRegionId = selectedRegionId,
                sectorAmerb = sectorAmerbNombreActual,
                caleta = caletaActual,
                tipoOrg = tipoOrg,
                organizacion = organizacionActual,
                numSeguimiento = numSeguimiento,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin
            )
            if (error != null) {
                validationError = error
            } else {
                validationError = null
                showConfirmDialog = true
            }
        }
    )

    // --- DIALOGO DE CONFIRMACIÓN ---
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirmar creación") },
            text = { Text("¿Está seguro que quiere crear esta \"$nomenclatura\"?\nSi es así, presione \"SI\", de lo contrario \"NO\".") },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    val req = OperacionUpsertRequest(
                        id = nomenclatura,
                        region = selectedRegionId,
                        sector = caletaActual,
                        sectorAmerbId = null,
                        sectorAmerb = sectorAmerbNombreActual,
                        tipoOrg = tipoOrg,
                        opaId = null,
                        org = organizacionActual,
                        numSeg = numSeguimiento.toIntOrNull(),
                        fechaInicio = fechaInicio.ifBlank { null },
                        fechaFin = fechaFin.ifBlank { null }
                    )

                    scope.launch {
                        val finalOp = DataManager.createOperacion(ctx, req)

                        showAddDialog = false
                        // Reset de campos para la próxima creación
                        selectedRegionId = regiones.firstOrNull()?.id
                        sectorAmerbInput = ""
                        numSeguimiento = ""
                        fechaInicio = todayIso()
                        fechaFin = todayIso()
                        caletaInput = ""
                        selectedCaleta = null
                        tipoOrg = "STI"
                        opaInput = ""

                        currentOpForBotes = finalOp
                        botesList.clear()
                        botesList.addAll(createDefaultBoteRows())
                        showBotesDialog = true
                    }
                }) { Text("SI") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("NO") }
            }
        )
    }

    // --- DIALOGO DE BOTES ---
    GestionBotesDialog(
        show = showBotesDialog,
        operacionId = currentOpForBotes?.id,
        operationRegionRom = currentOpForBotes
            ?.let { inferOperacionRegionId(it, sectoresAmerbApi, caletasApi, opasApi) }
            ?.let { rid -> regiones.find { it.id == rid }?.rom },
        operationCaleta = currentOpForBotes?.sector,
        botesList = botesList,
        botesMaestros = botesMaestros,
        validationError = validationError,
        onValidationErrorChange = { validationError = it },
        onDismiss = { showBotesDialog = false },
        onAddRow = {
            botesList.add(
                OperacionBoteDto(
                    zona = (botesList.maxOfOrNull { it.zona ?: 0 } ?: 0) + 1,
                    densTipo = "Transecto",
                    submareal = 1,
                )
            )
        },
        onSave = {
            val op = currentOpForBotes ?: return@GestionBotesDialog
            val updatedBotesUi = botesList.toList()
            val error = validarBotes(updatedBotesUi)
            if (error != null) {
                validationError = error
                return@GestionBotesDialog
            }

            validationError = null
            val updatedBotesApi = updatedBotesUi.map { b ->
                val dens = if (b.densTipo.equals("Cuadrante", true) || b.densTipo.equals("cuadrante", true)) "cuadrante" else "transecto"
                val isIntermareal = b.submareal == 0 || b.nombre?.equals("Intermareal", ignoreCase = true) == true
                b.copy(
                    densTipo = dens,
                    submareal = if (isIntermareal) 0 else 1,
                    boteMaestroId = if (isIntermareal) null else b.boteMaestroId,
                    nombre = if (isIntermareal) "Intermareal" else b.nombre,
                    transectos = b.transectos?.map { t ->
                        t.copy(tipo = if (dens == "cuadrante") "cuadrante" else "transecto")
                    }
                )
            }

            val localOp = op.copy(botes = updatedBotesApi)
            val firstBoteToEdit = updatedBotesApi
                .sortedWith(compareBy({ it.zona ?: Int.MAX_VALUE }, { it.nombre ?: "" }))
                .firstOrNull()
            currentOpForBotes = localOp
            persistOperacionSnapshot(ctx, localOp)
            showBotesDialog = false
            currentOperacionDetalle = localOp
            currentOperacionDetalleBoteKey = firstBoteToEdit?.let { boteSelectionKey(it) }
            showOperacionDetalleDialog = firstBoteToEdit != null

            trySyncOperacionSnapshot(scope, ctx, localOp) { syncedOp ->
                currentOpForBotes = syncedOp
                if (showOperacionDetalleDialog && currentOperacionDetalle?.id == syncedOp.id) {
                    currentOperacionDetalle = syncedOp
                    currentOperacionDetalleBoteKey = firstBoteToEdit
                        ?.let { savedBote ->
                            syncedOp.botes
                                ?.firstOrNull { sameBoteIdentity(it, savedBote) }
                                ?.let { boteSelectionKey(it) }
                        } ?: currentOperacionDetalleBoteKey
                }
            }
        }
    )

    if (showInicioDatePicker) {
        MyDatePickerDialog(onDateSelected = { fechaInicio = it; showInicioDatePicker = false }, onDismiss = { showInicioDatePicker = false })
    }
    if (showFinDatePicker) {
        MyDatePickerDialog(onDateSelected = { fechaFin = it; showFinDatePicker = false }, onDismiss = { showFinDatePicker = false })
    }

    if (showOperacionDetalleDialog && currentOperacionDetalle != null) {
        OperacionDataDialog(
            opInitial = currentOperacionDetalle!!,
            regionLabel = currentOperacionDetalle
                ?.let { inferOperacionRegionId(it, sectoresAmerbApi, caletasApi, opasApi) }
                ?.let { regionLabelById[it] },
            especiesMaestras = especiesMaestras,
            initialSelectedBoteKey = currentOperacionDetalleBoteKey,
            onOperacionUpdated = { updatedOp ->
                currentOperacionDetalle = updatedOp
                if (currentOpForBotes?.id == updatedOp.id) {
                    currentOpForBotes = updatedOp
                }
            },
            onDismiss = {
                showOperacionDetalleDialog = false
                currentOperacionDetalleBoteKey = null
            }
        )
    }

    pendingDeleteOperacion?.let { itemToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteOperacion = null },
            title = { Text("Confirmar eliminación") },
            text = {
                Text(
                    "Vas a eliminar la operación ${itemToDelete.op.id}. Esta acción también puede borrar sus datos en la web y no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteOperacion = null
                        scope.launch {
                            DataManager.deleteOperacion(ctx, itemToDelete.op.id, itemToDelete.source)
                        }
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteOperacion = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Operaciones", color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (canSyncUi) Color(0xFF4CAF50) else Color.Gray, androidx.compose.foundation.shape.CircleShape)
                                .border(1.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { isLoading = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = Color.White)
                    }
                    IconButton(
                        enabled = canSyncUi && !isSyncingAll,
                        onClick = {
                            scope.launch {
                                syncAndRefreshCatalogs()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sincronizar todo", tint = Color.White)
                    }
                    IconButton(onClick = {
                        if (selectedRegionId == null) {
                            selectedRegionId = regiones.firstOrNull()?.id
                        }
                        if (fechaInicio.isBlank()) fechaInicio = todayIso()
                        if (fechaFin.isBlank()) fechaFin = todayIso()
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Nueva", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val grouped = remember(operacionesFiltradas, sectoresAmerbApi, caletasApi, opasApi) {
                operacionesFiltradas
                    .groupBy { inferOperacionRegionId(it.op, sectoresAmerbApi, caletasApi, opasApi) }
                    .toList()
                    .sortedWith(compareBy({ it.first ?: Int.MAX_VALUE }, { it.first ?: Int.MAX_VALUE }))
            }
            val regionExpanded = remember { mutableStateMapOf<String, Boolean>() }
            LaunchedEffect(grouped.size) {
                grouped.forEach { pair ->
                    val regionKey = pair.first?.toString() ?: "null"
                    if (!regionExpanded.containsKey(regionKey)) {
                        regionExpanded[regionKey] = false
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                item {
                    Text("Operaciones", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Cada operación agrupa botes con sus datos técnicos", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OperacionesFiltersHeader(
                        textoBusqueda = textoBusqueda,
                        onTextoBusquedaChange = { textoBusqueda = it },
                        filtroSector = filtroSector,
                        onFiltroSectorChange = { filtroSector = it },
                        sectoresDisponibles = sectoresDisponibles,
                        filtroMes = filtroMes,
                        onFiltroMesChange = { filtroMes = it },
                        mesesDisponibles = mesesDisponibles,
                        totalOperaciones = operacionesFiltradas.size,
                        canSync = canSyncUi && !isSyncingAll,
                        onSyncClick = { scope.launch { syncAndRefreshCatalogs() } },
                    )
                }
                if (grouped.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Sin resultados", fontWeight = FontWeight.Bold)
                                Text(
                                    "No hay operaciones que coincidan con los filtros actuales.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                grouped.forEach { (regionId, ops) ->
                    val regionKey = regionId?.toString() ?: "null"
                    item {
                        OperacionesRegionHeader(
                            titulo = regionId?.let { regionLabelById[it] } ?: "Sin región",
                            totalOperaciones = ops.size,
                            isExpanded = regionExpanded[regionKey] ?: false,
                            onToggle = { regionExpanded[regionKey] = !(regionExpanded[regionKey] ?: false) },
                        )
                    }

                    if (regionExpanded[regionKey] == true) {
                        items(ops, key = { "${it.source}-${it.op.id}" }) { item ->
                            val itemActual = operacionesUi.firstOrNull { it.op.id == item.op.id } ?: item
                            OperacionCard(
                                item = itemActual,
                                isExpanded = expandedOpId == itemActual.op.id,
                                onExpandClick = {
                                    if (expandedOpId == itemActual.op.id) {
                                        expandedOpId = null
                                    } else {
                                        lastExpandClickAt = System.currentTimeMillis()
                                        expandedOpId = itemActual.op.id
                                        if (itemActual.op.botes.isNullOrEmpty() && AppState.isEffectivelyOnline()) scope.launch {
                                            val refreshed = runCatching {
                                                DataManager.refreshOperacionDetail(ctx, itemActual.op.id)
                                            }.getOrNull()
                                            if (refreshed != null) {
                                                currentOpForBotes = if (currentOpForBotes?.id == refreshed.id) refreshed else currentOpForBotes
                                            }
                                        }
                                    }
                                },
                                onEditBotesClick = {
                                    currentOpForBotes = itemActual.op
                                    botesList.clear()
                                    botesList.addAll(itemActual.op.botes ?: emptyList())
                                    showBotesDialog = true
                                },
                                onEditDataClick = { bote ->
                                    if (System.currentTimeMillis() - lastExpandClickAt < 350L) {
                                        return@OperacionCard
                                    }
                                    currentOperacionDetalle = itemActual.op
                                    currentOperacionDetalleBoteKey = boteSelectionKey(bote)
                                    showOperacionDetalleDialog = true
                                },
                                onDeleteClick = {
                                    pendingDeleteOperacion = itemActual
                                },
                                onUploadLocalClick = {
                                    if (itemActual.source != OperacionSource.LC) return@OperacionCard
                                    if (!AppState.isEffectivelyOnline()) return@OperacionCard
                                    scope.launch {
                                        val ok = DataManager.tryUploadOperacion(ctx, itemActual.op)
                                        if (!ok) {
                                            DataManager.markOperacionDirty(itemActual.op.id)
                                            DataManager.persistCache(ctx)
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeciesGridItem(especie: EspecieDto, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.clickable { onClick() }.height(92.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colors.bitecmaTeal else colors.bitecmaBorder
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colors.bitecmaTealContainer else colors.bitecmaSoftBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    especie.com, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 13.sp,
                    maxLines = 2, 
                    textAlign = TextAlign.Center,
                    color = if (isSelected) colors.bitecmaTeal else colors.bitecmaNavyStrong
                )
                Text(
                    especie.sci ?: "", 
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, 
                    fontSize = 9.sp,
                    color = colors.bitecmaMutedText, 
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle, 
                    null, 
                    tint = colors.bitecmaTeal, 
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun BoteTipoToggleButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    icon: ImageVector,
    activeColor: Color,
    inactiveTextColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeColor else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) activeColor else borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (selected) Color.White else inactiveTextColor
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else inactiveTextColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun BoteRowItem(
    index: Int,
    bote: OperacionBoteDto,
    isSearchActive: Boolean,
    onOpenSearch: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (OperacionBoteDto) -> Unit
) {
    var showUnitWarning by remember { mutableStateOf(false) }
    var nextUnitType by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val zonaFocusRequester = remember { FocusRequester() }
    val buzoFocusRequester = remember { FocusRequester() }

    val isDark = isSystemInDarkTheme()
    val comboBg = if (isDark) Color(0xFF0D47A1) else Color.White
    val comboText = if (isDark) Color.White else Color.Black
    val comboBorder = if (isDark) Color(0xFF1976D2) else Color(0xFFF1F3F5)
    val labelColor = if (isDark) Color(0xFFBBDEFB) else Color.Gray
    val isIntermareal = bote.submareal == 0 || bote.nombre?.equals("Intermareal", ignoreCase = true) == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(index.toString(), Modifier.weight(0.3f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.LightGray else Color.Gray)

        BasicTextField(
            value = bote.zona?.toString() ?: "",
            onValueChange = { onUpdate(bote.copy(zona = it.toIntOrNull())) },
            modifier = Modifier
                .weight(0.75f)
                .focusRequester(zonaFocusRequester)
                .padding(horizontal = 4.dp)
                .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                .background(comboBg, RoundedCornerShape(8.dp))
                .padding(10.dp),
            textStyle = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = comboText),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { buzoFocusRequester.requestFocus() }
            )
        )

        Box(Modifier.weight(1.2f).padding(horizontal = 4.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                    .background(comboBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoteTipoToggleButton(
                        modifier = Modifier.weight(1f),
                        selected = !isIntermareal,
                        label = "Sub",
                        icon = Icons.Default.DirectionsBoat,
                        activeColor = Color(0xFF003366),
                        inactiveTextColor = comboText,
                        borderColor = comboBorder,
                        onClick = {
                            onUpdate(
                                bote.copy(
                                    submareal = 1,
                                    nombre = if (bote.nombre.equals("Intermareal", true)) null else bote.nombre,
                                )
                            )
                        }
                    )
                    BoteTipoToggleButton(
                        modifier = Modifier.weight(1f),
                        selected = isIntermareal,
                        label = "Inter",
                        icon = Icons.Default.DirectionsWalk,
                        activeColor = Color(0xFF5E35B1),
                        inactiveTextColor = comboText,
                        borderColor = comboBorder,
                        onClick = {
                            onUpdate(
                                bote.copy(
                                    submareal = 0,
                                    nombre = "Intermareal",
                                    boteMaestroId = null,
                                )
                            )
                        }
                    )
                }
            }
        }

        Box(Modifier.weight(1.55f).padding(horizontal = 4.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, if (isSearchActive) Color(0xFF00897B) else comboBorder, RoundedCornerShape(8.dp))
                    .background(comboBg, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                color = Color.Transparent
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isIntermareal) {
                        Text(
                            text = "Intermareal",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = comboText,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        BasicTextField(
                            value = bote.nombre.orEmpty(),
                            onValueChange = {
                                onUpdate(
                                    bote.copy(
                                        nombre = it,
                                        boteMaestroId = null,
                                        submareal = 1,
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = comboText,
                            ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (bote.nombre.isNullOrBlank()) {
                                        Text("Nombre bote", color = labelColor, fontSize = 13.sp)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                    if (!isIntermareal) {
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Buscar bote maestro",
                                tint = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.Waves,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFB39DDB) else Color(0xFF5E35B1),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        BasicTextField(
            value = bote.buzo ?: "",
            onValueChange = { onUpdate(bote.copy(buzo = it)) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(buzoFocusRequester)
                .padding(horizontal = 4.dp)
                .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                .background(comboBg, RoundedCornerShape(8.dp))
                .padding(10.dp),
            textStyle = TextStyle(fontSize = 13.sp, color = comboText),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (bote.buzo.isNullOrBlank()) {
                        Text("Nombre buzo", color = labelColor, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            }
        )

        var expandedUni by remember { mutableStateOf(false) }
        Box(Modifier.weight(0.95f).padding(horizontal = 4.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedUni = true }
                    .border(1.5.dp, comboBorder, RoundedCornerShape(8.dp))
                    .background(comboBg, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                color = Color.Transparent
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(densTipoLabel(bote.densTipo), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = comboText, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null, tint = labelColor, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(
                expanded = expandedUni,
                onDismissRequest = { expandedUni = false },
                modifier = Modifier.background(comboBg)
            ) {
                listOf("Transecto", "Cuadrante").forEach { u ->
                    DropdownMenuItem(
                        text = { Text(u, fontSize = 13.sp, color = comboText) },
                        onClick = {
                            if (bote.densTipo != u && !bote.transectos.isNullOrEmpty()) {
                                nextUnitType = u
                                showUnitWarning = true
                            } else {
                                onUpdate(bote.copy(densTipo = u))
                            }
                            expandedUni = false
                        }
                    )
                }
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }

    if (showUnitWarning) {
        AlertDialog(
            onDismissRequest = { showUnitWarning = false },
            title = { Text("BITECMA Dice:", fontWeight = FontWeight.Black, color = Color(0xFF003366)) },
            text = { Text("Al cambiar la unidad de muestreo, solo se perderán los datos de densidad. ¿Desea continuar?", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdate(bote.copy(densTipo = nextUnitType, transectos = emptyList()))
                        showUnitWarning = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("SÍ, CAMBIAR") }
            },
            dismissButton = {
                TextButton(onClick = { showUnitWarning = false }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }
}

@Composable
fun BoteMaestroSearchDialog(
    botes: List<BoteMaestroDto>,
    operationRegionRom: String?,
    operationCaleta: String?,
    onSelect: (BoteMaestroDto) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    
    // Estado para menús abatibles (jerarquía)
    var expandedRegionRom by remember(operationRegionRom, operationCaleta) { mutableStateOf<String?>(null) }
    var expandedCaleta by remember(operationCaleta) { mutableStateOf(operationCaleta) }

    fun normalizeRom(value: String?): String? {
        val s = value?.trim()
        if (s.isNullOrBlank()) return null
        val left = s.split("—").firstOrNull()?.trim().orEmpty()
        val token = left.split(" ").firstOrNull()?.trim().orEmpty()
        return token.takeIf { it.isNotBlank() }
    }

    val opCaleta = operationCaleta?.trim().takeIf { !it.isNullOrBlank() }
    val opRom = normalizeRom(operationRegionRom)
    fun normalizeKey(value: String?): String? {
        val raw = value?.trim()
        if (raw.isNullOrBlank()) return null
        val noParen = raw.replace(Regex("\\(.*?\\)"), " ")
        val noMarks = Normalizer.normalize(noParen, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        val cleaned = noMarks
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return cleaned.takeIf { it.isNotBlank() }
    }
    val opCaletaKey = normalizeKey(opCaleta)

    val baseBotes = remember(botes, opCaletaKey, opRom) {
        var filtered = botes
        if (opCaletaKey != null) {
            filtered = filtered.filter { b ->
                val bKey = normalizeKey(b.caleta)
                bKey != null && (bKey == opCaletaKey || bKey.contains(opCaletaKey) || opCaletaKey.contains(bKey))
            }
        }
        if (opRom != null) {
            filtered = filtered.filter { b ->
                val bRom = normalizeRom(b.region_rom ?: b.region)
                bRom == null || bRom.equals(opRom, ignoreCase = true)
            }
        }
        filtered
    }

    val regionKeys = remember(baseBotes) { baseBotes.mapNotNull { it.region_rom ?: it.region }.distinct().sorted() }
    LaunchedEffect(regionKeys, opRom, operationRegionRom, opCaletaKey) {
        if (expandedRegionRom == null) {
            val byExact = operationRegionRom?.let { desired -> regionKeys.firstOrNull { it.equals(desired, ignoreCase = true) } }
            val byRom = opRom?.let { desiredRom -> regionKeys.firstOrNull { normalizeRom(it)?.equals(desiredRom, ignoreCase = true) == true } }
            expandedRegionRom = byExact ?: byRom ?: regionKeys.firstOrNull()
        }
        if (!opCaleta.isNullOrBlank()) {
            expandedCaleta = opCaleta
        }
    }

    val queryFilteredBotes = baseBotes.filter { b ->
        (b.nombre ?: "").contains(query, true) ||
            (b.nrpa ?: "").contains(query, true) ||
            (b.nmatricula ?: "").contains(query, true)
    }

    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111B2B) else Color.White
    val headerBg = if (isDark) Color(0xFF0D47A1) else Color(0xFFF8F9FA)
    val textColor = if (isDark) Color.White else Color.Black
    val comboBg = if (isDark) Color(0xFF0D47A1) else Color.White
    val comboBorder = if (isDark) Color(0xFF1976D2) else Color(0xFFF1F3F5)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
            color = bgColor
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(headerBg).padding(16.dp)
                ) {
                    Text("SELECCIONAR BOTE MAESTRO", fontWeight = FontWeight.Black, fontSize = 16.sp, color = if (isDark) Color.White else Color(0xFF003366))
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = if (isDark) Color.LightGray else Color.Gray)
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar por nombre, RPA o matrícula...", color = if (isDark) Color.LightGray else Color.Gray) },
                        modifier = Modifier.fillMaxWidth().background(comboBg, RoundedCornerShape(12.dp)),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366)) },
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = comboBorder,
                            focusedBorderColor = if (isDark) Color(0xFF64B5F6) else Color(0xFF003366),
                            unfocusedContainerColor = comboBg,
                            focusedContainerColor = comboBg
                        )
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    Text("JERARQUÍA: REGIÓN > CALETA > BOTE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
                    if (!opCaleta.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Filtrado por caleta: ${opCaleta.uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFBBDEFB) else Color(0xFF003366))
                    }
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (baseBotes.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (!opCaleta.isNullOrBlank()) "No hay botes para la caleta seleccionada" else "No hay botes disponibles",
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        if (query.isEmpty()) {
                            // Mostrar Jerarquía: Región > Caleta > Bote
                            val regions = regionKeys
                            
                            items(regions) { rom ->
                                val isRegionExpanded = expandedRegionRom == rom
                                HierarchicalItem(
                                    label = "REGIÓN $rom",
                                    isExpanded = isRegionExpanded,
                                    onClick = { 
                                        expandedRegionRom = if (isRegionExpanded) null else rom
                                        expandedCaleta = null
                                    },
                                    level = 0
                                )
                                
                                if (isRegionExpanded) {
                                    val caletas = baseBotes.filter { (it.region_rom ?: it.region) == rom }
                                            .mapNotNull { it.caleta }
                                            .distinct()
                                            .sorted()
                                    
                                    caletas.forEach { caleta ->
                                        val isCaletaExpanded = expandedCaleta?.equals(caleta, ignoreCase = true) == true
                                        HierarchicalItem(
                                            label = caleta.uppercase(),
                                            isExpanded = isCaletaExpanded,
                                            onClick = { expandedCaleta = if (isCaletaExpanded) null else caleta },
                                            level = 1
                                        )
                                        
                                        if (isCaletaExpanded) {
                                            val botesInCaleta = baseBotes.filter { (it.region_rom ?: it.region) == rom && it.caleta?.equals(caleta, true) == true }
                                            
                                            botesInCaleta.forEach { b ->
                                                BoteFinalItem(b, onSelect)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Mostrar Resultados de Búsqueda Directa
                            items(queryFilteredBotes) { b ->
                                BoteFinalItem(b, onSelect)
                            }
                            if (queryFilteredBotes.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("No se encontraron botes con '$query'", color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HierarchicalItem(label: String, isExpanded: Boolean, onClick: () -> Unit, level: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = (level * 8).dp)
            .clickable { onClick() },
        color = if (isExpanded) Color(0xFFE3F2FD).copy(alpha = 0.5f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = if (isExpanded) Color(0xFF003366) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                fontSize = (14 - level).sp,
                fontWeight = if (isExpanded) FontWeight.Black else FontWeight.Bold,
                color = if (isExpanded) Color(0xFF003366) else Color.DarkGray
            )
        }
    }
}

@Composable
fun BoteFinalItem(b: BoteMaestroDto, onSelect: (BoteMaestroDto) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 24.dp)
            .clickable { onSelect(b) },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F3F5)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsBoat, null, tint = Color(0xFF003366), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(b.nombre ?: "S/N", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("RPA: ${b.nrpa ?: "—"} · MAT: ${b.nmatricula ?: "—"}", fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00897B), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OperacionDataDialog(
    opInitial: OperacionDto,
    regionLabel: String?,
    especiesMaestras: List<EspecieDto>,
    initialSelectedBoteKey: String? = null,
    onOperacionUpdated: (OperacionDto) -> Unit = {},
    onDismiss: () -> Unit
) {
    ExtractedOperacionDataDialog(
        opInitial = opInitial,
        regionLabel = regionLabel,
        especiesMaestras = especiesMaestras,
        initialSelectedBoteKey = initialSelectedBoteKey,
        onOperacionUpdated = onOperacionUpdated,
        onDismiss = onDismiss,
    )
}

@Composable
private fun SpeciesPickerDialog(
    title: String = "Seleccionar especies",
    species: List<EspecieDto>,
    currentSelectedIds: Set<Int>,
    onDismiss: () -> Unit,
    onApply: (Set<Int>) -> Unit
) {
    ExtractedSpeciesPickerDialog(
        title = title,
        species = species,
        currentSelectedIds = currentSelectedIds,
        onDismiss = onDismiss,
        onApply = onApply,
    )
}

@Composable
private fun LpIngresoDialog(
    speciesId: Int,
    sampleKind: String,
    speciesName: String,
    currentSamples: List<LpSampleDto>,
    onDismiss: () -> Unit,
    onUpdateSamples: (List<LpSampleDto>) -> Unit,
    onRemoveSpecies: () -> Unit
) {
    ExtractedLpIngresoDialog(
        speciesId = speciesId,
        sampleKind = sampleKind,
        speciesName = speciesName,
        currentSamples = currentSamples,
        onDismiss = onDismiss,
        onUpdateSamples = onUpdateSamples,
        onRemoveSpecies = onRemoveSpecies,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDatePickerDialog(onDateSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate(); val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd"); onDateSelected(date.format(fmt)) }; onDismiss() }) { Text("OK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }) { DatePicker(state = datePickerState) }
}
