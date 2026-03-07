package com.thingspeak.monitor.core.utils

import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import java.lang.StringBuilder
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument.PageInfo
import java.io.OutputStream

/**
 * Utility class for exporting data to various formats.
 */
object ExportUtils {

    /**
     * Generates CSV text based on feed entries.
     * 
     * @param entries List of entries to export.
     * @param fieldNames Map of channel field names.
     */
    fun generateCsv(
        entries: List<FeedEntry>,
        fieldNames: Map<Int, String>
    ): String {
        val sb = StringBuilder()
        
        val sortedFieldIndices = fieldNames.keys.sorted()
        sb.append("Date,Entry_ID")
        sortedFieldIndices.forEach { index ->
            sb.append(",${escapeCsv(fieldNames[index] ?: "Field $index")}")
        }
        sb.append("\n")
        
        entries.forEach { entry ->
            sb.append("${escapeCsv(entry.createdAt)},${entry.entryId}")
            sortedFieldIndices.forEach { index ->
                sb.append(",${escapeCsv(entry.fields[index] ?: "")}")
            }
            sb.append("\n")
        }
        
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val strValue = value.replace("\n", " ").trim()
        if (strValue.contains(",") || strValue.contains("\"")) {
            return "\"" + strValue.replace("\"", "\"\"") + "\""
        }
        return strValue
    }

    /**
     * Generates multi-page PDF report with all data.
     */
    fun writePdfReport(
        outputStream: OutputStream,
        channelName: String,
        entries: List<FeedEntry>,
        fieldNames: Map<Int, String>
    ) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val pageInfo = PageInfo.Builder(pageWidth, pageHeight, 1).create()
        
        var currentPageNumber = 1
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        val paint = Paint()
        
        var y = margin

        // Header on the first page
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("ThingSpeak Report: $channelName", margin, y, paint)
        
        y += 30f
        paint.textSize = 10f
        paint.isFakeBoldText = false
        val currentDateTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        canvas.drawText("Generation date: $currentDateTime", margin, y, paint)
        canvas.drawText("Entry count: ${entries.size}", margin, y + 15f, paint)
        
        y += 40f
        paint.strokeWidth = 1f
        canvas.drawLine(margin, y, pageWidth - margin, y, paint)
        y += 20f
        
        val sortedIndices = fieldNames.keys.sorted().take(4) // More fields if they fit
        
        fun drawTableHeaders(c: Canvas, startY: Float) {
            paint.textSize = 10f
            paint.isFakeBoldText = true
            c.drawText("Date", margin, startY, paint)
            c.drawText("ID", margin + 140f, startY, paint)
            sortedIndices.forEachIndexed { i, index ->
                val label = fieldNames[index]?.take(15) ?: "F$index"
                c.drawText(label, margin + 190f + (i * 85f), startY, paint)
            }
            c.drawLine(margin, startY + 5f, pageWidth - margin, startY + 5f, paint)
            paint.isFakeBoldText = false
        }

        drawTableHeaders(canvas, y)
        y += 20f

        entries.forEach { entry ->
            if (y > pageHeight - margin) {
                pdfDocument.finishPage(currentPage)
                currentPageNumber++
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                y = margin
                drawTableHeaders(canvas, y)
                y += 20f
            }
            
            paint.textSize = 9f
            canvas.drawText(entry.createdAt.take(19).replace("T", " "), margin, y, paint)
            canvas.drawText(entry.entryId.toString(), margin + 140f, y, paint)
            
            sortedIndices.forEachIndexed { i, index ->
                val valStr = entry.fields[index]?.take(10) ?: "-"
                canvas.drawText(valStr, margin + 190f + (i * 85f), y, paint)
            }
            y += 18f
        }

        pdfDocument.finishPage(currentPage)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }
}
