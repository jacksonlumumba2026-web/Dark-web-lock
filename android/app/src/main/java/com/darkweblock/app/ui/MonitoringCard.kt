package com.darkweblock.app.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MonitoringCard(
    monitoring: Boolean,
    notificationsAllowed: Boolean,
    batteryUnrestricted: Boolean,
    onSetMonitoring: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Background monitoring", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (monitoring) "On — watching for disconnects" else "Off",
                    color = if (monitoring) Palette.Good else Palette.Muted,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = monitoring,
                onCheckedChange = onSetMonitoring,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Palette.Text,
                    checkedTrackColor = Palette.Accent,
                    uncheckedTrackColor = Palette.Ink,
                    uncheckedBorderColor = Palette.Stroke,
                ),
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "When on, the app keeps watching even after you close it. It logs every connect and " +
                "disconnect and alerts you the moment your earbuds disconnect, so you notice if you walk away.",
            color = Palette.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(12.dp))
        listOf(
            "Android shows a permanent quiet notice while this runs. That is required; it cannot be hidden.",
            "Alerts only if the earbuds had been connected for at least a minute, so briefly re-casing them does not alert.",
            "Uses a little extra battery. Turn it off any time with this switch or the notice's Stop button.",
            "It still cannot lock or control the earbuds. It only watches and notifies.",
        ).forEach { Bullet(it) }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(Palette.Ink.copy(alpha = 0.55f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Battery optimisation", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (batteryUnrestricted) {
                        "Unrestricted — monitoring can run reliably."
                    } else {
                        "Restricted — Samsung may stop monitoring in the background. Set this app to Unrestricted."
                    },
                    color = if (batteryUnrestricted) Palette.Good else Palette.Warn,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        if (!batteryUnrestricted) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Fix battery setting", onOpenBatterySettings)
        }

        if (monitoring && !notificationsAllowed) {
            Spacer(Modifier.height(14.dp))
            Notice(
                "Monitoring is running, but notifications are blocked, so you will not see alerts or the notice. " +
                    "Allow notifications in app settings.",
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Open app settings", onOpenAppSettings)
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("•", color = Palette.Accent, fontSize = 14.sp, modifier = Modifier.width(16.dp))
        Text(text, color = Palette.Muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
