package com.signalsoop.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.signalsoop.app.security.ScanPolicy
import com.signalsoop.app.ui.theme.ScoopBlue
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopSurfaceHigh
import com.signalsoop.app.ui.theme.ScoopWhite

@Composable
fun SecurityInfoCard(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = ScoopSurfaceHigh,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = ScoopBlue)
                    Text(
                        "Privacy & security",
                        style = MaterialTheme.typography.titleMedium,
                        color = ScoopWhite,
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = ScoopMuted,
                )
            }

            if (expanded) {
                SectionTitle("What we protect")
                ScanPolicy.privacyBullets.forEach { bullet ->
                    Bullet(bullet)
                }
                SectionTitle("Why permissions are requested")
                ScanPolicy.permissionBullets.forEach { bullet ->
                    Bullet(bullet)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = ScoopBlue,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun Bullet(text: String) {
    Text(
        "• $text",
        style = MaterialTheme.typography.bodyMedium,
        color = ScoopMuted,
    )
}
