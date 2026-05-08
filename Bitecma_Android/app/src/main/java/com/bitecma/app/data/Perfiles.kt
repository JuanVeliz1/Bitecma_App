package com.bitecma.app.data

data class Usuario(
    val id: Int,
    val nombre: String,
    val correo: String,
    val numero: String = "",
    var contrasena: String,
    var rol: String,
    var activo: Boolean = true,
    var ultimoAcceso: String = "Primera vez"
)

object PerfilesData {
    val perfiles = mutableListOf(
        Usuario(id = 0, nombre = "Bitecma", correo = "bitecma@bitecma.cl", numero = "+56945273088", contrasena = "12345678", rol = "Admin"),
        Usuario(id = 1, nombre = "Armando Rosson", correo = "arosson@bitecma.cl", numero = "+56945273088", contrasena = "12345678", rol = "Admin"),
        Usuario(id = 2, nombre = "Lorena Olmos", correo = "lolmos@bitecma.cl", contrasena = "1234", rol = "Biólogo"),
        Usuario(id = 3, nombre = "Claudio Romero", correo = "cromero@bitecma.cl", contrasena = "1234", rol = "Biólogo"),
        Usuario(id = 4, nombre = "Cesar Pedrini", correo = "cpedrini@bitecma.cl", contrasena = "1234", rol = "Biólogo"),
        Usuario(id = 5, nombre = "Cristian Medina", correo = "cmedina@bitecma.cl", contrasena = "1234", rol = "Biólogo"),
        Usuario(id = 6, nombre = "Hugo Carrillo", correo = "hcarrillo@bitecma.cl", contrasena = "1234", rol = "Biólogo")
    )
}
