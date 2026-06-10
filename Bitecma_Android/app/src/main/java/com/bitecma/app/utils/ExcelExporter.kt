package com.bitecma.app.utils

import com.bitecma.app.network.OperacionDto

object ExcelExporter {

    fun generateOperacionExcel(operacion: OperacionDto): ByteArray {
        fun csvEscape(value: String): String {
            val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
            val escaped = value.replace("\"", "\"\"")
            return if (needsQuotes) "\"$escaped\"" else escaped
        }

        val sb = StringBuilder()
        sb.appendLine(listOf("Campo", "Valor").joinToString(",") { csvEscape(it) })
        sb.appendLine(listOf("ID Operación", operacion.id).joinToString(",") { csvEscape(it) })
        sb.appendLine(listOf("Sector", operacion.sector).joinToString(",") { csvEscape(it) })
        sb.appendLine(listOf("Organización", operacion.org.orEmpty()).joinToString(",") { csvEscape(it) })
        sb.appendLine()

        val headers = listOf("Medio", "Bote", "Zona", "Buzo", "Unidad", "Unidad #", "Área", "Sustrato", "Counts")
        sb.appendLine(headers.joinToString(",") { csvEscape(it) })

        val botes = operacion.botes.orEmpty()
        botes.forEach { bote ->
            val isIntermareal = bote.submareal == 0 || bote.nombre.equals("Intermareal", ignoreCase = true)
            val medio = if (isIntermareal) "Intermareal" else "Submareal"
            val boteNombre = bote.nombre.orEmpty()
            val zona = bote.zona?.toString().orEmpty()
            val buzo = bote.buzo.orEmpty()
            val unidad = bote.densTipo.orEmpty()

            val unidades = bote.transectos.orEmpty()
            if (unidades.isEmpty()) {
                val row = listOf(medio, boteNombre, zona, buzo, unidad, "", "", "", "")
                sb.appendLine(row.joinToString(",") { csvEscape(it) })
            } else {
                unidades.forEach { u ->
                    val countsCell = u.counts?.entries
                        ?.sortedBy { it.key }
                        ?.joinToString(";") { (k, v) -> "$k=$v" }
                        .orEmpty()
                    val row = listOf(
                        medio,
                        boteNombre,
                        zona,
                        buzo,
                        unidad,
                        u.num?.toString().orEmpty(),
                        u.area?.toString().orEmpty(),
                        u.sustrato.orEmpty(),
                        countsCell
                    )
                    sb.appendLine(row.joinToString(",") { csvEscape(it) })
                }
            }
        }

        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}
