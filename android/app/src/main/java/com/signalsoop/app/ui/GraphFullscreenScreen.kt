package com.signalsoop.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.signalsoop.app.HistoryViewModel
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphFullscreenScreen(
    viewModel: HistoryViewModel,
    graphJson: String,
    statusMessage: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScoopBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text("3D knowledge graph", color = ScoopWhite)
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = ScoopWhite)
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = { viewModel.refreshGraphAndInsights() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh graph", tint = ScoopGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScoopBlack),
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (graphJson.length > 20) {
                KnowledgeGraph3DView(
                    graphJson = graphJson,
                    onNodeSelected = viewModel::onGraphNodeSelected,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    "Run scans to populate your knowledge graph.",
                    color = ScoopMuted,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            Text(
                statusMessage,
                color = ScoopMuted,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
            )
        }
    }
}
