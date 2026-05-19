package com.signalsoop.app.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object ScanPermissions {
    fun required(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_WIFI_STATE)
        add(Manifest.permission.CHANGE_WIFI_STATE)
        add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    fun missing(context: Context): Array<String> =
        required().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun rationaleFor(permission: String): String = when (permission) {
        Manifest.permission.ACCESS_FINE_LOCATION ->
            "Android requires location permission for Wi-Fi and BLE scans on many devices. " +
                "Signal Scoop does not upload your location."
        Manifest.permission.NEARBY_WIFI_DEVICES ->
            "Allows listing nearby Wi-Fi networks without using Bluetooth for location."
        Manifest.permission.BLUETOOTH_SCAN ->
            "Needed to detect nearby Bluetooth Low Energy devices during a scan."
        Manifest.permission.BLUETOOTH_CONNECT ->
            "Needed to read names of Bluetooth devices already paired with your phone."
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN ->
            "Needed for Bluetooth scanning on this Android version."
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE ->
            "Needed to trigger a Wi-Fi survey and read nearby access points."
        else -> "Required for local signal scanning."
    }
}
