package com.signalsoop.app.scan

import android.content.Context
import android.nfc.NfcAdapter
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory

class NfcScanner(private val context: Context) {
    fun scan(): List<Finding> {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        val (title, detail, risk) = when {
            adapter == null -> Triple(
                "NFC not supported",
                "This device has no NFC hardware.",
                0,
            )
            adapter.isEnabled -> Triple(
                "NFC ready",
                "NFC hardware is present and enabled.",
                0,
            )
            else -> Triple(
                "NFC disabled",
                "NFC hardware exists but is turned off in system settings.",
                5,
            )
        }

        return listOf(
            Finding(
                id = "nfc-status",
                category = SignalCategory.NFC,
                title = title,
                detail = detail,
                riskPoints = risk,
            ),
        )
    }
}
