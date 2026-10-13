package com.darkweblock.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.darkweblock.app.ui.DarkWebLockTheme
import com.darkweblock.app.ui.HomeScreen

/** Whether the app may read paired devices ("Nearby devices" on Android 12+). */
enum class ConnectPermission {
    GRANTED,
    NOT_ASKED,
    DENIED,

    /** Denied in a way Android will no longer show the prompt; only app settings can fix it. */
    BLOCKED,
}

class MainActivity : ComponentActivity() {

    private val status = mutableStateOf(BluetoothStatus.NOT_SUPPORTED)
    private val permission = mutableStateOf(ConnectPermission.NOT_ASKED)
    private val pairedDevices = mutableStateOf<List<PairedDevice>>(emptyList())

    // Only used as a "something changed" signal; the state is always re-read
    // from the adapter rather than trusted from the broadcast.
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh()
        }
    }

    private val requestConnect =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            prefs().edit().putBoolean(KEY_ASKED_CONNECT, true).apply()
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light system-bar icons on a transparent bar: the app is dark even
        // when the phone itself is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        refresh()

        setContent {
            DarkWebLockTheme {
                HomeScreen(
                    status = status.value,
                    permission = permission.value,
                    pairedDevices = pairedDevices.value,
                    onRefresh = ::refresh,
                    onOpenBluetoothSettings = ::openBluetoothSettings,
                    onRequestPermission = ::requestPermission,
                    onOpenAppSettings = ::openAppSettings,
                    onShareReport = ::shareReport,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        // Also picks up changes made in system settings (pairing, unpairing,
        // granting the permission) when the user comes back to the app.
        refresh()
    }

    override fun onStop() {
        unregisterReceiver(stateReceiver)
        super.onStop()
    }

    private fun refresh() {
        status.value = readBluetoothStatus(this)
        permission.value = readPermission()
        pairedDevices.value =
            if (permission.value == ConnectPermission.GRANTED && status.value == BluetoothStatus.ON) {
                readPairedDevices(this)
            } else {
                emptyList()
            }
    }

    private fun readPermission(): ConnectPermission {
        // Android 11 and older: the install-time BLUETOOTH permission covers this.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ConnectPermission.GRANTED
        val perm = Manifest.permission.BLUETOOTH_CONNECT
        return when {
            checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED -> ConnectPermission.GRANTED
            !prefs().getBoolean(KEY_ASKED_CONNECT, false) -> ConnectPermission.NOT_ASKED
            shouldShowRequestPermissionRationale(perm) -> ConnectPermission.DENIED
            else -> ConnectPermission.BLOCKED
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestConnect.launch(Manifest.permission.BLUETOOTH_CONNECT)
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

    private fun shareReport() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Dark Web Lock paired devices report")
            .putExtra(Intent.EXTRA_TEXT, pairedDevicesReport(pairedDevices.value))
        startActivity(Intent.createChooser(send, "Share report"))
    }

    private fun prefs() = getSharedPreferences("dark_web_lock", MODE_PRIVATE)

    private companion object {
        const val KEY_ASKED_CONNECT = "asked_bluetooth_connect"
    }
}
