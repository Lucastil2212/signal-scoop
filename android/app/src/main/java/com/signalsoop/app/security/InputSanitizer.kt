package com.signalsoop.app.security

/**
 * Input validation for user-provided and wire-format data (defense-in-depth).
 */
object InputSanitizer {
    const val MAX_MESH_MESSAGE_CHARS = 4_096
    const val MAX_SCAN_NAME_CHARS = 120
    const val MAX_PET_NAME_CHARS = 64
    const val MAX_GRAPH_JSON_CHARS = 512_000
    const val MAX_NOTE_CHARS = 2_000

    fun meshMessage(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_MESH_MESSAGE_CHARS) return null
        if (trimmed.any { c -> c.code < 32 && c != '\n' && c != '\t' }) return null
        return trimmed
    }

    fun scanName(text: String): String? {
        val trimmed = text.trim().take(MAX_SCAN_NAME_CHARS)
        return trimmed.takeIf { it.isNotEmpty() }
    }

    fun petName(text: String): String? {
        val trimmed = text.trim().take(MAX_PET_NAME_CHARS)
        return trimmed.takeIf { it.isNotEmpty() }
    }

    fun graphJsonForWebView(json: String): String? {
        if (json.length > MAX_GRAPH_JSON_CHARS) return null
        if (json.count { it == '{' } != json.count { it == '}' }) return null
        return json
    }

    fun wireJson(bytes: ByteArray): String? {
        if (bytes.size > MeshSecurityGuard.MAX_WIRE_FRAME_BYTES) return null
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return null
        if (text.length > MeshSecurityGuard.MAX_WIRE_FRAME_BYTES) return null
        return text
    }

    fun javascriptLabel(value: String): String =
        value.take(200).replace("\\", "").replace("\"", "'")
}
