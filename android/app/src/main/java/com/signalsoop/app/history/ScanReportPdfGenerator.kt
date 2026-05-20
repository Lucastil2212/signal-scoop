package com.signalsoop.app.history

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds a local PDF report from user-selected scans (no network). */
object ScanReportPdfGenerator {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private const val LINE = 16f

    fun write(
        context: Context,
        scans: List<ScanSnapshot>,
        insights: KnowledgeGraphInsights?,
    ): File {
        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(dir, "signal-scoop-report-$stamp.pdf")
        val doc = PdfDocument()
        val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val headPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val mutedPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f
                color = 0xFF666666.toInt()
            }

        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN
        val maxTextWidth = PAGE_W - MARGIN * 2

        fun newPage() {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_H - MARGIN) newPage()
        }

        fun drawWrapped(text: String, paint: Paint = bodyPaint) {
            wrap(text, paint, maxTextWidth).forEach { line ->
                ensureSpace(LINE)
                canvas.drawText(line, MARGIN, y, paint)
                y += LINE
            }
        }

        canvas.drawText("Signal Scoop — Knowledge Graph Report", MARGIN, y, titlePaint)
        y += 28f
        drawWrapped(
            "Generated ${SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date())}",
            mutedPaint,
        )
        drawWrapped("${scans.size} scan(s) selected · on-device only · Manticore Technologies", mutedPaint)
        y += 8f

        insights?.let { ins ->
            ensureSpace(LINE * 3)
            canvas.drawText("Graph summary", MARGIN, y, headPaint)
            y += 20f
            drawWrapped("${ins.totalScans} total scans · ${ins.scansWithGps} with GPS · ${ins.uniquePlaces} places")
            ins.recurringSignals.take(8).forEach { sig ->
                drawWrapped("• ${sig.label} — seen ${sig.scanCount}× · ${sig.category.label}")
            }
            y += 8f
        }

        val dateFmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        scans.sortedByDescending { it.scannedAtEpochMs }.forEach { scan ->
            ensureSpace(LINE * 5)
            canvas.drawText(scan.name, MARGIN, y, headPaint)
            y += 20f
            drawWrapped(dateFmt.format(Date(scan.scannedAtEpochMs)), mutedPaint)
            scan.geoFix?.let { drawWrapped("GPS ${it.formatCoordinates()} · ${it.formatAccuracy()}") }
            scan.riskSummary?.let { r ->
                drawWrapped("Risk ${r.level.label} · ${r.score}/100 — ${r.level.description}")
                r.highlights.forEach { drawWrapped("  • $it") }
            }
            val radio =
                scan.findings.filter {
                    it.category != SignalCategory.SYSTEM && it.category != SignalCategory.SENSORS
                }
            drawWrapped("${radio.size} radio findings:", mutedPaint)
            radio.take(35).forEach { f ->
                val rssi = f.signalStrength?.let { " · $it dBm" }.orEmpty()
                drawWrapped("${f.category.label}: ${f.title} — ${f.detail}$rssi")
            }
            if (radio.size > 35) {
                drawWrapped("… and ${radio.size - 35} more (see app History)", mutedPaint)
            }
            y += 12f
        }

        canvas.drawText("— End of report —", MARGIN, PAGE_H - MARGIN, mutedPaint)
        doc.finishPage(page)
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (paint.measureText(candidate) <= maxWidth) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf(text) }
    }
}
