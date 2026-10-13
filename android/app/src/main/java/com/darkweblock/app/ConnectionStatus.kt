package com.darkweblock.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context

/** Live audio-connection state of one device, as Android's profile services report it. */
data class ConnectionStatus(
    val music: ProfileState,
    val calls: ProfileState,
) {
    /** Connected for at least one audio profile right now. */
    val isConnected: Boolean
        get() = music == ProfileState.CONNECTED || calls == ProfileState.CONNECTED

    val summary: String
        get() = when {
            music == ProfileState.CONNECTED && calls == ProfileState.CONNECTED -> "Connected (music + calls)"
            music == ProfileState.CONNECTED -> "Connected (music)"
            calls == ProfileState.CONNECTED -> "Connected (calls)"
            music == ProfileState.CONNECTING || calls == ProfileState.CONNECTING -> "Connecting…"
            music == ProfileState.DISCONNECTING || calls == ProfileState.DISCONNECTING -> "Disconnecting…"
            music == ProfileState.UNKNOWN && calls == ProfileState.UNKNOWN -> "Status unavailable"
            else -> "Not connected"
        }
}

enum class ProfileState {
    CONNECTED,
    CONNECTING,
    DISCONNECTING,
    DISCONNECTED,

    /** The profile service was not ready, so this reading is unknown rather than disconnected. */
    UNKNOWN,
}

/**
 * Keeps short-lived connections to the A2DP (music) and HFP (calls) profile
 * services and reports, per device address, whether it is connected right now.
 *
 * This is read-only: it never connects, disconnects or changes audio routing.
 * Callers must hold BLUETOOTH_CONNECT on Android 12+.
 */
class ConnectionStatusReader(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    @Volatile private var a2dp: BluetoothProfile? = null
    @Volatile private var headset: BluetoothProfile? = null

    private var onChange: (() -> Unit)? = null

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dp = proxy
                BluetoothProfile.HEADSET -> headset = proxy
            }
            onChange?.invoke()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dp = null
                BluetoothProfile.HEADSET -> headset = null
            }
        }
    }

    /** Binds the profile services. [onReady] is called once each becomes available. */
    fun start(onReady: () -> Unit) {
        onChange = onReady
        val a = adapter ?: return
        runCatching { a.getProfileProxy(context, listener, BluetoothProfile.A2DP) }
        runCatching { a.getProfileProxy(context, listener, BluetoothProfile.HEADSET) }
    }

    fun stop() {
        val a = adapter
        a2dp?.let { runCatching { a?.closeProfileProxy(BluetoothProfile.A2DP, it) } }
        headset?.let { runCatching { a?.closeProfileProxy(BluetoothProfile.HEADSET, it) } }
        a2dp = null
        headset = null
        onChange = null
    }

    @SuppressLint("MissingPermission")
    fun statusFor(address: String): ConnectionStatus {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        return ConnectionStatus(
            music = readState(a2dp, device),
            calls = readState(headset, device),
        )
    }

    @SuppressLint("MissingPermission")
    private fun readState(profile: BluetoothProfile?, device: BluetoothDevice?): ProfileState {
        if (profile == null || device == null) return ProfileState.UNKNOWN
        return when (runCatching { profile.getConnectionState(device) }.getOrNull()) {
            BluetoothProfile.STATE_CONNECTED -> ProfileState.CONNECTED
            BluetoothProfile.STATE_CONNECTING -> ProfileState.CONNECTING
            BluetoothProfile.STATE_DISCONNECTING -> ProfileState.DISCONNECTING
            BluetoothProfile.STATE_DISCONNECTED -> ProfileState.DISCONNECTED
            else -> ProfileState.UNKNOWN
        }
    }
}
