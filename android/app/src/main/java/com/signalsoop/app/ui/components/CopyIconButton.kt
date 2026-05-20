package com.signalsoop.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.util.ClipboardUtil

@Composable
fun CopyIconButton(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    IconButton(
        onClick = { ClipboardUtil.copy(context, label, value) },
        modifier = modifier,
    ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy $label", tint = ScoopMuted)
    }
}
