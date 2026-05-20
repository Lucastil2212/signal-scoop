package com.signalsoop.app.history

import androidx.compose.ui.graphics.Color
import com.signalsoop.app.model.SignalCategory

object GraphColorPalette {
    val place = Color(0xFF00AEEF)
    val user = Color(0xFFFFB020)
    val evrus = Color(0xFF7B61FF)
    val device = Color(0xFFFF4D6D)
    val linkObserved = Color(0xFF8BA4C4)
    val linkPlace = Color(0xFF00AEEF)
    val linkRepeat = Color(0xFFFFB020)
    val linkUser = Color(0xFFFFB020)
    val linkEvrus = Color(0xFF7B61FF)
    val linkDevice = Color(0xFFFF4D6D)

    private val scanPalette =
        listOf(
            Color(0xFF39FF14),
            Color(0xFF00AEEF),
            Color(0xFFFFB020),
            Color(0xFFFF4D6D),
            Color(0xFF7B61FF),
            Color(0xFF7AE7FF),
            Color(0xFFE040FB),
            Color(0xFF69F0AE),
        )

    fun scanColor(index: Int, total: Int): Color = scanPalette[index.coerceAtLeast(0) % scanPalette.size]

    fun signalColor(category: String?): Color =
        when (category?.uppercase()) {
            SignalCategory.BLE.name -> Color(0xFF7AE7FF)
            SignalCategory.WIFI.name -> Color(0xFFFFB020)
            SignalCategory.BLUETOOTH.name -> Color(0xFFFF4D6D)
            else -> Color(0xFFB8ECFF)
        }

    fun signalLabel(category: String?): String =
        when (category?.uppercase()) {
            SignalCategory.BLE.name -> "BLE"
            SignalCategory.WIFI.name -> "Wi-Fi"
            SignalCategory.BLUETOOTH.name -> "Bluetooth"
            else -> "Signal"
        }

    fun nodeTypeColor(type: String): Color =
        when (type) {
            KnowledgeGraphBuilder.NODE_SCAN -> Color(0xFF39FF14)
            KnowledgeGraphBuilder.NODE_PLACE -> place
            KnowledgeGraphBuilder.NODE_SIGNAL -> Color(0xFF7AE7FF)
            "USER" -> user
            "EVRUS" -> evrus
            "DEVICE" -> device
            else -> Color(0xFF9AA3B2)
        }

    fun relationColor(relation: String): Color =
        when (relation) {
            KnowledgeGraphBuilder.REL_AT_PLACE -> linkPlace
            KnowledgeGraphBuilder.REL_OBSERVED -> linkObserved
            KnowledgeGraphBuilder.REL_REPEAT -> linkRepeat
            "USER_NOTE" -> linkUser
            "EVRUS_ID" -> linkEvrus
            "DEVICE_LINK" -> linkDevice
            else -> linkObserved
        }

    fun relationLabel(relation: String): String =
        when (relation) {
            KnowledgeGraphBuilder.REL_AT_PLACE -> "At place"
            KnowledgeGraphBuilder.REL_OBSERVED -> "Observed in scan"
            KnowledgeGraphBuilder.REL_REPEAT -> "Seen again"
            "USER_NOTE" -> "Your note"
            "EVRUS_ID" -> "EVRUS link"
            "DEVICE_LINK" -> "Device link"
            else -> relation
        }

    fun relationDescription(relation: String): String =
        when (relation) {
            KnowledgeGraphBuilder.REL_AT_PLACE ->
                "This scan was recorded at the linked place coordinates."
            KnowledgeGraphBuilder.REL_OBSERVED ->
                "This signal was detected during the linked scan session."
            KnowledgeGraphBuilder.REL_REPEAT ->
                "This signal appeared in more than one scan (recurring)."
            "USER_NOTE" -> "A note you added to the graph."
            "EVRUS_ID" -> "Local EVRUS identity anchor for this node."
            "DEVICE_LINK" -> "A device you linked to this signal locally."
            else -> "Relationship in your local knowledge graph."
        }

    fun alphaForEpoch(epochMs: Long?, timeMin: Long, timeMax: Long, focused: Boolean): Float {
        if (focused) return 1f
        if (epochMs == null || timeMax <= timeMin) return 0.95f
        val t = (epochMs - timeMin).toFloat() / (timeMax - timeMin).toFloat()
        return 0.5f + t.coerceIn(0f, 1f) * 0.5f
    }
}

fun GraphVisLink.key(): String = "$sourceId|$targetId|$relation"

fun linkKeyToParts(key: String): Triple<String, String, String>? {
    val parts = key.split("|", limit = 3)
    if (parts.size != 3) return null
    return Triple(parts[0], parts[1], parts[2])
}
