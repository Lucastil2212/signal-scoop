package com.signalsoop.app.history

data class GraphLinkSelection(
    val sourceId: String,
    val targetId: String,
    val relation: String,
) {
    val key: String get() = "$sourceId|$targetId|$relation"
}
