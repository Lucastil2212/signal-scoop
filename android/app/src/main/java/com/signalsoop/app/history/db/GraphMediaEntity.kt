package com.signalsoop.app.history.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "graph_media")
data class GraphMediaEntity(
    @PrimaryKey val id: String,
    val scanId: String?,
    val nodeId: String?,
    val signalKey: String?,
    val mediaType: String,
    val filePath: String,
    val caption: String?,
    val capturedAtEpochMs: Long,
)
