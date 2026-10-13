package com.darkweblock.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkweblock.app.BluetoothStatus
import com.darkweblock.app.PermissionState
import com.darkweblock.app.PairedDevice

@Composable
fun PairedDevicesCard(
    status: BluetoothStatus,
    permission: PermissionState,
    devices: List<PairedDevice>,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onShareReport: () -> Unit,
    serviceLookups: Map<String, String>,
    onRefreshServices: (String) -> Unit,
) {
    Card {
        Text("Paired devices", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Devices saved on this phone, exactly as Android reports them. Paired does not mean connected.",
            color = Palette.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(18.dp))

        when {
            status == BluetoothStatus.NOT_SUPPORTED -> Notice("This phone has no Bluetooth.")

            permission == PermissionState.NOT_ASKED || permission == PermissionState.DENIED -> {
                Notice(
                    "Android needs the \"Nearby devices\" permission before any app can read your paired list. " +
                        "It is used only to read that list on this phone.",
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

            status != BluetoothStatus.ON ->
                Notice("Turn Bluetooth on. Android only shows the paired list while Bluetooth is on.")

            devices.isEmpty() -> Notice("No paired devices. Pair your earbuds in Samsung Bluetooth settings, then come back.")

            else -> {
                Text(
                    "${devices.size} paired · tap one for details",
                    color = Palette.Muted,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    devices.forEach { DeviceRow(it, serviceLookups[it.address], onRefreshServices) }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onShareReport,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Share report", color = Palette.Text) }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: PairedDevice, lookup: String?, onRefreshServices: (String) -> Unit) {
    var expanded by rememberSaveable(device.address) { mutableStateOf(false) }
    val title = device.alias ?: device.name ?: "(no name)"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Ink.copy(alpha = 0.55f))
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(device.deviceClass, color = Palette.Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            Tag(if (device.isAudio) "AUDIO" else "PAIRED", if (device.isAudio) Palette.Accent else Palette.Muted)
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Detail("Name from device", device.name ?: "(none reported)")
            if (device.alias != null && device.alias != device.name) Detail("Name on this phone", device.alias)
            Detail("Address", device.address, mono = true)
            Detail("Type", device.type)
            Detail(
                "Class",
                "${device.majorClass} / ${device.deviceClass}" + (device.rawClass?.let { " · 0x%06X".format(it) } ?: ""),
            )
            Spacer(Modifier.height(8.dp))
            Text("Services Android has cached", color = Palette.Muted, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            val services = device.services
            if (services.isNullOrEmpty()) {
                Text("None cached yet.", color = Palette.Text, fontSize = 14.sp)
            } else {
                services.forEach {
                    Text(
                        (if (it.isVendor) "◆ " else "• ") + it.label,
                        color = if (it.isVendor) Palette.Warn else Palette.Text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    if (it.isVendor) {
                        Text(it.uuid, color = Palette.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onRefreshServices(device.address) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Ask device for its services again", color = Palette.Text, fontSize = 14.sp) }
            Text(
                lookup ?: "The device must be switched on (out of its case) and nearby to answer.",
                color = if (lookup == null) Palette.Muted else Palette.Text,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Connection status is not checked in this build.",
                color = Palette.Muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun Detail(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.width(128.dp))
        Text(
            value,
            color = Palette.Text,
            fontSize = 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
internal fun Tag(label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
internal fun Notice(text: String) {
    Text(
        text,
        color = Palette.Text.copy(alpha = 0.85f),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Ink.copy(alpha = 0.55f))
            .padding(16.dp),
    )
}

@Composable
internal fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent),
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}
