package com.tripsdoc.timerapp

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "timer_store")

object TimerStore {
    private val KEY_REMAINING = longPreferencesKey("remaining_ms")
    private val KEY_STATE = stringPreferencesKey("state")

    private val KEY_USE_SYSTEM_THEME = booleanPreferencesKey("use_system_theme")
    private val KEY_DARK_THEME_MANUAL = booleanPreferencesKey("dark_theme_manual")

    suspend fun write(context: Context, remainingMs: Long, state: TimerState) {
        context.dataStore.edit {
            it[KEY_REMAINING] = remainingMs
            it[KEY_STATE] = state.name
        }
    }

    suspend fun read(context: Context): Pair<Long, TimerState> {
        val prefs: Preferences = context.dataStore.data.first()
        val rem = prefs[KEY_REMAINING] ?: 30_000L
        val state = prefs[KEY_STATE]?.let { runCatching { TimerState.valueOf(it) }.getOrNull() } ?: TimerState.Idle
        return rem to state
    }

    // THEME WRITE
    suspend fun writeTheme(context: Context, useSystem: Boolean, darkManual: Boolean) {
        context.dataStore.edit {
            it[KEY_USE_SYSTEM_THEME] = useSystem
            it[KEY_DARK_THEME_MANUAL] = darkManual
        }
    }

    // THEME READ (returns pair: useSystem, darkManual)
    suspend fun readTheme(context: Context): Pair<Boolean, Boolean> {
        val prefs: Preferences = context.dataStore.data.first()
        val useSystem = prefs[KEY_USE_SYSTEM_THEME] ?: true   // default: follow device
        val darkManual = prefs[KEY_DARK_THEME_MANUAL] ?: false
        return useSystem to darkManual
    }
}
