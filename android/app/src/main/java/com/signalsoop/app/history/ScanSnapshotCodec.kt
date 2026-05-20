package com.signalsoop.app.history

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskLevel
import com.signalsoop.app.model.RiskSummary
import com.signalsoop.app.model.SignalCategory
import org.json.JSONArray
import org.json.JSONObject

object ScanSnapshotCodec {
    fun encodeFindings(findings: List<Finding>): String {
        val array = JSONArray()
        findings.forEach { f ->
            array.put(
                JSONObject()
                    .put("id", f.id)
                    .put("category", f.category.name)
                    .put("title", f.title)
                    .put("detail", f.detail)
                    .put("signalStrength", f.signalStrength ?: JSONObject.NULL)
                    .put("riskPoints", f.riskPoints),
            )
        }
        return array.toString()
    }

    fun decodeFindings(json: String): List<Finding> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    Finding(
                        id = o.getString("id"),
                        category = SignalCategory.valueOf(o.getString("category")),
                        title = o.getString("title"),
                        detail = o.getString("detail"),
                        signalStrength =
                            if (o.isNull("signalStrength")) null else o.getInt("signalStrength"),
                        riskPoints = o.optInt("riskPoints", 0),
                    ),
                )
            }
        }
    }

    fun encodeRiskHighlights(highlights: List<String>): String {
        val array = JSONArray()
        highlights.forEach { array.put(it) }
        return array.toString()
    }

    fun decodeRiskHighlights(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) add(array.getString(i))
        }
    }

    fun riskSummaryFrom(
        score: Int?,
        levelName: String?,
        highlightsJson: String,
    ): RiskSummary? {
        if (score == null || levelName == null) return null
        val level = runCatching { RiskLevel.valueOf(levelName) }.getOrNull() ?: return null
        return RiskSummary(
            level = level,
            score = score,
            highlights = decodeRiskHighlights(highlightsJson),
        )
    }
}
