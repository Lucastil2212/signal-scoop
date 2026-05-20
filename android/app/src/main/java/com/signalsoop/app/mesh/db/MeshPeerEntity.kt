package com.signalsoop.app.mesh.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mesh_peers")
data class MeshPeerEntity(
    @PrimaryKey val peerId: String,
    val principal: String,
    val displayName: String,
    val host: String?,
    val port: Int,
    val bleAddress: String?,
    val prekeysJson: String?,
    val lastSeenEpochMs: Long,
)
