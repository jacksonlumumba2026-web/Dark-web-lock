package com.darkweblock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkweblock.app.ConnectionStatus
import com.darkweblock.app.LogEntry
import com.darkweblock.app.OwnerProfile
import com.darkweblock.app.PairedDevice
import com.darkweblock.app.formatEventTime

@Composable
fun DashboardCard(
    profile: OwnerProfile,
    device: PairedDevice?,
    connection: ConnectionStatus?,
    logEntries: List<LogEntry>,
    onSave: (OwnerProfile) -> Unit,
    onClear: () -> Unit,
    onShareCertificate: () -> Unit,
    onOpenMap: (Double, Double, String) -> Unit,
) {
    var editing by remember(profile) { mutableStateOf(!profile.isRegistered) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Palette.AccentDeep.copy(alpha = 0.45f), Palette.Surface)))
            .border(1.dp, Palette.Accent.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
            .padding(22.dp),
    ) {
        Text("OWNER DASHBOARD", color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(14.dp))

        if (editing) {
            OwnerForm(profile, device, onSave = { onSave(it); editing = false }, onCancel = { editing = false }.takeIf { profile.isRegistered })
        } else {
            RegisteredView(
                profile = profile,
                device = device,
                connection = connection,
                logEntries = logEntries,
                onEdit = { editing = true },
                onClear = onClear,
                onShareCertificate = onShareCertificate,
                onOpenMap = onOpenMap,
            )
        }
    }
}

@Composable
private fun RegisteredView(
    profile: OwnerProfile,
    device: PairedDevice?,
    connection: ConnectionStatus?,
    logEntries: List<LogEntry>,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    onShareCertificate: () -> Unit,
    onOpenMap: (Double, Double, String) -> Unit,
) {
    Text(profile.ownerName, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    if (profile.contact.isNotBlank()) {
        Text(profile.contact, color = Palette.Muted, fontSize = 14.sp)
    }

    Spacer(Modifier.height(18.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Ink.copy(alpha = 0.6f))
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Registered device", color = Palette.Muted, fontSize = 12.sp, letterSpacing = 1.sp)
                    Text(
                        profile.deviceLabel.ifBlank { device?.let { it.alias ?: it.name ?: "(no name)" } ?: "No device" },
                        color = Palette.Text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                LiveDot(connection)
            }

            if (device != null) {
                Spacer(Modifier.height(14.dp))
                Fact("Address", device.address, mono = true)
                Fact("Type", device.type)
                Fact("Class", device.deviceClass)
                Fact("Audio profiles", audioProfiles(device))
                Fact("Status now", connection?.summary ?: "Checking…")
                Fact("Last seen connected", lastSeen(device.address, connection, logEntries))

                val place = lastKnownPlace(device.address, logEntries)
                if (place != null) {
                    Spacer(Modifier.height(12.dp))
                    val label = profile.deviceLabel.ifBlank { device.alias ?: device.name ?: "Earbuds" }
                    LastKnownPlace(place, label, onOpenMap)
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Pair your earbuds and grant Nearby devices to link verified details here.",
                    color = Palette.Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text(
        "These device facts are read from Android, not typed in. This is an ownership record and an " +
            "evidence trail — it cannot stop anyone else pairing with the earbuds.",
        color = Palette.Muted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onShareCertificate,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent),
        enabled = device != null,
    ) { Text("Share ownership record", fontWeight = FontWeight.SemiBold) }
    Spacer(Modifier.height(8.dp))
    Row {
        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.weight(1f).height(50.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Edit", color = Palette.Text) }
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = onClear, modifier = Modifier.height(50.dp)) {
            Text("Remove", color = Palette.Bad)
        }
    }
}

@Composable
private fun OwnerForm(
    profile: OwnerProfile,
    device: PairedDevice?,
    onSave: (OwnerProfile) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(profile.ownerName) }
    var contact by remember { mutableStateOf(profile.contact) }
    var label by remember {
        mutableStateOf(profile.deviceLabel.ifBlank { device?.let { it.alias ?: it.name ?: "" } ?: "" })
    }

    Text(
        if (profile.isRegistered) "Edit owner details" else "Register as owner",
        color = Palette.Text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text("Stored only on this phone. Never uploaded.", color = Palette.Muted, fontSize = 13.sp)
    Spacer(Modifier.height(16.dp))

    Field("Owner name", name, "Your name") { name = it.take(60) }
    Field("Contact (optional)", contact, "Phone or email") { contact = it.take(80) }
    Field("Device label (optional)", label, "e.g. My TWS earbuds") { label = it.take(60) }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onSave(OwnerProfile(name.trim(), contact.trim(), label.trim())) },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Accent,
            disabledContainerColor = Palette.Accent.copy(alpha = 0.3f),
            disabledContentColor = Palette.Text.copy(alpha = 0.6f),
        ),
    ) { Text("Save owner details", fontWeight = FontWeight.SemiBold) }
    if (onCancel != null) {
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = Palette.Muted)
        }
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(label, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = Palette.Muted.copy(alpha = 0.6f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Palette.Ink.copy(alpha = 0.6f),
                unfocusedContainerColor = Palette.Ink.copy(alpha = 0.6f),
                focusedTextColor = Palette.Text,
                unfocusedTextColor = Palette.Text,
                cursorColor = Palette.Accent,
                focusedIndicatorColor = Palette.Accent,
                unfocusedIndicatorColor = Palette.Stroke,
            ),
        )
    }
}

@Composable
private fun Fact(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.width(140.dp))
        Text(
            value,
            color = Palette.Text,
            fontSize = 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun LiveDot(connection: ConnectionStatus?) {
    val connected = connection?.isConnected == true
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background((if (connected) Palette.Good else Palette.Muted).copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected) Palette.Good else Palette.Muted))
        Spacer(Modifier.width(6.dp))
        Text(
            if (connected) "LIVE" else "OFFLINE",
            color = if (connected) Palette.Good else Palette.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun LastKnownPlace(entry: LogEntry, label: String, onOpenMap: (Double, Double, String) -> Unit) {
    val lat = entry.lat ?: return
    val lon = entry.lon ?: return
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Accent.copy(alpha = 0.12f))
            .padding(14.dp),
    ) {
        Column {
            Text("Where you last had them", color = Palette.Accent, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${entry.label} ${formatEventTime(entry.epochMillis)}" +
                    (entry.accuracyM?.let { " · within ${it.toInt()} m" } ?: ""),
                color = Palette.Text,
                fontSize = 14.sp,
            )
            Text(
                "%.5f, %.5f".format(lat, lon),
                color = Palette.Muted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onOpenMap(lat, lon, label) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent),
            ) { Text("Open in Maps", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** The most recent disconnect for this device that captured a location. */
private fun lastKnownPlace(address: String, logEntries: List<LogEntry>): LogEntry? =
    logEntries.firstOrNull { it.address == address && it.hasLocation }

private fun audioProfiles(device: PairedDevice): String {
    val labels = device.services?.filter { !it.isVendor }?.map { it.label }?.filter {
        it.contains("A2DP") || it.contains("AVRCP") || it.contains("HFP") || it.contains("Headset")
    }
    return if (labels.isNullOrEmpty()) "Not reported" else labels.joinToString(", ")
}

private fun lastSeen(address: String, connection: ConnectionStatus?, logEntries: List<LogEntry>): String {
    if (connection?.isConnected == true) return "Connected now"
    val lastConnect = logEntries.firstOrNull { it.address == address && it.connected }
    return lastConnect?.let { formatEventTime(it.epochMillis) } ?: "No record yet"
}
