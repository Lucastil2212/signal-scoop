package com.signalsoop.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.RiskScorer
import com.signalsoop.app.model.RiskSummary
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.scan.ScanCoordinator
import com.signalsoop.app.scan.ScanPermissions
import com.signalsoop.app.security.PermissionGuard
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
    val selectedCategory: SignalCategory = SignalCategory.ALL,
    val permissionNeeded: Boolean = false,
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = ScanCoordinator(application)
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
                )
            }

            try {
                val results = coordinator.runFullScan { message ->
                    _uiState.update { state -> state.copy(statusMessage = message) }
                }
                val risk = RiskScorer.summarize(results.filter { it.category != SignalCategory.SYSTEM })
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        findings = results,
                        riskSummary = risk,
                        statusMessage = "Last scan finished · ${results.size} findings",
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
                statusMessage = "Results cleared for your privacy.",
            )
        }
    }
}
