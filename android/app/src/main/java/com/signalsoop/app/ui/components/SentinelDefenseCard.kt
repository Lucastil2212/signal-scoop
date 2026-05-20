package com.signalsoop.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.signalsoop.app.security.DefenseSentinel
import com.signalsoop.app.ui.theme.ScoopDanger
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWarning
import com.signalsoop.app.ui.theme.ScoopWhite

@Composable
fun SentinelDefenseCard(
    report: DefenseSentinel.SentinelReport,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ScoopSurfaceHigh,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = ScoopGreen)
                    Text("Defense sentinel", style = MaterialTheme.typography.titleMedium, color = ScoopWhite)
                    Text(
                        "${report.defenseScore}/100 · ${report.postureLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        color = postureColor(report.defenseScore),
                    )
                }
                CopyIconButton(
                    label = "defense sentinel",
                    value =
                        buildString {
                            append("Defense ${report.defenseScore}/100 · ${report.postureLabel}\n")
                            report.alerts.forEach { a ->
                                append("${a.title}: ${a.detail}\n→ ${a.action}\n")
                            }
                            report.playbook.forEach { append("• $it\n") }
                        },
                )
            }
            report.alerts.take(4).forEach { alert ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(alert.title, color = severityColor(alert.severity), style = MaterialTheme.typography.labelLarge)
                    Text(alert.detail, color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
                    Text("→ ${alert.action}", color = ScoopWhite, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Playbook", color = ScoopGreen, style = MaterialTheme.typography.labelMedium)
            report.playbook.take(3).forEach { line ->
                Text("• $line", color = ScoopMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun postureColor(score: Int) =
    when {
        score >= 80 -> ScoopGreen
        score >= 50 -> ScoopWarning
        else -> ScoopDanger
    }

private fun severityColor(severity: DefenseSentinel.Severity) =
    when (severity) {
        DefenseSentinel.Severity.INFO -> ScoopMuted
        DefenseSentinel.Severity.WATCH -> ScoopWarning
        DefenseSentinel.Severity.ALERT -> ScoopDanger
        DefenseSentinel.Severity.CRITICAL -> ScoopDanger
    }
