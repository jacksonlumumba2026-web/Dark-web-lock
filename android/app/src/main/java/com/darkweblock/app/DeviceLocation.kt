package com.darkweblock.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

/** A single location reading. */
data class LocationFix(val lat: Double, val lon: Double, val accuracyM: Float?, val timeMillis: Long)

/**
 * Reads the phone's last-known location from the platform LocationManager.
 *
 * This is deliberately "last known", not a fresh GPS fix: it is instant, uses no
 * extra battery, and matches the feature — where the phone last was when the
 * earbuds disconnected. It can be a few minutes old or null if location has been
 * off. Needs a location permission; returns null without one.
 */
object DeviceLocation {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): LocationFix? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var best: android.location.Location? = null
        for (provider in providers) {
            val location = runCatching {
                if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
            }.getOrNull() ?: continue
            if (best == null || location.time > best.time) best = location
        }
        return best?.let { LocationFix(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null, it.time) }
    }
}
