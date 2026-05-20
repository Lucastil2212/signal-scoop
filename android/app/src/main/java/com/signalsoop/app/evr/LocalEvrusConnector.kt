package com.signalsoop.app.evr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.signalsoop.app.history.db.EvrusIdentityLinkEntity
import com.signalsoop.app.history.db.ScanHistoryDao
import java.security.MessageDigest
import java.util.UUID

/**
 * Local-first EVRUS connector: persists identity links in Room and optionally
 * hands off to an installed EVRUS app via explicit Intent (no cloud relay).
 */
class LocalEvrusConnector(
    private val context: Context,
    private val dao: ScanHistoryDao,
) : EvrusConnector {
    companion object {
        const val EVRUS_PACKAGE = "com.evrus.identity"
        const val ACTION_BIND_IDENTITY = "com.evrus.identity.BIND_LOCAL"
        const val ACTION_P2P_HANDSHAKE = "com.evrus.p2p.LOCAL_HANDSHAKE"
        const val ACTION_EVRMORE_ANCHOR = "com.evrmore.chain.ANCHOR_LOCAL"
    }

    override suspend fun isCompanionAvailable(): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(EVRUS_PACKAGE, PackageManager.GET_ACTIVITIES)
            true
        }.getOrDefault(false)

    override suspend fun currentIdentity(): EvrusIdentity? {
        val latest = dao.getAllEvrusLinks().firstOrNull() ?: return null
        return EvrusIdentity(
            did = latest.evrusDid,
            displayName = latest.displayName,
            p2pPeerId = latest.p2pPeerId,
        )
    }

    override suspend fun linkSignal(
        signalKey: String,
        petName: String?,
        scanId: String?,
    ): Result<EvrusLinkResult> {
        val did =
            currentIdentity()?.did
                ?: "did:evrus:local:${UUID.randomUUID()}"
        val peerId = "p2p:local:${signalKey.hashCode().toUInt()}"
        val chainRef = "evrmore:local:${UUID.randomUUID()}"

        val entity =
            EvrusIdentityLinkEntity(
                id = UUID.randomUUID().toString(),
                signalKey = signalKey,
                scanId = scanId,
                evrusDid = did,
                displayName = petName,
                p2pPeerId = peerId,
                evrmoreChainRef = chainRef,
                linkedAtEpochMs = System.currentTimeMillis(),
            )
        dao.upsertEvrusLink(entity)

        if (isCompanionAvailable()) {
            runCatching {
                context.sendBroadcast(
                    Intent(ACTION_BIND_IDENTITY).setPackage(EVRUS_PACKAGE).apply {
                        putExtra("signalKey", signalKey)
                        putExtra("evrusDid", did)
                        putExtra("scanId", scanId)
                    },
                )
            }
        }

        return Result.success(
            EvrusLinkResult(
                linkId = entity.id,
                did = did,
                p2pPeerId = peerId,
                evrmoreRef = chainRef,
            ),
        )
    }

    override suspend fun publishGraphDigest(digestSha256: String): Result<String> {
        val anchor = "evrmore:anchor:local:$digestSha256"
        if (isCompanionAvailable()) {
            runCatching {
                context.sendBroadcast(
                    Intent(ACTION_EVRMORE_ANCHOR).setPackage(EVRUS_PACKAGE).apply {
                        putExtra("digest", digestSha256)
                        putExtra("anchorRef", anchor)
                    },
                )
            }
        }
        return Result.success(anchor)
    }

    fun digestOf(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
