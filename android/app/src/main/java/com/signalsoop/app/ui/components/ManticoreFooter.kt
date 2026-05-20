package com.signalsoop.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.signalsoop.app.R
import com.signalsoop.app.ui.theme.ScoopMuted

@Composable
fun ManticoreFooter(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelMedium,
            color = ScoopMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.company_name),
            style = MaterialTheme.typography.bodySmall,
            color = ScoopMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.license_notice),
            style = MaterialTheme.typography.bodySmall,
            color = ScoopMuted.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
    }
}
