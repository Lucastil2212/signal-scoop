package com.signalsoop.app.assistant

enum class QueryIntent {
    SUMMARY,
    ANALYZE,
    COUNT_BLE,
    COUNT_WIFI,
    COUNT_BLUETOOTH,
    LIST_UNKNOWN_BLE,
    LIST_HIDDEN_WIFI,
    LIST_STRONGEST,
    LIST_PAIRED,
    LIST_BLE,
    LIST_WIFI,
    NFC,
    HELP,
    GENERAL,
}

object QueryClassifier {
    fun classify(question: String): QueryIntent {
        val q = question.lowercase().trim()

        if (q.isEmpty()) return QueryIntent.HELP
        if (matchesAny(q, "help", "what can you", "what do you", "commands", "examples")) {
            return QueryIntent.HELP
        }
        if (containsWord(q, "nfc")) return QueryIntent.NFC

        if (matchesAny(q, "how many ble", "count ble", "number of ble", "ble count")) {
            return QueryIntent.COUNT_BLE
        }
        if (matchesAny(q, "how many wi", "how many wifi", "count wi", "wifi networks", "wi-fi count")) {
            return QueryIntent.COUNT_WIFI
        }
        if (matchesAny(q, "how many paired", "paired bluetooth", "count paired", "bonded")) {
            return QueryIntent.COUNT_BLUETOOTH
        }

        if (matchesAny(q, "unknown ble", "unnamed ble", "unnamed bluetooth low energy")) {
            return QueryIntent.LIST_UNKNOWN_BLE
        }
        if (matchesAny(q, "hidden wi", "hidden ssid", "hidden network")) {
            return QueryIntent.LIST_HIDDEN_WIFI
        }
        if (matchesAny(q, "strongest", "closest", "nearest", "best signal", "strong signal", "highest rssi")) {
            return QueryIntent.LIST_STRONGEST
        }
        if (matchesAny(q, "paired device", "bonded device", "list paired", "show paired")) {
            return QueryIntent.LIST_PAIRED
        }
        if (matchesAny(q, "list ble", "show ble", "ble devices", "nearby ble")) {
            return QueryIntent.LIST_BLE
        }
        if (matchesAny(q, "list wi", "list wifi", "show wi", "wifi networks", "access points")) {
            return QueryIntent.LIST_WIFI
        }

        if (matchesAny(q, "analyze", "analysis", "assess", "evaluate", "concern", "worried", "safe")) {
            return QueryIntent.ANALYZE
        }
        if (
            containsWord(q, "summarize") ||
            containsWord(q, "summary") ||
            containsWord(q, "overview") ||
            containsWord(q, "recap") ||
            matchesAny(q, "what did you find", "what was found", "tell me about the scan")
        ) {
            return QueryIntent.SUMMARY
        }

        return QueryIntent.GENERAL
    }

    private fun matchesAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it) }

    private fun containsWord(text: String, word: String): Boolean =
        Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(text)
}
