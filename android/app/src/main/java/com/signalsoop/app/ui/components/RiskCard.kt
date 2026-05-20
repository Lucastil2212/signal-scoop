package com.signalsoop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.signalsoop.app.model.RiskLevel
import com.signalsoop.app.model.RiskSummary
import com.signalsoop.app.ui.theme.ScoopBlue
import com.signalsoop.app.ui.theme.ScoopDanger
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWarning
import com.signalsoop.app.ui.theme.ScoopWhite

@Composable
fun RiskCard(summary: RiskSummary, modifier: Modifier = Modifier) {
    val accent = when (summary.level) {
        RiskLevel.LOW -> ScoopGreen
        RiskLevel.MODERATE -> ScoopBlue
        RiskLevel.ELEVATED -> ScoopWarning
        RiskLevel.HIGH -> ScoopDanger
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(ScoopSurfaceHigh, ScoopSurfaceHigh.copy(alpha = 0.7f)),
                ),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Signal risk", style = MaterialTheme.typography.labelLarge, color = ScoopMuted)
                Text(
                    summary.level.label,
                    style = MaterialTheme.typography.headlineLarge,
                    color = accent,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${summary.score}/100",
                    style = MaterialTheme.typography.titleLarge,
                    color = ScoopWhite,
                )
                CopyIconButton(
                    label = "risk summary",
                    value =
                        buildString {
                            append("${summary.level.label} ${summary.score}/100\n")
                            append(summary.level.description)
                            append('\n')
                            summary.highlights.forEach { append("• $it\n") }
                        },
                )
            }
        }

        LinearProgressIndicator(
            progress = { summary.score / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = ScoopMuted.copy(alpha = 0.25f),
        )

        Text(
            summary.level.description,
            style = MaterialTheme.typography.bodyMedium,
            color = ScoopMuted,
        )

        summary.highlights.forEach { highlight ->
            Text("• $highlight", style = MaterialTheme.typography.bodyMedium, color = ScoopWhite)
        }
    }
}
