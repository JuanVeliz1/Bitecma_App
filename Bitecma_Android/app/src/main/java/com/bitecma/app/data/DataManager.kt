package com.bitecma.app.data

import androidx.compose.runtime.mutableStateListOf
import com.bitecma.app.network.OperacionDto
import com.bitecma.app.network.OperacionBoteDto

object DataManager {
    // Maestro de Botes
    val botes = mutableStateListOf(
        BoteMaestro("5MENTARIO", "RIQUELME", "963244", "1980", "I — Tarapacá"),
        BoteMaestro("ABDON I", "CAVANCHA", "700599", "3490", "I — Tarapacá"),
        BoteMaestro("ABRAHAM", "RIQUELME", "18200", "934", "I — Tarapacá"),
        BoteMaestro("VICENTE ANDRÉS I", "CHAN-CHAN", "123456", "788", "XIV — Los Ríos")
    )

    // Maestro de Especies
    val especies = mutableStateListOf(
        EspecieMaestra(1, "Loco", "Concholepas concholepas"),
        EspecieMaestra(2, "Choro", "Choromytilus chorus"),
        EspecieMaestra(5, "Erizo rojo", "Loxechinus albus"),
        EspecieMaestra(7, "Lapa rosada", "Fissurella cumingi"),
        EspecieMaestra(25, "Macha", "Mesodesma donacium")
    )

    val operacionesBd = mutableStateListOf<OperacionDto>()

    val operacionesLc = mutableStateListOf(
        OperacionDto(
            id = "OP-2026-001",
            sector = "Chan-chan",
            region = 14,
            fechaInicio = "2026-04-21",
            fechaFin = "2026-04-21",
            botes = listOf(
                OperacionBoteDto(nombre = "VICENTE ANDRÉS I", zona = 1, buzo = "CHINO", densTipo = "transecto"),
                OperacionBoteDto(nombre = "DANIELITO I", zona = 2, buzo = "RAMÓN", densTipo = "cuadrante")
            )
        )
    )
}
