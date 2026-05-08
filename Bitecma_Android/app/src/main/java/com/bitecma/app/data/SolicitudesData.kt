package com.bitecma.app.data

data class NuevoUsuarioRequest(
    val id: String,
    val nombres: String,
    val apellidos: String,
    val rut: String,
    val numero: String,
    val nombreArchivoCv: String, // Simulación del archivo
    val formatoCv: String, // "PDF" o "WORD"
    var asignadoAAdminId: Int? = null,
    var estado: String = "PENDIENTE" // PENDIENTE, APROBADO, RECHAZADO
)

object SolicitudesData {
    val solicitudes = mutableListOf<NuevoUsuarioRequest>()
}
