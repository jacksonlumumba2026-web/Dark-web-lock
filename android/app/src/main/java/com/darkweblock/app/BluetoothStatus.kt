package com.darkweblock.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager

/** What the phone's own Bluetooth radio reports. Says nothing about earbuds. */
enum class BluetoothStatus {
    NOT_SUPPORTED,
    OFF,
    TURNING_ON,
    ON,
    TURNING_OFF,
}

/**
 * Reads the adapter state. On Android 12+ this needs no runtime permission; on
 * Android 11 and older it uses the install-time BLUETOOTH permission.
 */
fun readBluetoothStatus(context: Context): BluetoothStatus {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
        return BluetoothStatus.NOT_SUPPORTED
    }
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        ?: return BluetoothStatus.NOT_SUPPORTED

    return when (adapter.state) {
        BluetoothAdapter.STATE_ON -> BluetoothStatus.ON
        BluetoothAdapter.STATE_TURNING_ON -> BluetoothStatus.TURNING_ON
        BluetoothAdapter.STATE_TURNING_OFF -> BluetoothStatus.TURNING_OFF
        else -> BluetoothStatus.OFF
    }
}
