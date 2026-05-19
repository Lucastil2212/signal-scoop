package com.signalsoop.app.scan

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.security.PermissionGuard
import com.signalsoop.app.security.ScanPolicy
import android.location.LocationManager
import android.provider.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class WifiScanner(private val context: Context) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @SuppressLint("MissingPermission")
    suspend fun scan(timeoutMs: Long = ScanPolicy.MAX_WIFI_WAIT_MS): List<Finding> {
        if (!PermissionGuard.canScanWifi(context)) {
            return listOf(
                Finding(
                    id = "wifi-permission",
                    category = SignalCategory.WIFI,
                    title = "Wi-Fi permission required",
                    detail = "Grant Wi-Fi and location permissions to scan access points.",
                ),
            )
        }

        if (!isLocationEnabled()) {
            return listOf(
                Finding(
                    id = "wifi-location-off",
                    category = SignalCategory.WIFI,
                    title = "Location services off",
                    detail = "Enable device location in system settings for Wi-Fi scan results on this phone.",
                    riskPoints = 3,
                ),
            )
        }

        if (!wifiManager.isWifiEnabled) {
            return listOf(
                Finding(
                    id = "wifi-disabled",
                    category = SignalCategory.WIFI,
                    title = "Wi-Fi is off",
                    detail = "Turn on Wi-Fi to scan for nearby access points.",
                    riskPoints = 5,
                ),
            )
        }

        val results = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        context.unregisterReceiver(this)
                        cont.resume(wifiManager.scanResults)
                    }
                }

                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )

                cont.invokeOnCancellation {
                    runCatching { context.unregisterReceiver(receiver) }
                }

                val started = wifiManager.startScan()
                if (!started) {
                    context.unregisterReceiver(receiver)
                    cont.resume(wifiManager.scanResults)
                }
            }
        } ?: wifiManager.scanResults

        if (results.isEmpty()) {
            return listOf(
                Finding(
                    id = "wifi-empty",
                    category = SignalCategory.WIFI,
                    title = "No Wi-Fi networks found",
                    detail = "Scan returned no results. Android may be throttling scans or location may be off.",
                    riskPoints = 3,
                ),
            )
        }

        return results.map { ap ->
            val ssid = ap.SSID.trim('"').ifBlank { "Hidden SSID" }
            val hidden = ssid.equals("<unknown ssid>", ignoreCase = true) ||
                ssid.equals("Hidden SSID", ignoreCase = true) ||
                ap.SSID.isBlank()
            val risk = when {
                hidden -> 15
                ap.level > -50 -> 10
                ap.level > -65 -> 5
                else -> 2
            }

            Finding(
                id = "wifi-${ap.BSSID}",
                category = SignalCategory.WIFI,
                title = if (hidden) "Hidden network" else ssid,
                detail = "${ap.BSSID} · ${ap.level} dBm · ${ap.capabilities}",
                signalStrength = ap.level,
                riskPoints = risk,
            )
        }.sortedByDescending { it.signalStrength ?: Int.MIN_VALUE }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF,
            ) != Settings.Secure.LOCATION_MODE_OFF
        }
    }
}
