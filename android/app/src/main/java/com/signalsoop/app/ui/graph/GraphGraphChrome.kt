package com.signalsoop.app.ui.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.signalsoop.app.history.GraphColorPalette
import com.signalsoop.app.ui.theme.ScoopBlack
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWhite

@Composable
fun GraphGraphChrome(
    nodeCount: Int,
    linkCount: Int,
    usesMap: Boolean,
    filterLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ScoopSurfaceHigh,
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text =
                    buildString {
                        append("$nodeCount nodes · $linkCount links")
                        append(if (usesMap) " · map" else " · layout")
                        append(" · ")
                        append(filterLabel)
                    },
                color = ScoopWhite,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "Past scans: tap for signals · nodes & lines: tap for details",
                color = ScoopMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            GraphLegendRow(compact = false)
        }
    }
}

@Composable
fun GraphLegendRow(compact: Boolean = false) {
    if (compact) {
        Text("Green = scan · cyan = BLE · amber = Wi-Fi · pink = BT", color = ScoopMuted, style = MaterialTheme.typography.labelSmall)
        return
    }
    val scroll = rememberScrollState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LegendChip(Color(0xFF39FF14), "Scan")
        LegendChip(Color(0xFF7AE7FF), "BLE")
        LegendChip(Color(0xFFFFB020), "Wi-Fi")
        LegendChip(Color(0xFFFF4D6D), "BT")
        LegendChip(GraphColorPalette.place, "Place")
        LegendChip(GraphColorPalette.linkObserved, "Link")
        LegendChip(GraphColorPalette.linkRepeat, "Repeat")
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Surface(color = ScoopBlack, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(8.dp).clip(CircleShape).background(color),
            )
            Text(label, color = ScoopMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
