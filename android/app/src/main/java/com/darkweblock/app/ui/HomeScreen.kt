package com.darkweblock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkweblock.app.BluetoothStatus
import com.darkweblock.app.ConnectionStatus
import com.darkweblock.app.LogEntry
import com.darkweblock.app.OwnerProfile
import com.darkweblock.app.NearbyScanner
import com.darkweblock.app.PermissionState
import com.darkweblock.app.PairedDevice

@Composable
fun HomeScreen(
    status: BluetoothStatus,
    ownerProfile: OwnerProfile,
    isPro: Boolean,
    connectPermission: PermissionState,
    scanPermission: PermissionState,
    pairedDevices: List<PairedDevice>,
    serviceLookups: Map<String, String>,
    connectionStatus: Map<String, ConnectionStatus>,
    logEntries: List<LogEntry>,
    monitoring: Boolean,
    notificationsAllowed: Boolean,
    batteryUnrestricted: Boolean,
    locationEnabled: Boolean,
    locationGranted: Boolean,
    scanner: NearbyScanner,
    onRefresh: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRequestConnectPermission: () -> Unit,
    onRequestScanPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefreshServices: (String) -> Unit,
    onShareReport: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onShareScanReport: () -> Unit,
    onClearLog: () -> Unit,
    onShareLog: () -> Unit,
    onSetMonitoring: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSetLocation: (Boolean) -> Unit,
    onOpenMap: (Double, Double, String) -> Unit,
    onSaveOwner: (OwnerProfile) -> Unit,
    onClearOwner: () -> Unit,
    onShareOwnership: () -> Unit,
    onUnlockPro: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Ink)
            .background(
                Brush.radialGradient(
                    colors = listOf(Palette.AccentDeep.copy(alpha = 0.35f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0f, 0f),
                    radius = 1100f,
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header()
            DashboardCard(
                profile = ownerProfile,
                devices = pairedDevices,
                connectionStatus = connectionStatus,
                logEntries = logEntries,
                onSave = onSaveOwner,
                onClear = onClearOwner,
                onShareCertificate = onShareOwnership,
                onOpenMap = onOpenMap,
                isPro = isPro,
                onUnlockPro = onUnlockPro,
            )
            StatusCard(status, onRefresh, onOpenBluetoothSettings)
            PairedDevicesCard(
                status = status,
                permission = connectPermission,
                devices = pairedDevices,
                onRequestPermission = onRequestConnectPermission,
                onOpenAppSettings = onOpenAppSettings,
                onShareReport = onShareReport,
                serviceLookups = serviceLookups,
                connectionStatus = connectionStatus,
                onRefreshServices = onRefreshServices,
            )
            NearbyScanCard(
                status = status,
                permission = scanPermission,
                scanner = scanner,
                onRequestPermission = onRequestScanPermission,
                onOpenAppSettings = onOpenAppSettings,
                onStartScan = onStartScan,
                onStopScan = onStopScan,
                onShareReport = onShareScanReport,
            )
            MonitoringCard(
                monitoring = monitoring,
                notificationsAllowed = notificationsAllowed,
                batteryUnrestricted = batteryUnrestricted,
                locationEnabled = locationEnabled,
                locationGranted = locationGranted,
                onSetMonitoring = onSetMonitoring,
                onOpenAppSettings = onOpenAppSettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onSetLocation = onSetLocation,
            )
            ConnectionLogCard(
                entries = logEntries,
                onClear = onClearLog,
                onShare = onShareLog,
                onOpenMap = onOpenMap,
            )
            ScopeCard()
            Text(
                "v0.10 · Step 10 · Test build",
                color = Palette.Muted.copy(alpha = 0.6f),
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Text(
            "BLUETOOTH OWNERSHIP TOOLKIT",
            color = Palette.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                append("Dark Web\n")
                withStyle(SpanStyle(color = Palette.Accent)) { append("Lock") }
            },
            color = Palette.Text,
            fontSize = 44.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.5).sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Find your earbuds, know when they disconnect, and see where you last had them.",
            color = Palette.Muted,
            fontSize = 16.sp,
            lineHeight = 23.sp,
        )
    }
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.Surface)),
            )
            .border(1.dp, Palette.Stroke, RoundedCornerShape(28.dp))
            .padding(22.dp),
    ) { content() }
}

internal data class Pill(val label: String, val color: Color)

private fun supportedPill(status: BluetoothStatus) =
    if (status == BluetoothStatus.NOT_SUPPORTED) Pill("NO", Palette.Bad) else Pill("YES", Palette.Good)

private fun powerPill(status: BluetoothStatus) = when (status) {
    BluetoothStatus.ON -> Pill("ON", Palette.Good)
    BluetoothStatus.OFF -> Pill("OFF", Palette.Bad)
    BluetoothStatus.TURNING_ON -> Pill("TURNING ON", Palette.Warn)
    BluetoothStatus.TURNING_OFF -> Pill("TURNING OFF", Palette.Warn)
    BluetoothStatus.NOT_SUPPORTED -> Pill("N/A", Palette.Muted)
}

@Composable
private fun StatusCard(
    status: BluetoothStatus,
    onRefresh: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    Card {
        Text("Phone Bluetooth", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Read live from Android. Updates automatically when you switch Bluetooth on or off.",
            color = Palette.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(18.dp))
        StatusRow("Bluetooth supported", supportedPill(status))
        Spacer(Modifier.height(12.dp))
        StatusRow("Bluetooth switched on", powerPill(status))
        Spacer(Modifier.height(20.dp))

        if (status == BluetoothStatus.OFF) {
            Button(
                onClick = onOpenBluetoothSettings,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent),
            ) { Text("Open Bluetooth settings", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Check again", color = Palette.Text) }
    }
}

@Composable
internal fun StatusRow(label: String, pill: Pill) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Ink.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(pill.color.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(pill.color))
            Spacer(Modifier.size(8.dp))
            Text(pill.label, color = pill.color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun ScopeCard() {
    Card {
        Text("What this build does", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        listOf(
            "Checks whether this phone has Bluetooth and whether it is on.",
            "Tracks when your earbuds connect and disconnect, and can save where you last had them.",
            "Scans nearby for 15 seconds only when you tap Scan. It only listens; it never connects or pairs.",
            "Asks only for \"Nearby devices\", only when you tap a button, and never uses it for location.",
            "Does not lock, block or control earbuds. That needs proof of support from the earbuds' firmware.",
        ).forEach {
            Row(Modifier.padding(vertical = 5.dp)) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Palette.Accent),
                )
                Spacer(Modifier.size(12.dp))
                Text(it, color = Palette.Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}
