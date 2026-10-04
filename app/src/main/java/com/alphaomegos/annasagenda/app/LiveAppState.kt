package com.alphaomegos.annasagenda.app

import java.lang.ref.WeakReference
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*

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

    // A view model is reading the state from disk and will hold it shortly.
    // A widget tick in that window must wait for it: written to disk now, it
    // would be overwritten by the state the view model already read (review,
    // 04.10).
    @Volatile
    private var loading: Boolean = false

    fun loadingStarted() {
        loading = true
    }

    fun loadingEnded() {
        loading = false
    }

    /**
     * The view model, waiting up to [timeoutMs] if one is still loading.
     * Null when there is none to wait for, or it did not arrive in time.
     */
    suspend fun viewModelOnceLoaded(timeoutMs: Long = 3_000L): AppViewModel? {
        var waited = 0L
        while (loading && ref?.get() == null && waited < timeoutMs) {
            kotlinx.coroutines.delay(50L)
            waited += 50L
        }
        return ref?.get()
    }

    fun attach(vm: AppViewModel) {
        ref = WeakReference(vm)
        loading = false
    }

    fun detach(vm: AppViewModel) {
        if (ref?.get() === vm) ref = null
    }

    fun viewModel(): AppViewModel? = ref?.get()
}
