package com.signalsoop.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.signalsoop.app.ui.theme.ScoopBlue
import com.signalsoop.app.ui.theme.ScoopGreen
import com.signalsoop.app.ui.theme.ScoopMuted
import com.signalsoop.app.ui.theme.ScoopTech
import com.signalsoop.app.ui.theme.ScoopWarning
import com.signalsoop.app.ui.theme.ScoopWhite

private val TECH_PATTERN =
    Regex(
        """(-?\d+\s*dBm)|(\d+/100)|(\[[^\]]+\])|((?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2})|(\b(?:BLE|Wi-Fi|WiFi|Bluetooth|NFC|RSSI|BSSID|SSID)\b)""",
    )

@Composable
fun ChatRichText(
    text: String,
    modifier: Modifier = Modifier,
    baseColor: Color = ScoopWhite,
) {
    val annotated = remember(text) { annotateChatText(text, baseColor) }
    Text(
        annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun annotateChatText(text: String, baseColor: Color): AnnotatedString =
    buildAnnotatedString {
        val bodyStyle = SpanStyle(color = baseColor)
        val bulletPrefix = SpanStyle(color = ScoopMuted, fontWeight = FontWeight.SemiBold)

        text.lines().forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) append('\n')

            if (line.startsWith("• ") || line.startsWith("- ")) {
                withStyle(bulletPrefix) { append(line.take(2)) }
                appendStyledLine(line.drop(2), bodyStyle)
            } else {
                appendStyledLine(line, bodyStyle)
            }
        }
    }

private fun AnnotatedString.Builder.appendStyledLine(line: String, bodyStyle: SpanStyle) {
    var cursor = 0
    val matches = TECH_PATTERN.findAll(line).toList()
    if (matches.isEmpty()) {
        withStyle(bodyStyle) { append(line) }
        return
    }
    for (match in matches) {
        val start = match.range.first
        if (start > cursor) {
            withStyle(bodyStyle) { append(line.substring(cursor, start)) }
        }
        withStyle(spanForMatch(match.value)) { append(match.value) }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) {
        withStyle(bodyStyle) { append(line.substring(cursor)) }
    }
}

private fun spanForMatch(value: String): SpanStyle =
    when {
        value.contains("dBm", ignoreCase = true) ->
            SpanStyle(
                color = ScoopGreen,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        value.contains("/100") ->
            SpanStyle(color = ScoopWarning, fontWeight = FontWeight.Bold)
        value.startsWith("[") ->
            SpanStyle(color = ScoopBlue, fontWeight = FontWeight.SemiBold)
        value.contains(':') ->
            SpanStyle(
                color = ScoopTech,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
        else ->
            SpanStyle(color = ScoopTech, fontWeight = FontWeight.SemiBold)
    }
