package com.signalsoop.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import com.signalsoop.app.llm.ModelStorage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.signalsoop.app.ScanUiState
import com.signalsoop.app.assistant.AssistantViewModel
import com.signalsoop.app.assistant.ChatMessage
import com.signalsoop.app.llm.LiteRtModelPreset
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopBlue
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    assistantViewModel: AssistantViewModel,
    scanState: ScanUiState,
    modifier: Modifier = Modifier,
) {
    val uiState by assistantViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var presetExpanded by remember { mutableStateOf(false) }
    var showModelPick by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun importPickedUri(uri: Uri) {
        val name =
            context.contentResolver.queryDisplayName(uri)
                ?: uri.lastPathSegment
                ?: "imported.task"
        assistantViewModel.importModelFromUri(
            openStream = {
                context.contentResolver.openInputStream(uri) ?: error("Cannot read selected file")
            },
            suggestedName = name,
        )
    }

    val pickAtUri =
        rememberLauncherForActivityResult(OpenDocumentAtUri()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            importPickedUri(uri)
        }

    if (showModelPick) {
        ModelPickDialog(
            modelsFolderPath = uiState.modelsFolderPath,
            localOptions = uiState.localModelOptions,
            onDismiss = {
                showModelPick = false
                assistantViewModel.refreshLocalModelOptions()
            },
            onSelectLocal = { path ->
                showModelPick = false
                assistantViewModel.selectLocalModel(path)
            },
            onBrowseModelsFolder = {
                showModelPick = false
                pickAtUri.launch(ModelStorage.modelsFolderDocumentUri(context))
            },
            onBrowseDownloads = {
                showModelPick = false
                pickAtUri.launch(ModelStorage.downloadsFolderDocumentUri())
            },
            onBrowseAnywhere = {
                showModelPick = false
                pickAtUri.launch(null)
            },
        )
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Ask about your scan",
            style = MaterialTheme.typography.titleLarge,
            color = ScoopWhite,
        )
        Text(
            "Answers use only this session's scan data and an on-device model. " +
                "Optional HTTPS download fetches the checkpoint once; scan results are never uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = ScoopMuted,
        )

        ExposedDropdownMenuBox(presetExpanded, { presetExpanded = it }) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = uiState.preset.title,
                onValueChange = {},
                label = { Text("Model preset", color = ScoopMuted) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(presetExpanded) },
            )
            ExposedDropdownMenu(
                expanded = presetExpanded,
                onDismissRequest = { presetExpanded = false },
            ) {
                LiteRtModelPreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.title) },
                        onClick = {
                            assistantViewModel.setPreset(preset)
                            presetExpanded = false
                        },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { assistantViewModel.downloadPreset() },
                enabled = !uiState.downloading,
                modifier = Modifier.weight(1f),
            ) {
                if (uiState.downloading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("${uiState.downloadBytes / (1024L * 1024L)} MiB")
                } else {
                    Text("Download .task")
                }
            }
            OutlinedButton(
                onClick = {
                    assistantViewModel.refreshLocalModelOptions()
                    showModelPick = true
                },
            ) {
                Text("Pick model")
            }
        }

        OutlinedTextField(
            value = uiState.hfToken,
            onValueChange = assistantViewModel::setHfToken,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("HF token (gated models only)", color = ScoopMuted) },
            singleLine = true,
        )

        Text(uiState.statusLine, style = MaterialTheme.typography.bodySmall, color = ScoopMuted)

        uiState.deviceHints.take(2).forEach { hint ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ScoopSurfaceHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(hint, Modifier.padding(10.dp), color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.messages.isEmpty()) {
                item {
                    Text(
                        suggestedPrompts(scanState),
                        color = ScoopMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(uiState.messages) { msg -> ChatBubble(msg) }
            if (uiState.isGenerating) {
                item {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = ScoopGreen, strokeWidth = 2.dp)
                        Text("Thinking on-device…", color = ScoopMuted)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = uiState.userInput,
                onValueChange = assistantViewModel::setUserInput,
                modifier = Modifier.weight(1f),
                label = { Text("Your question", color = ScoopMuted) },
                enabled = !uiState.isGenerating,
            )
            Button(
                onClick = { assistantViewModel.ask(scanState) },
                enabled = !uiState.isGenerating && uiState.userInput.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
                colors =
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = ScoopBlue,
                        contentColor = ScoopBlack,
                    ),
            ) {
                Icon(Icons.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val container = if (message.fromUser) ScoopBlue.copy(alpha = 0.22f) else ScoopSurfaceHigh
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Card(colors = CardDefaults.cardColors(containerColor = container)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (message.fromUser) "You" else "Scoop",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (message.fromUser) ScoopBlue else ScoopGreen,
                )
                Text(message.text, color = ScoopWhite, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun suggestedPrompts(scanState: ScanUiState): String {
    return if (scanState.findings.isEmpty()) {
        "Run Scan first, then try:\n• Which BLE devices look unknown?\n• Summarize Wi-Fi networks with strong signal.\n• What does the risk score mean?"
    } else {
        "Try:\n• Summarize the highest-risk findings.\n• List unknown BLE devices.\n• Explain the risk score in plain language."
    }
}
