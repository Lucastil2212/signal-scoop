package com.signalsoop.app.history

import androidx.compose.ui.graphics.Color
import com.signalsoop.app.model.SignalCategory

object GraphColorPalette {
    val place = Color(0xFF00AEEF)
    val user = Color(0xFFE040FB)
    val device = Color(0xFFFF4D6D)
    val linkObserved = Color(0xFF8BA4C4)
    val linkPlace = Color(0xFF00AEEF)
    val linkRepeat = Color(0xFFFFB020)
    val linkUser = Color(0xFFE040FB)
    val linkDevice = Color(0xFFFF4D6D)

    val ble = Color(0xFF7AE7FF)
    val wifi = Color(0xFFFFB020)
    val bluetooth = Color(0xFFFF4D6D)
    val nfc = Color(0xFFFFD54F)
    val sensors = Color(0xFFB388FF)
    val system = Color(0xFF9AA3B2)

    data class LegendEntry(val color: Color, val label: String)

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

    fun signalColorArgb(category: SignalCategory): Int =
        when (category) {
            SignalCategory.BLE -> 0xFF7AE7FF.toInt()
            SignalCategory.WIFI -> 0xFFFFB020.toInt()
            SignalCategory.BLUETOOTH -> 0xFFFF4D6D.toInt()
            SignalCategory.NFC -> 0xFFFFD54F.toInt()
            SignalCategory.SENSORS -> 0xFFB388FF.toInt()
            SignalCategory.SYSTEM -> 0xFF9AA3B2.toInt()
            else -> 0xFFB8ECFF.toInt()
        }

    fun signalColorArgb(category: String?): Int =
        category?.let { runCatching { signalColorArgb(SignalCategory.valueOf(it.uppercase())) }.getOrNull() }
            ?: 0xFFB8ECFF.toInt()

    fun riskColorArgb(score: Int): Int =
        when {
            score >= 70 -> 0xFFFF4D6D.toInt()
            score >= 45 -> 0xFFFFB020.toInt()
            score >= 20 -> 0xFFFFD54F.toInt()
            else -> 0xFF39FF14.toInt()
        }

    fun signalColor(category: String?): Color =
        when (category?.uppercase()) {
            SignalCategory.BLE.name -> ble
            SignalCategory.WIFI.name -> wifi
            SignalCategory.BLUETOOTH.name -> bluetooth
            SignalCategory.NFC.name -> nfc
            SignalCategory.SENSORS.name -> sensors
            SignalCategory.SYSTEM.name -> system
            else -> Color(0xFFB8ECFF)
        }

    fun signalLabel(category: String?): String =
        when (category?.uppercase()) {
            SignalCategory.BLE.name -> "BLE"
            SignalCategory.WIFI.name -> "Wi-Fi"
            SignalCategory.BLUETOOTH.name -> "Bluetooth"
            SignalCategory.NFC.name -> "NFC"
            SignalCategory.SENSORS.name -> "Sensor"
            SignalCategory.SYSTEM.name -> "System"
            else -> "Signal"
        }

    fun nodeTypeColor(type: String): Color =
        when (type) {
            KnowledgeGraphBuilder.NODE_SCAN -> Color(0xFF39FF14)
            KnowledgeGraphBuilder.NODE_PLACE -> place
            KnowledgeGraphBuilder.NODE_SIGNAL -> ble
            "USER" -> user
            "DEVICE" -> device
            else -> system
        }

    fun relationColor(relation: String): Color =
        when (relation) {
            KnowledgeGraphBuilder.REL_AT_PLACE -> linkPlace
            KnowledgeGraphBuilder.REL_OBSERVED -> linkObserved
            KnowledgeGraphBuilder.REL_REPEAT -> linkRepeat
            "USER_NOTE" -> linkUser
            "DEVICE_LINK" -> linkDevice
            else -> linkObserved
        }

    fun relationLabel(relation: String): String =
        when (relation) {
            KnowledgeGraphBuilder.REL_AT_PLACE -> "At place"
            KnowledgeGraphBuilder.REL_OBSERVED -> "Observed in scan"
            KnowledgeGraphBuilder.REL_REPEAT -> "Seen again"
            "USER_NOTE" -> "Your note"
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
                "This signal was seen in a prior scan and appeared again in this session."
            "USER_NOTE" -> "A note you added to the graph."
            "DEVICE_LINK" -> "A device you linked to this signal locally."
            else -> "Relationship in your local knowledge graph."
        }

    fun signalLegendEntries(): List<LegendEntry> =
        listOf(
            LegendEntry(ble, "BLE"),
            LegendEntry(wifi, "Wi-Fi"),
            LegendEntry(bluetooth, "Bluetooth"),
            LegendEntry(nfc, "NFC"),
            LegendEntry(sensors, "Sensor"),
            LegendEntry(place, "Place"),
        )

    fun linkLegendEntries(): List<LegendEntry> =
        listOf(
            LegendEntry(linkObserved, "Observed"),
            LegendEntry(linkPlace, "At place"),
            LegendEntry(linkRepeat, "Repeat"),
            LegendEntry(linkUser, "Note"),
            LegendEntry(linkDevice, "Device"),
        )

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
