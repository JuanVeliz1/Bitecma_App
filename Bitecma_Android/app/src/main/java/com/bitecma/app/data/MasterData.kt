package com.bitecma.app.data

import androidx.compose.runtime.mutableStateListOf

data class BoteMaestro(
    val nombre: String,
    val caleta: String,
    val rpa: String,
    val matricula: String,
    val regionId: String
)

data class EspecieMaestra(
    val id: Int,
    val nombreComun: String,
    val nombreCientifico: String
)

data class OperacionLocal(
    val id: Int,
    var sector: String,
    var fecha: String,
    var botes: Int,
    var estado: String = "Pendiente"
)

object MasterData {
    val botes = listOf(
        BoteMaestro("5MENTARIO", "RIQUELME", "963244", "1980", "I — Tarapacá"),
        BoteMaestro("ABDON I", "CAVANCHA", "700599", "3490", "I — Tarapacá"),
        BoteMaestro("ABRAHAM", "RIQUELME", "18200", "934", "I — Tarapacá"),
        BoteMaestro("ABUELITA", "RIQUELME", "901057", "251", "I — Tarapacá"),
        BoteMaestro("ACUARIO 3", "SAN MARCOS", "914216", "62", "I — Tarapacá"),
        BoteMaestro("AGUSTINA", "CHANAVAYA", "124889", "204", "I — Tarapacá"),
        BoteMaestro("VICENTE ANDRÉS I", "CHAN-CHAN", "123456", "788", "XIV — Los Ríos")
    )

    val especies = listOf(
        EspecieMaestra(1, "Loco", "Concholepas concholepas"),
        EspecieMaestra(2, "Choro", "Choromytilus chorus"),
        EspecieMaestra(3, "Chorito", "Mytilus chilensis"),
        EspecieMaestra(4, "Cholga", "Aulacomya atra"),
        EspecieMaestra(5, "Erizo rojo", "Loxechinus albus"),
        EspecieMaestra(6, "Lapa bonete", "Fissurella costata"),
        EspecieMaestra(7, "Lapa rosada", "Fissurella cumingi"),
        EspecieMaestra(8, "Lapa blanquilla", "Fissurella limbata"),
        EspecieMaestra(9, "Lapa reina", "Fissurella maxima"),
        EspecieMaestra(10, "Lapa negra", "Fissurella latimarginata"),
        EspecieMaestra(11, "Lapa picta", "Fissurella picta"),
        EspecieMaestra(18, "Piure", "Pyura chilensis"),
        EspecieMaestra(19, "Almeja", "Retrotapes lenticularis"),
        EspecieMaestra(25, "Macha", "Mesodesma donacium")
    )

    val regiones = listOf(
        "I — Tarapacá", "II — Antofagasta", "III — Atacama", "IV — Coquimbo", 
        "V — Valparaíso", "VI — O'Higgins", "VII — Maule", "VIII — Biobío",
        "IX — La Araucanía", "X — Los Lagos", "XI — Aysén", "XII — Magallanes",
        "XIV — Los Ríos", "XV — Arica y Parinacota", "XVI — Ñuble"
    )

    val operacionesLocales = mutableStateListOf<OperacionLocal>()
}
