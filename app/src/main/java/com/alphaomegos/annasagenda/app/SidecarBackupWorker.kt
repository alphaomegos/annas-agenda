package com.alphaomegos.annasagenda.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.alphaomegos.annasagenda.data.AppStateLoadResult
import com.alphaomegos.annasagenda.data.AppStateStore
import com.alphaomegos.annasagenda.model.SidecarBackupSlot
import com.alphaomegos.annasagenda.support.SidecarBackupFailure
import com.alphaomegos.annasagenda.support.nextIdFor
import com.alphaomegos.annasagenda.support.stateWithIdHighWaterAtLeast
import com.alphaomegos.annasagenda.util.resolveStoredCoverFiles
import com.alphaomegos.annasagenda.util.writeBackupZipFile
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How a backup going up is doing, as the screen shows it: waiting (for the network), the parts sent of all. */
internal data class SidecarBackupProgress(val waiting: Boolean, val sent: Int, val of: Int)

/** How the last backup by hand ended, as the screen says it: done, or why not. */
internal data class SidecarBackupOutcome(val id: java.util.UUID, val done: Boolean, val failure: SidecarBackupFailure?)

/**
 * A backup taken to oursidecar in the background (16.10): by hand when the
 * person asks — on any network, and gone on with after the app is closed —
 * and by itself once a week, on Wi-Fi and charging (Eduard). The archive
 * is the manual export's, made from the state as saved; the rest is
 * SidecarBackupUploader's.
 *
 * Stopped by Android or by the network, it is tried again later and goes
 * on from the parts the server has. What retrying cannot mend — signed
 * out, three by hand and the server asking which goes, encryption not
 * chosen — ends it and says why.
 */
internal class SidecarBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slot = SidecarBackupSlot.entries.firstOrNull { it.wire == inputData.getString(KEY_SLOT) } ?: return Result.failure()
        val account = SidecarBackupAccounts.of(applicationContext)
        if (slot == SidecarBackupSlot.AUTO && !account.status.value.auto) return Result.success()
        val uploader = SidecarBackupUploaders.of(applicationContext)
        // A backup by hand asked for now is of the state now: one left half-way before is given up.
        if (slot == SidecarBackupSlot.MANUAL && runAttemptCount == 0 && inputData.getBoolean(KEY_FRESH, false)) {
            uploader.discard(slot)
        }
        val request = SidecarBackupRequest(slot, inputData.getString(KEY_NOTE), inputData.getString(KEY_REPLACE))
        val result = uploader.run(request, { file -> writeArchive(applicationContext, file) }) { sent, of ->
            setProgress(workDataOf(KEY_SENT to sent, KEY_OF to of))
        }
        return when (result) {
            is SidecarBackupResult.Ok -> Result.success(workDataOf(KEY_BACKUP to result.value.id))
            is SidecarBackupResult.Failed -> when (result.failure) {
                SidecarBackupFailure.NO_NETWORK,
                SidecarBackupFailure.SERVER,
                SidecarBackupFailure.TOO_MANY_ATTEMPTS,
                SidecarBackupFailure.PARTS_MISSING,
                SidecarBackupFailure.UNEXPECTED,
                -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure(workDataOf(KEY_FAILURE to result.failure.name))
                else -> Result.failure(workDataOf(KEY_FAILURE to result.failure.name))
            }
        }
    }

    companion object {
        private const val WORK_MANUAL = "sidecar-backup-manual"
        private const val WORK_AUTO = "sidecar-backup-auto"
        private const val KEY_SLOT = "slot"
        private const val KEY_NOTE = "note"
        private const val KEY_REPLACE = "replace"
        private const val KEY_FRESH = "fresh"
        private const val KEY_SENT = "sent"
        private const val KEY_OF = "of"
        private const val KEY_BACKUP = "backup"
        private const val KEY_FAILURE = "failure"
        private const val MAX_ATTEMPTS = 8

        /** The state as saved, written as the manual export's archive into [file]; false when there is none to write. */
        private suspend fun writeArchive(context: Context, file: File): Boolean {
            val store = AppStateStore(context.applicationContext)
            val loaded = store.load() as? AppStateLoadResult.Loaded ?: return false
            val state = stateWithIdHighWaterAtLeast(loaded.state, nextIdFor(loaded.state))
            return writeBackupZipFile(file, store.encodeToJson(state), resolveStoredCoverFiles(context.applicationContext, state))
        }

        /**
         * A backup by hand now, with [note], in place of [replace] when the
         * server asked which goes. The work's id, to know its end by.
         */
        fun startManual(context: Context, note: String?, replace: String?): java.util.UUID {
            val request = OneTimeWorkRequestBuilder<SidecarBackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(
                    workDataOf(
                        KEY_SLOT to SidecarBackupSlot.MANUAL.wire,
                        KEY_NOTE to note,
                        KEY_REPLACE to replace,
                        KEY_FRESH to true,
                    ),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_MANUAL, ExistingWorkPolicy.REPLACE, request)
            return request.id
        }

        fun cancelManual(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_MANUAL)
        }

        /**
         * The weekly backup kept in step with [on] (16.10): once a week, on
         * Wi-Fi, charging, the battery not low. Kept as it was when already
         * planned, so turning the screen on and off does not push it back.
         */
        fun planAuto(context: Context, on: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!on) {
                work.cancelUniqueWork(WORK_AUTO)
                return
            }
            val request = PeriodicWorkRequestBuilder<SidecarBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setInputData(workDataOf(KEY_SLOT to SidecarBackupSlot.AUTO.wire))
                .build()
            work.enqueueUniquePeriodicWork(WORK_AUTO, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** How the backup by hand going up is doing; null when none is. */
        fun progress(context: Context): Flow<SidecarBackupProgress?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_MANUAL).map { infos ->
                val info = infos.firstOrNull { !it.state.isFinished } ?: return@map null
                SidecarBackupProgress(
                    waiting = info.state != WorkInfo.State.RUNNING,
                    sent = info.progress.getInt(KEY_SENT, 0),
                    of = info.progress.getInt(KEY_OF, 0),
                )
            }

        /** How the last backup by hand ended; null while it goes, or when there was none. */
        fun outcome(context: Context): Flow<SidecarBackupOutcome?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_MANUAL).map { infos ->
                val info = infos.firstOrNull() ?: return@map null
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> SidecarBackupOutcome(info.id, true, null)
                    WorkInfo.State.FAILED -> SidecarBackupOutcome(
                        info.id,
                        false,
                        SidecarBackupFailure.entries.firstOrNull { it.name == info.outputData.getString(KEY_FAILURE) }
                            ?: SidecarBackupFailure.UNEXPECTED,
                    )
                    else -> null
                }
            }
    }
}

/** The one uploader of the process (16.10), with its folder in the no-backup one. */
internal object SidecarBackupUploaders {
    @Volatile
    private var instance: SidecarBackupUploader? = null

    fun of(context: Context): SidecarBackupUploader = instance ?: synchronized(this) {
        instance ?: SidecarBackupUploader(
            dir = File(File(context.applicationContext.noBackupFilesDir, "sidecar"), "upload"),
            account = SidecarBackupAccounts.of(context),
            client = SidecarBackupsClient(SidecarBackupUrlConnection),
            appVersion = appVersionName(context),
        ).also { instance = it }
    }
}

/** The app's own version, as the system knows it: "22.0". */
internal fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
