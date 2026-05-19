package com.signalsoop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWarning
import com.signalsoop.app.ui.theme.ScoopWhite

@Composable
fun AssistantBetaBanner(
    versionLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = ScoopSurfaceHigh,
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "BETA",
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ScoopWarning.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = ScoopWarning,
                )
                Text(
                    versionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = ScoopWhite,
                )
            }
            Text(
                "Ask is new in this release. Summaries work without a model; for open-ended chat, " +
                    "use Qwen2.5-0.5B or Gemma 3 1B on phones with more RAM. SmolLM is fastest but least detailed.",
                style = MaterialTheme.typography.bodySmall,
                color = ScoopMuted,
            )
        }
    }
}
