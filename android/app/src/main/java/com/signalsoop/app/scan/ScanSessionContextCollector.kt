package com.signalsoop.app.scan

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.nfc.NfcAdapter
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.signalsoop.app.model.ScanSessionContext

object ScanSessionContextCollector {
    fun collect(
        context: Context,
        scanDurationMs: Long,
    ): ScanSessionContext =
        runCatching { collectUnsafe(context, scanDurationMs) }
            .getOrElse { fallbackContext(scanDurationMs) }

    private fun collectUnsafe(
        context: Context,
        scanDurationMs: Long,
    ): ScanSessionContext {
        val app = context.applicationContext
        val wifiManager = app.getSystemService(WifiManager::class.java)
        val bluetoothManager = app.getSystemService(BluetoothManager::class.java)
        val nfcAdapter = NfcAdapter.getDefaultAdapter(app)

        return ScanSessionContext(
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            scanDurationMs = scanDurationMs,
            permissionsGranted = grantedPermissionLabels(app),
            airplaneModeOn = isAirplaneModeOn(app),
            wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrElse { false },
            bluetoothEnabled = runCatching { bluetoothManager?.adapter?.isEnabled == true }.getOrElse { false },
            nfcEnabled = runCatching { nfcAdapter?.isEnabled == true }.getOrElse { false },
            vpnActive = isVpnActive(app),
        )
    }

    private fun fallbackContext(scanDurationMs: Long): ScanSessionContext =
        ScanSessionContext(
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            scanDurationMs = scanDurationMs,
            permissionsGranted = emptyList(),
            airplaneModeOn = false,
            wifiEnabled = false,
            bluetoothEnabled = false,
            nfcEnabled = false,
            vpnActive = null,
        )

    private fun grantedPermissionLabels(context: Context): List<String> =
        ScanPermissions.required()
            .filter { isGranted(context, it) }
            .map { permission ->
                when {
                    permission.contains("BLUETOOTH_SCAN") -> "bluetooth_scan"
                    permission.contains("BLUETOOTH_CONNECT") -> "bluetooth_connect"
                    permission.contains("BLUETOOTH") -> "bluetooth"
                    permission.contains("LOCATION") -> "location"
                    permission.contains("WIFI") -> "wifi"
                    permission.contains("NEARBY_WIFI") -> "nearby_wifi"
                    else -> permission.substringAfterLast('.').lowercase()
                }
            }
            .distinct()
            .sorted()

    private fun isGranted(context: Context, permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun isAirplaneModeOn(context: Context): Boolean =
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) != 0

    private fun isVpnActive(context: Context): Boolean? {
        if (!hasNetworkStatePermission(context)) return null
        return runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }.getOrNull()
    }

    private fun hasNetworkStatePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) ==
            PackageManager.PERMISSION_GRANTED
}
