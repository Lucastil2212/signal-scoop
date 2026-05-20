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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    fun onPermissionsDenied() {
        refreshPermissionState()
        _uiState.update {
            it.copy(
                isScanning = false,
                statusMessage =
                    "Some permissions were denied. Open Settings and allow Bluetooth, Wi-Fi, and Location, then tap Scan again.",
            )
        }
    }

    fun selectCategory(category: SignalCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /**
     * @param onNeedPermissions Called when runtime permissions are still missing (opens system dialog).
     */
    fun startScan(onNeedPermissions: (() -> Unit)? = null) {
        clearStaleScanState()

        if (_uiState.value.isScanning) return

        if (!PermissionGuard.hasAllRequired(getApplication())) {
            refreshPermissionState()
            if (onNeedPermissions != null) {
                onNeedPermissions()
            } else {
                _uiState.update {
                    it.copy(
                        statusMessage =
                            "Grant permissions before scanning. Nothing is collected until you approve.",
                    )
                }
            }
            return
        }

        scanJob?.cancel()
        scanJob =
            viewModelScope.launch {
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
                    val locationJob: Deferred<ScanGeoFix?> =
                        async(Dispatchers.IO) {
                            withContext(Dispatchers.Main) {
                                _uiState.update { state ->
                                    state.copy(statusMessage = "Capturing GPS fix…")
                                }
                            }
                            gpsCapture.capture()
                        }

                    val scanRun =
                        withContext(Dispatchers.Default) {
                            coordinator.runFullScan { message ->
                                _uiState.update { state ->
                                    state.copy(statusMessage = message)
                                }
                            }
                        }

                    val results = scanRun.findings
                    val geoFix =
                        withTimeoutOrNull(18_000L) {
                            locationJob.await()
                        }
                    val filtered = results.filter { it.category != SignalCategory.SYSTEM }
                    val risk = RiskScorer.summarize(filtered)
                    val sentinel = DefenseSentinel.analyze(filtered, risk)
                    val saved =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                app.scanHistoryRepository.saveScan(
                                    findings = results,
                                    riskSummary = risk,
                                    geoFix = geoFix,
                                    sessionContext = scanRun.sessionContext,
                                )
                            }.onFailure { error ->
                                android.util.Log.e("ScanViewModel", "saveScan failed", error)
                            }.getOrNull()
                        }
                    val status =
                        buildString {
                            append("Last scan finished · ${results.size} findings")
                            geoFix?.let { fix ->
                                append(" · GPS ${fix.formatCoordinates()} (${fix.formatAccuracy()})")
                            } ?: append(" · GPS unavailable (enable location for coordinates)")
                            if (saved != null) {
                                append(" · saved to History")
                            } else {
                                append(" · could not save to History (results shown above)")
                            }
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
                } catch (_: CancellationException) {
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            statusMessage = "Scan cancelled.",
                        )
                    }
                } catch (error: Exception) {
                    android.util.Log.e("ScanViewModel", "scan failed", error)
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

    /** Clears sensitive in-memory results when the app is no longer visible. */
    fun clearSensitiveResults() {
        if (_uiState.value.isScanning) return
        scanJob?.cancel()
        scanJob = null
        _uiState.update {
            it.copy(
                isScanning = false,
                findings = emptyList(),
                riskSummary = null,
                lastGeoFix = null,
                lastSavedSnapshot = null,
                sentinelReport = null,
                statusMessage = "Live results cleared. Saved scans remain in History on this device.",
            )
        }
    }

    /** Recover from a cancelled job that left [ScanUiState.isScanning] stuck true. */
    private fun clearStaleScanState() {
        if (_uiState.value.isScanning && scanJob?.isActive != true) {
            _uiState.update { it.copy(isScanning = false) }
        }
    }
}
