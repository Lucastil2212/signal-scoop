package com.signalsoop.app.history

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskSummary

/** A persisted scan session with metadata for the local knowledge graph. */
data class ScanSnapshot(
    val id: String,
    val name: String,
    val scannedAtEpochMs: Long,
    val geoFix: ScanGeoFix?,
    val findings: List<Finding>,
    val riskSummary: RiskSummary?,
)
