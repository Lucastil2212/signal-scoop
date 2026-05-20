package com.signalsoop.app.util

import org.json.JSONArray
import org.json.JSONObject

/** org.json toString() can return null on JVM unit tests; use a safe encoder. */
object JsonEncoding {
    fun JSONArray.encodeToString(): String =
        toString()?.takeIf { it.isNotBlank() && it != "null" } ?: manualArray()

    fun JSONObject.encodeToString(): String =
        toString()?.takeIf { it.isNotBlank() && it != "null" } ?: manualObject()

    private fun JSONArray.manualArray(): String =
        buildString {
            append('[')
            for (i in 0 until length()) {
                if (i > 0) append(',')
                append(valueToJson(get(i)))
            }
            append(']')
        }

    private fun JSONObject.manualObject(): String =
        buildString {
            append('{')
            val keys = keys() ?: return "{}"
            var first = true
            while (keys.hasNext()) {
                val key = keys.next()
                if (!first) append(',')
                first = false
                append(JSONObject.quote(key))
                append(':')
                append(valueToJson(get(key)))
            }
            append('}')
        }

    private fun valueToJson(value: Any?): String =
        when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> value.encodeToString()
            is JSONArray -> value.encodeToString()
            is Number, is Boolean -> value.toString()
            else -> JSONObject.quote(value.toString())
        }
}
