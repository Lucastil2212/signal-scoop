package com.signalsoop.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskScorer
import com.signalsoop.app.model.RiskSummary
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.history.ScanGeoFix
import com.signalsoop.app.history.ScanSnapshot
import com.signalsoop.app.scan.GpsLocationCapture
import com.signalsoop.app.scan.ScanCoordinator
import com.signalsoop.app.scan.ScanPermissions
import com.signalsoop.app.security.DefenseSentinel
import com.signalsoop.app.security.PermissionGuard
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val isScanning: Boolean = false,
    val statusMessage: String = "Tap Scan to survey nearby signals on this device.",
    val findings: List<Finding> = emptyList(),
    val riskSummary: RiskSummary? = null,
    val lastGeoFix: ScanGeoFix? = null,
    val lastSavedSnapshot: ScanSnapshot? = null,
    val sentinelReport: DefenseSentinel.SentinelReport? = null,
    val selectedCategory: SignalCategory = SignalCategory.ALL,
    val permissionNeeded: Boolean = false,
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SignalScoopApp
    private val coordinator = ScanCoordinator(application)
    private val gpsCapture = GpsLocationCapture(application)
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()
    private var scanJob: Job? = null

    fun refreshPermissionState() {
        val missing = ScanPermissions.missing(getApplication())
        _uiState.update { it.copy(permissionNeeded = missing.isNotEmpty()) }
    }

    fun selectCategory(category: SignalCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun startScan() {
        if (_uiState.value.isScanning) return

        if (!PermissionGuard.hasAllRequired(getApplication())) {
            refreshPermissionState()
            _uiState.update {
                it.copy(
                    statusMessage = "Grant permissions before scanning. Nothing is collected until you approve.",
                )
            }
            return
        }

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    statusMessage = "Starting scan…",
                    findings = emptyList(),
                    riskSummary = null,
                    lastGeoFix = null,
                    lastSavedSnapshot = null,
                    sentinelReport = null,
                )
            }

            try {
                var locationJob: Deferred<ScanGeoFix?>? = null
                locationJob =
                    async {
                        _uiState.update { state ->
                            state.copy(statusMessage = "Capturing GPS fix…")
                        }
                        gpsCapture.capture()
                    }

                val scanRun =
                    coordinator.runFullScan { message ->
                        _uiState.update { state -> state.copy(statusMessage = message) }
                    }
                val results = scanRun.findings
                val geoFix = locationJob.await()
                val filtered = results.filter { it.category != SignalCategory.SYSTEM }
                val risk = RiskScorer.summarize(filtered)
                val sentinel = DefenseSentinel.analyze(filtered, risk)
                val saved =
                    app.scanHistoryRepository.saveScan(
                        findings = results,
                        riskSummary = risk,
                        geoFix = geoFix,
                        sessionContext = scanRun.sessionContext,
                    )
                val status =
                    buildString {
                        append("Last scan finished · ${results.size} findings")
                        geoFix?.let { fix ->
                            append(" · GPS ${fix.formatCoordinates()} (${fix.formatAccuracy()})")
                        } ?: append(" · GPS unavailable (enable location for coordinates)")
                        append(" · saved to History")
                    }
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        findings = results,
                        riskSummary = risk,
                        lastGeoFix = geoFix,
                        lastSavedSnapshot = saved,
                        sentinelReport = sentinel,
                        statusMessage = status,
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        statusMessage = "Scan failed: ${error.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false, statusMessage = "Scan cancelled.") }
    }

    /** Clears sensitive in-memory results (e.g. when app is no longer visible). */
    fun clearSensitiveResults() {
        cancelScan()
        _uiState.update {
            it.copy(
                findings = emptyList(),
                riskSummary = null,
                lastGeoFix = null,
                lastSavedSnapshot = null,
                sentinelReport = null,
                statusMessage = "Live results cleared. Saved scans remain in History on this device.",
            )
        }
    }
}
