package com.alphaomegos.annasagenda.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alphaomegos.annasagenda.support.millisUntilTomorrow
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * Today as a screen should know it (05.11, the internals review): it turns at
 * midnight while the screen is open, and is looked at again whenever the app
 * comes back to the front — a wait does not run while the phone sleeps. A
 * screen left open overnight no longer lives in yesterday.
 */
@Composable
fun rememberToday(): LocalDate {
    var today by rememberSaveable { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilTomorrow(LocalDateTime.now(), today))
            today = LocalDate.now()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return today
}
