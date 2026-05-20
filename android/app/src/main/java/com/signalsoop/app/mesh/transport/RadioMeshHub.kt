package com.signalsoop.app.mesh.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
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
 * BLE device links route to the same hub when peer IP is known.
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

    fun start(port: Int = MESH_PORT) {
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
        scope.launch {
            runCatching {
                val socket = Socket()
                socket.connect(InetSocketAddress(peer.host, peer.port), 5_000)
                attachSocket(peer.peerId, socket)
            }.onFailure { Log.w(TAG, "connect failed: ${it.message}") }
        }
    }

    fun connectHost(host: String, port: Int, peerId: String) {
        scope.launch {
            runCatching {
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
                attachSocket("peer-${socket.inetAddress.hostAddress}", socket)
            }
        }.onFailure { Log.w(TAG, "accept loop: ${it.message}") }
    }

    private fun attachSocket(peerId: String, socket: Socket) {
        scope.launch {
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            connectedWriters[peerId] = output
            while (running.get() && !socket.isClosed) {
                val size = runCatching { input.readInt() }.getOrNull() ?: break
                if (size <= 0 || size > MAX_FRAME) break
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
        private const val TAG = "RadioMeshHub"
        const val MESH_PORT = 28777
        const val SERVICE_TYPE = "_signalscoop-mesh._tcp."
        const val SERVICE_NAME = "signalscoop"
        const val MAX_FRAME = 512 * 1024
    }
}
