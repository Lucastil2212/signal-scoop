package com.signalsoop.app.assistant

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskSummary
import com.signalsoop.app.model.SignalCategory

/**
 * Builds a grounded context block from in-memory scan results for the on-device LLM.
 * Scan payloads never leave the device.
 */
object SignalContextBuilder {
    private const val MAX_FINDINGS = 72

    fun buildPrompt(
        question: String,
        findings: List<Finding>,
        riskSummary: RiskSummary?,
    ): String {
        val ranked =
            findings
                .filter { it.category != SignalCategory.SYSTEM }
                .sortedWith(
                    compareByDescending<Finding> { it.riskPoints }
                        .thenByDescending { it.signalStrength ?: Int.MIN_VALUE },
                )
                .take(MAX_FINDINGS)

        val riskBlock =
            if (riskSummary == null) {
                "Risk summary: (no scan yet — run Scan first)"
            } else {
                buildString {
                    append("Risk summary: score ${riskSummary.score}/100, level ${riskSummary.level.label}")
                    if (riskSummary.highlights.isNotEmpty()) {
                        append("\nHighlights: ")
                        append(riskSummary.highlights.joinToString("; "))
                    }
                }
            }

        val findingsBlock =
            if (ranked.isEmpty()) {
                "(no signal findings in the current session)"
            } else {
                ranked.joinToString(separator = "\n") { f ->
                    val rssi = f.signalStrength?.let { " RSSI $it dBm" }.orEmpty()
                    "[${f.category.label}] ${f.title} — ${f.detail}$rssi"
                }
            }

        return """
You are Signal Scoop's on-device assistant. Answer using ONLY the scan data below.
Do not invent devices or MAC addresses. If data is missing, say so.
Be concise. Use bullet points when listing devices.

$riskBlock

Scan findings (${ranked.size} shown, ${findings.size} total):
---
$findingsBlock
---

Question: $question
        """.trimIndent()
    }
}
