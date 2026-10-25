package com.alphaomegos.annasagenda.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.data.AppStateLoadResult
import com.alphaomegos.annasagenda.data.AppStateStore
import com.alphaomegos.annasagenda.data.StepsTogetherFileDto
import com.alphaomegos.annasagenda.data.sidecarJson
import com.alphaomegos.annasagenda.data.toFileDto
import com.alphaomegos.annasagenda.data.toState
import com.alphaomegos.annasagenda.model.StepDay
import com.alphaomegos.annasagenda.model.StepInvitation
import com.alphaomegos.annasagenda.support.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Steps together on this phone (25.10): what the server last said — sharing,
 * friends and their days, invitations — kept in a file under
 * noBackupFilesDir, which neither Android's backup nor the app's own takes:
 * the friends' days are theirs. One for the process.
 *
 * [sync] sends the user's last thirty-one days up (when sharing is on), then
 * fetches friends and invitations, and tells once of each new invitation.
 * It runs when the app comes to the front, after the steps are read, and
 * every six hours in the background with a network (StepsTogetherWorker).
 * Nothing happens without a sign-in to oursidecar (the backups' one).
 */
internal object StepsTogether {
    private val mutex = Mutex()
    private val client = StepsSharingClient(SidecarBackupUrlConnection)
    private val _state = MutableStateFlow(StepsTogetherState())
    val state: StateFlow<StepsTogetherState> = _state.asStateFlow()
    @Volatile private var loaded = false

    private fun file(context: Context) = File(File(context.applicationContext.noBackupFilesDir, "sidecar"), "steps-together.json")

    private fun loadOnce(context: Context) {
        if (loaded) return
        val read = runCatching {
            file(context).takeIf { it.isFile }?.let { sidecarJson.decodeFromString(StepsTogetherFileDto.serializer(), it.readText()) }
        }.getOrNull()
        _state.value = read?.toState() ?: StepsTogetherState()
        loaded = true
    }

    private fun save(context: Context) {
        runCatching {
            val f = file(context)
            f.parentFile?.mkdirs()
            f.writeText(sidecarJson.encodeToString(StepsTogetherFileDto.serializer(), _state.value.toFileDto()))
        }
    }

    /** What is known, loaded from the file the first time it is asked for. */
    fun of(context: Context): StateFlow<StepsTogetherState> {
        loadOnce(context)
        return state
    }

    private suspend fun credentials(context: Context): Pair<String, String>? =
        SidecarBackupAccounts.of(context).credentials()

    /**
     * One round with the server. [ownDays] are the user's days when the app
     * has them in hand; the background reads them from the saved state.
     */
    suspend fun sync(context: Context, ownDays: List<StepDay>? = null) = mutex.withLock {
        loadOnce(context)
        val (server, token) = credentials(context) ?: run {
            _state.update { it.copy(signedIn = false) }
            return@withLock
        }
        val sharing = when (val r = client.sharing(server, token)) {
            is StepsSharingResult.Ok -> r.value
            is StepsSharingResult.Failed -> {
                _state.update { it.copy(failure = r.failure, signedIn = r.failure != StepsSharingFailure.SIGNED_OUT) }
                return@withLock
            }
        }
        var failure: StepsSharingFailure? = null
        if (sharing.enabled) {
            val days = ownDays ?: savedDays(context)
            val up = client.uploadDays(server, token, stepsToShare(days, LocalDate.now()))
            if (up is StepsSharingResult.Failed) failure = up.failure
        }
        val friends = when (val r = client.friends(server, token)) {
            is StepsSharingResult.Ok -> r.value
            is StepsSharingResult.Failed -> { failure = failure ?: r.failure; _state.value.friends }
        }
        val invitations = when (val r = client.invitations(server, token)) {
            is StepsSharingResult.Ok -> r.value
            is StepsSharingResult.Failed -> { failure = failure ?: r.failure; _state.value.invitations }
        }
        val before = _state.value
        val news = stepInvitationsToNotify(invitations, before.notified)
        news.forEach { notifyInvitation(context, it) }
        _state.value = before.copy(
            sharing = sharing,
            friends = friends,
            invitations = invitations,
            prefs = stepFriendPrefsFor(friends, before.prefs),
            notified = stepInvitationsNotifiedAfter(invitations, before.notified + news.map { it.id }),
            syncedAt = Instant.now().toString(),
            failure = failure,
            signedIn = true,
        )
        save(context)
    }

    private suspend fun savedDays(context: Context): List<StepDay> =
        (AppStateStore(context).load() as? AppStateLoadResult.Loaded)?.state?.stepDays.orEmpty()

    /** A friend's line on the chart, or the user's own ([id] null), shown or hidden. */
    fun setShown(context: Context, id: String?, shown: Boolean) {
        loadOnce(context)
        _state.update { s ->
            if (id == null) s.copy(selfShown = shown)
            else s.copy(prefs = s.prefs + (id to (s.prefs[id] ?: StepFriendPref(0)).copy(shown = shown)))
        }
        save(context)
    }

    /**
     * A request about sharing, then a sync so what the screen shows follows;
     * what the request ended in is returned for the screen to say.
     */
    suspend fun <T> act(context: Context, request: suspend (StepsSharingClient, String, String) -> StepsSharingResult<T>): StepsSharingResult<T> {
        val (server, token) = credentials(context) ?: return StepsSharingResult.Failed(StepsSharingFailure.SIGNED_OUT)
        val result = request(client, server, token)
        sync(context)
        return result
    }

    /** Every six hours with a network, kept as planned so opening the app does not push it back. */
    fun plan(context: Context) {
        val request = PeriodicWorkRequestBuilder<StepsTogetherWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_STEPS_TOGETHER, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    @SuppressLint("MissingPermission") // checked by canPostNotifications
    private fun notifyInvitation(context: Context, invitation: StepInvitation) {
        if (!canPostNotifications(context)) return
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_STEPS_TOGETHER, context.getString(R.string.steps_together_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(context, invitation.id.hashCode(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_STEPS_TOGETHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.steps_together_invited, invitation.name))
            .setContentText(context.getString(R.string.steps_together_invited_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(invitation.id.hashCode(), n)
    }
}

internal const val CHANNEL_STEPS_TOGETHER = "steps_together"
private const val WORK_STEPS_TOGETHER = "steps-together"

/** The background half of [StepsTogether.sync]. */
internal class StepsTogetherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { StepsTogether.sync(applicationContext) }
        return Result.success()
    }
}
