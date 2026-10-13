package com.darkweblock.app

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.darkweblock.app.ui.DarkWebLockTheme
import com.darkweblock.app.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private val status = mutableStateOf(BluetoothStatus.NOT_SUPPORTED)

    // Only used as a "something changed" signal; the state is always re-read
    // from the adapter rather than trusted from the broadcast.
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh()
        }
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
                    onRefresh = ::refresh,
                    onOpenBluetoothSettings = ::openBluetoothSettings,
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
        refresh()
    }

    override fun onStop() {
        unregisterReceiver(stateReceiver)
        super.onStop()
    }

    private fun refresh() {
        status.value = readBluetoothStatus(this)
    }

    // Turning Bluetooth on from inside the app would need the "Nearby devices"
    // permission, so this build sends the user to system settings instead.
    private fun openBluetoothSettings() {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }
}
