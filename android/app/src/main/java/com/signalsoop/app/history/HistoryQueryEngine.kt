package com.signalsoop.app.history

object HistoryQueryEngine {
    private val historyKeywords =
        listOf(
            "history",
            "previous",
            "past scan",
            "over time",
            "recurring",
            "repeat",
            "seen before",
            "same place",
            "same location",
            "knowledge graph",
            "graph insight",
            "trend",
            "compare scans",
            "across scans",
            "last time",
            "last scan",
            "saved scan",
            "multiple scans",
        )

    fun matches(question: String): Boolean {
        val q = question.lowercase()
        return historyKeywords.any { q.contains(it) }
    }

    fun tryAnswer(question: String, insights: KnowledgeGraphInsights): String? {
        if (!matches(question)) return null
        val digest = KnowledgeGraphInsightsEngine.formatDigest(insights)
        return buildString {
            appendLine("From your on-device knowledge graph:")
            appendLine()
            append(digest.trimEnd())
            appendLine()
            appendLine("Open the Graph tab for maps, timelines, and per-scan signal lists.")
        }
    }
}
