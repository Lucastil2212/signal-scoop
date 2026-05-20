package com.signalsoop.app.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.FindingExtras
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.security.PermissionGuard
import com.signalsoop.app.security.ScanPolicy
import kotlinx.coroutines.delay

class BleScanner(private val context: Context) {
    private val bluetoothManager =
        context.getSystemService(BluetoothManager::class.java)

    @SuppressLint("MissingPermission")
    suspend fun scan(durationMs: Long = ScanPolicy.MAX_BLE_SCAN_MS): List<Finding> {
        if (!PermissionGuard.canScanBle(context)) {
            return listOf(
                Finding(
                    id = "ble-permission",
                    category = SignalCategory.BLE,
                    title = "Bluetooth permission required",
                    detail = "Grant Bluetooth permissions before scanning.",
                ),
            )
        }
        val adapter = bluetoothManager?.adapter
            ?: return listOf(
                Finding(
                    id = "ble-unsupported",
                    category = SignalCategory.BLE,
                    title = "BLE unavailable",
                    detail = "Bluetooth Low Energy is not supported on this device.",
                ),
            )

        if (!adapter.isEnabled) {
            return listOf(
                Finding(
                    id = "ble-disabled",
                    category = SignalCategory.BLE,
                    title = "Bluetooth is off",
                    detail = "Enable Bluetooth to scan for nearby BLE devices.",
                    riskPoints = 5,
                ),
            )
        }

        val scanner = adapter.bluetoothLeScanner
            ?: return listOf(
                Finding(
                    id = "ble-no-scanner",
                    category = SignalCategory.BLE,
                    title = "BLE scanner unavailable",
                    detail = "The system BLE scanner could not be opened.",
                ),
            )

        val collected = linkedMapOf<String, BleObservation>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val address = device.address
                val now = System.currentTimeMillis()
                val existing = collected[address]
                val observation = mergeBleResult(result, existing, now)
                collected[address] = observation
            }

            override fun onScanFailed(errorCode: Int) {
                collected["ble-error"] = BleObservation(
                    finding =
                        Finding(
                            id = "ble-error",
                            category = SignalCategory.BLE,
                            title = "BLE scan failed",
                            detail = "Android returned error code $errorCode.",
                            riskPoints = 3,
                        ),
                    firstSeenMs = System.currentTimeMillis(),
                    lastSeenMs = System.currentTimeMillis(),
                )
            }
        }

        try {
            scanner.startScan(callback)
            delay(durationMs)
        } finally {
            scanner.stopScan(callback)
        }

        return if (collected.isEmpty()) {
            listOf(
                Finding(
                    id = "ble-empty",
                    category = SignalCategory.BLE,
                    title = "No BLE devices found",
                    detail = "No nearby BLE advertisements were detected during the scan window.",
                ),
            )
        } else {
            collected.values
                .map { it.toFinding() }
                .sortedByDescending { it.signalStrength ?: Int.MIN_VALUE }
        }
    }

    @SuppressLint("MissingPermission")
    private fun mergeBleResult(
        result: ScanResult,
        existing: BleObservation?,
        now: Long,
    ): BleObservation {
        val device = result.device
        val name = device.name ?: "Unknown BLE device"
        val address = device.address
        val rssi = result.rssi
        val record = result.scanRecord
        val manufacturerId =
            record?.manufacturerSpecificData?.let { data ->
                if (data.size() > 0) data.keyAt(0) else null
            }
        val serviceUuids =
            record?.serviceUuids?.map { it.toString() }?.distinct()?.take(8)
        val txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
            ?: result.txPower.takeIf { it != Int.MIN_VALUE }
        val connectable =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else null
        val adHex =
            record?.bytes?.take(24)?.joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }

        val risk = when {
            name.contains("unknown", ignoreCase = true) -> 12
            rssi > -45 -> 10
            rssi > -60 -> 5
            else -> 2
        }

        val firstSeen = existing?.firstSeenMs ?: now
        val extras =
            FindingExtras(
                bleManufacturerId = manufacturerId,
                bleServiceUuids = serviceUuids,
                bleTxPower = txPower,
                bleAdvertisementHex = adHex,
                bleConnectable = connectable,
                firstSeenEpochMs = firstSeen,
                lastSeenEpochMs = now,
            )

        return BleObservation(
            finding =
                Finding(
                    id = "ble-$address",
                    category = SignalCategory.BLE,
                    title = name,
                    detail = "$address · $rssi dBm",
                    signalStrength = rssi,
                    riskPoints = risk,
                    extras = extras,
                ),
            firstSeenMs = firstSeen,
            lastSeenMs = now,
        )
    }

    private data class BleObservation(
        val finding: Finding,
        val firstSeenMs: Long,
        val lastSeenMs: Long,
    ) {
        fun toFinding(): Finding = finding
    }
}
