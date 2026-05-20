package com.signalsoop.app.evr

/**
 * External-local bridge for EVRUS identity, P2P, and EVRMORE blockchain.
 * All traffic stays on-device until the user installs/connects the EVRUS companion.
 */
interface EvrusConnector {
    suspend fun isCompanionAvailable(): Boolean
    suspend fun currentIdentity(): EvrusIdentity?
    suspend fun linkSignal(
        signalKey: String,
        petName: String?,
        scanId: String?,
    ): Result<EvrusLinkResult>
    suspend fun publishGraphDigest(digestSha256: String): Result<String>
}
