package com.signalsoop.app

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signalsoop.app.mesh.MeshCoordinator
import com.signalsoop.app.mesh.MeshRepository
import com.signalsoop.app.mesh.crypto.MeshCrypto
import com.signalsoop.app.mesh.db.MeshMessageEntity
import com.signalsoop.app.mesh.db.MeshPeerEntity
import com.signalsoop.app.mesh.transport.RadioMeshHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class MeshUiState(
    val radioOn: Boolean = false,
    val statusLine: String = "Tap a tile to start local mesh radio.",
    val activeSessionId: String? = null,
    val composeText: String = "",
    val voiceActive: Boolean = false,
    val nearbyPeers: List<RadioMeshHub.DiscoveredPeer> = emptyList(),
)

class MeshViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SignalScoopApp
    private val localPrincipal = "did:evrus:mesh:${app.deviceMeshId}"
    private val coordinator = MeshCoordinator(application, app.meshDao, viewModelScope, localPrincipal)
    private val repository = MeshRepository(application, app.meshDao)

    private val _uiState = MutableStateFlow(MeshUiState())
    val uiState: StateFlow<MeshUiState> = _uiState.asStateFlow()

    val messages: StateFlow<List<MeshMessageEntity>> =
        repository.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedPeers: StateFlow<List<MeshPeerEntity>> =
        repository.peers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        coordinator.peers
            .onEach { peer ->
                _uiState.update { it.copy(nearbyPeers = it.nearbyPeers + peer) }
            }
            .launchIn(viewModelScope)
    }

    fun toggleRadio() {
        if (_uiState.value.radioOn) {
            coordinator.stopRadio()
            _uiState.update { it.copy(radioOn = false, voiceActive = false, statusLine = "Mesh radio off.") }
        } else {
            coordinator.startRadio()
            coordinator.ensurePrekeys()
            _uiState.update { it.copy(radioOn = true, statusLine = "Mesh radio on — discovering nearby devices…") }
        }
    }

    fun connectNearby(peer: RadioMeshHub.DiscoveredPeer, name: String) {
        viewModelScope.launch {
            coordinator.connectToPeer(peer, name, signalKey = null)
            coordinator.ensurePrekeys()
            val remotePrincipal = "did:evrus:mesh:${peer.peerId}"
            var remote = coordinator.remoteBundle(remotePrincipal)
            var attempts = 0
            while (remote == null && attempts < 20) {
                kotlinx.coroutines.delay(200)
                remote = coordinator.remoteBundle(remotePrincipal)
                attempts++
            }
            val bundle =
                remote
                    ?: MeshCrypto.PrekeyBundle(
                        principal = remotePrincipal,
                        identityPubB64 = coordinator.ensurePrekeys().identityPubB64,
                        signedPrekeyPubB64 = coordinator.ensurePrekeys().signedPrekeyPubB64,
                        signedPrekeySigB64 = coordinator.ensurePrekeys().signedPrekeySigB64,
                        oneTimePubB64 = coordinator.ensurePrekeys().oneTimePubB64,
                    )
            val sessionId = coordinator.initiateSession(bundle.principal, bundle)
            _uiState.update {
                it.copy(
                    activeSessionId = sessionId,
                    statusLine = "Connected to $name · E2EE session ready",
                )
            }
        }
    }

    fun connectLinkedDevice(displayName: String, bleAddress: String, host: String?, port: Int) {
        viewModelScope.launch {
            if (host != null) {
                val peer = RadioMeshHub.DiscoveredPeer(bleAddress, host, port)
                connectNearby(peer, displayName)
            } else {
                _uiState.update {
                    it.copy(statusLine = "Enable mesh radio on both phones (same Wi-Fi) to reach $displayName")
                }
            }
        }
    }

    fun setComposeText(text: String) {
        _uiState.update { it.copy(composeText = text) }
    }

    fun sendText() {
        val sessionId = _uiState.value.activeSessionId ?: return
        val text = _uiState.value.composeText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val ok = coordinator.sendText(sessionId, text)
            _uiState.update {
                it.copy(
                    composeText = "",
                    statusLine = if (ok) "Encrypted message sent (local radio)." else "Send failed — check mesh radio.",
                )
            }
        }
    }

    fun toggleVoice() {
        val session = _uiState.value.activeSessionId ?: return
        if (_uiState.value.voiceActive) {
            coordinator.stopVoice()
            _uiState.update { it.copy(voiceActive = false, statusLine = "Voice mesh stopped.") }
        } else {
            coordinator.startVoice(session)
            _uiState.update { it.copy(voiceActive = true, statusLine = "Voice mesh live — speak near the mic.") }
        }
    }

    fun exportInbox(onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val file = repository.exportInboxTxt()
            val uri =
                FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file,
                )
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            onReady(intent)
        }
    }

    override fun onCleared() {
        coordinator.stopRadio()
        super.onCleared()
    }
}
