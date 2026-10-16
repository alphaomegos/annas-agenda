package com.alphaomegos.annasagenda.screens.music

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.app.MusicLibraries
import com.alphaomegos.annasagenda.app.MusicCache
import com.alphaomegos.annasagenda.app.MusicSaveAllWorker
import com.alphaomegos.annasagenda.support.musicCacheKey
import com.alphaomegos.annasagenda.support.musicDownloadPlan
import com.alphaomegos.annasagenda.support.musicSaveItems
import com.alphaomegos.annasagenda.app.SidecarAvatarStores
import com.alphaomegos.annasagenda.musicUpstreamFactory
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import com.alphaomegos.annasagenda.app.SidecarConnections
import com.alphaomegos.annasagenda.components.SidecarAccountDialog
import com.alphaomegos.annasagenda.components.sidecarFailureText
import com.alphaomegos.annasagenda.model.MetroStyle
import com.alphaomegos.annasagenda.model.MusicPrefs
import com.alphaomegos.annasagenda.model.MusicSort
import com.alphaomegos.annasagenda.model.MusicTab
import com.alphaomegos.annasagenda.model.SidecarTrack
import com.alphaomegos.annasagenda.screens.metro.LocalMetroStyle
import com.alphaomegos.annasagenda.screens.metro.LocalMetroStyleDetails
import com.alphaomegos.annasagenda.screens.metro.MetroDialog
import com.alphaomegos.annasagenda.screens.metro.MetroKey
import com.alphaomegos.annasagenda.screens.metro.MetroKeyBar
import com.alphaomegos.annasagenda.screens.metro.MetroMapTick
import com.alphaomegos.annasagenda.screens.metro.MetroRadio
import com.alphaomegos.annasagenda.screens.metro.MetroStyleDialog
import com.alphaomegos.annasagenda.screens.metro.MetroTextButton
import com.alphaomegos.annasagenda.screens.metro.MetroThemed
import com.alphaomegos.annasagenda.screens.metro.MetroTopBar
import com.alphaomegos.annasagenda.screens.metro.metroFrame
import com.alphaomegos.annasagenda.support.SidecarSignInState
import com.alphaomegos.annasagenda.support.musicArtistShown
import com.alphaomegos.annasagenda.support.musicArtists
import com.alphaomegos.annasagenda.support.musicMemberName
import com.alphaomegos.annasagenda.support.musicQueue
import com.alphaomegos.annasagenda.support.musicResumed
import com.alphaomegos.annasagenda.app.musicPlayerMemoryFile
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.alphaomegos.annasagenda.support.musicSorted
import com.alphaomegos.annasagenda.support.musicTabKey
import com.alphaomegos.annasagenda.support.musicTabShown
import com.alphaomegos.annasagenda.support.musicTabs
import com.alphaomegos.annasagenda.support.musicTracksOf
import com.alphaomegos.annasagenda.support.parseMusicTab
import com.alphaomegos.annasagenda.util.appLocale
import kotlinx.coroutines.launch

/**
 * The music of oursidecar (16.10, Eduard): its lists as the web shows them —
 * all songs, by artist, each person's — in the order chosen, in the
 * section's own style. Opens only signed in; otherwise it says so and
 * offers the sign-in.
 */
@Composable
fun MusicScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs = state.musicPrefs
    MetroThemed(prefs.style, prefs.styleDetails) {
        MusicScreenContent(prefs = prefs, onPrefs = { edit -> vm.setMusicPrefs(edit) }, onBack = onBack)
    }
}

@Composable
private fun MusicScreenContent(prefs: MusicPrefs, onPrefs: ((MusicPrefs) -> MusicPrefs) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val locale = appLocale()
    val scope = rememberCoroutineScope()
    val connection = remember(context) { SidecarConnections.of(context) }
    val library = remember(context) { MusicLibraries.of(context) }
    val status by connection.status.collectAsStateWithLifecycle()
    val lib by library.state.collectAsStateWithLifecycle()
    val signIn = status.signIn(System.currentTimeMillis())
    val space = status.space

    var accountOpen by rememberSaveable { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    var styleOpen by rememberSaveable { mutableStateOf(false) }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var artistOpen by rememberSaveable { mutableStateOf(false) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val controller = rememberMusicController()
    val now = rememberMusicNowPlaying(controller)
    val unknownArtist = stringResource(R.string.music_unknown_artist)
    val memoryFile = remember(context) { musicPlayerMemoryFile(context) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var clearAsked by rememberSaveable { mutableStateOf(false) }
    var saveAllAsked by rememberSaveable { mutableStateOf(false) }

    // Signed out (or another person in): nothing of the last one's music is kept in sight.
    LaunchedEffect(status.account?.userId) {
        if (status.account == null) library.forget()
    }
    // The list asked for on entering, and again for another sidecar or a fresh sign-in.
    LaunchedEffect(space?.id, signIn == SidecarSignInState.SIGNED_IN) {
        if (signIn == SidecarSignInState.SIGNED_IN && space != null) library.refresh(space.id)
    }
    val refresh: () -> Unit = { space?.let { s -> scope.launch { library.refresh(s.id) } } }

    val tabs = musicTabs(lib.members, prefs.artistTab)
    val tab = musicTabShown(parseMusicTab(prefs.tab), tabs)
    val artists = musicArtists(lib.tracks, locale)
    val artist = musicArtistShown(prefs.artist, artists)
    val shown = musicSorted(musicTracksOf(lib.tracks, tab, artist), prefs.sort, locale)
    val namesById = lib.members.associate { it.id to musicMemberName(it) }

    // What is kept on the phone (16.10): looked at again whenever it may have changed.
    LaunchedEffect(prefs.cacheWhilePlaying) { MusicCache.keepWhilePlaying = prefs.cacheWhilePlaying }
    val cacheRevision by MusicCache.revision.collectAsStateWithLifecycle()
    val saving by MusicCache.saving.collectAsStateWithLifecycle()
    val kept by produceState(emptySet<String>(), lib.tracks, cacheRevision) {
        value = withContext(Dispatchers.IO) { MusicCache.keptIds(context, lib.tracks) }
    }
    val keptBytes by produceState(0L, cacheRevision) { value = withContext(Dispatchers.IO) { MusicCache.bytes(context) } }
    val upstream = remember(context) { musicUpstreamFactory(context, connection) }
    // "Save all" for the list shown (16.10): what it would fetch, and how it is going.
    val saveAll by remember(context) { MusicSaveAllWorker.progress(context) }.collectAsStateWithLifecycle(initialValue = null)
    val savePlan = musicDownloadPlan(shown, shown.filter { it.id in kept }.mapNotNull { musicCacheKey(it) }.toSet(), emptySet())
    // Copies of tracks gone from the sidecar, or replaced on it, go once its list is in.
    LaunchedEffect(lib.loaded, lib.tracks) { if (lib.loaded) MusicCache.dropStale(context, lib.tracks) }

    val mediaItemOf: (SidecarTrack) -> MediaItem = { track -> musicMediaItem(track, connection.audioUrl(track.id), unknownArtist) }
    val panelShown = playerOpen && controller != null && now.trackId != null

    // A tap plays the list from that track on, going round it; a track with no file is passed by.
    val play: (SidecarTrack) -> Unit = { start ->
        val queue = musicQueue(shown, start) { it.hasAudio }
        if (controller != null && queue != null) controller.playMusic(queue, mediaItemOf)
    }

    // The app opened again with the player empty — Android let the service go — finds it where it was, paused.
    LaunchedEffect(controller, lib.loaded, space?.id) {
        val c = controller ?: return@LaunchedEffect
        val spaceId = space?.id ?: return@LaunchedEffect
        if (!lib.loaded || c.mediaItemCount > 0) return@LaunchedEffect
        val memory = withContext(Dispatchers.IO) { memoryFile.read() } ?: return@LaunchedEffect
        val resumed = musicResumed(memory, spaceId, lib.tracks) { it.hasAudio } ?: return@LaunchedEffect
        if (c.mediaItemCount == 0) c.resumeMusic(resumed, memory.shuffle, memory.repeat, mediaItemOf)
    }

    Scaffold(
        // Norton Commander's keys along the bottom, in DOS.
        bottomBar = {
            Column {
                if (!panelShown) MusicMiniBar(now, controller) { playerOpen = true }
                MetroKeyBar(
                    listOf(
                        MetroKey(2, stringResource(R.string.music_sort)) { sortOpen = true },
                        MetroKey(3, stringResource(R.string.metro_key_style)) { styleOpen = true },
                        MetroKey(5, stringResource(R.string.music_refresh), refresh),
                        MetroKey(9, stringResource(R.string.metro_key_setup)) { setupOpen = true },
                        MetroKey(10, stringResource(R.string.metro_key_quit), onBack),
                    ),
                )
            }
        },
        topBar = {
            MetroTopBar(
                title = space?.name?.takeIf { it.isNotBlank() && signIn == SidecarSignInState.SIGNED_IN }
                    ?: stringResource(R.string.menu_music),
                onBack = onBack,
                actions = {
                    if (signIn == SidecarSignInState.SIGNED_IN && space != null) {
                        IconButton(onClick = refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.music_refresh))
                        }
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.music_sort))
                        }
                    }
                    IconButton(onClick = { styleOpen = true }) {
                        Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.metro_style))
                    }
                    IconButton(onClick = { setupOpen = true }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.music_settings))
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            val c = controller
            when {
                panelShown && c != null -> MusicPlayerPanel(c, now) { playerOpen = false }
                signIn != SidecarSignInState.SIGNED_IN -> MusicMessage(
                    text = stringResource(
                        if (signIn == SidecarSignInState.ATTENTION) R.string.sidecar_session_ended else R.string.sidecar_sign_in_why,
                    ),
                    action = stringResource(R.string.sidecar_sign_in),
                    onAction = { accountOpen = true },
                )
                space == null -> MusicMessage(text = stringResource(R.string.sidecar_no_spaces))
                else -> {
                    MusicTabStrip(
                        tabs = tabs,
                        selected = tab,
                        label = { t ->
                            when (t) {
                                MusicTab.All -> stringResource(R.string.music_tab_all)
                                MusicTab.ByArtist -> stringResource(R.string.music_tab_artist)
                                is MusicTab.Member -> namesById[t.userId] ?: "?"
                            }
                        },
                        onSelect = { t -> onPrefs { it.copy(tab = musicTabKey(t)) } },
                    )
                    if (tab == MusicTab.ByArtist) {
                        MetroTextButton(onClick = { artistOpen = true }, enabled = artists.isNotEmpty(), modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(artist ?: stringResource(R.string.music_no_artists), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val going = saveAll
                    if (going != null) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (going.waiting || going.total == 0) {
                                    stringResource(R.string.music_save_all_waiting)
                                } else {
                                    stringResource(R.string.music_save_all_going, going.done, going.total)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            MetroTextButton(onClick = { MusicSaveAllWorker.cancel(context) }) { Text(stringResource(R.string.cancel)) }
                        }
                    } else if (savePlan.tracks.isNotEmpty() && lib.loaded) {
                        MetroTextButton(onClick = { saveAllAsked = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(stringResource(R.string.music_save_all, savePlan.tracks.size, Formatter.formatShortFileSize(context, savePlan.bytes)))
                        }
                    }
                    if (lib.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    lib.failure?.let { failure ->
                        MusicMessage(
                            text = sidecarFailureText(failure),
                            action = stringResource(R.string.music_retry),
                            onAction = refresh,
                            compact = lib.loaded,
                        )
                    }
                    if (lib.loaded && shown.isEmpty()) {
                        MusicMessage(text = stringResource(R.string.music_empty))
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(shown, key = { it.id }) { track ->
                            MusicTrackRow(
                                track = track,
                                addedBy = if (tab is MusicTab.Member) null else namesById[track.createdByUserId],
                                size = track.audioSize?.let { Formatter.formatShortFileSize(context, it) },
                                current = track.id == now.trackId,
                                kept = track.id in kept,
                                saving = track.id in saving,
                                onPlay = { play(track) },
                                onSave = { MusicCache.save(context, track, connection.audioUrl(track.id), upstream) },
                                onRemove = { removing = track.id },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (accountOpen) {
        SidecarAccountDialog(
            status = status,
            nowMillis = System.currentTimeMillis(),
            onSignIn = { server, email, password -> connection.signIn(server, email, password) },
            onSignOut = {
                connection.signOut()
                SidecarAvatarStores.of(context).forget()
                // The queue was this person's.
                musicPlayerMemoryFile(context).forget()
            },
            onRefresh = { connection.refreshAccount() },
            onChooseSpace = { id -> scope.launch { connection.chooseSpace(id) } },
            onDismiss = { accountOpen = false },
        )
    }

    if (sortOpen) {
        MusicChoiceDialog(
            title = stringResource(R.string.music_sort),
            choices = MusicSort.entries.map { it to stringResource(musicSortName(it)) },
            chosen = prefs.sort,
            onChoose = { sort -> onPrefs { it.copy(sort = sort) } },
            onDismiss = { sortOpen = false },
        )
    }

    if (artistOpen) {
        MusicChoiceDialog(
            title = stringResource(R.string.music_artist),
            choices = artists.map { it to it },
            chosen = artist,
            onChoose = { name -> onPrefs { it.copy(artist = name) } },
            onDismiss = { artistOpen = false },
        )
    }

    if (styleOpen) {
        MetroStyleDialog(
            current = prefs.style,
            details = prefs.styleDetails,
            onPick = { style -> onPrefs { it.copy(style = style) } },
            onDetails = { on -> onPrefs { it.copy(styleDetails = on) } },
            eraColors = false,
            onEraColors = null,
            onDismiss = { styleOpen = false },
        )
    }

    if (setupOpen) {
        MetroDialog(
            onDismissRequest = { setupOpen = false },
            title = { Text(stringResource(R.string.music_settings)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MetroMapTick(stringResource(R.string.music_pref_artist_tab), prefs.artistTab, Modifier.fillMaxWidth()) { on ->
                        onPrefs { it.copy(artistTab = on) }
                    }
                    MetroMapTick(stringResource(R.string.music_pref_keep_played), prefs.cacheWhilePlaying, Modifier.fillMaxWidth()) { on ->
                        onPrefs { it.copy(cacheWhilePlaying = on) }
                    }
                    MetroMapTick(stringResource(R.string.music_pref_wifi_only), prefs.downloadOnWifiOnly, Modifier.fillMaxWidth()) { on ->
                        onPrefs { it.copy(downloadOnWifiOnly = on) }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.music_kept_size, Formatter.formatShortFileSize(context, keptBytes)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        MetroTextButton(onClick = { clearAsked = true }, enabled = keptBytes > 0L) {
                            Text(stringResource(R.string.music_clear))
                        }
                    }
                }
            },
            confirmButton = { MetroTextButton(onClick = { setupOpen = false }) { Text(stringResource(R.string.ok)) } },
        )
    }

    lib.tracks.firstOrNull { it.id == removing }?.let { track ->
        MetroDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.music_remove_title)) },
            text = { Text(stringResource(R.string.music_remove_text, track.title)) },
            confirmButton = {
                MetroTextButton(onClick = {
                    MusicCache.remove(context, track)
                    removing = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { MetroTextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (saveAllAsked) {
        MetroDialog(
            onDismissRequest = { saveAllAsked = false },
            title = { Text(stringResource(R.string.music_save_all_title)) },
            text = {
                Text(
                    stringResource(R.string.music_save_all_text, savePlan.tracks.size, Formatter.formatShortFileSize(context, savePlan.bytes)) +
                        if (prefs.downloadOnWifiOnly) "\n" + stringResource(R.string.music_save_all_wifi) else "",
                )
            },
            confirmButton = {
                MetroTextButton(onClick = {
                    MusicSaveAllWorker.start(context, musicSaveItems(savePlan, status.server), prefs.downloadOnWifiOnly)
                    saveAllAsked = false
                }) { Text(stringResource(R.string.music_save)) }
            },
            dismissButton = { MetroTextButton(onClick = { saveAllAsked = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (clearAsked) {
        MetroDialog(
            onDismissRequest = { clearAsked = false },
            title = { Text(stringResource(R.string.music_clear_title)) },
            text = { Text(stringResource(R.string.music_clear_text, Formatter.formatShortFileSize(context, keptBytes))) },
            confirmButton = {
                MetroTextButton(onClick = {
                    MusicCache.clear(context)
                    clearAsked = false
                }) { Text(stringResource(R.string.music_clear)) }
            },
            dismissButton = { MetroTextButton(onClick = { clearAsked = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun musicSortName(sort: MusicSort): Int = when (sort) {
    MusicSort.NEWEST -> R.string.music_sort_newest
    MusicSort.OLDEST -> R.string.music_sort_oldest
    MusicSort.AZ -> R.string.music_sort_az
    MusicSort.ZA -> R.string.music_sort_za
}

/** A word in the middle of the screen, with a button under it when there is something to do. */
@Composable
private fun MusicMessage(text: String, action: String? = null, onAction: () -> Unit = {}, compact: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = if (compact) 8.dp else 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (action != null) {
            MetroTextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** The lists along the top: today's chips; in the old styles, words lit up as the program lit its selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicTabStrip(
    tabs: List<MusicTab>,
    selected: MusicTab,
    label: @Composable (MusicTab) -> String,
    onSelect: (MusicTab) -> Unit,
) {
    val style = LocalMetroStyle.current
    val details = LocalMetroStyleDetails.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val on = tab == selected
            val text = label(tab)
            if (style == MetroStyle.MODERN) {
                FilterChip(selected = on, onClick = { onSelect(tab) }, label = { Text(text, maxLines = 1) })
            } else {
                val (ground, ink) = when {
                    !on -> Color.Transparent to colors.onSurface
                    style == MetroStyle.DOS -> colors.primaryContainer to colors.onPrimaryContainer
                    else -> colors.primary to colors.onPrimary
                }
                Box(
                    modifier = Modifier
                        .then(if (style == MetroStyle.WIN95 && details) Modifier.metroFrame(style, details = true, sunken = on) else Modifier)
                        .background(ground)
                        .clickable(role = Role.Tab) { onSelect(tab) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (style == MetroStyle.WIN95 || !details) text else if (on) "[$text]" else " $text ",
                        color = ink,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** One track: its name, its artist and who added it, and what it lacks. */
@Composable
private fun MusicTrackRow(
    track: SidecarTrack,
    addedBy: String?,
    size: String?,
    current: Boolean,
    kept: Boolean,
    saving: Boolean,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (current) colors.secondaryContainer else Color.Transparent)
            .clickable(enabled = track.hasAudio, role = Role.Button, onClick = onPlay)
            .alpha(if (track.hasAudio) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // DOS and the terminal point at the one playing, as their lists did.
            val pointed = current && musicStyled() && LocalMetroStyle.current != MetroStyle.WIN95
            Text(
                if (pointed) "> ${track.title}" else track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (current) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val by = listOfNotNull(
                track.artist?.takeIf { it.isNotBlank() } ?: stringResource(R.string.music_unknown_artist),
                addedBy?.let { stringResource(R.string.music_added_by, it) },
            ).joinToString(" · ")
            Text(by, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val marks = listOfNotNull(
                if (track.approvedAt == null) stringResource(R.string.music_waiting) else null,
                if (!track.hasAudio) stringResource(R.string.music_no_file) else null,
            )
            if (marks.isNotEmpty()) {
                Text(marks.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
            }
        }
        if (size != null) {
            Text(size, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
        // Kept on the phone or not (16.10): a tap keeps it; a kept one, a tap asks to take it off.
        if (track.hasAudio) {
            when {
                saving -> if (musicStyled()) {
                    Text("...", style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.padding(horizontal = 12.dp))
                } else {
                    CircularProgressIndicator(modifier = Modifier.padding(horizontal = 12.dp).size(22.dp), strokeWidth = 2.dp)
                }
                kept -> MusicControl(Icons.Default.DownloadDone, "KEPT", stringResource(R.string.music_kept), onRemove, on = true)
                else -> MusicControl(Icons.Default.Download, "SAVE", stringResource(R.string.music_save), onSave, lit = false)
            }
        }
    }
}

/** One of several, in the section's style: the orders, or the artists. */
@Composable
private fun <T> MusicChoiceDialog(
    title: String,
    choices: List<Pair<T, String>>,
    chosen: T?,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    MetroDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { (value, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                onChoose(value)
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MetroRadio(selected = value == chosen, onClick = {
                            onChoose(value)
                            onDismiss()
                        })
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { MetroTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
