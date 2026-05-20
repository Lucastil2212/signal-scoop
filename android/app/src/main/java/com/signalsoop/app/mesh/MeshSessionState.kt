package com.signalsoop.app.mesh

data class MeshSessionState(
    val id: String,
    val localPrincipal: String,
    val remotePrincipal: String,
    val rootKeyHex: String,
    var sendChainHex: String,
    var sendCounter: Int,
    var recvChainHex: String,
    var recvCounter: Int,
    val dhPubB64: String,
    val skippedKeys: MutableMap<Int, String> = mutableMapOf(),
)
