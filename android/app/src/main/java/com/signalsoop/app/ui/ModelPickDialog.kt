package com.signalsoop.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.signalsoop.app.assistant.LocalModelOption
import com.signalsoop.app.ui.theme.ScoopBlue
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopWhite

@Composable
fun ModelPickDialog(
    modelsFolderPath: String,
    localOptions: List<LocalModelOption>,
    onDismiss: () -> Unit,
    onSelectLocal: (String) -> Unit,
    onBrowseModelsFolder: () -> Unit,
    onBrowseDownloads: () -> Unit,
    onBrowseAnywhere: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select model", color = ScoopWhite) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Download folder:",
                    style = MaterialTheme.typography.labelMedium,
                    color = ScoopBlue,
                )
                Text(
                    modelsFolderPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = ScoopMuted,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                if (localOptions.isEmpty()) {
                    Text(
                        "No .task files here yet. Download a preset or browse storage below.",
                        color = ScoopMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    Text(
                        "On this device",
                        style = MaterialTheme.typography.labelMedium,
                        color = ScoopBlue,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    localOptions.forEach { option ->
                        ModelOptionRow(option) { onSelectLocal(option.absolutePath) }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }

                Text(
                    "Browse storage",
                    style = MaterialTheme.typography.labelMedium,
                    color = ScoopBlue,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                DialogAction("Open download folder", onBrowseDownloads)
                DialogAction("Open Signal Scoop models folder", onBrowseModelsFolder)
                DialogAction("Browse anywhere…", onBrowseAnywhere)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ScoopMuted)
            }
        },
    )
}

@Composable
private fun ModelOptionRow(option: LocalModelOption, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            option.label,
            color = ScoopWhite,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            if (option.isCurrentPresetDownload) "Downloaded for current preset" else "Saved model",
            color = if (option.isCurrentPresetDownload) ScoopGreen else ScoopMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = ScoopWhite,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
    )
}
