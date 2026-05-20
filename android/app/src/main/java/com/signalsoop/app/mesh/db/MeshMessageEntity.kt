package com.signalsoop.app.mesh.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mesh_messages")
data class MeshMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val peerPrincipal: String,
    val direction: String,
    val plaintext: String,
    val sentAtEpochMs: Long,
    val exported: Boolean,
)
