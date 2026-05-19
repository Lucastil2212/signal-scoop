package com.signalsoop.app.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.security.PermissionGuard

class BluetoothScanner(private val context: Context) {
    private val bluetoothManager =
        context.getSystemService(BluetoothManager::class.java)

    @SuppressLint("MissingPermission")
    fun scanPaired(): List<Finding> {
        if (!PermissionGuard.canAccessPairedBluetooth(context)) {
            return listOf(
                Finding(
                    id = "bt-permission",
                    category = SignalCategory.BLUETOOTH,
                    title = "Bluetooth permission required",
                    detail = "Grant Bluetooth permissions to list paired devices.",
                ),
            )
        }
        val adapter = bluetoothManager?.adapter
            ?: return listOf(
                Finding(
                    id = "bt-unsupported",
                    category = SignalCategory.BLUETOOTH,
                    title = "Bluetooth unavailable",
                    detail = "This device does not expose a Bluetooth adapter.",
                ),
            )

        if (!adapter.isEnabled) {
            return listOf(
                Finding(
                    id = "bt-disabled",
                    category = SignalCategory.BLUETOOTH,
                    title = "Bluetooth is off",
                    detail = "Turn on Bluetooth to list paired devices.",
                    riskPoints = 5,
                ),
            )
        }

        val bonded = adapter.bondedDevices.orEmpty()
        if (bonded.isEmpty()) {
            return listOf(
                Finding(
                    id = "bt-none-paired",
                    category = SignalCategory.BLUETOOTH,
                    title = "No paired devices",
                    detail = "Your phone has no bonded Bluetooth devices.",
                ),
            )
        }

        return bonded.map { device ->
            val name = device.name ?: "Unknown paired device"
            Finding(
                id = "paired-${device.address}",
                category = SignalCategory.BLUETOOTH,
                title = name,
                detail = "Address ${device.address} · Type ${device.type}",
                riskPoints = if (name.contains("unknown", ignoreCase = true)) 8 else 0,
            )
        }
    }
}
