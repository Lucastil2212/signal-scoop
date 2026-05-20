package com.signalsoop.app.assistant

import com.signalsoop.app.history.KnowledgeGraphInsights
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory

/**
 * Builds compact LLM prompts via [ContextIntegrationLayer] (scan + knowledge graph).
 */
object SignalContextBuilder {
    private const val MAX_NOTABLE_ROWS = 12

    fun buildWithinTokenBudget(
        question: String,
        findings: List<Finding>,
        analytics: ScanAnalytics,
        historyInsights: KnowledgeGraphInsights?,
        taskHint: String,
        countTokens: (String) -> Int,
        maxInputTokens: Int,
    ): String {
        val layers = ContextIntegrationLayer.build(analytics, findings, historyInsights)
        var rowLimit = layers.notableRows.size.coerceAtMost(MAX_NOTABLE_ROWS)
        var includeHistory = layers.historyDigest != null

        while (rowLimit >= 2) {
            val prompt =
                ContextIntegrationLayer.assemblePrompt(
                    layers = layers,
                    question = question,
                    taskHint = taskHint,
                    rowLimit = rowLimit,
                    compact = false,
                    includeHistory = includeHistory,
                )
            if (countTokens(prompt) <= maxInputTokens) return prompt
            if (includeHistory) {
                includeHistory = false
                continue
            }
            rowLimit = (rowLimit * 3) / 4
        }

        return ContextIntegrationLayer.assemblePrompt(
            layers = layers,
            question = question,
            taskHint = taskHint,
            rowLimit = 2,
            compact = true,
            includeHistory = false,
        )
    }

    internal fun selectNotableRowsForIntegration(findings: List<Finding>): List<Finding> =
        selectNotableRows(findings)

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
}
