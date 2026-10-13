package com.darkweblock.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

/**
 * A foreground service that watches audio connections while the app is closed.
 *
 * Android requires any background Bluetooth work to run as a foreground service
 * with a permanent, low-priority notice. This service only listens for A2DP
 * (music) and HFP (calls) connection changes: it logs each one and raises a
 * "left-behind" alert on disconnect. It never connects to, controls or locks
 * any device.
 */
class MonitorService : Service() {

    private lateinit var log: ConnectionLog
    private val lastConnected = mutableMapOf<String, Boolean>()

    /** When each device last connected, so a quick re-case does not raise an alert. */
    private val connectedSince = mutableMapOf<String, Long>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handle(intent)
    }

    override fun onCreate() {
        super.onCreate()
        log = ConnectionLog(applicationContext)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // If the service cannot enter the foreground (e.g. a missing permission),
        // stop cleanly rather than crash.
        val started = runCatching { startInForeground() }.isSuccess
        if (!started) {
            setEnabled(applicationContext, false)
            stopSelf()
            return START_NOT_STICKY
        }
        setEnabled(applicationContext, true)
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        // If the system restarts the service after killing it, keep monitoring.
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        setEnabled(applicationContext, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handle(intent: Intent) {
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        val device = IntentCompat.getParcelableExtra(
            intent,
            BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java,
        ) ?: return

        val connected = when (state) {
            BluetoothProfile.STATE_CONNECTED -> true
            BluetoothProfile.STATE_DISCONNECTED -> false
            else -> return // ignore the transitional connecting/disconnecting states
        }

        // A2DP and HFP both fire; the log de-dupes, but also skip an unchanged repeat.
        if (lastConnected[device.address] == connected) return
        lastConnected[device.address] = connected

        val now = System.currentTimeMillis()
        val name = deviceName(device)
        log.add(LogEntry(device.address, name, connected, now))

        if (connected) {
            connectedSince[device.address] = now
            return
        }

        // Alert on disconnect only if the device had been connected long enough,
        // so taking the earbuds out and putting them straight back does not alert.
        // If we never saw it connect (already connected when monitoring began),
        // treat it as a real left-behind and alert.
        val since = connectedSince.remove(device.address)
        val connectedLongEnough = since == null || now - since >= ALERT_MIN_CONNECTED_MS
        if (connectedLongEnough) alertLeftBehind(name)
    }

    @SuppressLint("MissingPermission")
    private fun deviceName(device: BluetoothDevice): String =
        runCatching {
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else null) ?: device.name
        }.getOrNull() ?: "(no name)"

    private fun startInForeground() {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setContentTitle("Dark Web Lock is watching your earbuds")
            .setContentText("Tap the app to see status. Android requires this notice while monitoring.")
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentIntent(openAppIntent())
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(ONGOING_ID, notification)
        }
    }

    private fun alertLeftBehind(name: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setContentTitle("$name disconnected")
            .setContentText("Your earbuds just disconnected. If you are moving, you may be leaving them behind.")
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The quiet notice Android requires while watching for disconnects."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Left-behind alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts you when your earbuds disconnect."
            },
        )
    }

    companion object {
        private const val ACTION_STOP = "com.darkweblock.app.STOP_MONITOR"
        private const val CHANNEL_ONGOING = "monitoring"
        private const val CHANNEL_ALERT = "alerts"
        private const val ONGOING_ID = 1001
        private const val ALERT_MIN_CONNECTED_MS = 60_000L
        private const val PREF = "dark_web_lock"
        private const val KEY_ENABLED = "monitoring_enabled"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MonitorService::class.java).setAction(ACTION_STOP))
            setEnabled(context, false)
        }

        /** The user's desired state, so the toggle survives restarts. */
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        private fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }
}
