package com.signalsoop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.signalsoop.app.MeshViewModel
import com.signalsoop.app.ui.components.ManticoreFooter
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopBlue
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWhite

private enum class ConnectPane { Home, Messages, Voice, Nearby }

private data class HomeTile(
    val id: String,
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val pane: ConnectPane,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectHomeScreen(
    meshViewModel: MeshViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by meshViewModel.uiState.collectAsState()
    var pane by remember { mutableStateOf(ConnectPane.Home) }
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        containerColor = ScoopBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Connect", color = ScoopWhite)
                        Text(
                            "Local mesh · EVRUS E2EE text",
                            style = MaterialTheme.typography.bodySmall,
                            color = ScoopMuted,
                        )
                    }
                },
                navigationIcon = {},
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
            Text(
                uiState.statusLine,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = ScoopMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            when (pane) {
                ConnectPane.Home ->
                    MeshHomeGrid(
                        modifier = Modifier.weight(1f),
                        meshViewModel = meshViewModel,
                        onOpen = { tile ->
                            when (tile.id) {
                                "export" ->
                                    meshViewModel.exportInbox { intent ->
                                        context.startActivity(
                                            android.content.Intent.createChooser(intent, "Export mesh TXT"),
                                        )
                                    }
                                else -> pane = tile.pane
                            }
                        },
                    )
                ConnectPane.Messages -> MeshMessagesPane(meshViewModel, onBack = { pane = ConnectPane.Home })
                ConnectPane.Voice -> MeshVoicePane(meshViewModel, onBack = { pane = ConnectPane.Home })
                ConnectPane.Nearby ->
                    MeshNearbyPane(
                        meshViewModel = meshViewModel,
                        onBack = { pane = ConnectPane.Home },
                        onConnected = { pane = ConnectPane.Messages },
                    )
            }
            ManticoreFooter()
        }
    }
}

@Composable
private fun MeshHomeGrid(
    modifier: Modifier = Modifier,
    meshViewModel: MeshViewModel,
    onOpen: (HomeTile) -> Unit,
) {
    val tiles =
        listOf(
            HomeTile("radio", "Mesh Radio", "Wi-Fi / BLE discovery", Icons.Rounded.Radio, ConnectPane.Nearby),
            HomeTile("messages", "Messages", "EVRUS encrypted text", Icons.Rounded.Chat, ConnectPane.Messages),
            HomeTile("voice", "Voice Mesh", "LoRa-style speaker", Icons.Rounded.Mic, ConnectPane.Voice),
            HomeTile("export", "Export TXT", "Download inbox locally", Icons.Rounded.Download, ConnectPane.Home),
            HomeTile("graph", "Graph Link", "Open knowledge graph", Icons.Rounded.Hub, ConnectPane.Nearby),
        )
    Box(modifier = modifier.fillMaxWidth()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(tiles, key = { it.id }) { tile ->
            Surface(
                modifier =
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onOpen(tile) },
                shape = RoundedCornerShape(22.dp),
                color = ScoopSurfaceHigh,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(tile.icon, contentDescription = null, tint = ScoopGreen, modifier = Modifier.padding(4.dp))
                    Column {
                        Text(tile.label, color = ScoopWhite, style = MaterialTheme.typography.titleMedium)
                        Text(tile.subtitle, color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun MeshMessagesPane(meshViewModel: MeshViewModel, onBack: () -> Unit) {
    val uiState by meshViewModel.uiState.collectAsState()
    val messages by meshViewModel.messages.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("← Home", color = ScoopBlue, modifier = Modifier.clickable(onClick = onBack))
        messages.take(30).forEach { msg ->
            Text(
                "[${msg.direction}] ${msg.plaintext}",
                color = if (msg.direction == "in") ScoopGreen else ScoopWhite,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        androidx.compose.material3.OutlinedTextField(
            value = uiState.composeText,
            onValueChange = meshViewModel::setComposeText,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Text only") },
        )
        androidx.compose.material3.Button(onClick = { meshViewModel.sendText() }) {
            Text("Send encrypted")
        }
    }
}

@Composable
private fun MeshVoicePane(meshViewModel: MeshViewModel, onBack: () -> Unit) {
    val uiState by meshViewModel.uiState.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("← Home", color = ScoopBlue, modifier = Modifier.align(Alignment.Start).clickable(onClick = onBack))
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(ScoopSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Mic, null, tint = if (uiState.voiceActive) ScoopGreen else ScoopMuted)
                Text(
                    if (uiState.voiceActive) "Voice mesh live" else "Tap to open voice channel",
                    color = ScoopWhite,
                )
            }
        }
        androidx.compose.material3.Button(
            onClick = { meshViewModel.toggleVoice() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.voiceActive) "Stop voice" else "Start voice mesh")
        }
    }
}

@Composable
private fun MeshNearbyPane(
    meshViewModel: MeshViewModel,
    onBack: () -> Unit,
    onConnected: () -> Unit,
) {
    val uiState by meshViewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("← Home", color = ScoopBlue, modifier = Modifier.clickable(onClick = onBack))
        androidx.compose.material3.Button(onClick = { meshViewModel.toggleRadio() }) {
            Text(if (uiState.radioOn) "Stop mesh radio" else "Start mesh radio")
        }
        uiState.nearbyPeers.forEach { peer ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ScoopSurfaceHigh,
                modifier =
                    Modifier.fillMaxWidth().clickable {
                        meshViewModel.connectNearby(peer, peer.peerId)
                        onConnected()
                    },
            ) {
                Text(
                    "Peer ${peer.peerId} @ ${peer.host}:${peer.port}",
                    modifier = Modifier.padding(14.dp),
                    color = ScoopWhite,
                )
            }
        }
        if (uiState.nearbyPeers.isEmpty()) {
            Text("No peers yet. Both phones need mesh radio on and the same Wi-Fi.", color = ScoopMuted)
        }
    }
}
