package com.signalsoop.app.history

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.FindingExtrasCodec
import com.signalsoop.app.model.RiskLevel
import com.signalsoop.app.model.RiskSummary
import com.signalsoop.app.model.ScanSessionContext
import com.signalsoop.app.model.ScanSessionContextCodec
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.util.JsonEncoding.encodeToString
import org.json.JSONArray
import org.json.JSONObject

object ScanSnapshotCodec {
    fun encodeFindings(findings: List<Finding>): String {
        val array = JSONArray()
        findings.forEach { f ->
            array.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("category", f.category.name)
                    put("title", f.title)
                    put("detail", f.detail)
                    put("signalStrength", f.signalStrength ?: JSONObject.NULL)
                    put("riskPoints", f.riskPoints)
                    FindingExtrasCodec.toJson(f.extras)?.let { put("extras", it) }
                },
            )
        }
        return array.encodeToString()
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
                        extras =
                            FindingExtrasCodec.fromJson(
                                if (o.has("extras") && !o.isNull("extras")) o.getJSONObject("extras") else null,
                            ),
                    ),
                )
            }
        }
    }

    fun encodeRiskHighlights(highlights: List<String>): String {
        val array = JSONArray()
        highlights.forEach { array.put(it) }
        return array.encodeToString()
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

    fun encodeSessionContext(ctx: ScanSessionContext?): String? =
        ctx?.let { ScanSessionContextCodec.encode(it) }

    fun decodeSessionContext(json: String?): ScanSessionContext? =
        ScanSessionContextCodec.decode(json)
}
