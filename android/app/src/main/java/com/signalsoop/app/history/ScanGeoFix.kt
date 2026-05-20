package com.signalsoop.app.history

/** GPS fix captured at scan time via the device location stack (on-device only). */
data class ScanGeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double?,
    val capturedAtEpochMs: Long,
    val provider: String,
) {
    fun formatCoordinates(): String =
        String.format("%.5f, %.5f", latitude, longitude)

    fun formatAccuracy(): String =
        if (accuracyMeters.isFinite() && accuracyMeters > 0f) {
            "±${accuracyMeters.toInt()} m"
        } else {
            "accuracy unknown"
        }
}
