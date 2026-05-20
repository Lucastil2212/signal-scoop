package com.signalsoop.app.history.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** User-added note or observation node in the knowledge graph. */
@Entity(tableName = "user_graph_nodes")
data class UserGraphNodeEntity(
    @PrimaryKey val id: String,
    val label: String,
    val body: String,
    val linkedScanId: String?,
    val linkedSignalKey: String?,
    val createdAtEpochMs: Long,
)
