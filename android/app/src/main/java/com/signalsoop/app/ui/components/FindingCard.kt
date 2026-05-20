package com.signalsoop.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import com.signalsoop.app.ui.theme.ScoopBlue
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurface
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWarning
import com.signalsoop.app.ui.theme.ScoopWhite

@Composable
fun FindingCard(finding: Finding, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ScoopSurface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryChip(finding.category)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    finding.signalStrength?.let { rssi ->
                        Text(
                            "$rssi dBm",
                            style = MaterialTheme.typography.labelLarge,
                            color = ScoopGreen,
                        )
                    }
                    CopyIconButton(
                        label = finding.category.label,
                        value =
                            buildString {
                                append(finding.title)
                                append('\n')
                                append(finding.detail)
                                finding.signalStrength?.let { append("\n$it dBm") }
                            },
                    )
                }
            }
            Text(
                finding.title,
                style = MaterialTheme.typography.titleMedium,
                color = ScoopWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                finding.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = ScoopMuted,
            )
        }
    }
}

@Composable
private fun CategoryChip(category: SignalCategory) {
    val color = when (category) {
        SignalCategory.BLE -> ScoopBlue
        SignalCategory.WIFI -> ScoopGreen
        SignalCategory.BLUETOOTH -> ScoopBlue
        SignalCategory.SENSORS -> ScoopMuted
        SignalCategory.NFC -> ScoopWarning
        SignalCategory.SYSTEM -> ScoopMuted
        SignalCategory.ALL -> ScoopWhite
    }

    Surface(
        color = ScoopSurfaceHigh,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            category.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}
