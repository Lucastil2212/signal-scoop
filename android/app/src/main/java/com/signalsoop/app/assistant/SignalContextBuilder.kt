package com.signalsoop.app.assistant

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskSummary
import com.signalsoop.app.model.SignalCategory

/**
 * Builds compact LLM prompts from [ScanAnalytics] plus a few notable rows.
 */
object SignalContextBuilder {
    private const val MAX_NOTABLE_ROWS = 12
    private const val MAX_DETAIL_CHARS = 64

    fun buildWithinTokenBudget(
        question: String,
        findings: List<Finding>,
        riskSummary: RiskSummary?,
        analytics: ScanAnalytics,
        taskHint: String,
        countTokens: (String) -> Int,
        maxInputTokens: Int,
    ): String {
        val notable = selectNotableRows(findings)
        var rowLimit = notable.size.coerceAtMost(MAX_NOTABLE_ROWS)

        while (rowLimit >= 2) {
            val prompt = assemble(analytics, notable.take(rowLimit), question, taskHint)
            if (countTokens(prompt) <= maxInputTokens) return prompt
            rowLimit = (rowLimit * 3) / 4
        }

        return assemble(analytics, notable.take(2), question, taskHint, compact = true)
    }

    private fun selectNotableRows(findings: List<Finding>): List<Finding> {
        val radio =
            findings
                .filter {
                    it.category == SignalCategory.BLE ||
                        it.category == SignalCategory.WIFI ||
                        it.category == SignalCategory.BLUETOOTH ||
                        it.category == SignalCategory.NFC
                }
                .sortedWith(
                    compareByDescending<Finding> { it.riskPoints }
                        .thenByDescending { it.signalStrength ?: Int.MIN_VALUE },
                )
        return radio.take(MAX_NOTABLE_ROWS)
    }

    private fun assemble(
        analytics: ScanAnalytics,
        rows: List<Finding>,
        question: String,
        taskHint: String,
        compact: Boolean = false,
    ): String = buildString {
        appendLine("You are Signal Scoop. Use ONLY the facts below. Do not invent devices.")
        appendLine("Task: $taskHint")
        appendLine()
        appendLine("--- SCAN FACTS ---")
        analytics.formatDigest().lines().forEach { appendLine(it) }
        if (rows.isNotEmpty()) {
            appendLine()
            appendLine("Notable rows:")
            rows.forEach { f ->
                val rssi = f.signalStrength?.let { " ${it}dBm" }.orEmpty()
                val detail = f.detail.truncate(if (compact) 36 else MAX_DETAIL_CHARS)
                appendLine("- [${f.category.label}] ${f.title.truncate(44)} — $detail$rssi")
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
