package com.tripsdoc.timerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.PopupProperties

class MainActivity : ComponentActivity() {

    private val vm: TimerViewModel by viewModels() // AndroidViewModel works with default factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val effectiveDark = if (ui.useSystemTheme) systemDark else ui.darkThemeManual

            AppTheme(darkTheme = effectiveDark) {
                MaterialTheme { // keeps your typography/shapes; optional
                    Surface(Modifier.fillMaxSize()) {
                        TimerScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeAndKeepScreenItems(ui: TimerUi, vm: TimerViewModel) {
    DropdownMenuItem(
        text = { Text("Follow device theme") },
        onClick = { vm.setUseSystemTheme(!ui.useSystemTheme) },
        trailingIcon = {
            Switch(
                checked = ui.useSystemTheme,
                onCheckedChange = { vm.setUseSystemTheme(it) }
            )
        }
    )
    if (!ui.useSystemTheme) {
        DropdownMenuItem(
            text = { Text("Dark mode") },
            onClick = { vm.setDarkThemeManual(!ui.darkThemeManual) },
            trailingIcon = {
                Switch(
                    checked = ui.darkThemeManual,
                    onCheckedChange = { vm.setDarkThemeManual(it) }
                )
            }
        )
    }
    DropdownMenuItem(
        text = { Text("Keep screen on") },
        onClick = { vm.setKeepScreenOn(!ui.keepScreenOn) },
        trailingIcon = {
            Switch(
                checked = ui.keepScreenOn,
                onCheckedChange = { vm.setKeepScreenOn(it) }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(vm: TimerViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val menuOpen = remember { mutableStateOf(false) }

    // Keep-screen-on when enabled & running
    DisposableEffect(ui.keepScreenOn && ui.state == TimerState.Running) {
        if (ui.keepScreenOn && ui.state == TimerState.Running) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Haptic when finishing
    LaunchedEffect(ui.justFinished) {
        if (ui.justFinished) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.ackFinishHandled()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Timer") },
                navigationIcon = {
                    // Anchor + menu in the SAME Box
                    Box {
                        IconButton(
                            modifier = Modifier.wrapContentSize(Alignment.TopEnd),
                            onClick = { menuOpen.value = true }
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuOpen.value,
                            onDismissRequest = { menuOpen.value = false },
                            // small Y offset to appear just below the top bar
                            offset = DpOffset(0.dp, 0.dp),
                            properties = PopupProperties(focusable = true)
                        ) {
                            // items with trailing Switches...
                            ThemeAndKeepScreenItems(ui, vm)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            // Inputs
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "Minutes",
                    value = ui.inputMinutes,
                    enabled = ui.state == TimerState.Idle || ui.state == TimerState.Finished,
                    onValueChange = { vm.updateMinutes(it) }
                )
                NumberField(
                    label = "Seconds",
                    value = ui.inputSeconds,
                    enabled = ui.state == TimerState.Idle || ui.state == TimerState.Finished,
                    onValueChange = { vm.updateSeconds(it) }
                )
            }

            // Countdown display
            Text(ui.remainingFormatted, style = MaterialTheme.typography.displaySmall)

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (ui.state) {
                    TimerState.Idle, TimerState.Finished -> {
                        Button(onClick = { focus.clearFocus(); vm.start() }) { Text("Start") }
                    }
                    TimerState.Running -> {
                        Button(onClick = { vm.pause() }) { Text("Pause") }
                    }
                    TimerState.Paused -> {
                        Button(onClick = { vm.resume() }) { Text("Resume") }
                    }
                }
                OutlinedButton(
                    enabled = ui.state != TimerState.Idle,
                    onClick = { vm.reset() }
                ) { Text("Reset") }
            }

            if (ui.errorMessage != null) {
                Text(ui.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    val focus = LocalFocusManager.current
    TextField(
        value = value,
        onValueChange = { str ->
            val filtered = str.filter { it.isDigit() }.take(2)
            onValueChange(filtered)
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.width(140.dp)
    )
}
