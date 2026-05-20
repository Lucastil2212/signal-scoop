package com.signalsoop.app.scan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.signalsoop.app.history.ScanGeoFix
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Captures a one-shot GPS fix using the platform [LocationManager] (no network geolocation APIs).
 */
class GpsLocationCapture(private val context: Context) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    suspend fun capture(timeoutMs: Long = 15_000L): ScanGeoFix? {
        if (!hasLocationPermission()) return null
        if (!isGpsEnabled()) return null

        val fromCurrent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestCurrentLocation(timeoutMs)
            } else {
                null
            }

        val fix = fromCurrent ?: requestSingleUpdate(timeoutMs) ?: bestRecentGpsFix()
        return fix?.toGeoFix()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun isGpsEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(timeoutMs: Long): Location? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                locationManager.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    signal,
                    Executors.newSingleThreadExecutor(),
                ) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            }
        }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(timeoutMs: Long): Location? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener =
                    object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locationManager.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) =
                            Unit

                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) {
                            locationManager.removeUpdates(this)
                            if (cont.isActive) cont.resume(null)
                        }
                    }

                cont.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }

                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        }

    @SuppressLint("MissingPermission")
    private fun bestRecentGpsFix(): Location? {
        val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (gps != null && ageMs(gps) < MAX_STALE_MS) return gps
        return null
    }

    private fun Location.toGeoFix(): ScanGeoFix =
        ScanGeoFix(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else Float.NaN,
            altitudeMeters = if (hasAltitude()) altitude else null,
            capturedAtEpochMs = time,
            provider = provider ?: LocationManager.GPS_PROVIDER,
        )

    private fun ageMs(location: Location): Long =
        (System.currentTimeMillis() - location.time).coerceAtLeast(0L)

    companion object {
        private const val MAX_STALE_MS = 120_000L
    }
}
