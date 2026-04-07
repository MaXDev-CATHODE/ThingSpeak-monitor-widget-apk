package com.thingspeak.monitor.core.utils

import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import java.lang.StringBuilder
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument.PageInfo
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Utility class for exporting data to various formats.
 */
object ExportUtils {

    /**
     * Helper to format a UTC timestamp (ISO-8601) into a specific timezone.
     */
    private fun formatTimestamp(timestamp: String, timezoneId: String?): String {
        return try {
            val instant = Instant.parse(timestamp)
            val zoneId = timezoneId?.let { 
                try { ZoneId.of(it) } catch (e: Exception) { ZoneId.systemDefault() }
            } ?: ZoneId.systemDefault()
            val zonedDateTime = instant.atZone(zoneId)
            zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } catch (e: Exception) {
            // Fallback to raw string manipulation if parsing fails
            timestamp.take(19).replace("T", " ")
        }
    }

    /**
     * Generates CSV text based on feed entries.
     * 
     * @param entries List of entries to export.
     * @param fieldNames Map of channel field names.
     * @param timezone Optional timezone override (e.g. "GMT-5")
     */
    fun generateCsv(
        entries: List<FeedEntry>,
        fieldNames: Map<Int, String>,
        timezone: String? = null
    ): String {
        val sb = StringBuilder()
        
        val sortedFieldIndices = fieldNames.keys.sorted()
        sb.append("Date,Entry_ID")
        sortedFieldIndices.forEach { index ->
            sb.append(",${escapeCsv(fieldNames[index] ?: "Field $index")}")
        }
        sb.append("\n")
        
        entries.forEach { entry ->
            val formattedDate = formatTimestamp(entry.createdAt, timezone)
            sb.append("${escapeCsv(formattedDate)},${entry.entryId}")
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
     * 
     * @param timezone Optional timezone override (e.g. "GMT-5")
     */
    fun writePdfReport(
        outputStream: OutputStream,
        channelName: String,
        entries: List<FeedEntry>,
        fieldNames: Map<Int, String>,
        timezone: String? = null
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
        
        // Use user timezone for "Generation date" as well
        val generationZone = timezone?.let { 
            try { ZoneId.of(it) } catch (e: Exception) { ZoneId.systemDefault() }
        } ?: ZoneId.systemDefault()
        
        val currentDateTime = ZonedDateTime.now(generationZone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        canvas.drawText("Generation date: $currentDateTime (${generationZone.id})", margin, y, paint)
        canvas.drawText("Entry count: ${entries.size}", margin, y + 15f, paint)
        
        y += 40f
        paint.strokeWidth = 1f
        canvas.drawLine(margin, y, pageWidth - margin, y, paint)
        y += 20f
        
        val sortedIndices = fieldNames.keys.sorted()
        val colWidth = if (sortedIndices.size > 4) (pageWidth - margin - 190f) / sortedIndices.size else 85f
        
        fun drawTableHeaders(c: Canvas, startY: Float) {
            paint.textSize = 10f
            paint.isFakeBoldText = true
            c.drawText("Date", margin, startY, paint)
            c.drawText("ID", margin + 140f, startY, paint)
            sortedIndices.forEachIndexed { i, index ->
                val label = fieldNames[index]?.take(if (sortedIndices.size > 5) 8 else 15) ?: "F$index"
                c.drawText(label, margin + 190f + (i * colWidth), startY, paint)
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
            val formattedDate = formatTimestamp(entry.createdAt, timezone)
            canvas.drawText(formattedDate, margin, y, paint)
            canvas.drawText(entry.entryId.toString(), margin + 140f, y, paint)
            
            sortedIndices.forEachIndexed { i, index ->
                val valStr = entry.fields[index]?.take(if (sortedIndices.size > 5) 6 else 10) ?: "-"
                canvas.drawText(valStr, margin + 190f + (i * colWidth), y, paint)
            }
            y += 18f
        }

        pdfDocument.finishPage(currentPage)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }
}
