package com.example.data

import android.content.Context
import android.content.Context.MODE_PRIVATE

/** Persists app-level security settings (global app lock, failed-attempt counter). */
object SecuritySettingsStore {
    private const val PREFS = "security_prefs"
    private const val KEY_APP_LOCK = "app_lock_enabled"
    private const val KEY_APP_PIN_MARKER = "app_pin_marker"
    private const val KEY_FAILED = "failed_attempts"

    fun isAppLockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_APP_LOCK, false)

    fun setAppLockEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_APP_LOCK, enabled).apply()

    fun getAppPinMarker(context: Context): String =
        context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_APP_PIN_MARKER, "") ?: ""

    fun setAppPinMarker(context: Context, marker: String) =
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_APP_PIN_MARKER, marker).apply()

    fun getFailedAttempts(context: Context): Int =
        context.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_FAILED, 0)

    fun setFailedAttempts(context: Context, count: Int) =
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_FAILED, count).apply()

    fun resetFailedAttempts(context: Context) = setFailedAttempts(context, 0)
}
