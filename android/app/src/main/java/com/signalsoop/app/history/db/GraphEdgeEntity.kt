package com.signalsoop.app.history.db

import androidx.room.Entity

@Entity(
    tableName = "graph_edges",
    primaryKeys = ["fromNodeId", "toNodeId", "relation"],
)
data class GraphEdgeEntity(
    val fromNodeId: String,
    val toNodeId: String,
    val relation: String,
    val scanId: String?,
    val weight: Int,
)
