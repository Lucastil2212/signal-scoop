package com.signalsoop.app.assistant

import com.signalsoop.app.history.HistoryQueryEngine
import com.signalsoop.app.history.KnowledgeGraphInsights
import com.signalsoop.app.history.KnowledgeGraphInsightsEngine
import com.signalsoop.app.llm.MpLlmInference
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskSummary

enum class AnswerSource { LOCAL, LLM }

data class AssistantAnswer(
    val text: String,
    val source: AnswerSource,
)

/**
 * Hybrid assistant: structured scan/graph data for reliable basics; on-device LLM for open
 * questions with [ContextIntegrationLayer] (current scan + knowledge graph).
 */
class ScanAssistant(
    private val llm: MpLlmInference,
) {
    suspend fun respond(
        question: String,
        findings: List<Finding>,
        riskSummary: RiskSummary?,
        historyInsights: KnowledgeGraphInsights? = null,
    ): AssistantAnswer {
        val analytics = ScanAnalytics.from(findings, riskSummary)
        val intent = QueryClassifier.classify(question)

        historyInsights?.let { insights ->
            HistoryQueryEngine.tryAnswer(question, insights)?.let { local ->
                return AssistantAnswer(local.trim(), AnswerSource.LOCAL)
            }
        }

        ScanQueryEngine.tryAnswer(intent, analytics)?.let { local ->
            return AssistantAnswer(local.trim(), AnswerSource.LOCAL)
        }

        if (!llm.isLoaded()) {
            return AssistantAnswer(
                noModelAnswer(question, intent, analytics, historyInsights),
                AnswerSource.LOCAL,
            )
        }

        return try {
            val text =
                llm.generateFromScan(
                    question = question,
                    findings = findings,
                    riskSummary = riskSummary,
                    analytics = analytics,
                    historyInsights = historyInsights,
                    taskHint = inferTaskHint(question, intent),
                )
            AssistantAnswer(text.trim(), AnswerSource.LLM)
        } catch (err: Throwable) {
            AssistantAnswer(
                buildString {
                    appendLine(noModelAnswer(question, QueryIntent.GENERAL, analytics, historyInsights))
                    appendLine()
                    appendLine("(On-device model failed: ${err.message ?: "error"})")
                    append("Try SmolLM-135M, a shorter question, or a built-in command like “how many BLE devices?”.")
                },
                AnswerSource.LOCAL,
            )
        }
    }

    private fun noModelAnswer(
        question: String,
        intent: QueryIntent,
        analytics: ScanAnalytics,
        historyInsights: KnowledgeGraphInsights?,
    ): String =
        when (intent) {
            QueryIntent.SUMMARY -> ScanQueryEngine.buildSummary(analytics)
            QueryIntent.ANALYZE -> ScanQueryEngine.buildAnalysis(analytics)
            else ->
                buildString {
                    appendLine("That question needs an on-device .task model, or try a built-in command.")
                    appendLine()
                    append(ScanQueryEngine.helpText())
                    historyInsights?.takeIf { it.totalScans > 0 }?.let { insights ->
                        appendLine()
                        appendLine("Knowledge graph snapshot:")
                        append(KnowledgeGraphInsightsEngine.formatDigest(insights).trimEnd())
                    }
                    appendLine()
                    append("Your question: \"${question.trim()}\"")
                }
        }

    private fun inferTaskHint(question: String, intent: QueryIntent): String {
        val q = question.lowercase()
        return when {
            intent == QueryIntent.SUMMARY || q.contains("summar") ->
                "Summarize this scan in 3-6 bullet points."
            intent == QueryIntent.ANALYZE || q.contains("analy") || q.contains("assess") ->
                "Analyze what the scan suggests; note limits of passive survey."
            q.contains("risk") || q.contains("danger") || q.contains("safe") ->
                "Explain the risk score and main concerns in plain language."
            q.contains("explain") -> "Explain for a non-expert using only these facts."
            q.contains("compare") || q.contains("versus") || q.contains("recurring") || q.contains("history") ->
                "Use knowledge-graph history and current scan facts; compare across scans when relevant."
            else ->
                "Answer only the question asked. Do not output a full scan summary unless explicitly requested."
        }
    }
}
