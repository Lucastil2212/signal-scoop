package com.signalsoop.app.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.FindingExtras
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
            val typeLabel = bluetoothTypeLabel(device.type)
            Finding(
                id = "paired-${device.address}",
                category = SignalCategory.BLUETOOTH,
                title = name,
                detail = "Address ${device.address} · $typeLabel",
                riskPoints = if (name.contains("unknown", ignoreCase = true)) 8 else 0,
                extras =
                    FindingExtras(
                        bluetoothBondState = bondStateLabel(device.bondState),
                        bluetoothDeviceClass = device.bluetoothClass?.let { formatDeviceClass(it) },
                        bluetoothDeviceType = typeLabel,
                    ),
            )
        }
    }

    private fun bondStateLabel(state: Int): String =
        when (state) {
            BluetoothDevice.BOND_BONDED -> "bonded"
            BluetoothDevice.BOND_BONDING -> "bonding"
            BluetoothDevice.BOND_NONE -> "not bonded"
            else -> "unknown"
        }

    private fun bluetoothTypeLabel(type: Int): String =
        when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual mode"
            BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
            else -> "Type $type"
        }

    @Suppress("DEPRECATION")
    private fun formatDeviceClass(deviceClass: android.bluetooth.BluetoothClass): String {
        val major = deviceClass.majorDeviceClass
        val majorLabel =
            when (major) {
                android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/video"
                android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> "Computer"
                android.bluetooth.BluetoothClass.Device.Major.PHONE -> "Phone"
                android.bluetooth.BluetoothClass.Device.Major.WEARABLE -> "Wearable"
                android.bluetooth.BluetoothClass.Device.Major.TOY -> "Toy"
                android.bluetooth.BluetoothClass.Device.Major.HEALTH -> "Health"
                android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
                android.bluetooth.BluetoothClass.Device.Major.IMAGING -> "Imaging"
                else -> "Major $major"
            }
        return "$majorLabel (0x${deviceClass.deviceClass.toString(16)})"
    }
}
