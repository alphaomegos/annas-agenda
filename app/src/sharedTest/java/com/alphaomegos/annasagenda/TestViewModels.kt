package com.alphaomegos.annasagenda

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.alphaomegos.annasagenda.app.AppViewModel

/**
 * The view models a test makes, and the end of them.
 *
 * A view model built with its bare constructor is never cleared: nothing owns
 * it, so nothing ever cancels its scope. Its autosave and its notification
 * loop outlive the test, and their debounces fire a moment later from a
 * timer thread — while the next test is calling Dispatchers.setMain, or this
 * one Dispatchers.resetMain. That is «Dispatchers.Main is used concurrently»
 * (0270, 17.10), failing whichever test happened to be next rather than the
 * one that left the loop running. A view model left alive also stays
 * attached to LiveAppState, where the next test's widget and alarm code
 * would find it.
 *
 * So every one comes from here, kept in a ViewModelStore the way an activity
 * keeps its own, and [clear] in tearDown — before resetMain — clears them
 * all the way the system would: onCleared, and the scope cancelled.
 */
internal class TestViewModels {

    private val store = ViewModelStore()
    private var made = 0

    fun appViewModel(app: Application): AppViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app) as T
        }
        // A key of its own each time: a test that "revives" the app makes a
        // second one over the same store, and must get a new one, not the first.
        return ViewModelProvider(store, factory)["app-view-model-${made++}", AppViewModel::class.java]
    }

    fun clear() = store.clear()
}
