package com.bitecma.app.data

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
