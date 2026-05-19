package com.signalsoop.app.model

data class Finding(
    val id: String,
    val category: SignalCategory,
    val title: String,
    val detail: String,
    val signalStrength: Int? = null,
    val riskPoints: Int = 0,
)
