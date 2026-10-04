package com.alphaomegos.annasagenda

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/**
 * Deletes the file the app keeps its state in.
 *
 * This used to be nine private copies, one per test class, each spelling the
 * path out as a literal. Now there is one, and it asks for the path the same
 * way [AppStateStore] does — so if the store is ever renamed, this follows
 * instead of quietly deleting a file nobody writes any more.
 *
 * **What this does not do**, and it is worth saying every time it is called:
 * it does not clear the store. `appStateDataStore()` is one instance per
 * process and keeps what it read in memory, so under a test runner — where the
 * process is every test at once — the next load can answer from memory with
 * the previous test's data. Tests that need an empty store build their own
 * over their own file (see [AppStateStoreContractTest]). This helper is for
 * what it was always used for: not leaving a file behind on the device.
 */
internal fun clearAppStateStoreFile(context: Context) {
    val file = context.preferencesDataStoreFile(APP_STATE_STORE_NAME)
    if (file.exists()) {
        file.delete()
    }
}
