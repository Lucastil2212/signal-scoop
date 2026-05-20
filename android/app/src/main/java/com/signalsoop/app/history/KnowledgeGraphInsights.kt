package com.signalsoop.app.history

import com.signalsoop.app.history.db.GraphEdgeEntity
import com.signalsoop.app.history.db.GraphNodeEntity
import com.signalsoop.app.history.db.SavedScanEntity
import com.signalsoop.app.model.SignalCategory

data class KnowledgeGraphInsights(
    val totalScans: Int,
    val scansWithGps: Int,
    val uniquePlaces: Int,
    val recurringSignals: List<RecurringSignalInsight>,
    val placeSummaries: List<PlaceInsight>,
    val averageRiskScore: Int?,
    val riskTrendNote: String?,
) {
    data class RecurringSignalInsight(
        val label: String,
        val category: SignalCategory,
        val scanCount: Int,
        val detail: String,
    )

    data class PlaceInsight(
        val label: String,
        val scanCount: Int,
        val coordinates: String,
    )
}

object KnowledgeGraphInsightsEngine {
    fun from(
        scans: List<SavedScanEntity>,
        nodes: List<GraphNodeEntity>,
        edges: List<GraphEdgeEntity>,
    ): KnowledgeGraphInsights {
        val scanNodes = nodes.filter { it.nodeType == KnowledgeGraphBuilder.NODE_SCAN }
        val placeNodes = nodes.filter { it.nodeType == KnowledgeGraphBuilder.NODE_PLACE }
        val signalNodes = nodes.filter { it.nodeType == KnowledgeGraphBuilder.NODE_SIGNAL }

        val scansByPlace =
            edges
                .filter { it.relation == KnowledgeGraphBuilder.REL_AT_PLACE }
                .groupBy { it.toNodeId }

        val placeSummaries =
            placeNodes
                .map { place ->
                    val scanCount = scansByPlace[place.id]?.size ?: 0
                    KnowledgeGraphInsights.PlaceInsight(
                        label = place.label,
                        scanCount = scanCount,
                        coordinates = place.label.removePrefix("Near ").trim(),
                    )
                }
                .filter { it.scanCount > 0 }
                .sortedByDescending { it.scanCount }
                .take(6)

        val observationsBySignal =
            edges
                .filter { it.relation == KnowledgeGraphBuilder.REL_OBSERVED }
                .groupBy { it.toNodeId }

        val recurringSignals =
            signalNodes
                .mapNotNull { signal ->
                    val obs = observationsBySignal[signal.id] ?: return@mapNotNull null
                    val scanIds = obs.mapNotNull { it.scanId }.distinct()
                    if (scanIds.size < 2) return@mapNotNull null
                    val categoryName =
                        signal.metadataJson?.let { meta ->
                            runCatching {
                                org.json.JSONObject(meta).getString("category")
                            }.getOrNull()
                        }
                    val category =
                        categoryName?.let { name ->
                            runCatching { SignalCategory.valueOf(name) }.getOrNull()
                        } ?: SignalCategory.BLE
                    KnowledgeGraphInsights.RecurringSignalInsight(
                        label = signal.label,
                        category = category,
                        scanCount = scanIds.size,
                        detail = signal.metadataJson?.let {
                            runCatching {
                                org.json.JSONObject(it).optString("detail")
                            }.getOrNull()
                        }.orEmpty(),
                    )
                }
                .sortedByDescending { it.scanCount }
                .take(12)

        val riskScores = scans.mapNotNull { it.riskScore }
        val averageRisk =
            if (riskScores.isEmpty()) null else riskScores.average().toInt()

        val trendNote =
            when {
                scans.size < 2 -> null
                riskScores.size < 2 -> "Save more scans to compare risk over time."
                else -> {
                    val recent = scans.take(3).mapNotNull { it.riskScore }
                    val older = scans.drop(3).take(3).mapNotNull { it.riskScore }
                    if (recent.isEmpty() || older.isEmpty()) {
                        "Average risk across ${scans.size} scans: $averageRisk/100."
                    } else {
                        val recentAvg = recent.average().toInt()
                        val olderAvg = older.average().toInt()
                        val delta = recentAvg - olderAvg
                        when {
                            delta >= 10 -> "Recent scans trend higher risk (+$delta vs earlier average)."
                            delta <= -10 -> "Recent scans trend lower risk ($delta vs earlier average)."
                            else -> "Risk has been stable across recent scans (avg $recentAvg/100)."
                        }
                    }
                }
            }

        return KnowledgeGraphInsights(
            totalScans = scans.size,
            scansWithGps = scans.count { it.latitude != null && it.longitude != null },
            uniquePlaces = placeNodes.size,
            recurringSignals = recurringSignals,
            placeSummaries = placeSummaries,
            averageRiskScore = averageRisk,
            riskTrendNote = trendNote,
        )
    }

    fun formatDigest(insights: KnowledgeGraphInsights): String = buildString {
        appendLine("Scan history (${insights.totalScans} saved scans, ${insights.scansWithGps} with GPS)")
        insights.averageRiskScore?.let { appendLine("Average risk score: $it/100") }
        insights.riskTrendNote?.let { appendLine(it) }
        if (insights.uniquePlaces > 0) {
            appendLine("Places in graph: ${insights.uniquePlaces}")
            insights.placeSummaries.take(3).forEach { p ->
                appendLine("• ${p.label}: ${p.scanCount} scan(s)")
            }
        }
        if (insights.recurringSignals.isNotEmpty()) {
            appendLine("Recurring signals (seen in multiple scans):")
            insights.recurringSignals.take(8).forEach { s ->
                appendLine("• ${s.category.label} ${s.label} — ${s.scanCount} scans")
            }
        }
    }
}
