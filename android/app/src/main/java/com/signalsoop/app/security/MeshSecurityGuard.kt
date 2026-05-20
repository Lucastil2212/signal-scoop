package com.signalsoop.app.security

import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Defensive controls for the optional local mesh (Connect tab).
 * Mesh only accepts peers on private LAN addresses; no internet-wide binding.
 */
object MeshSecurityGuard {
    const val MAX_WIRE_FRAME_BYTES = 256 * 1024
    const val MAX_VOICE_PCM_BYTES = 8 * 1024
    const val MAX_INBOUND_FRAMES_PER_SECOND = 40
    const val MAX_TCP_PEERS = 8
    const val MESH_PORT = 28777

    private val inboundFrameCount = AtomicInteger(0)
    private var lastSecondMark = System.currentTimeMillis()

    fun isAllowedMeshPeerHost(host: String): Boolean {
        val addr =
            runCatching { InetAddress.getByName(host.trim()) }.getOrNull() ?: return false
        if (addr.isLoopbackAddress) return true
        if (addr is Inet6Address) {
            return addr.isLinkLocalAddress || addr.isSiteLocalAddress
        }
        val bytes = addr.address
        if (bytes.size != 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        // 10.0.0.0/8
        if (b0 == 10) return true
        // 172.16.0.0/12
        if (b0 == 172 && b1 in 16..31) return true
        // 192.168.0.0/16
        if (b0 == 192 && b1 == 168) return true
        // 169.254.0.0/16 link-local
        if (b0 == 169 && b1 == 254) return true
        return false
    }

    fun acceptInboundFrame(size: Int): Boolean {
        if (size <= 0 || size > MAX_WIRE_FRAME_BYTES) return false
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastSecondMark > 1_000L) {
                inboundFrameCount.set(0)
                lastSecondMark = now
            }
            if (inboundFrameCount.incrementAndGet() > MAX_INBOUND_FRAMES_PER_SECOND) return false
        }
        return true
    }

    fun canAcceptAnotherPeer(currentPeerCount: Int): Boolean = currentPeerCount < MAX_TCP_PEERS
}
