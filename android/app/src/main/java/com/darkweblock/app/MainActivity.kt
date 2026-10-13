package com.darkweblock.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothA2dp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.darkweblock.app.ui.DarkWebLockTheme
import com.darkweblock.app.ui.HomeScreen
import java.time.LocalTime

/** Whether a group of runtime permissions is held. */
enum class PermissionState {
    GRANTED,
    NOT_ASKED,
    DENIED,

    /** Denied in a way Android will no longer show the prompt; only app settings can fix it. */
    BLOCKED,
}

class MainActivity : ComponentActivity() {

    private val status = mutableStateOf(BluetoothStatus.NOT_SUPPORTED)
    private val connectPermission = mutableStateOf(PermissionState.NOT_ASKED)
    private val scanPermission = mutableStateOf(PermissionState.NOT_ASKED)
    private val pairedDevices = mutableStateOf<List<PairedDevice>>(emptyList())

    /** Per-address result of the last "ask the device for its services" request. */
    private val serviceLookups = mutableStateMapOf<String, String>()

    private lateinit var scanner: NearbyScanner
    private lateinit var connectionReader: ConnectionStatusReader

    /** Live connection status per paired device address. */
    private val connectionStatus = mutableStateMapOf<String, ConnectionStatus>()

    private lateinit var connectionLog: ConnectionLog
    private lateinit var ownerStore: OwnerStore
    private val ownerProfile = mutableStateOf(OwnerProfile())

    /** Connection history entries, newest first, shown in the UI. */
    private val logEntries = mutableStateOf<List<LogEntry>>(emptyList())

    /** Last connected/disconnected value witnessed per address, to detect transitions. */
    private val lastConnected = mutableMapOf<String, Boolean>()

    private val monitoring = mutableStateOf(false)
    private val notificationsAllowed = mutableStateOf(true)
    private val batteryUnrestricted = mutableStateOf(true)
    private val locationEnabled = mutableStateOf(false)
    private val locationGranted = mutableStateOf(false)

    // Only used as "something changed" signals; state is always re-read from
    // the adapter rather than trusted from the broadcast.
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                if (readBluetoothStatus(context) != BluetoothStatus.ON) scanner.stop()
            }
            refresh()
        }
    }

    private val uuidReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(
                intent,
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java,
            ) ?: return
            val answered = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID) != null
            val time = LocalTime.now().withNano(0)
            serviceLookups[device.address] = if (answered) {
                "Device answered at $time. List below is up to date."
            } else {
                "No answer at $time — the device may be off, in its case or out of range. Showing the saved list."
            }
            refresh()
        }
    }

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The broadcast only tells us "something changed"; the true state is
            // always re-read from the profile services.
            refreshConnections()
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    private val requestLocation =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.any { it }
            locationGranted.value = granted
            MonitorService.setLocationEnabled(applicationContext, granted)
            locationEnabled.value = granted
            // Restart the running monitor so it adopts the location service type.
            if (granted && MonitorService.isEnabled(this)) {
                MonitorService.start(this)
            }
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsAllowed.value = granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            // Start regardless: the service runs even if the alert channel is blocked,
            // and the user is told alerts will be silent until they allow notifications.
            MonitorService.start(applicationContext)
            monitoring.value = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light system-bar icons on a transparent bar: the app is dark even
        // when the phone itself is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        scanner = NearbyScanner(applicationContext)
        connectionReader = ConnectionStatusReader(applicationContext)
        connectionLog = ConnectionLog(applicationContext)
        ownerStore = OwnerStore(applicationContext)
        ownerProfile.value = ownerStore.load()
        logEntries.value = connectionLog.entries()
        refresh()

        setContent {
            DarkWebLockTheme {
                HomeScreen(
                    status = status.value,
                    ownerProfile = ownerProfile.value,
                    connectPermission = connectPermission.value,
                    scanPermission = scanPermission.value,
                    pairedDevices = pairedDevices.value,
                    serviceLookups = serviceLookups,
                    connectionStatus = connectionStatus,
                    logEntries = logEntries.value,
                    monitoring = monitoring.value,
                    notificationsAllowed = notificationsAllowed.value,
                    batteryUnrestricted = batteryUnrestricted.value,
                    locationEnabled = locationEnabled.value,
                    locationGranted = locationGranted.value,
                    scanner = scanner,
                    onRefresh = ::refresh,
                    onOpenBluetoothSettings = ::openBluetoothSettings,
                    onRequestConnectPermission = { request(connectPermissions, KEY_ASKED_CONNECT) },
                    onRequestScanPermission = { request(scanPermissions, KEY_ASKED_SCAN) },
                    onOpenAppSettings = ::openAppSettings,
                    onRefreshServices = ::refreshServices,
                    onShareReport = { share("Dark Web Lock paired devices report", pairedDevicesReport(pairedDevices.value, connectionStatus)) },
                    onStartScan = scanner::start,
                    onStopScan = scanner::stop,
                    onShareScanReport = { share("Dark Web Lock nearby scan report", nearbyScanReport(scanner)) },
                    onClearLog = ::clearLog,
                    onShareLog = { share("Dark Web Lock connection history", connectionLogReport(logEntries.value)) },
                    onSetMonitoring = ::setMonitoring,
                    onOpenBatterySettings = ::openBatterySettings,
                    onSetLocation = ::setLocation,
                    onOpenMap = ::openMap,
                    onSaveOwner = ::saveOwner,
                    onClearOwner = ::clearOwner,
                    onShareOwnership = ::shareOwnership,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            uuidReceiver,
            IntentFilter(BluetoothDevice.ACTION_UUID),
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            connectionReceiver,
            IntentFilter().apply {
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        connectionReader.start(::refreshConnections)
        // Also picks up changes made in system settings (pairing, unpairing,
        // granting a permission) when the user comes back to the app.
        refresh()
    }

    override fun onStop() {
        // Scanning drains battery and should never continue in the background.
        scanner.stop()
        connectionReader.stop()
        unregisterReceiver(stateReceiver)
        unregisterReceiver(uuidReceiver)
        unregisterReceiver(connectionReceiver)
        super.onStop()
    }

    private fun refresh() {
        status.value = readBluetoothStatus(this)
        monitoring.value = MonitorService.isEnabled(this)
        notificationsAllowed.value =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        batteryUnrestricted.value =
            getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: true
        locationGranted.value = DeviceLocation.hasPermission(this)
        locationEnabled.value = MonitorService.isLocationEnabled(this) && locationGranted.value
        connectPermission.value = readPermission(connectPermissions, KEY_ASKED_CONNECT)
        scanPermission.value = readPermission(scanPermissions, KEY_ASKED_SCAN)
        pairedDevices.value =
            if (connectPermission.value == PermissionState.GRANTED && status.value == BluetoothStatus.ON) {
                readPairedDevices(this)
            } else {
                emptyList()
            }
        refreshConnections()
    }

    /** Re-reads the live audio-connection status for every paired device. */
    private fun refreshConnections() {
        if (connectPermission.value != PermissionState.GRANTED || status.value != BluetoothStatus.ON) {
            connectionStatus.clear()
            return
        }
        // Profile proxies may not be bound yet; skip recording until at least one is,
        // so an early "unknown" is never logged as a disconnect.
        var anyKnown = false
        pairedDevices.value.forEach { device ->
            val current = connectionReader.statusFor(device.address)
            connectionStatus[device.address] = current

            val known = current.music != ProfileState.UNKNOWN || current.calls != ProfileState.UNKNOWN
            if (!known) return@forEach
            anyKnown = true

            val wasConnected = lastConnected[device.address]
            val isConnected = current.isConnected
            when {
                // First reliable reading this session: set a baseline without logging,
                // because we cannot know when an already-connected device connected.
                wasConnected == null -> lastConnected[device.address] = isConnected
                wasConnected != isConnected -> {
                    lastConnected[device.address] = isConnected
                    recordEvent(device, isConnected)
                }
            }
        }
        if (!anyKnown) return
    }

    private fun recordEvent(device: PairedDevice, connected: Boolean) {
        val entry = LogEntry(
            address = device.address,
            name = device.alias ?: device.name ?: "(no name)",
            connected = connected,
            epochMillis = System.currentTimeMillis(),
        )
        connectionLog.add(entry)
        logEntries.value = connectionLog.entries()
    }

    private fun clearLog() {
        connectionLog.clear()
        logEntries.value = emptyList()
    }

    private fun saveOwner(profile: OwnerProfile) {
        ownerStore.save(profile)
        ownerProfile.value = profile
    }

    private fun clearOwner() {
        ownerStore.clear()
        ownerProfile.value = OwnerProfile()
    }

    /** The audio device the dashboard features: the first paired audio device, else the first. */
    private fun primaryDevice(): PairedDevice? =
        pairedDevices.value.firstOrNull { it.isAudio } ?: pairedDevices.value.firstOrNull()

    private fun shareOwnership() {
        val device = primaryDevice() ?: return
        val connection = connectionStatus[device.address]
        val seen = when {
            connection?.isConnected == true -> "Connected now"
            else -> logEntries.value.firstOrNull { it.address == device.address && it.connected }
                ?.let { formatEventTime(it.epochMillis) } ?: "No record yet"
        }
        share("Dark Web Lock ownership record", ownershipRecord(ownerProfile.value, device, connection, seen))
    }

    private fun setMonitoring(enabled: Boolean) {
        if (!enabled) {
            MonitorService.stop(this)
            monitoring.value = false
            return
        }
        // The connected-device foreground service needs BLUETOOTH_CONNECT on
        // Android 12+; without it the service cannot start. Ask for it first.
        if (connectPermission.value != PermissionState.GRANTED) {
            request(connectPermissions, KEY_ASKED_CONNECT)
            return
        }
        // Android 13+ asks for notification permission so the required notice and
        // the alerts can show. The service starts either way.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        MonitorService.start(this)
        monitoring.value = true
    }

    // Reading the paired list: Android 12+ needs "Nearby devices"; older versions
    // are covered by the install-time BLUETOOTH permission.
    private val connectPermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()

    // Scanning: Android 12+ needs "Nearby devices" (declared as never used for
    // location); Android 11 and older can only scan with location permission.
    private val scanPermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun readPermission(perms: List<String>, askedKey: String): PermissionState = when {
        perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } -> PermissionState.GRANTED
        !prefs().getBoolean(askedKey, false) -> PermissionState.NOT_ASKED
        perms.any { shouldShowRequestPermissionRationale(it) } -> PermissionState.DENIED
        else -> PermissionState.BLOCKED
    }

    private fun request(perms: List<String>, askedKey: String) {
        if (perms.isEmpty()) return
        prefs().edit().putBoolean(askedKey, true).apply()
        requestPermissions.launch(perms.toTypedArray())
    }

    /** Asks Android to query the device again for the services it offers (SDP). */
    @SuppressLint("MissingPermission")
    private fun refreshServices(address: String) {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val sent = runCatching { adapter.getRemoteDevice(address).fetchUuidsWithSdp() }.getOrDefault(false)
        serviceLookups[address] = if (sent) {
            "Asked at ${LocalTime.now().withNano(0)} — waiting for the device to answer…"
        } else {
            "Android could not send the request. Check Bluetooth is on and try again."
        }
    }

    // Turning Bluetooth on from inside the app is deliberately not offered; the
    // user does it in system settings.
    private fun openBluetoothSettings() {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }

    private fun setLocation(enabled: Boolean) {
        if (!enabled) {
            MonitorService.setLocationEnabled(this, false)
            locationEnabled.value = false
            if (MonitorService.isEnabled(this)) MonitorService.start(this) // drop the location type
            return
        }
        if (!DeviceLocation.hasPermission(this)) {
            requestLocation.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            return
        }
        MonitorService.setLocationEnabled(this, true)
        locationEnabled.value = true
        if (MonitorService.isEnabled(this)) MonitorService.start(this)
    }

    /** Opens the device's map app at a saved last-known location. */
    private fun openMap(lat: Double, lon: Double, label: String) {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lon")))
        }
    }

    /** Opens the battery-optimisation list so the user can set this app to Unrestricted. */
    private fun openBatterySettings() {
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (list.resolveActivity(packageManager) != null) {
            startActivity(list)
        } else {
            openAppSettings()
        }
    }

    private fun share(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "Share report"))
    }

    private fun prefs() = getSharedPreferences("dark_web_lock", MODE_PRIVATE)

    private companion object {
        const val KEY_ASKED_CONNECT = "asked_bluetooth_connect"
        const val KEY_ASKED_SCAN = "asked_bluetooth_scan"
    }
}
