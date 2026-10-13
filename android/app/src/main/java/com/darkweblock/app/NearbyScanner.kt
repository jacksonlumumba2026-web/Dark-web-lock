package com.darkweblock.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

/**
 * A device reported by Android's device search (the one Samsung settings uses).
 * Android mixes some BLE devices into these results, so [type] says which it is.
 */
data class ClassicResult(
    val address: String,
    val name: String?,
    val type: String,
    val rssi: Int?,
    val majorClass: String,
    val deviceClass: String,
    val isAudio: Boolean,
    val isPaired: Boolean,
)

/** A BLE advertisement — the only kind of signal a website using Web Bluetooth can see. */
data class BleResult(
    val address: String,
    val advertisedName: String?,
    val systemName: String?,
    val rssi: Int,
    val connectable: Boolean,
    val services: List<Service>,
    val serviceData: List<ServiceDataEntry>,
    val manufacturer: List<ManufacturerData>,
    val isPaired: Boolean,
    val timesSeen: Int,
)

/** Bytes a BLE advert attaches to one service; the first byte often says what kind of frame it is. */
data class ServiceDataEntry(val service: Service, val hex: String)

data class ManufacturerData(val companyId: Int, val hex: String) {
    val company: String get() = companyName(companyId)
}

/**
 * Runs a Classic inquiry and a BLE scan side by side for [SCAN_SECONDS].
 * The caller must hold BLUETOOTH_SCAN and BLUETOOTH_CONNECT on Android 12+
 * (location on 11 and older). Only listens; never connects to anything.
 */
class NearbyScanner(private val context: Context) {

    var scanning by mutableStateOf(false)
        private set
    var secondsLeft by mutableIntStateOf(0)
        private set
    var hasScanned by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Addresses seen in the "earbuds off" scan; null until the user saves one. */
    var baseline by mutableStateOf<Set<String>?>(null)
        private set

    /** True while the results on screen are the very scan that was saved as the baseline. */
    var showingBaselineScan by mutableStateOf(false)
        private set

    val classic = mutableStateMapOf<String, ClassicResult>()
    val ble = mutableStateMapOf<String, BleResult>()

    private val handler = Handler(Looper.getMainLooper())
    private var bonded: Set<String> = emptySet()
    private var receiverRegistered = false
    private var bleRunning = false

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isNew(address: String) = baseline?.let { address !in it } ?: false

    fun saveBaseline() {
        baseline = (classic.keys + ble.keys).toSet()
        showingBaselineScan = true
    }

    fun clearBaseline() {
        baseline = null
        showingBaselineScan = false
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val a = adapter
        if (a == null || !a.isEnabled) {
            error = "Turn Bluetooth on first."
            return
        }
        stop()
        classic.clear()
        ble.clear()
        error = null
        bonded = runCatching { a.bondedDevices.map { it.address }.toSet() }.getOrDefault(emptySet())

        val problems = mutableListOf<String>()

        ContextCompat.registerReceiver(
            context,
            classicReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiverRegistered = true
        val classicStarted = runCatching {
            if (a.isDiscovering) a.cancelDiscovery()
            a.startDiscovery()
        }.getOrDefault(false)
        if (!classicStarted) problems += "Classic scan could not start."

        val scanner = a.bluetoothLeScanner
        if (scanner == null) {
            problems += "BLE scan is not available right now."
        } else {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            runCatching { scanner.startScan(null, settings, bleCallback) }
                .onSuccess { bleRunning = true }
                .onFailure { problems += "BLE scan could not start." }
        }

        if (problems.isNotEmpty()) error = problems.joinToString(" ")
        hasScanned = true
        showingBaselineScan = false
        scanning = true
        secondsLeft = SCAN_SECONDS
        handler.postDelayed(tick, 1000)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        handler.removeCallbacks(tick)
        val a = adapter
        if (bleRunning) {
            // Throws if Bluetooth was switched off mid-scan; the scan is gone either way.
            runCatching { a?.bluetoothLeScanner?.stopScan(bleCallback) }
            bleRunning = false
        }
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(classicReceiver) }
            receiverRegistered = false
        }
        runCatching { if (a?.isDiscovering == true) a.cancelDiscovery() }
        scanning = false
        secondsLeft = 0
    }

    private val tick = object : Runnable {
        override fun run() {
            secondsLeft -= 1
            if (secondsLeft <= 0) stop() else handler.postDelayed(this, 1000)
        }
    }

    private val classicReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(
                intent,
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java,
            ) ?: return
            val previous = classic[device.address]
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                .takeIf { it != Short.MIN_VALUE }?.toInt()
            val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                ?: runCatching { device.name }.getOrNull()
            val btClass: BluetoothClass? = IntentCompat.getParcelableExtra(
                intent,
                BluetoothDevice.EXTRA_CLASS,
                BluetoothClass::class.java,
            ) ?: runCatching { device.bluetoothClass }.getOrNull()

            classic[device.address] = ClassicResult(
                address = device.address,
                name = name ?: previous?.name,
                type = runCatching { deviceTypeLabel(device.type) }.getOrDefault("Unknown"),
                rssi = rssi ?: previous?.rssi,
                majorClass = btClass?.let { majorClassLabel(it.majorDeviceClass) } ?: previous?.majorClass ?: "Not reported",
                deviceClass = btClass?.let { deviceClassLabel(it.deviceClass) } ?: previous?.deviceClass ?: "Not reported",
                isAudio = btClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO || previous?.isAudio == true,
                isPaired = device.address in bonded,
            )
        }
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = record(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::record)

        override fun onScanFailed(errorCode: Int) {
            bleRunning = false
            error = when (errorCode) {
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED,
                ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY,
                ->
                    "Android paused BLE scanning because of too many scans in a row. Wait 30 seconds and try again."
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "This phone does not support this kind of BLE scan."
                else -> "BLE scan failed (Android error $errorCode)."
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun record(result: ScanResult) {
        val address = result.device.address
        val previous = ble[address]
        val scanRecord = result.scanRecord
        val manufacturer = scanRecord?.manufacturerSpecificData?.let { data ->
            (0 until data.size()).map { i -> ManufacturerData(data.keyAt(i), data.valueAt(i).toHex()) }
        }.orEmpty()

        ble[address] = BleResult(
            address = address,
            advertisedName = scanRecord?.deviceName ?: previous?.advertisedName,
            systemName = runCatching { result.device.name }.getOrNull() ?: previous?.systemName,
            rssi = result.rssi,
            connectable = result.isConnectable,
            // Adverts and scan responses can carry different fields, so keep everything seen.
            services = merge(previous?.services, scanRecord?.serviceUuids?.map { serviceFor(it.uuid.toString()) }),
            serviceData = (
                scanRecord?.serviceData?.map { (uuid, bytes) -> ServiceDataEntry(serviceFor(uuid.uuid.toString()), bytes.toHex()) }
                    .orEmpty() + previous?.serviceData.orEmpty()
                ).distinctBy { it.service.uuid },
            manufacturer = (previous?.manufacturer.orEmpty() + manufacturer).distinct(),
            isPaired = address in bonded,
            timesSeen = (previous?.timesSeen ?: 0) + 1,
        )
    }

    private fun merge(a: List<Service>?, b: List<Service>?) = (a.orEmpty() + b.orEmpty()).distinctBy { it.uuid }

    companion object {
        const val SCAN_SECONDS = 15
    }
}

private fun ByteArray.toHex(): String {
    val shown = take(32).joinToString(" ") { "%02X".format(it) }
    return if (size > 32) "$shown …" else shown
}

// Bluetooth SIG company identifiers for the makers most likely to be nearby.
private val knownCompanies = mapOf(0x004C to "Apple", 0x0006 to "Microsoft", 0x0075 to "Samsung", 0x00E0 to "Google")

private fun companyName(id: Int) = "${knownCompanies[id] ?: "Company"} 0x%04X".format(id)

/** Plain-text scan results the user can share or paste when asking for help. */
fun nearbyScanReport(scanner: NearbyScanner): String = buildString {
    appendLine("Dark Web Lock — nearby scan report")
    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Listening only; nothing was connected to.")
    when {
        scanner.baseline == null -> appendLine("No baseline saved.")
        scanner.showingBaselineScan -> appendLine("THIS IS THE BASELINE SCAN (earbuds off), ${scanner.baseline?.size} devices.")
        else -> appendLine("Compared with the baseline (earbuds off, ${scanner.baseline?.size} devices). NEW = not in baseline.")
    }

    appendLine()
    appendLine("DEVICE SEARCH (${scanner.classic.size}) — Android's search; includes some BLE devices")
    scanner.classic.values.sortedByDescending { it.rssi ?: Int.MIN_VALUE }.forEach { d ->
        val tags = listOfNotNull("PAIRED".takeIf { d.isPaired }, "NEW".takeIf { scanner.isNew(d.address) })
        appendLine("• ${d.name ?: "(no name)"} ${tags.joinToString(" ")}".trimEnd())
        appendLine("  ${d.address} · ${d.rssi?.let { "$it dBm" } ?: "signal ?"} · ${d.type} · ${d.majorClass} / ${d.deviceClass}")
    }

    appendLine()
    appendLine("BLE SCAN (${scanner.ble.size})")
    scanner.ble.values.sortedByDescending { it.rssi }.forEach { d ->
        val tags = listOfNotNull("PAIRED".takeIf { d.isPaired }, "NEW".takeIf { scanner.isNew(d.address) })
        appendLine("• ${d.advertisedName ?: d.systemName ?: "(no name)"} ${tags.joinToString(" ")}".trimEnd())
        appendLine("  ${d.address} · ${d.rssi} dBm · ${if (d.connectable) "connectable" else "not connectable"} · seen ${d.timesSeen}x")
        d.services.forEach { appendLine("  Service: ${it.label} [${it.uuid}]") }
        d.serviceData.forEach { appendLine("  Service data: ${it.service.label} [${it.service.uuid}]: ${it.hex}") }
        d.manufacturer.forEach { appendLine("  Maker data: ${it.company}: ${it.hex}") }
    }
}
