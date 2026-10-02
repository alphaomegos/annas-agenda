package com.alphaomegos.annasagenda

import java.lang.ref.WeakReference

/**
 * The view model holding the state, while there is one.
 *
 * The widget ticks tasks from outside the app. With the app open, its view
 * model holds the state in memory and writes it down every few hundred
 * milliseconds — so a tick written straight to disk would be written over
 * by the next autosave. While a view model is here, the widget goes through
 * it; with none, nothing in this process holds the state, and the widget
 * reads and writes the disk itself.
 *
 * Attached only once the state has loaded and autosave runs, so the widget
 * never acts on the empty default a view model starts with, or on one that
 * found the data unreadable.
 */
internal object LiveAppState {
    @Volatile
    private var ref: WeakReference<AppViewModel>? = null

    fun attach(vm: AppViewModel) {
        ref = WeakReference(vm)
    }

    fun detach(vm: AppViewModel) {
        if (ref?.get() === vm) ref = null
    }

    fun viewModel(): AppViewModel? = ref?.get()
}
