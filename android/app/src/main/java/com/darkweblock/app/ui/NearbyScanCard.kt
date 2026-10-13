package com.darkweblock.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkweblock.app.BleResult
import com.darkweblock.app.BluetoothStatus
import com.darkweblock.app.ClassicResult
import com.darkweblock.app.NearbyScanner
import com.darkweblock.app.PermissionState

@Composable
fun NearbyScanCard(
    status: BluetoothStatus,
    permission: PermissionState,
    scanner: NearbyScanner,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onShareReport: () -> Unit,
) {
    Card {
        Text("Nearby scan", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Two searches side by side for ${NearbyScanner.SCAN_SECONDS} seconds: Android's device search (what Samsung " +
                "settings uses) and a BLE scan (the only kind a website can see). Listening only — nothing is connected.",
            color = Palette.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(18.dp))

        when {
            status == BluetoothStatus.NOT_SUPPORTED -> Notice("This phone has no Bluetooth.")

            permission == PermissionState.NOT_ASKED || permission == PermissionState.DENIED -> {
                Notice(
                    "Scanning needs the \"Nearby devices\" permission. This app declares it is never used to " +
                        "work out your location.",
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Allow Nearby devices", onRequestPermission)
            }

            permission == PermissionState.BLOCKED -> {
                Notice(
                    "\"Nearby devices\" was denied, so Android will not ask again. To allow it: " +
                        "App settings → Permissions → Nearby devices → Allow.",
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Open app settings", onOpenAppSettings)
            }

            status != BluetoothStatus.ON -> Notice("Turn Bluetooth on to scan.")

            else -> ScanBody(scanner, onStartScan, onStopScan, onShareReport)
        }
    }
}

@Composable
private fun ScanBody(
    scanner: NearbyScanner,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onShareReport: () -> Unit,
) {
    if (scanner.scanning) {
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = Palette.Accent.copy(alpha = 0.35f),
                disabledContentColor = Palette.Text,
            ),
        ) { Text("Scanning… ${scanner.secondsLeft}s", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onStopScan,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Stop", color = Palette.Text) }
    } else {
        PrimaryButton(if (scanner.hasScanned) "Scan again" else "Scan for ${NearbyScanner.SCAN_SECONDS} seconds", onStartScan)
    }

    scanner.error?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, color = Palette.Warn, fontSize = 13.sp, lineHeight = 18.sp)
    }

    if (scanner.hasScanned && !scanner.scanning) {
        Spacer(Modifier.height(16.dp))
        BaselineSection(scanner)
    }

    if (scanner.hasScanned) {
        val classic = scanner.classic.values.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
        val ble = scanner.ble.values.sortedByDescending { it.rssi }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Device search", classic.size)
        Text(
            "Android adds some BLE devices here too; the type line says which. Earbuds usually answer only in pairing mode.",
            color = Palette.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            classic.forEach { ClassicRow(it, scanner.isNew(it.address)) }
        }
        if (classic.isEmpty() && !scanner.scanning) EmptyLine("No devices answered.")

        Spacer(Modifier.height(20.dp))
        SectionHeader("BLE scan", ble.size)
        Text(
            "Unnamed entries here are what browsers list as \"Unknown device\".",
            color = Palette.Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ble.forEach { BleRow(it, scanner.isNew(it.address)) }
        }
        if (ble.isEmpty() && !scanner.scanning) {
            EmptyLine("No BLE devices found. After several scans in a row Android pauses scanning for about 30 seconds.")
        }

        if (!scanner.scanning) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onShareReport,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Share scan report", color = Palette.Text) }
        }
    }
}

@Composable
private fun BaselineSection(scanner: NearbyScanner) {
    val baseline = scanner.baseline
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Accent.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Text("Which entries are my earbuds?", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        if (scanner.showingBaselineScan) {
            Text(
                "Saved: the results below ARE the \"earbuds off\" baseline (${baseline?.size} devices). " +
                    "Now take the earbuds out, wait until they connect, and tap Scan again.",
                color = Palette.Good,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            TextButton(onClick = scanner::clearBaseline) { Text("Clear baseline", color = Palette.Accent) }
        } else if (baseline == null) {
            Text(
                "1. Put the earbuds in their case and close it.\n" +
                    "2. Scan, then save the result as the baseline.\n" +
                    "3. Take the earbuds out and scan again.\n" +
                    "Entries marked NEW appeared only after the earbuds came out.",
                color = Palette.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = scanner::saveBaseline,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Save this scan as \"earbuds off\"", color = Palette.Text, fontSize = 14.sp) }
        } else {
            Text(
                "Baseline saved with ${baseline.size} devices. Entries marked NEW were not in it. " +
                    "BLE devices can change address every few minutes, so NEW is a clue, not proof — repeat to confirm.",
                color = Palette.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            TextButton(onClick = scanner::clearBaseline) { Text("Clear baseline", color = Palette.Accent) }
        }
    }
}

private fun shortType(type: String) = when {
    type.startsWith("Classic") -> "Classic"
    type.startsWith("Low Energy") -> "BLE"
    type.startsWith("Dual") -> "Dual"
    else -> "type ?"
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Text(
        "${label.uppercase()} · $count",
        color = Palette.Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, color = Palette.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ClassicRow(device: ClassicResult, isNew: Boolean) {
    ResultRow(
        key = "c-${device.address}",
        title = device.name ?: "(no name)",
        subtitle = "${device.address} · ${device.rssi?.let { "$it dBm" } ?: "signal ?"} · ${shortType(device.type)}",
        tags = listOfNotNull(
            ("NEW" to Palette.Good).takeIf { isNew },
            ("PAIRED" to Palette.Accent).takeIf { device.isPaired },
            ("AUDIO" to Palette.Muted).takeIf { device.isAudio },
        ),
    ) {
        Detail("Name", device.name ?: "(none reported)")
        Detail("Address", device.address, mono = true)
        Detail("Signal", device.rssi?.let { "$it dBm" } ?: "Not reported")
        Detail("Type", device.type)
        Detail("Class", "${device.majorClass} / ${device.deviceClass}")
    }
}

@Composable
private fun BleRow(device: BleResult, isNew: Boolean) {
    ResultRow(
        key = "b-${device.address}",
        title = device.advertisedName ?: device.systemName ?: "(no name)",
        subtitle = "${device.address} · ${device.rssi} dBm",
        tags = listOfNotNull(
            ("NEW" to Palette.Good).takeIf { isNew },
            ("PAIRED" to Palette.Accent).takeIf { device.isPaired },
        ),
    ) {
        Detail("Advertised name", device.advertisedName ?: "(none — browsers show \"Unknown\")")
        Detail("Name on phone", device.systemName ?: "(none)")
        Detail("Address", device.address, mono = true)
        Detail("Signal", "${device.rssi} dBm")
        Detail("Connectable", if (device.connectable) "Yes" else "No (broadcast only)")
        Detail("Seen", "${device.timesSeen} times")
        if (device.services.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Advertised services", color = Palette.Muted, fontSize = 12.sp, letterSpacing = 1.sp)
            device.services.forEach {
                Text(
                    (if (it.isVendor) "◆ " else "• ") + it.label,
                    color = if (it.isVendor) Palette.Warn else Palette.Text,
                    fontSize = 13.sp,
                )
            }
        }
        if (device.serviceData.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Service data", color = Palette.Muted, fontSize = 12.sp, letterSpacing = 1.sp)
            device.serviceData.forEach {
                Text(
                    (if (it.service.isVendor) "◆ " else "• ") + it.service.label,
                    color = if (it.service.isVendor) Palette.Warn else Palette.Text,
                    fontSize = 13.sp,
                )
                Text(it.hex, color = Palette.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        if (device.manufacturer.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Maker data", color = Palette.Muted, fontSize = 12.sp, letterSpacing = 1.sp)
            device.manufacturer.forEach {
                Text(it.company, color = Palette.Text, fontSize = 13.sp)
                Text(it.hex, color = Palette.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ResultRow(
    key: String,
    title: String,
    subtitle: String,
    tags: List<Pair<String, Color>>,
    details: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Ink.copy(alpha = 0.55f))
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Palette.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            tags.forEach { (label, color) ->
                Spacer(Modifier.width(6.dp))
                Tag(label, color)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            details()
        }
    }
}
