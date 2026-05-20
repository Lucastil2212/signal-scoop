package com.signalsoop.app.mesh

import android.content.Context
import com.signalsoop.app.mesh.crypto.MeshCrypto
import com.signalsoop.app.mesh.db.MeshDao
import com.signalsoop.app.mesh.db.MeshMessageEntity
import com.signalsoop.app.mesh.db.MeshPeerEntity
import com.signalsoop.app.mesh.db.MeshSessionEntity
import com.signalsoop.app.mesh.transport.RadioMeshHub
import com.signalsoop.app.mesh.voice.VoiceMeshEngine
import com.signalsoop.app.security.InputSanitizer
import com.signalsoop.app.security.MeshSecurityGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class MeshCoordinator(
    context: Context,
    private val dao: MeshDao,
    private val scope: CoroutineScope,
    private val localPrincipal: String,
) {
    private val hub = RadioMeshHub(context, localPrincipal)
    private var localPrekeys: MeshCrypto.LocalPrekeys? = null
    private val sessions = mutableMapOf<String, MeshSessionState>()
    private val remotePrekeys = mutableMapOf<String, MeshCrypto.PrekeyBundle>()
    private var voiceEngine: VoiceMeshEngine? = null

    val peers = hub.discoveredPeers

    fun startRadio() {
        hub.start()
        hub.inbound
            .onEach { bytes -> handleInbound(bytes) }
            .launchIn(scope)
    }

    fun stopRadio() {
        voiceEngine?.release()
        hub.stop()
    }

    fun ensurePrekeys(): MeshCrypto.PrekeyBundle {
        val existing = localPrekeys
        if (existing != null) return existing.bundle
        val generated = MeshCrypto.generateLocalPrekeys(localPrincipal)
        localPrekeys = generated
        val frame = MeshWire.prekeysEnvelope(prekeysToJson(generated.bundle))
        hub.broadcast(frame.toByteArray())
        return generated.bundle
    }

    suspend fun connectToPeer(peer: RadioMeshHub.DiscoveredPeer, displayName: String, signalKey: String?) {
        if (!MeshSecurityGuard.isAllowedMeshPeerHost(peer.host)) return
        hub.connect(peer)
        dao.upsertPeer(
            MeshPeerEntity(
                peerId = peer.peerId,
                principal = "did:evrus:mesh:${peer.peerId}",
                displayName = displayName,
                host = peer.host,
                port = peer.port,
                bleAddress = signalKey,
                prekeysJson = null,
                lastSeenEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun initiateSession(remotePrincipal: String, remoteBundle: MeshCrypto.PrekeyBundle): String {
        if (!MeshCrypto.verifySignedPrekey(remoteBundle)) error("Invalid prekeys")
        val (ephPriv, ephPub) = MeshCrypto.randomEphKeypair()
        val rk = MeshCrypto.deriveRootKey(ephPriv, remoteBundle.signedPrekeyPubB64, remoteBundle.oneTimePubB64)
        val sessionId = "sess_${UUID.randomUUID()}"
        val sendCk = MeshCrypto.kdfChain(rk, "INIT")
        val recvCk = MeshCrypto.kdfChain(rk, "INIT")
        val dhPub = MeshBytesHelper.b64(ephPub)
        val state =
            MeshSessionState(
                id = sessionId,
                localPrincipal = localPrincipal,
                remotePrincipal = remotePrincipal,
                rootKeyHex = rk,
                sendChainHex = sendCk,
                sendCounter = 0,
                recvChainHex = recvCk,
                recvCounter = 0,
                dhPubB64 = dhPub,
            )
        sessions[sessionId] = state
        persistSession(state, null, null)
        val init = MeshWire.sessionInitItem(sessionId, localPrincipal, remotePrincipal, dhPub, remoteBundle.oneTimePubB64)
        hub.broadcast(MeshWire.mailEnvelope(init).toByteArray())
        return sessionId
    }

    suspend fun sendText(sessionId: String, plaintext: String): Boolean {
        val safe = InputSanitizer.meshMessage(plaintext) ?: return false
        val session = sessions[sessionId] ?: loadSession(sessionId) ?: return false
        val counter = session.sendCounter
        val (enc, nextCk) =
            MeshCrypto.encryptMessage(
                session.sendChainHex,
                counter,
                safe,
                sessionId,
                session.localPrincipal,
                session.remotePrincipal,
                session.dhPubB64,
            )
        session.sendChainHex = nextCk
        session.sendCounter++
        sessions[sessionId] = session
        persistSession(session, null, null)
        val item =
            MeshWire.chatItem(
                sessionId,
                session.localPrincipal,
                session.remotePrincipal,
                MeshWire.MeshEncryptedFields(
                    enc.counter,
                    enc.ciphertextB64,
                    enc.ivB64,
                    enc.tagB64,
                    enc.adJson,
                ),
            )
        hub.broadcast(MeshWire.mailEnvelope(item).toByteArray())
        dao.upsertMessage(
            MeshMessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                peerPrincipal = session.remotePrincipal,
                direction = "out",
                plaintext = safe,
                sentAtEpochMs = System.currentTimeMillis(),
                exported = false,
            ),
        )
        return true
    }

    fun startVoice(sessionId: String) {
        voiceEngine?.release()
        voiceEngine = VoiceMeshEngine(hub, sessionId, scope).also { it.startTransmit() }
    }

    fun stopVoice() {
        voiceEngine?.release()
        voiceEngine = null
    }

    private suspend fun handleInbound(bytes: ByteArray) {
        if (!MeshSecurityGuard.acceptInboundFrame(bytes.size)) return
        InputSanitizer.wireJson(bytes) ?: return
        val json = MeshWire.parse(bytes) ?: return
        when (json.optString("kind")) {
            MeshWire.KIND_PREKEYS -> {
                val bundle = json.getJSONObject("bundle")
                val principal = bundle.getString("principal")
                remotePrekeys[principal] = parsePrekeyBundle(bundle)
            }
            MeshWire.KIND_MAIL -> {
                val item = json.getJSONObject("item")
                when (item.optString("type")) {
                    "session_init" -> handleSessionInit(item)
                    "chat" -> handleChat(item)
                }
            }
            MeshWire.KIND_VOICE -> {
                val pcm = json.optString("pcm")
                if (pcm.isNotEmpty() && pcm.length <= MeshSecurityGuard.MAX_VOICE_PCM_BYTES * 2) {
                    voiceEngine?.playInbound(pcm)
                }
            }
        }
    }

    private suspend fun handleSessionInit(item: JSONObject) {
        val sessionId = item.getString("sessionId")
        val from = item.getString("from")
        val ephPub = item.getString("ephPub")
        val local = localPrekeys ?: return
        val oneTimePub = item.optString("oneTimePub").takeIf { it.isNotBlank() && it != "null" }
        val rk =
            MeshCrypto.deriveResponderRootKey(
                local.signedPrekeyPrivate,
                ephPub,
                local.oneTimePrivate,
                oneTimePub,
            )
        val sendCk = MeshCrypto.kdfChain(rk, "INIT")
        val recvCk = MeshCrypto.kdfChain(rk, "INIT")
        val (_, spkPub) = MeshCrypto.randomEphKeypair()
        val state =
            MeshSessionState(
                id = sessionId,
                localPrincipal = localPrincipal,
                remotePrincipal = from,
                rootKeyHex = rk,
                sendChainHex = sendCk,
                sendCounter = 0,
                recvChainHex = recvCk,
                recvCounter = 0,
                dhPubB64 = MeshBytesHelper.b64(spkPub),
            )
        sessions[sessionId] = state
        persistSession(state, null, null)
    }

    fun remoteBundle(principal: String): MeshCrypto.PrekeyBundle? = remotePrekeys[principal]

    private suspend fun handleChat(item: JSONObject) {
        val sessionId = item.getString("sessionId")
        val session = sessions[sessionId] ?: loadSession(sessionId) ?: return
        val skipped = session.skippedKeys.mapValues { (_, v) -> MeshBytesHelper.decode(v) }.toMutableMap()
        val (plaintext, nextCk) =
            MeshCrypto.decryptMessage(
                session.recvChainHex,
                session.recvCounter,
                item.getInt("counter"),
                item.getString("ciphertext"),
                item.getString("iv"),
                item.getString("tag"),
                item.getJSONObject("ad").toString(),
                skipped,
            )
        session.recvChainHex = nextCk
        session.recvCounter = item.getInt("counter") + 1
        session.skippedKeys.clear()
        skipped.forEach { (k, v) -> session.skippedKeys[k] = MeshBytesHelper.b64(v) }
        sessions[sessionId] = session
        persistSession(session, null, null)
        dao.upsertMessage(
            MeshMessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                peerPrincipal = session.remotePrincipal,
                direction = "in",
                plaintext = plaintext,
                sentAtEpochMs = System.currentTimeMillis(),
                exported = false,
            ),
        )
    }

    private suspend fun loadSession(id: String): MeshSessionState? {
        val e = dao.getSession(id) ?: return null
        return MeshSessionState(
            id = e.id,
            localPrincipal = e.localPrincipal,
            remotePrincipal = e.remotePrincipal,
            rootKeyHex = e.rootKeyHex,
            sendChainHex = e.sendChainHex,
            sendCounter = e.sendCounter,
            recvChainHex = e.recvChainHex,
            recvCounter = e.recvCounter,
            dhPubB64 = e.dhPubB64,
            skippedKeys = e.skippedJson.split(";").filter { it.contains(":") }.associate {
                val p = it.split(":")
                p[0].toInt() to p[1]
            }.toMutableMap(),
        ).also { sessions[id] = it }
    }

    private suspend fun persistSession(state: MeshSessionState, host: String?, signalKey: String?) {
        val skipped = state.skippedKeys.entries.joinToString(";") { "${it.key}:${it.value}" }
        dao.upsertSession(
            MeshSessionEntity(
                id = state.id,
                localPrincipal = state.localPrincipal,
                remotePrincipal = state.remotePrincipal,
                rootKeyHex = state.rootKeyHex,
                sendChainHex = state.sendChainHex,
                sendCounter = state.sendCounter,
                recvChainHex = state.recvChainHex,
                recvCounter = state.recvCounter,
                dhPubB64 = state.dhPubB64,
                skippedJson = skipped,
                peerAddress = host,
                linkedSignalKey = signalKey,
            ),
        )
    }

    private fun parsePrekeyBundle(bundle: JSONObject): MeshCrypto.PrekeyBundle {
        val spk = bundle.getJSONObject("signedPrekey")
        val oneTimeArr = bundle.optJSONArray("oneTime")
        val oneTime = if (oneTimeArr != null && oneTimeArr.length() > 0) oneTimeArr.getString(0) else null
        return MeshCrypto.PrekeyBundle(
            principal = bundle.getString("principal"),
            identityPubB64 = bundle.getString("identityPub"),
            signedPrekeyPubB64 = spk.getString("pub"),
            signedPrekeySigB64 = spk.getString("sig"),
            oneTimePubB64 = oneTime,
        )
    }

    private fun prekeysToJson(bundle: MeshCrypto.PrekeyBundle): JSONObject =
        JSONObject()
            .put("principal", bundle.principal)
            .put("identityPub", bundle.identityPubB64)
            .put("signedPrekey", JSONObject().put("pub", bundle.signedPrekeyPubB64).put("sig", bundle.signedPrekeySigB64))
            .put("oneTime", bundle.oneTimePubB64?.let { org.json.JSONArray(listOf(it)) } ?: org.json.JSONArray())
}

private object MeshBytesHelper {
    fun b64(data: ByteArray) = com.signalsoop.app.mesh.crypto.MeshBytes.b64Encode(data)
    fun decode(b64: String) = com.signalsoop.app.mesh.crypto.MeshBytes.b64Decode(b64)
}
