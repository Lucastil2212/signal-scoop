package com.signalsoop.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.signalsoop.app.scan.ScanPermissions

object PermissionGuard {
    fun hasAllRequired(context: Context): Boolean =
        ScanPermissions.missing(context).isEmpty()

    fun canScanBle(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return isGranted(context, android.Manifest.permission.BLUETOOTH_SCAN)
        }
        return isGranted(context, android.Manifest.permission.BLUETOOTH) &&
            isGranted(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun canAccessPairedBluetooth(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return isGranted(context, android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        return isGranted(context, android.Manifest.permission.BLUETOOTH)
    }

    fun canScanWifi(context: Context): Boolean {
        val wifiState = isGranted(context, android.Manifest.permission.ACCESS_WIFI_STATE) &&
            isGranted(context, android.Manifest.permission.CHANGE_WIFI_STATE)
        if (!wifiState) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearby = isGranted(context, android.Manifest.permission.NEARBY_WIFI_DEVICES)
            if (nearby) return true
        }

        return isGranted(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
