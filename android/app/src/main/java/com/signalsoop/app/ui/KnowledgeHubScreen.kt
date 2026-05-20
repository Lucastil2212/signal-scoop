package com.signalsoop.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.signalsoop.app.HistoryViewModel
import com.signalsoop.app.history.GraphMediaStorage
import com.signalsoop.app.history.KnowledgeGraphInsights
import com.signalsoop.app.history.ScanSnapshot
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.ui.components.CopyIconButton
import com.signalsoop.app.ui.graph.KnowledgeGraphGeoTimelineView
import com.signalsoop.app.ui.components.FindingCard
import com.signalsoop.app.ui.components.ManticoreFooter
import com.signalsoop.app.ui.components.RiskCard
import com.signalsoop.app.ui.util.ClipboardUtil
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopBlue
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWhite
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HubTab(val label: String) {
    Timeline("Timeline"),
    GraphMap("Map + time"),
    Vault("Vault"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeHubScreen(
    viewModel: HistoryViewModel,
    onOpenGraphFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var hubTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val savePdfLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri != null) {
                viewModel.generateReportPdf(
                    onReady = { file ->
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            file.inputStream().use { it.copyTo(out) }
                        }
                    },
                    onError = { },
                )
            }
        }

    val sharePdfLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    Dialogs(viewModel = viewModel, uiState = uiState)

    Scaffold(
        modifier = modifier,
        containerColor = ScoopBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Knowledge", color = ScoopWhite)
                        Text(
                            "Scans · map graph · local vault",
                            style = MaterialTheme.typography.bodySmall,
                            color = ScoopMuted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScoopBlack),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            SecondaryTabRow(
                selectedTabIndex = hubTab,
                containerColor = ScoopBlack,
                contentColor = ScoopGreen,
            ) {
                HubTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = hubTab == index,
                        onClick = { hubTab = index },
                        text = {
                            Text(
                                tab.label,
                                color = if (hubTab == index) ScoopGreen else ScoopMuted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }

            Text(
                uiState.statusMessage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = ScoopMuted,
            )

            ReportSelectionBar(
                selectedCount = uiState.reportSelectedIds.size,
                totalScans = uiState.snapshots.size,
                generating = uiState.reportGenerating,
                onSelectAll = viewModel::selectAllForReport,
                onClear = viewModel::clearReportSelection,
                onSavePdf = {
                    savePdfLauncher.launch("signal-scoop-report-${System.currentTimeMillis()}.pdf")
                },
                onSharePdf = {
                    viewModel.generateReportPdf(
                        onReady = { file ->
                            val uri =
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                            val intent =
                                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            sharePdfLauncher.launch(
                                android.content.Intent.createChooser(intent, "Share PDF report"),
                            )
                        },
                        onError = { },
                    )
                },
            )

            when (HubTab.entries[hubTab]) {
                HubTab.Timeline ->
                    TimelineTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f),
                    )
                HubTab.GraphMap -> {
                    LaunchedEffect(Unit) { viewModel.refreshGraphAndInsights() }
                    KnowledgeGraphGeoTimelineView(
                        visualization = uiState.graphVisualization,
                        filterScanId = uiState.graphFilterScanId,
                        onFilterScanChange = viewModel::setGraphTimelineFilter,
                        onNodeSelected = viewModel::onGraphNodeSelected,
                        onLinkSelected = viewModel::onGraphLinkSelected,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        emptyMessage =
                            "Save a scan on the Scan tab, then return here. Tap nodes or lines for details.",
                        footer = {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = onOpenGraphFullscreen,
                                    modifier = Modifier.weight(1f),
                                    enabled = (uiState.graphVisualization?.nodes?.isNotEmpty() == true),
                                ) {
                                    Icon(Icons.Rounded.OpenInFull, contentDescription = null)
                                    Text("Full screen")
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.refreshGraphAndInsights() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Refresh")
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.anchorGraphToEvrmore() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Anchor")
                                }
                            }
                        },
                    )
                }
                HubTab.Vault ->
                    VaultTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f),
                    )
            }
            ManticoreFooter()
        }
    }
}

@Composable
private fun Dialogs(viewModel: HistoryViewModel, uiState: com.signalsoop.app.HistoryUiState) {
    if (uiState.renameTargetId != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelRename,
            title = { Text("Rename scan") },
            text = {
                OutlinedTextField(
                    value = uiState.renameDraft,
                    onValueChange = viewModel::setRenameDraft,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::commitRename) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRename) { Text("Cancel") } },
        )
    }
    if (uiState.aliasSignalKey != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelAlias,
            title = { Text("Pet name for signal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.aliasPetName,
                        onValueChange = viewModel::setAliasPetName,
                        label = { Text("Friendly name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.aliasNotes,
                        onValueChange = viewModel::setAliasNotes,
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::commitAlias) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::cancelAlias) { Text("Cancel") } },
        )
    }
    if (uiState.showAddNoteDialog) {
        AlertDialog(
                onDismissRequest = viewModel::cancelAddNote,
                title = { Text("Add to knowledge graph") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.addNoteLabel,
                            onValueChange = viewModel::setAddNoteLabel,
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = uiState.addNoteBody,
                            onValueChange = viewModel::setAddNoteBody,
                            label = { Text("Observation") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { viewModel.commitAddNote() }) { Text("Add") } },
            dismissButton = { TextButton(onClick = viewModel::cancelAddNote) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TimelineTab(
    uiState: com.signalsoop.app.HistoryUiState,
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        uiState.insights?.let { insights ->
            item { GraphInsightsCard(insights) }
        }
        item {
            FilledTonalButton(
                onClick = { viewModel.beginAddNote(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("  Add observation to graph")
            }
        }
        if (uiState.snapshots.isEmpty()) {
            item {
                Text(
                    "No saved scans yet. Scan on the Scan tab — each run saves GPS, signals, and graph links.",
                    color = ScoopMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        uiState.snapshots.forEach { snapshot ->
            val expanded = uiState.selectedScanId == snapshot.id
            item(key = "card-${snapshot.id}") {
                ScanHistoryCard(
                    snapshot = snapshot,
                    expanded = expanded,
                    selectedForReport = snapshot.id in uiState.reportSelectedIds,
                    onReportToggle = { viewModel.toggleReportSelection(snapshot.id) },
                    onToggle = { viewModel.selectScan(if (expanded) null else snapshot.id) },
                    onRename = { viewModel.beginRename(snapshot.id, snapshot.name) },
                    onDelete = { viewModel.deleteScan(snapshot.id) },
                    onAddNote = { viewModel.beginAddNote(snapshot.id) },
                )
            }
            if (expanded) {
                item(key = "media-${snapshot.id}") {
                    MediaActionsRow(
                        scanId = snapshot.id,
                        signalKey = null,
                        viewModel = viewModel,
                    )
                }
                snapshot.riskSummary?.let { risk ->
                    item(key = "risk-${snapshot.id}") { RiskCard(summary = risk) }
                }
                val radio =
                    snapshot.findings.filter {
                        it.category != SignalCategory.SYSTEM && it.category != SignalCategory.SENSORS
                    }
                items(radio.take(40), key = { "${snapshot.id}-${it.id}" }) { finding ->
                    SignalFindingRow(
                        finding = finding,
                        petName = signalKeyFrom(finding)?.let { key ->
                            viewModel.aliasForKey(key, uiState.vault)
                        },
                        onPetName = {
                            signalKeyFrom(finding)?.let { key ->
                                viewModel.beginAlias(key, viewModel.aliasForKey(key, uiState.vault))
                            }
                        },
                        onLinkDevice = {
                            signalKeyFrom(finding)?.let { key ->
                                addressFrom(finding)?.let { addr ->
                                    viewModel.linkDevice(key, addr, finding.title)
                                }
                            }
                        },
                        onEvrus = {
                            signalKeyFrom(finding)?.let { key ->
                                viewModel.linkEvrus(
                                    key,
                                    viewModel.aliasForKey(key, uiState.vault) ?: finding.title,
                                    snapshot.id,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultTab(
    uiState: com.signalsoop.app.HistoryUiState,
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val vault = uiState.vault
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Everything you collected — on this device only.", color = ScoopMuted)
        }
        item {
            VaultStatCard(
                title = "Scans",
                value = "${vault?.scans?.size ?: 0}",
                subtitle = "Full radio surveys with GPS",
            )
        }
        item {
            VaultStatCard(
                title = "Pet names",
                value = "${vault?.aliases?.size ?: 0}",
                subtitle = "Friendly labels for signals",
            )
        }
        item {
            VaultStatCard(
                title = "Media",
                value = "${vault?.media?.size ?: 0}",
                subtitle = "Photos and videos",
            )
        }
        item {
            VaultStatCard(
                title = "EVRUS links",
                value = "${vault?.evrusLinks?.size ?: 0}",
                subtitle =
                    if (uiState.evrusCompanionAvailable) "Companion app detected" else "Local identity until EVRUS app installs",
            )
        }
        vault?.media?.forEach { media ->
            item(key = "media-${media.id}") {
                Surface(shape = RoundedCornerShape(12.dp), color = ScoopSurfaceHigh) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(media.mediaType, color = ScoopGreen, style = MaterialTheme.typography.labelMedium)
                            Text(media.filePath.substringAfterLast('/'), color = ScoopWhite, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { viewModel.deleteMedia(media.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = ScoopMuted)
                        }
                    }
                }
            }
        }
        vault?.userNotes?.forEach { note ->
            item(key = "note-${note.id}") {
                Surface(shape = RoundedCornerShape(12.dp), color = ScoopSurfaceHigh) {
                    Column(Modifier.padding(12.dp)) {
                        Text(note.label, color = ScoopWhite, style = MaterialTheme.typography.titleSmall)
                        Text(note.body, color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaActionsRow(
    scanId: String?,
    signalKey: String?,
    viewModel: HistoryViewModel,
) {
    val context = LocalContext.current
    var captureGeneration by remember { mutableIntStateOf(0) }
    val photoFile = remember(captureGeneration) { GraphMediaStorage.newPhotoFile(context) }
    val videoFile = remember(captureGeneration) { GraphMediaStorage.newVideoFile(context) }
    val photoUri =
        remember(photoFile) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile,
            )
        }
    val videoUri =
        remember(videoFile) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                videoFile,
            )
        }
    val takePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) viewModel.attachMedia(photoFile.absolutePath, "PHOTO", scanId, signalKey)
        }
    val takeVideo =
        rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
            if (ok) viewModel.attachMedia(videoFile.absolutePath, "VIDEO", scanId, signalKey)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = {
                captureGeneration++
                takePhoto.launch(photoUri)
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.height(18.dp))
            Text(" Photo", style = MaterialTheme.typography.labelMedium)
        }
        FilledTonalButton(
            onClick = {
                captureGeneration++
                takeVideo.launch(videoUri)
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.height(18.dp))
            Text(" Video", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SignalFindingRow(
    finding: Finding,
    petName: String?,
    onPetName: () -> Unit,
    onLinkDevice: () -> Unit,
    onEvrus: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FindingCard(finding = finding)
        if (petName != null) {
            Text("Pet name: $petName", color = ScoopGreen, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            TextButton(onClick = onPetName) { Text("Name") }
            TextButton(onClick = onLinkDevice) { Text("Link device") }
            TextButton(onClick = onEvrus) { Text("EVRUS") }
        }
    }
}

@Composable
private fun VaultStatCard(title: String, value: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = ScoopSurfaceHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(title, color = ScoopMuted, style = MaterialTheme.typography.labelMedium)
                Text(subtitle, color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text(value, color = ScoopGreen, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ReportSelectionBar(
    selectedCount: Int,
    totalScans: Int,
    generating: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = ScoopSurfaceHigh,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "PDF report · $selectedCount of $totalScans selected",
                color = ScoopWhite,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSelectAll) { Text("All") }
                TextButton(onClick = onClear) { Text("Clear") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSavePdf,
                    enabled = selectedCount > 0 && !generating,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ScoopGreen, contentColor = ScoopBlack),
                ) {
                    if (generating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Description, contentDescription = null)
                        Text(" Save PDF")
                    }
                }
                FilledTonalButton(
                    onClick = onSharePdf,
                    enabled = selectedCount > 0 && !generating,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun GraphInsightsCard(insights: KnowledgeGraphInsights) {
    Surface(shape = RoundedCornerShape(16.dp), color = ScoopSurfaceHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Hub, contentDescription = null, tint = ScoopGreen)
                    Text("Graph insights", style = MaterialTheme.typography.titleSmall, color = ScoopGreen)
                }
                CopyIconButton(
                    label = "graph insights",
                    value =
                        buildString {
                            append("${insights.totalScans} scans · ${insights.scansWithGps} GPS · ${insights.uniquePlaces} places\n")
                            insights.recurringSignals.forEach { append("• ${it.label} (${it.scanCount}×)\n") }
                        },
                )
            }
            Text(
                "${insights.totalScans} scans · ${insights.scansWithGps} GPS · ${insights.uniquePlaces} places",
                color = ScoopWhite,
                style = MaterialTheme.typography.bodySmall,
            )
            insights.recurringSignals.take(3).forEach { s ->
                Text("• ${s.label} (${s.scanCount}×)", color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ScanHistoryCard(
    snapshot: ScanSnapshot,
    expanded: Boolean,
    selectedForReport: Boolean,
    onReportToggle: () -> Unit,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddNote: () -> Unit,
) {
    val context = LocalContext.current
    val time = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(snapshot.scannedAtEpochMs))
    val geoLine = snapshot.geoFix?.let { "GPS ${it.formatCoordinates()} · ${it.formatAccuracy()}" }
    val riskLine = snapshot.riskSummary?.let { "${it.level.label} ${it.score}/100" }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ScoopSurfaceHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selectedForReport,
                    onCheckedChange = { onReportToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = ScoopGreen),
                )
                Text(
                    snapshot.name,
                    color = ScoopWhite,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).clickable(onClick = onToggle),
                )
                CopyIconButton(
                    label = "scan",
                    value =
                        buildString {
                            append(snapshot.name)
                            append('\n')
                            append(time)
                            geoLine?.let { append('\n').append(it) }
                            riskLine?.let { append("\nRisk: ").append(it) }
                            append("\nFindings: ${snapshot.findings.size}")
                        },
                )
                IconButton(onClick = onRename) { Icon(Icons.Rounded.Edit, null, tint = ScoopMuted) }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, null, tint = ScoopMuted) }
            }
            Text(time, color = ScoopMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable(onClick = onToggle))
            geoLine?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = ScoopGreen, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { ClipboardUtil.copy(context, "GPS", it) }) {
                        Icon(Icons.Rounded.ContentCopy, null, tint = ScoopMuted)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAddNote) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.height(16.dp))
                    Text("Note")
                }
                Text("${snapshot.findings.size} findings", color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun signalKeyFrom(finding: Finding): String? =
    when {
        finding.id.startsWith("ble-") -> finding.id.removePrefix("ble-")
        finding.id.startsWith("wifi-") -> finding.id.removePrefix("wifi-")
        else -> finding.id.takeIf { finding.category == SignalCategory.BLUETOOTH }
    }

private fun addressFrom(finding: Finding): String? {
    val part = finding.detail.substringBefore(" ·").trim()
    return part.takeIf { it.contains(':') && it.length >= 12 }
}
