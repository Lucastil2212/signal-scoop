package com.signalsoop.app.mesh.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.signalsoop.app.security.MeshSecurityGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local radio mesh over Wi-Fi (NSD discovery + TCP).
 * Accepts only private-LAN peers; frames are size- and rate-limited.
 */
class RadioMeshHub(
    context: Context,
    private val localPeerId: String,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val running = AtomicBoolean(false)

    private val _inbound = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val inbound: SharedFlow<ByteArray> = _inbound

    private val _peers = MutableSharedFlow<DiscoveredPeer>(extraBufferCapacity = 16)
    val discoveredPeers: SharedFlow<DiscoveredPeer> = _peers

    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val connectedWriters = mutableMapOf<String, DataOutputStream>()

    data class DiscoveredPeer(val peerId: String, val host: String, val port: Int)

    fun start(port: Int = MeshSecurityGuard.MESH_PORT) {
        if (!running.compareAndSet(false, true)) return
        scope.launch { acceptLoop(port) }
        registerService(port)
        startDiscovery()
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        runCatching { registrationListener?.let { nsd?.unregisterService(it) } }
        runCatching { discoveryListener?.let { nsd?.stopServiceDiscovery(it) } }
        connectedWriters.values.forEach { runCatching { it.close() } }
        connectedWriters.clear()
    }

    fun broadcast(frame: ByteArray) {
        if (frame.size > MeshSecurityGuard.MAX_WIRE_FRAME_BYTES) return
        scope.launch {
            connectedWriters.values.forEach { writer ->
                runCatching {
                    synchronized(writer) {
                        writer.writeInt(frame.size)
                        writer.write(frame)
                        writer.flush()
                    }
                }
            }
        }
    }

    fun connect(peer: DiscoveredPeer) {
        if (!MeshSecurityGuard.isAllowedMeshPeerHost(peer.host)) return
        scope.launch {
            runCatching {
                if (!MeshSecurityGuard.canAcceptAnotherPeer(connectedWriters.size)) return@launch
                val socket = Socket()
                socket.connect(InetSocketAddress(peer.host, peer.port), 5_000)
                attachSocket(peer.peerId, socket)
            }
        }
    }

    fun connectHost(host: String, port: Int, peerId: String) {
        if (!MeshSecurityGuard.isAllowedMeshPeerHost(host)) return
        scope.launch {
            runCatching {
                if (!MeshSecurityGuard.canAcceptAnotherPeer(connectedWriters.size)) return@launch
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 5_000)
                attachSocket(peerId, socket)
            }
        }
    }

    private fun acceptLoop(port: Int) {
        runCatching {
            serverSocket = ServerSocket(port)
            while (running.get()) {
                val socket = serverSocket?.accept() ?: break
                val host = socket.inetAddress?.hostAddress ?: continue
                if (!MeshSecurityGuard.isAllowedMeshPeerHost(host)) {
                    socket.close()
                    continue
                }
                if (!MeshSecurityGuard.canAcceptAnotherPeer(connectedWriters.size)) {
                    socket.close()
                    continue
                }
                attachSocket("peer-$host", socket)
            }
        }
    }

    private fun attachSocket(peerId: String, socket: Socket) {
        scope.launch {
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            connectedWriters[peerId] = output
            while (running.get() && !socket.isClosed) {
                val size = runCatching { input.readInt() }.getOrNull() ?: break
                if (!MeshSecurityGuard.acceptInboundFrame(size)) break
                val buf = ByteArray(size)
                input.readFully(buf)
                _inbound.emit(buf)
            }
            connectedWriters.remove(peerId)
            runCatching { socket.close() }
        }
    }

    private fun registerService(port: Int) {
        val serviceInfo =
            NsdServiceInfo().apply {
                serviceName = "${SERVICE_NAME}-${localPeerId.take(8)}"
                serviceType = SERVICE_TYPE
                setPort(port)
            }
        registrationListener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) = Unit
                override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = Unit
                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            }
        nsd?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun startDiscovery() {
        discoveryListener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) = Unit
                override fun onDiscoveryStopped(type: String) = Unit
                override fun onStartDiscoveryFailed(type: String, code: Int) = Unit
                override fun onStopDiscoveryFailed(type: String, code: Int) = Unit

                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceName.startsWith(SERVICE_NAME)) {
                        nsd?.resolveService(
                            service,
                            object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    val host = info.host?.hostAddress ?: return
                                    if (!MeshSecurityGuard.isAllowedMeshPeerHost(host)) return
                                    val peerId = info.serviceName.removePrefix("$SERVICE_NAME-")
                                    scope.launch {
                                        _peers.emit(DiscoveredPeer(peerId, host, info.port))
                                    }
                                }
                            },
                        )
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) = Unit
            }
        nsd?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    companion object {
        const val MESH_PORT = MeshSecurityGuard.MESH_PORT
        const val SERVICE_TYPE = "_signalscoop-mesh._tcp."
        const val SERVICE_NAME = "signalscoop"
    }
}
