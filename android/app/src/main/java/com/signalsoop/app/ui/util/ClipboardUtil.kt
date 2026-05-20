package com.signalsoop.app.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ClipboardUtil {
    fun copy(context: Context, label: String, value: String) {
        if (value.isBlank()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
    }

    fun copyFinding(context: Context, title: String, detail: String, rssi: Int?) {
        val text =
            buildString {
                append(title)
                append('\n')
                append(detail)
                rssi?.let { append("\nRSSI: $it dBm") }
            }
        copy(context, "finding", text)
    }

    fun copyScanSnapshot(context: Context, name: String, time: String, geo: String?, risk: String?, findingsCount: Int) {
        val text =
            buildString {
                append(name)
                append('\n')
                append(time)
                geo?.let { append("\n").append(it) }
                risk?.let { append("\nRisk: ").append(it) }
                append("\nFindings: $findingsCount")
            }
        copy(context, "scan", text)
    }
}
