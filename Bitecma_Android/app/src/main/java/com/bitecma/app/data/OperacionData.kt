package com.bitecma.app.data

data class SectorAmerb(
    val id: Int,
    val nombre: String,
    val region: Int
)

data class Opa(
    val id: Int,
    val nombre: String,
    val nombreCorto: String,
    val region: Int
)

object OperacionData {
    val sectoresAmerb = listOf(
        SectorAmerb(1, "PUNTA SEREMEÑO", 1),
        SectorAmerb(2, "AMARILLO", 1),
        SectorAmerb(3, "PISAGUA", 1),
        SectorAmerb(4, "CARAMUCHO SECTOR C", 1)
    )

    val opas = listOf(
        Opa(1, "COOPERATIVA DE PESCADORES ARTESANALES Y ACUICULTORES DE CALETA CHILENA", "PUNTA SEREMEÑO", 1),
        Opa(2, "S.T.I DEL MAR Nº 2 DE PISAGUA", "AMARILLO", 1),
        Opa(3, "S.T.I. BUZOS MARISCADORES DE CALETA PISAGUA", "PISAGUA", 1)
    )

    val caletasByRegion = mapOf(
        1 to listOf("Cáñamo", "Caramucho", "Cavancha", "Chanavaya (Pabellón de Pica)", "Chanavayita", "Chipana"),
        15 to listOf("Arica", "Camarones")
    )

    val tiposOrganizacion = listOf("STI", "ASOC", "OTRO")
}
