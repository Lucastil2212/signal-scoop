package com.signalsoop.app.scan

import android.content.Context
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.security.PermissionGuard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

class ScanCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val sensorScanner = SensorScanner(appContext)
    private val nfcScanner = NfcScanner(appContext)
    private val bluetoothScanner = BluetoothScanner(appContext)
    private val bleScanner = BleScanner(appContext)
    private val wifiScanner = WifiScanner(appContext)

    suspend fun runFullScan(onProgress: (String) -> Unit): ScanRunResult = coroutineScope {
        val startedAt = System.currentTimeMillis()
        if (!PermissionGuard.hasAllRequired(appContext)) {
            val ctx = ScanSessionContextCollector.collect(appContext, 0L)
            return@coroutineScope ScanRunResult(
                findings =
                    listOf(
                        Finding(
                            id = "permissions-missing",
                            category = SignalCategory.SYSTEM,
                            title = "Permissions not granted",
                            detail = "Grant all requested permissions, then run Scan again.",
                        ),
                    ),
                sessionContext = ctx,
            )
        }

        val findings = mutableListOf<Finding>()

        onProgress("Checking phone sensors…")
        findings += sensorScanner.scan()

        onProgress("Checking NFC…")
        findings += nfcScanner.scan()

        onProgress("Listing paired Bluetooth devices…")
        findings += bluetoothScanner.scanPaired()

        onProgress("Scanning BLE (about 8 seconds)…")
        val bleDeferred = async { bleScanner.scan() }
        onProgress("Scanning Wi-Fi…")
        val wifiDeferred = async { wifiScanner.scan() }

        findings += bleDeferred.await()
        findings += wifiDeferred.await()

        onProgress("Finalizing results…")
        delay(200)

        val durationMs = System.currentTimeMillis() - startedAt
        val sessionContext = ScanSessionContextCollector.collect(appContext, durationMs)

        findings += Finding(
            id = "scan-complete",
            category = SignalCategory.SYSTEM,
            title = "Scan complete",
            detail =
                "Found ${findings.size} items across local radios and sensors · " +
                    sessionContext.formatOneLiner(),
        )

        ScanRunResult(findings = findings, sessionContext = sessionContext)
    }
}
