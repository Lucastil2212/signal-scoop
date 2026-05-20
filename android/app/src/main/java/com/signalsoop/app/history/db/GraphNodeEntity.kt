package com.signalsoop.app.history.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "graph_nodes")
data class GraphNodeEntity(
    @PrimaryKey val id: String,
    val nodeType: String,
    val label: String,
    val metadataJson: String?,
)
