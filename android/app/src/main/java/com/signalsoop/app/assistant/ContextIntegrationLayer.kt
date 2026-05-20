package com.signalsoop.app.assistant

import com.signalsoop.app.history.KnowledgeGraphInsights
import com.signalsoop.app.history.KnowledgeGraphInsightsEngine
import com.signalsoop.app.model.Finding

/**
 * Layers on-device context for the Ask assistant: current scan facts, then knowledge-graph
 * history when available. Keeps prompts within a token budget for MediaPipe LiteRT.
 */
object ContextIntegrationLayer {
    data class Layers(
        val scanDigest: String,
        val historyDigest: String?,
        val notableRows: List<Finding>,
    )

    fun build(
        analytics: ScanAnalytics,
        findings: List<Finding>,
        historyInsights: KnowledgeGraphInsights?,
    ): Layers {
        val historyDigest =
            historyInsights
                ?.takeIf { it.totalScans > 0 }
                ?.let { KnowledgeGraphInsightsEngine.formatDigest(it).trimEnd() }
        return Layers(
            scanDigest = analytics.formatDigest().trimEnd(),
            historyDigest = historyDigest,
            notableRows = SignalContextBuilder.selectNotableRowsForIntegration(findings),
        )
    }

    fun assemblePrompt(
        layers: Layers,
        question: String,
        taskHint: String,
        rowLimit: Int,
        compact: Boolean = false,
        includeHistory: Boolean = true,
    ): String = buildString {
        appendLine("You are Signal Scoop. Use ONLY the facts below. Do not invent devices.")
        appendLine("Answer the user's question directly — do not repeat the fact sheet unless they asked for a summary.")
        appendLine("Task: $taskHint")
        appendLine()
        appendLine("--- CURRENT SCAN ---")
        layers.scanDigest.lines().forEach { appendLine(it) }
        val rows = layers.notableRows.take(rowLimit.coerceAtLeast(0))
        if (rows.isNotEmpty()) {
            appendLine()
            appendLine("Notable rows (this scan):")
            rows.forEach { f ->
                val rssi = f.signalStrength?.let { " ${it}dBm" }.orEmpty()
                val detail = f.detail.truncate(if (compact) 36 else 64)
                appendLine("- [${f.category.label}] ${f.title.truncate(44)} — $detail$rssi")
            }
        }
        if (includeHistory) {
            layers.historyDigest?.let { history ->
                appendLine("--- END CURRENT SCAN ---")
                appendLine()
                appendLine("--- KNOWLEDGE GRAPH (saved scans) ---")
                history.lines().forEach { appendLine(it) }
            }
        }
        appendLine("--- END FACTS ---")
        appendLine()
        append("Question: ")
        append(question.trim())
    }

    private fun String.truncate(max: Int): String =
        if (length <= max) this else take(max - 1) + "…"
}
