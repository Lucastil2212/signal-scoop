package com.signalsoop.app.mesh.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mesh_sessions")
data class MeshSessionEntity(
    @PrimaryKey val id: String,
    val localPrincipal: String,
    val remotePrincipal: String,
    val rootKeyHex: String,
    val sendChainHex: String,
    val sendCounter: Int,
    val recvChainHex: String,
    val recvCounter: Int,
    val dhPubB64: String,
    val skippedJson: String,
    val peerAddress: String?,
    val linkedSignalKey: String?,
)
