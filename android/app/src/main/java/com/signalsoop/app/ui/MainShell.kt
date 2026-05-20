package com.signalsoop.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.signalsoop.app.HistoryViewModel
import com.signalsoop.app.ScanViewModel
import com.signalsoop.app.assistant.AssistantViewModel
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
private enum class MainTab(val label: String) {
    Scan("Scan"),
    History("Graph"),
    Ask("Ask"),
}

@Composable
fun MainShell(
    scanViewModel: ScanViewModel,
    onScanClick: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val scanState by scanViewModel.uiState.collectAsState()
    val assistantViewModel: AssistantViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()
    var tab by remember { mutableStateOf(MainTab.Scan) }

    Scaffold(
        containerColor = ScoopBlack,
        bottomBar = {
            NavigationBar(containerColor = ScoopBlack) {
                MainTab.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = tab == dest,
                        onClick = { tab = dest },
                        icon = {
                            Icon(
                                when (dest) {
                                    MainTab.Scan -> Icons.Rounded.Radar
                                    MainTab.History -> Icons.Rounded.History
                                    MainTab.Ask -> Icons.Rounded.Chat
                                },
                                contentDescription = dest.label,
                            )
                        },
                        label = {
                            val label =
                                if (dest == MainTab.Ask) "${dest.label} β" else dest.label
                            Text(label, color = if (tab == dest) ScoopGreen else ScoopMuted)
                        },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            MainTab.Scan ->
                SignalScoopScreen(
                    viewModel = scanViewModel,
                    onScanClick = onScanClick,
                    onRequestPermissions = onRequestPermissions,
                    modifier = Modifier.padding(padding),
                    showBottomScanBar = false,
                )
            MainTab.History ->
                KnowledgeHubScreen(
                    viewModel = historyViewModel,
                    modifier = Modifier.padding(padding),
                )
            MainTab.Ask ->
                AssistantScreen(
                    assistantViewModel = assistantViewModel,
                    scanState = scanState,
                    modifier = Modifier.padding(padding),
                )
        }
    }
}
