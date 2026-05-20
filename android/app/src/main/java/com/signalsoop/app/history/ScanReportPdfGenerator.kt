package com.signalsoop.app.history

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.signalsoop.app.assistant.ScanAnalytics
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.FindingFormatter
import com.signalsoop.app.model.SignalCategory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds a local PDF report from user-selected scans (no network). */
object ScanReportPdfGenerator {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 44f
    private const val LINE = 14f
    private const val BULLET_GAP = 14f

    private val reportCategoryOrder =
        listOf(
            SignalCategory.BLE,
            SignalCategory.WIFI,
            SignalCategory.BLUETOOTH,
            SignalCategory.NFC,
            SignalCategory.SENSORS,
            SignalCategory.SYSTEM,
        )

    fun write(
        context: Context,
        scans: List<ScanSnapshot>,
        insights: KnowledgeGraphInsights?,
    ): File {
        val dir = File(context.filesDir, "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(dir, "signal-scoop-report-$stamp.pdf")
        val doc = PdfDocument()
        val renderer = PdfRenderer(doc)
        val dateFmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())

        renderer.drawTitle("Signal Scoop — Scan Report")
        renderer.drawMuted(
            "Generated ${dateFmt.format(Date())} · ${scans.size} scan(s) · " +
                "${scans.sumOf { it.findings.size }} findings · on-device only",
        )
        renderer.spacer(8f)

        renderer.drawHeading("Color key")
        drawSignalLegend(renderer)
        GraphColorPalette.linkLegendEntries().forEach { entry ->
            renderer.drawLegendEntry(linkColorArgb(entry.label), entry.label)
        }
        renderer.spacer(10f)

        renderer.drawHeading("Data we collect (every scan)")
        dataCollectionOverview().forEach { renderer.drawBullet(it, 0xFF8BA4C4.toInt()) }
        renderer.spacer(8f)

        insights?.let { ins -> writeInsights(renderer, ins) }

        scans.sortedByDescending { it.scannedAtEpochMs }.forEach { scan ->
            writeScan(renderer, scan, insights, dateFmt)
        }

        renderer.finish("— End of report —")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun drawSignalLegend(renderer: PdfRenderer) {
        GraphColorPalette.signalLegendEntries().forEach { entry ->
            renderer.drawLegendEntry(legendArgb(entry.label), entry.label)
        }
        renderer.drawLegendEntry(0xFF00AEEF.toInt(), "Place (GPS cluster)")
    }

    private fun legendArgb(label: String): Int =
        when (label) {
            "BLE" -> GraphColorPalette.signalColorArgb(SignalCategory.BLE)
            "Wi-Fi" -> GraphColorPalette.signalColorArgb(SignalCategory.WIFI)
            "Bluetooth" -> GraphColorPalette.signalColorArgb(SignalCategory.BLUETOOTH)
            "NFC" -> GraphColorPalette.signalColorArgb(SignalCategory.NFC)
            "Sensor" -> GraphColorPalette.signalColorArgb(SignalCategory.SENSORS)
            "Place" -> 0xFF00AEEF.toInt()
            else -> 0xFF9AA3B2.toInt()
        }

    private fun linkColorArgb(label: String): Int =
        when (label) {
            "Observed" -> 0xFF8BA4C4.toInt()
            "At place" -> 0xFF00AEEF.toInt()
            "Repeat" -> 0xFFFFB020.toInt()
            "Note" -> 0xFFE040FB.toInt()
            "EVRUS" -> 0xFF7B61FF.toInt()
            "Device" -> 0xFFFF4D6D.toInt()
            else -> 0xFF8BA4C4.toInt()
        }

    private fun writeInsights(renderer: PdfRenderer, ins: KnowledgeGraphInsights) {
        renderer.drawHeading("Knowledge graph insights")
        renderer.drawBody(
            "${ins.totalScans} saved scans · ${ins.scansWithGps} with GPS · ${ins.uniquePlaces} place clusters",
        )
        ins.averageRiskScore?.let { score ->
            renderer.drawBody("Average risk: $score/100", GraphColorPalette.riskColorArgb(score))
        }
        ins.riskTrendNote?.let { renderer.drawMuted(it) }
        if (ins.placeSummaries.isNotEmpty()) {
            renderer.drawSubheading("Places (GPS clusters)", 0xFF00AEEF.toInt())
            ins.placeSummaries.forEach { place ->
                renderer.drawBullet(
                    "${place.label} — ${place.scanCount} scan(s) at ${place.coordinates}",
                    0xFF00AEEF.toInt(),
                )
            }
        }
        if (ins.recurringSignals.isNotEmpty()) {
            renderer.drawSubheading(
                "Recurring signals (${ins.recurringSignals.size} total)",
                0xFFFFB020.toInt(),
            )
            ins.recurringSignals.forEach { sig ->
                renderer.drawBullet(
                    "${sig.category.label}: ${sig.label} — seen in ${sig.scanCount} scans" +
                        sig.detail.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    GraphColorPalette.signalColorArgb(sig.category),
                )
            }
        }
        renderer.spacer(10f)
    }

    private fun writeScan(
        renderer: PdfRenderer,
        scan: ScanSnapshot,
        insights: KnowledgeGraphInsights?,
        dateFmt: SimpleDateFormat,
    ) {
        renderer.drawHeading(scan.name)
        renderer.drawMuted(dateFmt.format(Date(scan.scannedAtEpochMs)))
        scan.geoFix?.let { fix ->
            renderer.drawBullet(
                "GPS ${fix.formatCoordinates()} · ${fix.formatAccuracy()} · ${fix.provider}",
                0xFF00AEEF.toInt(),
            )
            fix.altitudeMeters?.let {
                renderer.drawMuted("Altitude ${"%.0f".format(it)} m")
            }
        } ?: renderer.drawMuted("GPS: not recorded")

        val analytics = ScanAnalytics.from(scan.findings, scan.riskSummary)
        renderer.drawSubheading("Quick summary")
        analytics.formatDigest().lines().filter { it.isNotBlank() }.forEach { renderer.drawBody(it) }
        scan.sessionContext?.let { ctx ->
            renderer.drawSubheading("Scan environment")
            renderer.drawMuted(ctx.formatOneLiner())
            renderer.drawMuted(
                "Permissions: ${ctx.permissionsGranted.joinToString(", ")} · " +
                    "Wi-Fi ${if (ctx.wifiEnabled) "on" else "off"} · " +
                    "BT ${if (ctx.bluetoothEnabled) "on" else "off"} · " +
                    "NFC ${if (ctx.nfcEnabled) "on" else "off"}",
            )
        }
        scan.riskSummary?.let { r ->
            renderer.drawBody(
                "Risk ${r.level.label} · ${r.score}/100 — ${r.level.description}",
                GraphColorPalette.riskColorArgb(r.score),
            )
            r.highlights.forEach { renderer.drawBullet(it, GraphColorPalette.riskColorArgb(r.score)) }
        }
        renderer.drawMuted(
            "Totals: ${analytics.totalFindings} — ${analytics.bleCount} BLE · ${analytics.wifiCount} Wi-Fi · " +
                "${analytics.bluetoothCount} BT · ${analytics.nfcCount} NFC · " +
                "${analytics.sensorCount} sensors · ${analytics.systemCount} status",
        )

        val scanKeys = KnowledgeGraphBuilder.signalKeysFrom(scan.findings).keys
        insights?.recurringSignals
            ?.filter { it.signalKey in scanKeys }
            ?.takeIf { it.isNotEmpty() }
            ?.let { recurring ->
                renderer.drawSubheading("Also seen in other scans", 0xFFFFB020.toInt())
                recurring.forEach { sig ->
                    renderer.drawBullet(
                        "${sig.label} — ${sig.scanCount} scans total",
                        GraphColorPalette.signalColorArgb(sig.category),
                    )
                }
            }

        renderer.drawSubheading("All signals (${scan.findings.size}) — complete list")
        reportCategoryOrder.forEach { category ->
            val group =
                scan.findings
                    .filter { it.category == category }
                    .sortedWith(findingSortOrder(category))
            if (group.isEmpty()) return@forEach
            val color = GraphColorPalette.signalColorArgb(category)
            renderer.drawSubheading("${category.label} (${group.size})", color)
            group.forEach { finding ->
                renderer.drawFinding(finding, color)
            }
            renderer.spacer(4f)
        }
        renderer.spacer(12f)
    }

    private fun dataCollectionOverview(): List<String> =
        listOf(
            "BLE — MAC, name, RSSI, manufacturer ID, service UUIDs, TX power, connectable, adv bytes",
            "Wi-Fi — BSSID, SSID, RSSI, channel/band, connected-AP flag, capabilities",
            "Bluetooth — paired devices with bond state, device class, and link type",
            "NFC — hardware present and enabled/disabled",
            "Sensors — inventory with vendor, resolution, range, min delay, power",
            "Session — device model, Android version, scan duration, permissions, radios, VPN",
            "GPS — coordinates, accuracy, provider, altitude",
            "Risk — heuristic score from passive radio patterns",
            "Knowledge graph — links scans, places, and recurring signals locally",
        )

    private fun findingSortOrder(category: SignalCategory): Comparator<Finding> =
        when (category) {
            SignalCategory.BLE,
            SignalCategory.WIFI,
            -> compareByDescending<Finding> { it.signalStrength ?: Int.MIN_VALUE }.thenBy { it.title }
            SignalCategory.SENSORS -> compareBy { it.title }
            else -> compareByDescending<Finding> { it.riskPoints }.thenBy { it.title }
        }

    private class PdfRenderer(private val doc: PdfDocument) {
        private var pageNum = 1
        private var pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
        private var page = doc.startPage(pageInfo)
        private var canvas = page.canvas
        private var y = MARGIN
        private val maxTextWidth = PAGE_W - MARGIN * 2

        private val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = 0xFF1A1A1A.toInt()
            }
        private val headPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = 0xFF1A1A1A.toInt()
            }
        private val subheadPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF222222.toInt() }
        private val mutedPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9.5f
                color = 0xFF555555.toInt()
            }
        private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun drawTitle(text: String) {
            ensureSpace(28f)
            canvas.drawText(text, MARGIN, y, titlePaint)
            y += 26f
        }

        fun drawHeading(text: String) {
            ensureSpace(22f)
            y += 8f
            canvas.drawText(text, MARGIN, y, headPaint)
            y += 20f
        }

        fun drawSubheading(text: String, color: Int = 0xFF333333.toInt()) {
            ensureSpace(18f)
            subheadPaint.color = color
            canvas.drawText(text, MARGIN, y, subheadPaint)
            y += 17f
        }

        fun drawBody(text: String, color: Int = bodyPaint.color) {
            bodyPaint.color = color
            drawWrapped(text, bodyPaint)
        }

        fun drawMuted(text: String) = drawWrapped(text, mutedPaint)

        fun drawLegendEntry(colorArgb: Int, label: String) {
            ensureSpace(LINE)
            swatchPaint.color = colorArgb
            canvas.drawRoundRect(RectF(MARGIN, y - 9f, MARGIN + 10f, y + 1f), 2f, 2f, swatchPaint)
            canvas.drawText(label, MARGIN + BULLET_GAP, y, mutedPaint)
            y += LINE
        }

        fun drawBullet(text: String, colorArgb: Int) {
            ensureSpace(LINE)
            swatchPaint.color = colorArgb
            canvas.drawCircle(MARGIN + 4f, y - 4f, 4f, swatchPaint)
            drawWrapped(text, bodyPaint, MARGIN + BULLET_GAP)
        }

        fun drawFinding(finding: Finding, colorArgb: Int) {
            val rssi = finding.signalStrength?.let { " · $it dBm" }.orEmpty()
            val risk = if (finding.riskPoints > 0) " · risk +${finding.riskPoints}" else ""
            drawBullet("${finding.title} — ${finding.detail}$rssi$risk", colorArgb)
            FindingFormatter.extrasLines(finding.extras).forEach { line ->
                drawMuted("  $line")
            }
        }

        fun spacer(px: Float) {
            y += px
        }

        fun finish(footer: String) {
            ensureSpace(LINE)
            canvas.drawText(footer, MARGIN, PAGE_H - MARGIN, mutedPaint)
            doc.finishPage(page)
        }

        private fun newPage() {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        private fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_H - MARGIN) newPage()
        }

        private fun drawWrapped(text: String, paint: Paint, x: Float = MARGIN) {
            val width = PAGE_W - x - MARGIN
            wrap(text, paint, width).forEach { line ->
                ensureSpace(LINE)
                canvas.drawText(line, x, y, paint)
                y += LINE
            }
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        val paragraphs = text.split('\n')
        for (paragraph in paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("")
                continue
            }
            val words = paragraph.split(' ')
            var current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "${current} $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(word)
                } else {
                    if (current.isNotEmpty()) {
                        lines.add(current.toString())
                        current = StringBuilder()
                    }
                    if (paint.measureText(word) <= maxWidth) {
                        current.append(word)
                    } else {
                        breakLongToken(word, paint, maxWidth, lines)
                    }
                }
            }
            if (current.isNotEmpty()) lines.add(current.toString())
        }
        return lines.ifEmpty { listOf(text) }
    }

    private fun breakLongToken(token: String, paint: Paint, maxWidth: Float, lines: MutableList<String>) {
        var start = 0
        while (start < token.length) {
            var end = start + 1
            while (end <= token.length && paint.measureText(token.substring(start, end)) <= maxWidth) {
                end++
            }
            if (end - 1 == start) end = start + 1
            lines.add(token.substring(start, (end - 1).coerceAtLeast(start + 1)))
            start = (end - 1).coerceAtLeast(start + 1)
        }
    }
}
