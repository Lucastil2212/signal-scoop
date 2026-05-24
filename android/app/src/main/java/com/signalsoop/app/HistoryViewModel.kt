package com.signalsoop.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signalsoop.app.history.GraphColorPalette
import com.signalsoop.app.history.GraphLinkSelection
import com.signalsoop.app.history.GraphVisualization
import com.signalsoop.app.history.linkKeyToParts
import com.signalsoop.app.history.KnowledgeGraphBuilder
import com.signalsoop.app.history.KnowledgeGraphInsights
import com.signalsoop.app.history.ScanFindingCounts
import com.signalsoop.app.history.ScanHistoryRepository
import com.signalsoop.app.history.ScanReportPdfGenerator
import com.signalsoop.app.history.ScanSnapshot
import com.signalsoop.app.history.db.GraphEdgeEntity
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val snapshots: List<ScanSnapshot> = emptyList(),
    val insights: KnowledgeGraphInsights? = null,
    val vault: ScanHistoryRepository.VaultSnapshot? = null,
    val graphVisualization: GraphVisualization? = null,
    val graphEdges: List<GraphEdgeEntity> = emptyList(),
    val selectedScanId: String? = null,
    val scanDetailScanId: String? = null,
    val selectedGraphNode: String? = null,
    val graphDetailNodeId: String? = null,
    val graphDetailLink: GraphLinkSelection? = null,
    val graphFilterScanId: String? = null,
    val renameTargetId: String? = null,
    val renameDraft: String = "",
    val aliasSignalKey: String? = null,
    val aliasPetName: String = "",
    val aliasNotes: String = "",
    val showAddNoteDialog: Boolean = false,
    val addNoteScanId: String? = null,
    val addNoteLabel: String = "",
    val addNoteBody: String = "",
    val statusMessage: String = "Your knowledge graph stays on this device.",
    val reportSelectedIds: Set<String> = emptySet(),
    val reportGenerating: Boolean = false,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SignalScoopApp
    private val repository = app.scanHistoryRepository

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val snapshots: StateFlow<List<ScanSnapshot>> =
        repository.snapshots.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    init {
        viewModelScope.launch {
            repository.snapshots.collect { list ->
                val previousLatestId = _uiState.value.snapshots.firstOrNull()?.id
                val newLatestId = list.firstOrNull()?.id
                _uiState.update { it.copy(snapshots = list) }
                if (newLatestId != null && previousLatestId != null && newLatestId != previousLatestId) {
                    refreshGraphAndInsights(selectLatestScan = true)
                }
            }
        }
        refreshGraphAndInsights()
        viewModelScope.launch {
            repository.vault.collect { vault ->
                _uiState.update { it.copy(vault = vault) }
            }
        }
    }

    fun refreshGraphAndInsights(selectLatestScan: Boolean = false) {
        viewModelScope.launch {
            runCatching {
                repository.rebuildKnowledgeGraphFromHistory()
                val insights = repository.buildInsights()
                val visualization = repository.buildVisualization()
                val edges = repository.graphEdges()
                val latestScanId =
                    if (selectLatestScan) {
                        visualization.timelineScans.maxByOrNull { it.epochMs }?.scanId
                    } else {
                        null
                    }
                _uiState.update {
                    it.copy(
                        insights = insights,
                        graphVisualization = visualization,
                        graphEdges = edges,
                        graphFilterScanId = latestScanId ?: it.graphFilterScanId,
                        selectedGraphNode =
                            latestScanId?.let { id -> KnowledgeGraphBuilder.scanNodeId(id) }
                                ?: it.selectedGraphNode,
                        statusMessage = "Your knowledge graph stays on this device.",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        statusMessage =
                            "Could not refresh the knowledge graph. Try again or clear app data if this persists.",
                    )
                }
                android.util.Log.e("HistoryViewModel", "refreshGraphAndInsights failed", error)
            }
        }
    }

    fun selectScan(id: String?) {
        _uiState.update { it.copy(selectedScanId = id) }
    }

    fun openScanDetail(scanId: String) {
        val name = snapshotForScan(scanId)?.name ?: "Scan"
        val count = findingsForScan(scanId).size
        _uiState.update {
            it.copy(
                scanDetailScanId = scanId,
                selectedScanId = scanId,
                graphFilterScanId = scanId,
                statusMessage = "Viewing saved scan: $name ($count signals)",
            )
        }
    }

    fun dismissScanDetail() {
        _uiState.update { it.copy(scanDetailScanId = null) }
    }

    fun onGraphNodeSelected(nodeId: String, nodeType: String, label: String) {
        scanIdFromGraphNode(nodeId)?.let { scanId ->
            openScanDetail(scanId)
            _uiState.update { it.copy(selectedGraphNode = nodeId, graphDetailNodeId = null, graphDetailLink = null) }
            return
        }
        _uiState.update {
            it.copy(
                selectedGraphNode = nodeId,
                graphDetailNodeId = nodeId,
                graphDetailLink = null,
                statusMessage = "Node: $label",
            )
        }
    }

    fun onGraphLinkSelected(sourceId: String, targetId: String, relation: String) {
        val viz = _uiState.value.graphVisualization ?: return
        val source = viz.nodes.find { it.id == sourceId }?.label ?: sourceId
        val target = viz.nodes.find { it.id == targetId }?.label ?: targetId
        _uiState.update {
            it.copy(
                graphDetailNodeId = null,
                graphDetailLink = GraphLinkSelection(sourceId, targetId, relation),
                statusMessage = "Link: ${GraphColorPalette.relationLabel(relation)} · $source → $target",
            )
        }
    }

    fun openGraphNodeDetail(nodeId: String) {
        scanIdFromGraphNode(nodeId)?.let {
            openScanDetail(it)
            return
        }
        val label = _uiState.value.graphVisualization?.nodes?.find { it.id == nodeId }?.label ?: nodeId
        onGraphNodeSelected(nodeId, "", label)
    }

    fun dismissGraphDetail() {
        _uiState.update { it.copy(graphDetailNodeId = null, graphDetailLink = null) }
    }

    fun findLink(key: String): com.signalsoop.app.history.GraphVisLink? {
        val parts = linkKeyToParts(key) ?: return null
        return _uiState.value.graphVisualization?.links?.find {
            it.sourceId == parts.first && it.targetId == parts.second && it.relation == parts.third
        }
    }

    fun setGraphTimelineFilter(scanId: String?) {
        val scanNodeId = scanId?.let { KnowledgeGraphBuilder.scanNodeId(it) }
        _uiState.update {
            it.copy(
                graphFilterScanId = scanId,
                selectedGraphNode = scanNodeId,
                graphDetailNodeId = null,
                graphDetailLink = null,
                scanDetailScanId = if (scanId == null) null else it.scanDetailScanId,
            )
        }
    }

    fun scanIdFromGraphNode(nodeId: String): String? =
        if (nodeId.startsWith("scan:")) nodeId.removePrefix("scan:") else null

    fun signalKeyFromGraphNode(nodeId: String): String? =
        if (nodeId.startsWith("signal:")) nodeId.removePrefix("signal:") else null

    fun snapshotForScan(scanId: String): ScanSnapshot? =
        _uiState.value.snapshots.find { it.id == scanId }

    fun findingsForScan(scanId: String): List<Finding> =
        snapshotForScan(scanId)?.findings?.let { KnowledgeGraphBuilder.displayableFindings(it) }.orEmpty()

    fun findingCountsForScan(scanId: String): ScanFindingCounts =
        ScanFindingCounts.from(snapshotForScan(scanId)?.findings.orEmpty())

    fun findingsForSignalKey(signalKey: String): List<Finding> =
        _uiState.value.snapshots.flatMap { scan ->
            scan.findings.filter { signalKeyFromFinding(it) == signalKey }
        }.distinctBy { "${it.id}-${it.detail}" }

    fun scanIdsForSignal(signalKey: String): List<String> {
        val signalNodeId = "signal:$signalKey"
        return _uiState.value.graphEdges
            .filter { it.relation == KnowledgeGraphBuilder.REL_OBSERVED && it.toNodeId == signalNodeId }
            .mapNotNull { scanIdFromGraphNode(it.fromNodeId) }
            .distinct()
    }

    fun scanIdsAtPlace(placeNodeId: String): List<String> =
        _uiState.value.graphEdges
            .filter { it.relation == KnowledgeGraphBuilder.REL_AT_PLACE && it.toNodeId == placeNodeId }
            .mapNotNull { scanIdFromGraphNode(it.fromNodeId) }

    private fun signalKeyFromFinding(finding: Finding): String? =
        KnowledgeGraphBuilder.graphSignalKeyFrom(finding)

    fun beginRename(id: String, currentName: String) {
        _uiState.update { it.copy(renameTargetId = id, renameDraft = currentName) }
    }

    fun setRenameDraft(text: String) {
        _uiState.update { it.copy(renameDraft = text) }
    }

    fun commitRename() {
        val id = _uiState.value.renameTargetId ?: return
        val name = _uiState.value.renameDraft.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            repository.renameScan(id, name)
            _uiState.update {
                it.copy(renameTargetId = null, renameDraft = "", statusMessage = "Renamed scan.")
            }
            refreshGraphAndInsights()
        }
    }

    fun cancelRename() {
        _uiState.update { it.copy(renameTargetId = null, renameDraft = "") }
    }

    fun deleteScan(id: String) {
        viewModelScope.launch {
            repository.deleteScan(id)
            _uiState.update {
                it.copy(
                    selectedScanId = if (it.selectedScanId == id) null else it.selectedScanId,
                    scanDetailScanId = if (it.scanDetailScanId == id) null else it.scanDetailScanId,
                    statusMessage = "Scan removed from local history.",
                )
            }
            refreshGraphAndInsights()
        }
    }

    fun beginAlias(signalKey: String, currentPetName: String?) {
        _uiState.update {
            it.copy(
                aliasSignalKey = signalKey,
                aliasPetName = currentPetName.orEmpty(),
                aliasNotes = "",
            )
        }
    }

    fun setAliasPetName(text: String) {
        _uiState.update { it.copy(aliasPetName = text) }
    }

    fun setAliasNotes(text: String) {
        _uiState.update { it.copy(aliasNotes = text) }
    }

    fun commitAlias() {
        val key = _uiState.value.aliasSignalKey ?: return
        viewModelScope.launch {
            repository.setSignalAlias(key, _uiState.value.aliasPetName, _uiState.value.aliasNotes)
            _uiState.update {
                it.copy(aliasSignalKey = null, statusMessage = "Pet name saved for signal.")
            }
            refreshGraphAndInsights()
        }
    }

    fun cancelAlias() {
        _uiState.update { it.copy(aliasSignalKey = null) }
    }

    fun beginAddNote(scanId: String? = null) {
        _uiState.update {
            it.copy(
                showAddNoteDialog = true,
                addNoteScanId = scanId,
                addNoteLabel = "",
                addNoteBody = "",
            )
        }
    }

    fun setAddNoteLabel(text: String) {
        _uiState.update { it.copy(addNoteLabel = text) }
    }

    fun setAddNoteBody(text: String) {
        _uiState.update { it.copy(addNoteBody = text) }
    }

    fun commitAddNote(linkedSignalKey: String? = null) {
        val label = _uiState.value.addNoteLabel.trim()
        val body = _uiState.value.addNoteBody.trim()
        if (label.isEmpty() || body.isEmpty()) return
        viewModelScope.launch {
            repository.addUserGraphNote(
                label = label,
                body = body,
                linkedScanId = _uiState.value.addNoteScanId,
                linkedSignalKey = linkedSignalKey,
            )
            _uiState.update {
                it.copy(
                    showAddNoteDialog = false,
                    addNoteScanId = null,
                    addNoteLabel = "",
                    addNoteBody = "",
                    statusMessage = "Added to knowledge graph.",
                )
            }
            refreshGraphAndInsights()
        }
    }

    fun cancelAddNote() {
        _uiState.update {
            it.copy(showAddNoteDialog = false, addNoteScanId = null, addNoteLabel = "", addNoteBody = "")
        }
    }

    fun linkDevice(signalKey: String, address: String, label: String) {
        viewModelScope.launch {
            repository.linkDevice(signalKey, address, label, isPaired = false)
            _uiState.update { it.copy(statusMessage = "Device linked in graph (local metadata).") }
            refreshGraphAndInsights()
        }
    }

    fun aliasForKey(key: String, vault: ScanHistoryRepository.VaultSnapshot?): String? =
        vault?.aliases?.find { it.signalKey == key }?.petName

    fun toggleReportSelection(scanId: String) {
        _uiState.update { state ->
            val next =
                if (scanId in state.reportSelectedIds) {
                    state.reportSelectedIds - scanId
                } else {
                    state.reportSelectedIds + scanId
                }
            state.copy(reportSelectedIds = next)
        }
    }

    fun selectAllForReport() {
        _uiState.update {
            it.copy(reportSelectedIds = it.snapshots.map { s -> s.id }.toSet())
        }
    }

    fun clearReportSelection() {
        _uiState.update { it.copy(reportSelectedIds = emptySet()) }
    }

    fun generateReportPdf(onReady: (java.io.File) -> Unit, onError: (String) -> Unit) {
        val ids = _uiState.value.reportSelectedIds
        if (ids.isEmpty()) {
            onError("Select at least one scan for the report.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(reportGenerating = true) }
            runCatching {
                val selected = _uiState.value.snapshots.filter { it.id in ids }
                val reportInsights = repository.buildInsightsForReport()
                ScanReportPdfGenerator.write(getApplication(), selected, reportInsights)
            }.onSuccess { file ->
                _uiState.update {
                    it.copy(
                        reportGenerating = false,
                        statusMessage = "PDF report ready · ${file.name}",
                    )
                }
                onReady(file)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        reportGenerating = false,
                        statusMessage = err.message ?: "PDF export failed",
                    )
                }
                onError(err.message ?: "PDF export failed")
            }
        }
    }
}
