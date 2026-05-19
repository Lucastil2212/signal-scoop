Below is a safe Android MVP: it scans **BLE**, **Wi-Fi APs**, **paired Bluetooth devices**, **NFC availability**, and lists phone sensors. It does **not** hack, connect to, deauth, fingerprint private devices, or bypass Android permissions. Android requires `BLUETOOTH_SCAN` for BLE on Android 12+, Wi-Fi scans require location/Wi-Fi permissions and location services, and Android 13+ has `NEARBY_WIFI_DEVICES`. ([Android Developers][1])

### What it can detect

It can show:

| Source           | Detects                                                      |
| ---------------- | ------------------------------------------------------------ |
| BLE scan         | Nearby BLE beacons, earbuds, trackers, IoT devices           |
| Wi-Fi scan       | Nearby access points / hotspots                              |
| Paired Bluetooth | Devices already paired with your phone                       |
| Sensors          | Your phone’s magnetometer, light sensor, accelerometer, etc. |
| NFC              | Whether NFC hardware exists/enabled                          |

It cannot reliably detect passive microphones/cameras that are not transmitting.

---

## `AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
    <uses-feature android:name="android.hardware.nfc" android:required="false" />

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
        android:usesPermissionFlags="neverForLocation" />

    <application
        android:theme="@style/Theme.Material3.DayNight.NoActionBar"
        android:label="Bug Scanner">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

## `MainActivity.kt`

```kotlin
package com.example.bugscanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val findings = mutableStateListOf<String>()

    private lateinit var wifiManager: WifiManager
    private lateinit var sensorManager: SensorManager
    private val bluetoothManager by lazy {
        getSystemService(BluetoothManager::class.java)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            scanAll()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Bug Scanner", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))

                        Button(onClick = { requestPermissionsThenScan() }) {
                            Text("Scan nearby signals")
                        }

                        Spacer(Modifier.height(16.dp))

                        LazyColumn {
                            items(findings) { item ->
                                Card(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(item, Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissionsThenScan() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_WIFI_STATE)
            add(Manifest.permission.CHANGE_WIFI_STATE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            scanAll()
        }
    }

    private fun scanAll() {
        findings.clear()
        scanPhoneSensors()
        scanNfc()
        scanPairedBluetooth()
        scanBle()
        scanWifi()
    }

    private fun scanPhoneSensors() {
        findings += "Phone sensors detected:"

        sensorManager.getSensorList(Sensor.TYPE_ALL).forEach { sensor ->
            findings += "Sensor: ${sensor.name} | Vendor: ${sensor.vendor} | Type: ${sensor.stringType}"
        }
    }

    private fun scanNfc() {
        val nfc = NfcAdapter.getDefaultAdapter(this)

        findings += when {
            nfc == null -> "NFC: not supported"
            nfc.isEnabled -> "NFC: supported and enabled"
            else -> "NFC: supported but disabled"
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanPairedBluetooth() {
        val adapter = bluetoothManager.adapter

        if (adapter == null) {
            findings += "Bluetooth: not supported"
            return
        }

        findings += "Paired Bluetooth devices:"

        adapter.bondedDevices?.forEach {
            findings += "Paired: ${it.name ?: "Unknown"} | ${it.address} | ${it.type}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanBle() {
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            findings += "BLE: Bluetooth disabled or unavailable"
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            findings += "BLE: scanner unavailable"
            return
        }

        findings += "BLE scan started..."

        scanner.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: "Unknown BLE device"
                val address = device.address
                val rssi = result.rssi

                val line = "BLE: $name | $address | RSSI: $rssi dBm"

                if (!findings.contains(line)) {
                    findings += line
                }
            }

            override fun onScanFailed(errorCode: Int) {
                findings += "BLE scan failed: $errorCode"
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun scanWifi() {
        findings += "Wi-Fi scan requested..."

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                unregisterReceiver(this)

                val results = wifiManager.scanResults

                if (results.isEmpty()) {
                    findings += "Wi-Fi: no networks found or scan throttled"
                }

                results.forEach {
                    findings += "Wi-Fi: ${it.SSID.ifBlank { "Hidden SSID" }} | BSSID: ${it.BSSID} | RSSI: ${it.level} dBm | ${it.capabilities}"
                }
            }
        }

        registerReceiver(
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )

        val started = wifiManager.startScan()

        if (!started) {
            findings += "Wi-Fi scan could not start. Android may be throttling scans."
        }
    }
}
```

---

## `build.gradle.kts` app module

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.bugscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bugscanner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
}
```

Useful next upgrade:

add a **risk score** based on unknown BLE devices, hidden SSIDs, very strong RSSI, repeated appearances, and vendor lookup.

[1]: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions?utm_source=chatgpt.com "Bluetooth permissions | Connectivity | Android Developers"
