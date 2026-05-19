package com.signalsoop.app.assistant

import com.signalsoop.app.llm.MpLlmInference
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskSummary

enum class AnswerSource { LOCAL, LLM }

data class AssistantAnswer(
    val text: String,
    val source: AnswerSource,
)

/**
 * Hybrid assistant: structured scan data for reliable basics; on-device LLM for open questions.
 */
class ScanAssistant(
    private val llm: MpLlmInference,
) {
    suspend fun respond(
        question: String,
        findings: List<Finding>,
        riskSummary: RiskSummary?,
    ): AssistantAnswer {
        val analytics = ScanAnalytics.from(findings, riskSummary)
        val intent = QueryClassifier.classify(question)

        ScanQueryEngine.tryAnswer(intent, analytics)?.let { local ->
            return AssistantAnswer(local.trim(), AnswerSource.LOCAL)
        }

        if (!llm.isLoaded()) {
            return AssistantAnswer(
                buildString {
                    appendLine(ScanQueryEngine.fallbackSummary(analytics))
                    appendLine()
                    append("For custom questions, load a .task model (Pick model or Download .task).")
                },
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
                    taskHint = inferTaskHint(question),
                )
            AssistantAnswer(text.trim(), AnswerSource.LLM)
        } catch (err: Throwable) {
            AssistantAnswer(
                buildString {
                    appendLine(ScanQueryEngine.fallbackSummary(analytics))
                    appendLine()
                    appendLine("(On-device model failed: ${err.message ?: "error"})")
                    append("Try SmolLM-135M, a shorter question, or “summarize scan”.")
                },
                AnswerSource.LOCAL,
            )
        }
    }

    private fun inferTaskHint(question: String): String {
        val q = question.lowercase()
        return when {
            q.contains("summar") -> "Summarize this scan in 3-6 bullet points."
            q.contains("analy") || q.contains("assess") -> "Analyze what the scan suggests; note limits of passive survey."
            q.contains("risk") || q.contains("danger") || q.contains("safe") ->
                "Explain the risk score and main concerns in plain language."
            q.contains("explain") -> "Explain for a non-expert using only these facts."
            q.contains("compare") || q.contains("versus") -> "Compare categories (BLE vs Wi-Fi etc.) from the facts."
            else -> "Answer the question using only the scan facts. Be concise; use bullet points if listing devices."
        }
    }
}
