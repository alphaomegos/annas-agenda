package com.alphaomegos.annasagenda.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.alphaomegos.annasagenda.model.MusicSaveItem
import com.alphaomegos.annasagenda.musicUpstreamFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** How "save all" is going, as the screen shows it: waiting (for Wi-Fi), at work, or none. */
internal data class MusicSaveAllProgress(val waiting: Boolean, val done: Int, val total: Int)

/**
 * "Save all" (16.10, Eduard: "every track kept on the device"): the tracks
 * of a list fetched and kept one after another in the background, by
 * WorkManager — over Wi-Fi only when the settings say so, and on after the
 * app is closed. Stopped by Android after its while, or by a lost network,
 * it starts again later from the first track not yet kept.
 */
internal class MusicSaveAllWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val planFile = musicSavePlanFile(applicationContext)
        val items = planFile.read() ?: return@withContext Result.success()
        val upstream = musicUpstreamFactory(applicationContext, SidecarConnections.of(applicationContext))
        var done = 0
        var failed = 0
        setProgress(workDataOf(KEY_DONE to 0, KEY_TOTAL to items.size))
        for (item in items) {
            if (isStopped) return@withContext Result.retry()
            if (!MusicCache.saveNow(applicationContext, item, upstream)) failed++
            done++
            MusicCache.changed()
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to items.size))
        }
        if (failed > 0 && runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            planFile.forget()
            Result.success()
        }
    }

    companion object {
        private const val WORK = "music-save-all"
        private const val KEY_DONE = "done"
        private const val KEY_TOTAL = "total"
        private const val MAX_ATTEMPTS = 5

        /** [items] fetched in the background, instead of any "save all" before. */
        fun start(context: Context, items: List<MusicSaveItem>, wifiOnly: Boolean) {
            if (items.isEmpty()) return
            musicSavePlanFile(context).write(items)
            val request = OneTimeWorkRequestBuilder<MusicSaveAllWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
            musicSavePlanFile(context).forget()
        }

        /** How it is going; null when there is nothing to save, or it is done. */
        fun progress(context: Context): Flow<MusicSaveAllProgress?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK).map { infos ->
                val info = infos.firstOrNull { !it.state.isFinished } ?: return@map null
                MusicSaveAllProgress(
                    waiting = info.state != WorkInfo.State.RUNNING,
                    done = info.progress.getInt(KEY_DONE, 0),
                    total = info.progress.getInt(KEY_TOTAL, 0),
                )
            }
    }
}

/** What "save all" is to fetch, in the no-backup folder. */
internal fun musicSavePlanFile(context: Context): MusicSavePlanFile =
    MusicSavePlanFile(File(File(context.applicationContext.noBackupFilesDir, "music"), "save-all.json"))
