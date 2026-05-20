package com.signalsoop.app.history

object HistoryQueryEngine {
    private val historyKeywords =
        listOf(
            "history",
            "previous",
            "past scan",
            "over time",
            "recurring",
            "same place",
            "same location",
            "knowledge graph",
            "trend",
            "compare scans",
            "last time",
        )

    fun matches(question: String): Boolean {
        val q = question.lowercase()
        return historyKeywords.any { q.contains(it) }
    }

    fun tryAnswer(question: String, insights: KnowledgeGraphInsights): String? {
        if (!matches(question)) return null
        val digest = KnowledgeGraphInsightsEngine.formatDigest(insights)
        return buildString {
            appendLine("From your on-device scan history:")
            appendLine()
            append(digest.trimEnd())
            appendLine()
            appendLine("Open the History tab for full scan list, GPS coordinates, and saved findings.")
        }
    }
}
