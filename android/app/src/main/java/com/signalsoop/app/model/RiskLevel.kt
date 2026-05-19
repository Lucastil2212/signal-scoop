package com.signalsoop.app.model

enum class RiskLevel(val label: String, val description: String) {
    LOW("Low", "Few notable signals nearby. Routine environment."),
    MODERATE("Moderate", "Several unknown or strong nearby signals. Worth a closer look."),
    ELEVATED("Elevated", "Multiple strong or hidden signals detected. Review findings."),
    HIGH("High", "Many unknown BLE devices, hidden networks, or very strong RSSI nearby."),
}

data class RiskSummary(
    val level: RiskLevel,
    val score: Int,
    val highlights: List<String>,
)
