package com.signalsoop.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signalsoop.app.evr.EvrusConnector
import com.signalsoop.app.evr.LocalEvrusConnector
import com.signalsoop.app.history.GraphColorPalette
import com.signalsoop.app.history.GraphLinkSelection
import com.signalsoop.app.history.GraphVisualization
import com.signalsoop.app.history.linkKeyToParts
import com.signalsoop.app.history.KnowledgeGraphBuilder
import com.signalsoop.app.history.KnowledgeGraphInsights
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
    val graphJson: String = "",
    val graphVisualization: GraphVisualization? = null,
    val graphEdges: List<GraphEdgeEntity> = emptyList(),
    val selectedScanId: String? = null,
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
    val evrusCompanionAvailable: Boolean = false,
    val reportSelectedIds: Set<String> = emptySet(),
    val reportGenerating: Boolean = false,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SignalScoopApp
    private val repository = app.scanHistoryRepository
    val evrusConnector: EvrusConnector = app.evrusConnector

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val snapshots: StateFlow<List<ScanSnapshot>> =
        repository.snapshots.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    init {
        refreshGraphAndInsights()
        viewModelScope.launch {
            repository.snapshots.collect { list ->
                _uiState.update { it.copy(snapshots = list) }
                refreshGraphAndInsights()
            }
        }
        viewModelScope.launch {
            repository.vault.collect { vault ->
                _uiState.update { it.copy(vault = vault) }
            }
        }
        viewModelScope.launch {
            val available = evrusConnector.isCompanionAvailable()
            _uiState.update { it.copy(evrusCompanionAvailable = available) }
        }
    }

    fun refreshGraphAndInsights() {
        viewModelScope.launch {
            val insights = repository.buildInsights()
            val json = repository.buildGraphJson()
            val visualization = repository.buildVisualization()
            val edges = repository.graphEdges()
            _uiState.update {
                it.copy(
                    insights = insights,
                    graphJson = json,
                    graphVisualization = visualization,
                    graphEdges = edges,
                )
            }
        }
    }

    fun selectScan(id: String?) {
        _uiState.update { it.copy(selectedScanId = id) }
    }

    fun onGraphNodeSelected(nodeId: String, nodeType: String, label: String) {
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
        _uiState.update { it.copy(graphFilterScanId = scanId) }
    }

    fun scanIdFromGraphNode(nodeId: String): String? =
        if (nodeId.startsWith("scan:")) nodeId.removePrefix("scan:") else null

    fun signalKeyFromGraphNode(nodeId: String): String? =
        if (nodeId.startsWith("signal:")) nodeId.removePrefix("signal:") else null

    fun snapshotForScan(scanId: String): ScanSnapshot? =
        _uiState.value.snapshots.find { it.id == scanId }

    fun radioFindingsForScan(scanId: String): List<Finding> =
        snapshotForScan(scanId)?.findings?.filter(::isRadioFinding).orEmpty()

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

    private fun isRadioFinding(finding: Finding): Boolean =
        finding.category != SignalCategory.SYSTEM && finding.category != SignalCategory.SENSORS

    private fun signalKeyFromFinding(finding: Finding): String? =
        when {
            finding.id.startsWith("ble-") -> finding.id.removePrefix("ble-")
            finding.id.startsWith("wifi-") -> finding.id.removePrefix("wifi-")
            else -> finding.id.takeIf { finding.category == SignalCategory.BLUETOOTH }
        }

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

    fun attachMedia(filePath: String, mediaType: String, scanId: String?, signalKey: String?) {
        viewModelScope.launch {
            repository.attachMedia(
                filePath = filePath,
                mediaType = mediaType,
                scanId = scanId,
                signalKey = signalKey,
            )
            _uiState.update { it.copy(statusMessage = "Media attached to your graph.") }
            refreshGraphAndInsights()
        }
    }

    fun deleteMedia(id: String) {
        viewModelScope.launch {
            repository.deleteMedia(id)
            refreshGraphAndInsights()
        }
    }

    fun linkDevice(signalKey: String, address: String, label: String) {
        viewModelScope.launch {
            repository.linkDevice(signalKey, address, label, isPaired = false)
            _uiState.update { it.copy(statusMessage = "Device linked in graph (local metadata).") }
            refreshGraphAndInsights()
        }
    }

    fun linkEvrus(signalKey: String, petName: String?, scanId: String?) {
        viewModelScope.launch {
            evrusConnector.linkSignal(signalKey, petName, scanId)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            statusMessage =
                                "EVRUS linked · ${result.did.take(28)}… · P2P ${result.p2pPeerId?.take(16) ?: "local"}",
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(statusMessage = err.message ?: "EVRUS link failed")
                    }
                }
            refreshGraphAndInsights()
        }
    }

    fun anchorGraphToEvrmore() {
        viewModelScope.launch {
            val digest =
                (app as SignalScoopApp).let { connector ->
                    (connector.evrusConnector as? LocalEvrusConnector)?.digestOf(_uiState.value.graphJson)
                        ?: _uiState.value.graphJson.hashCode().toString()
                }
            evrusConnector.publishGraphDigest(digest)
                .onSuccess { ref ->
                    _uiState.update { it.copy(statusMessage = "Graph anchored locally · $ref") }
                }
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
                ScanReportPdfGenerator.write(getApplication(), selected, _uiState.value.insights)
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
