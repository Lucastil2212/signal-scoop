package com.signalsoop.app.history.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_scans")
data class SavedScanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scannedAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val locationCapturedAtEpochMs: Long?,
    val locationProvider: String?,
    val findingsJson: String,
    val riskScore: Int?,
    val riskLevel: String?,
    val riskHighlightsJson: String,
    val placeKey: String?,
)
