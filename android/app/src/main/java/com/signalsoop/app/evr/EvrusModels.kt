package com.signalsoop.app.evr

data class EvrusIdentity(
    val did: String,
    val displayName: String?,
    val p2pPeerId: String?,
)

data class EvrusLinkResult(
    val linkId: String,
    val did: String,
    val p2pPeerId: String?,
    val evrmoreRef: String?,
)
