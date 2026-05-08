package com.bitecma.app.utils

import android.content.Context
import com.bitecma.app.network.OperacionDto
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

object ExcelExporter {

    fun generateOperacionExcel(operacion: OperacionDto): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Resumen Operación")

        // Estilo para cabeceras
        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                bold = true
            }
            setFont(font)
        }

        // Información General
        var rowIdx = 0
        val row1 = sheet.createRow(rowIdx++)
        row1.createCell(0).apply { setCellValue("ID Operación:"); cellStyle = headerStyle }
        row1.createCell(1).setCellValue(operacion.id)

        val row2 = sheet.createRow(rowIdx++)
        row2.createCell(0).apply { setCellValue("Sector:"); cellStyle = headerStyle }
        row2.createCell(1).setCellValue(operacion.sector ?: "")

        val row3 = sheet.createRow(rowIdx++)
        row3.createCell(0).apply { setCellValue("Organización:"); cellStyle = headerStyle }
        row3.createCell(1).setCellValue(operacion.org ?: "")

        rowIdx++ // Espacio

        // Datos de Botes
        val headerRow = sheet.createRow(rowIdx++)
        val headers = listOf("Bote", "Zona", "Buzo", "Unidad", "Transecto #", "Área", "Sustrato")
        headers.forEachIndexed { i, h ->
            headerRow.createCell(i).apply { setCellValue(h); cellStyle = headerStyle }
        }

        // Botes y sus transectos
        operacion.botes?.forEach { bote ->
            bote.transectos?.forEach { t ->
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(bote.nombre ?: "")
                row.createCell(1).setCellValue(bote.zona?.toDouble() ?: 0.0)
                row.createCell(2).setCellValue(bote.buzo ?: "")
                row.createCell(3).setCellValue(bote.densTipo ?: "")
                row.createCell(4).setCellValue(t.num?.toDouble() ?: 0.0)
                row.createCell(5).setCellValue(t.area ?: 0.0)
                row.createCell(6).setCellValue(t.sustrato ?: "")
                
                // Counts de especies
                var cellOffset = 7
                t.counts?.forEach { (espId, count) ->
                    if (row.rowNum == headerRow.rowNum + 1) {
                        // Solo agregar cabecera de especie en la primera fila de datos si no existe
                        // Pero esto es simplificado, idealmente tendríamos cabeceras fijas para especies
                    }
                    // Por ahora solo listamos los counts en las siguientes celdas
                    row.createCell(cellOffset++).setCellValue("Esp ID $espId: $count")
                }
            }
        }

        val out = ByteArrayOutputStream()
        workbook.write(out)
        workbook.close()
        return out.toByteArray()
    }
}
