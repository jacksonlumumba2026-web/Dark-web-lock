package com.darkweblock.app

import android.content.Context

/**
 * Tracks the free-vs-Pro plan. Free tracks one device's location; Pro unlocks
 * all devices.
 *
 * NOTE: there is no real payment yet. The unlock here is a local TEST unlock so
 * the gating can be exercised before Google Play Billing is wired in (which needs
 * a Play Console account and configured products — see android/docs/MONETIZATION.md).
 */
object PlanStore {

    const val FREE_DEVICE_LIMIT = 1

    private const val PREF = "dark_web_lock"
    private const val KEY_PRO = "plan_pro"
    private const val KEY_FREE_ADDRESS = "free_device_address"

    fun isPro(context: Context): Boolean = prefs(context).getBoolean(KEY_PRO, false)

    fun setPro(context: Context, pro: Boolean) {
        prefs(context).edit().putBoolean(KEY_PRO, pro).apply()
    }

    /** The one device the free plan fully tracks; set by the UI from the sorted list. */
    fun freeDeviceAddress(context: Context): String? = prefs(context).getString(KEY_FREE_ADDRESS, null)

    fun setFreeDeviceAddress(context: Context, address: String?) {
        prefs(context).edit().putString(KEY_FREE_ADDRESS, address).apply()
    }

    /** Whether location may be captured and shown for this device under the current plan. */
    fun locationAllowedFor(context: Context, address: String): Boolean =
        isPro(context) || address == freeDeviceAddress(context)

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
