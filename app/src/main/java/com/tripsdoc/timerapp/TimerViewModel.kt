package com.tripsdoc.timerapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

enum class TimerState { Idle, Running, Paused, Finished }

data class TimerUi(
    val inputMinutes: String = "00",
    val inputSeconds: String = "30",
    val state: TimerState = TimerState.Idle,
    val remainingMillis: Long = 30_000L,
    val errorMessage: String? = null,
    val keepScreenOn: Boolean = true,
    val justFinished: Boolean = false,
    val useSystemTheme: Boolean = true,
    val darkThemeManual: Boolean = false
) {
    val remainingFormatted: String
        get() = formatMillis(remainingMillis)
}

private fun clampSeconds(sec: Int) = sec.coerceIn(0, 59)
private fun toMillis(mins: Int, secs: Int): Long = (mins * 60L + secs).coerceAtLeast(0L) * 1000L
internal fun formatMillis(ms: Long): String {
    val total = max(0L, ms) / 1000L
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

class TimerViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(TimerUi())
    val ui: StateFlow<TimerUi> = _ui

    private var ticker: Job? = null
    private var pausedRemaining: Long = _ui.value.remainingMillis

    init {
        // Load persisted theme on startup
        viewModelScope.launch(Dispatchers.IO) {
            val (useSystem, darkManual) = TimerStore.readTheme(getApplication())
            _ui.update { it.copy(useSystemTheme = useSystem, darkThemeManual = darkManual) }
        }
    }

    fun setUseSystemTheme(enabled: Boolean) {
        _ui.update { it.copy(useSystemTheme = enabled) }
        persistTheme()
    }

    fun setDarkThemeManual(dark: Boolean) {
        _ui.update { it.copy(darkThemeManual = dark) }
        persistTheme()
    }

    private fun persistTheme() {
        val ctx = getApplication<Application>()
        val u = _ui.value
        viewModelScope.launch(Dispatchers.IO) {
            TimerStore.writeTheme(ctx, u.useSystemTheme, u.darkThemeManual)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _ui.update { it.copy(keepScreenOn = enabled) }
    }

    fun updateMinutes(s: String) {
        _ui.update { it.copy(inputMinutes = s.padStart(1, '0')) }
        recomputeRemainingIfIdleOrFinished()
    }

    fun updateSeconds(s: String) {
        _ui.update { it.copy(inputSeconds = s.padStart(1, '0')) }
        recomputeRemainingIfIdleOrFinished()
    }

    private fun recomputeRemainingIfIdleOrFinished() {
        val u = _ui.value
        if (u.state == TimerState.Idle || u.state == TimerState.Finished) {
            val mins = u.inputMinutes.toIntOrNull() ?: 0
            val secs = clampSeconds(u.inputSeconds.toIntOrNull() ?: 0)
            _ui.update {
                it.copy(
                    inputSeconds = "%02d".format(secs),
                    remainingMillis = toMillis(mins, secs),
                    errorMessage = null
                )
            }
            publish()
        }
    }

    fun start() {
        val mins = _ui.value.inputMinutes.toIntOrNull() ?: 0
        val secs = clampSeconds(_ui.value.inputSeconds.toIntOrNull() ?: 0)
        val total = toMillis(mins, secs)
        if (total <= 0L) {
            _ui.update { it.copy(errorMessage = "Set a time greater than 00:00") }
            return
        }
        _ui.update { it.copy(justFinished = false) }
        beginCountdown(total)
    }

    fun pause() {
        if (_ui.value.state != TimerState.Running) return
        ticker?.cancel()
        pausedRemaining = _ui.value.remainingMillis
        _ui.update { it.copy(state = TimerState.Paused) }
        publish()
    }

    fun resume() {
        if (_ui.value.state != TimerState.Paused) return
        beginCountdown(pausedRemaining)
    }

    fun reset() {
        ticker?.cancel()
        _ui.update {
            it.copy(
                state = TimerState.Idle,
                remainingMillis = toMillis(
                    it.inputMinutes.toIntOrNull() ?: 0,
                    clampSeconds(it.inputSeconds.toIntOrNull() ?: 0)
                ),
                errorMessage = null,
                justFinished = false
            )
        }
        publish()
    }

    fun ackFinishHandled() {
        _ui.update { it.copy(justFinished = false) }
    }

    private fun beginCountdown(startFrom: Long) {
        ticker?.cancel()
        _ui.update {
            it.copy(
                state = TimerState.Running,
                remainingMillis = startFrom,
                errorMessage = null,
                justFinished = false
            )
        }
        publish()
        ticker = viewModelScope.launch {
            var t = startFrom
            while (t > 0 && _ui.value.state == TimerState.Running) {
                delay(1000L)
                t -= 1000L
                _ui.update { it.copy(remainingMillis = t) }
                publish()
            }
            if (_ui.value.state == TimerState.Running) {
                _ui.update { it.copy(state = TimerState.Finished, remainingMillis = 0L, justFinished = true) }
                publish()
            }
        }
    }

    private fun publish() {
        val ctx = getApplication<Application>()
        val u = _ui.value
        viewModelScope.launch(Dispatchers.IO) {
            TimerStore.write(ctx, u.remainingMillis, u.state)
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }
}
