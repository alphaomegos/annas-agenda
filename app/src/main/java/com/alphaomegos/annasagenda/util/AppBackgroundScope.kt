package com.alphaomegos.annasagenda.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A scope for work that must outlive the screen that started it.
 *
 * The automatic backup used to run in MainActivity's lifecycleScope, which is
 * cancelled the moment the activity is destroyed. onStop is followed by destroy
 * on a rotation, on a theme change, and whenever the system reclaims the
 * activity in the background — so the coroutine could be cancelled before the
 * write ever started, and the snapshot silently did not happen. Nothing told
 * anyone: the next time it worked, and the backup on disk was simply older than
 * the user assumed.
 *
 * Process-wide and never cancelled, because there is nothing above the process
 * that could cancel it meaningfully. A SupervisorJob so one failed job does not
 * take the scope down with it.
 */
val appBackgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
