package com.darkweblock.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build

/**
 * A device saved in the phone's paired ("bonded") list, exactly as Android
 * reports it. Being paired says nothing about whether it is connected now.
 */
data class PairedDevice(
    val name: String?,
    val alias: String?,
    val address: String,
    val type: String,
    val majorClass: String,
    val deviceClass: String,
    val rawClass: Int?,
    val isAudio: Boolean,
    /** Services Android cached from the device's last service lookup; null if none cached. */
    val services: List<Service>?,
)

data class Service(val uuid: String, val label: String, val isVendor: Boolean)

/**
 * Reads the bonded list. The caller must already hold BLUETOOTH_CONNECT on
 * Android 12+ (or BLUETOOTH on 11 and older). Returns an empty list when
 * Bluetooth is off, because Android does not expose the list then.
 */
@SuppressLint("MissingPermission")
fun readPairedDevices(context: Context): List<PairedDevice> {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    val bonded = adapter.bondedDevices ?: return emptyList()

    return bonded.map { it.toPairedDevice() }
        .sortedWith(compareByDescending<PairedDevice> { it.isAudio }.thenBy { (it.alias ?: it.name ?: "").lowercase() })
}

@SuppressLint("MissingPermission")
private fun BluetoothDevice.toPairedDevice(): PairedDevice {
    val btClass: BluetoothClass? = bluetoothClass
    return PairedDevice(
        name = name,
        alias = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) alias else null,
        address = address,
        type = deviceTypeLabel(type),
        majorClass = btClass?.let { majorClassLabel(it.majorDeviceClass) } ?: "Not reported",
        deviceClass = btClass?.let { deviceClassLabel(it.deviceClass) } ?: "Not reported",
        // BluetoothClass.hashCode() is the raw class-of-device value.
        rawClass = btClass?.hashCode(),
        isAudio = btClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO,
        services = uuids?.map { serviceFor(it.uuid.toString()) }?.distinctBy { it.uuid },
    )
}

internal fun deviceTypeLabel(type: Int) = when (type) {
    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic (BR/EDR)"
    BluetoothDevice.DEVICE_TYPE_LE -> "Low Energy (BLE) only"
    BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual mode (Classic + BLE)"
    else -> "Unknown"
}

internal fun majorClassLabel(major: Int) = when (major) {
    BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio / video"
    BluetoothClass.Device.Major.PHONE -> "Phone"
    BluetoothClass.Device.Major.COMPUTER -> "Computer"
    BluetoothClass.Device.Major.WEARABLE -> "Wearable"
    BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral (keyboard, mouse…)"
    BluetoothClass.Device.Major.HEALTH -> "Health"
    BluetoothClass.Device.Major.IMAGING -> "Imaging"
    BluetoothClass.Device.Major.NETWORKING -> "Networking"
    BluetoothClass.Device.Major.TOY -> "Toy"
    BluetoothClass.Device.Major.MISC -> "Miscellaneous"
    else -> "Uncategorized"
}

internal fun deviceClassLabel(cls: Int) = when (cls) {
    BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Headphones"
    BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "Wearable headset"
    BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "Hands-free"
    BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Loudspeaker"
    BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> "Portable audio"
    BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> "Car audio"
    BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> "Hi-fi audio"
    BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED -> "Audio (uncategorized)"
    BluetoothClass.Device.PHONE_SMART -> "Smartphone"
    BluetoothClass.Device.PHONE_CELLULAR -> "Cellphone"
    BluetoothClass.Device.COMPUTER_LAPTOP -> "Laptop"
    BluetoothClass.Device.COMPUTER_DESKTOP -> "Desktop"
    BluetoothClass.Device.WEARABLE_WRIST_WATCH -> "Watch"
    else -> "Other (0x%04X)".format(cls)
}

private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

// Standard Bluetooth SIG service numbers. Anything outside the SIG base UUID is
// a manufacturer's own service — a lead to investigate, not proof of a feature.
private val knownServices = mapOf(
    "1101" to "Serial Port (SPP)",
    "1108" to "Headset (HSP)",
    "1112" to "Headset audio gateway",
    "110a" to "Audio source",
    "110b" to "Audio sink (A2DP — music)",
    "110c" to "Remote control target (AVRCP)",
    "110d" to "Advanced audio (A2DP)",
    "110e" to "Remote control (AVRCP)",
    "110f" to "Remote control controller (AVRCP)",
    "111e" to "Hands-free (HFP — calls)",
    "111f" to "Hands-free audio gateway",
    "1200" to "PnP information (maker/product IDs)",
    "1800" to "Generic Access (BLE)",
    "1801" to "Generic Attribute (BLE)",
    "180a" to "Device Information (BLE)",
    "180f" to "Battery (BLE)",
    "1844" to "Volume Control (LE Audio)",
    "1846" to "Coordinated Set (LE Audio)",
    "184e" to "Audio Stream Control (LE Audio)",
    "1850" to "Published Audio Capabilities (LE Audio)",
    "fe2c" to "Google Fast Pair",
    "feaa" to "Eddystone (Google beacon format)",
)

internal fun serviceFor(uuid: String): Service {
    val lower = uuid.lowercase()
    if (lower.startsWith("0000") && lower.endsWith(BASE_UUID_SUFFIX)) {
        val short = lower.substring(4, 8)
        val label = knownServices[short] ?: "Standard service 0x${short.uppercase()}"
        return Service(lower, label, isVendor = false)
    }
    return Service(lower, "Manufacturer-specific service", isVendor = true)
}

/** Plain-text report the user can share or paste when asking for help. */
fun pairedDevicesReport(devices: List<PairedDevice>): String = buildString {
    appendLine("Dark Web Lock — paired devices report")
    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Paired = saved on this phone. Connection status was NOT checked.")
    devices.forEach { d ->
        appendLine()
        appendLine("• ${d.alias ?: d.name ?: "(no name)"}")
        appendLine("  Name reported by device: ${d.name ?: "(none)"}")
        appendLine("  Address: ${d.address}")
        appendLine("  Type: ${d.type}")
        appendLine("  Class: ${d.majorClass} / ${d.deviceClass}" + (d.rawClass?.let { " (0x%06X)".format(it) } ?: ""))
        if (d.services == null) {
            appendLine("  Services: none cached")
        } else {
            d.services.forEach { appendLine("  Service: ${it.label} [${it.uuid}]") }
        }
    }
}
