package com.darkweblock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkweblock.app.LogEntry
import com.darkweblock.app.formatEventTime

@Composable
fun ConnectionLogCard(
    entries: List<LogEntry>,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onOpenMap: (Double, Double, String) -> Unit,
) {
    Card {
        Text("Connection history", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Each time a paired device connects or disconnects. With background monitoring on, changes are " +
                "recorded even while the app is closed; with it off, only while the app is open.",
            color = Palette.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(18.dp))

        if (entries.isEmpty()) {
            Notice(
                "No events yet. Keep the app open, then put your earbuds in the case and take them out — " +
                    "each change will appear here.",
            )
            return@Card
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.take(30).forEach { EventRow(it, onOpenMap) }
        }
        if (entries.size > 30) {
            Text(
                "Showing the 30 most recent of ${entries.size}.",
                color = Palette.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Row {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Share", color = Palette.Text) }
            Spacer(Modifier.width(12.dp))
            TextButton(
                onClick = onClear,
                modifier = Modifier.height(52.dp),
            ) { Text("Clear", color = Palette.Bad) }
        }
    }
}

@Composable
private fun EventRow(entry: LogEntry, onOpenMap: (Double, Double, String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Ink.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (entry.connected) Palette.Good else Palette.Bad),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${entry.label} · ${entry.name}",
                color = Palette.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(entry.address, color = Palette.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(formatEventTime(entry.epochMillis), color = Palette.Muted, fontSize = 12.sp)
            if (entry.hasLocation) {
                Text(
                    "📍 Map",
                    color = Palette.Accent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { onOpenMap(entry.lat!!, entry.lon!!, entry.name) },
                )
            }
        }
    }
}
