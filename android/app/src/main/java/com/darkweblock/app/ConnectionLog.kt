package com.darkweblock.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One observed connect or disconnect of a device. */
data class LogEntry(
    val address: String,
    val name: String,
    val connected: Boolean,
    val epochMillis: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
) {
    val label: String get() = if (connected) "Connected" else "Disconnected"
    val hasLocation: Boolean get() = lat != null && lon != null
}

/**
 * A small, persistent log of connection changes seen while the app is open.
 *
 * It records only transitions the app actually witnessed; it does not claim to
 * know what happened while it was closed. Stored in SharedPreferences as JSON,
 * newest first, capped at [MAX_ENTRIES].
 */
class ConnectionLog(context: Context) {

    private val prefs = context.getSharedPreferences("dark_web_lock_log", Context.MODE_PRIVATE)

    fun entries(): List<LogEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                LogEntry(
                    address = o.getString("address"),
                    name = o.getString("name"),
                    connected = o.getBoolean("connected"),
                    epochMillis = o.getLong("time"),
                    lat = if (o.has("lat")) o.getDouble("lat") else null,
                    lon = if (o.has("lon")) o.getDouble("lon") else null,
                    accuracyM = if (o.has("acc")) o.getDouble("acc").toFloat() else null,
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Appends an event, keeping the list newest-first and capped. A repeat of the
     * same device + state within [DEDUPE_MS] is dropped, so the A2DP and HFP
     * profiles firing together, or the app and the background service both seeing
     * one change, never produce two log lines.
     */
    fun add(entry: LogEntry) {
        val current = entries()
        val latest = current.firstOrNull()
        if (latest != null &&
            latest.address == entry.address &&
            latest.connected == entry.connected &&
            entry.epochMillis - latest.epochMillis in 0..DEDUPE_MS
        ) {
            return
        }
        val updated = (listOf(entry) + current).take(MAX_ENTRIES)
        val array = JSONArray()
        updated.forEach { e ->
            val o = JSONObject()
                .put("address", e.address)
                .put("name", e.name)
                .put("connected", e.connected)
                .put("time", e.epochMillis)
            if (e.lat != null && e.lon != null) {
                o.put("lat", e.lat).put("lon", e.lon)
                e.accuracyM?.let { o.put("acc", it.toDouble()) }
            }
            array.put(o)
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    /** Adds coordinates to the most recent matching entry (same device, same time). */
    fun updateLocation(address: String, epochMillis: Long, lat: Double, lon: Double, accuracyM: Float?) {
        val current = entries().toMutableList()
        val index = current.indexOfFirst { it.address == address && it.epochMillis == epochMillis }
        if (index < 0) return
        current[index] = current[index].copy(lat = lat, lon = lon, accuracyM = accuracyM)
        val array = JSONArray()
        current.forEach { e ->
            val o = JSONObject()
                .put("address", e.address)
                .put("name", e.name)
                .put("connected", e.connected)
                .put("time", e.epochMillis)
            if (e.lat != null && e.lon != null) {
                o.put("lat", e.lat).put("lon", e.lon)
                e.accuracyM?.let { o.put("acc", it.toDouble()) }
            }
            array.put(o)
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        const val MAX_ENTRIES = 200
        private const val DEDUPE_MS = 5000L
        private const val KEY = "connection_events"
    }
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Formats an event time as "Today 10:59", "Yesterday 14:03", or "3 Oct 2026 09:12". */
fun formatEventTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val instant = Instant.ofEpochMilli(epochMillis)
    val dateTime = instant.atZone(zone)
    val day = dateTime.toLocalDate()
    val today = LocalDate.now(zone)
    val prefix = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(dateFormat)
    }
    return "$prefix ${dateTime.format(timeFormat)}"
}

/** Plain-text history for sharing. */
fun connectionLogReport(entries: List<LogEntry>): String = buildString {
    appendLine("Dark Web Lock — connection history")
    appendLine("Events seen while the app was open. ${entries.size} recorded.")
    entries.forEach { e ->
        appendLine("${formatEventTime(e.epochMillis)} · ${e.label} · ${e.name} (${e.address})")
        if (e.hasLocation) {
            appendLine("    Last-known location: ${e.lat}, ${e.lon}" + (e.accuracyM?.let { " (±${it.toInt()} m)" } ?: ""))
        }
    }
}
