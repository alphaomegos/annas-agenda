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

    Scaffold(
        // Norton Commander's keys along the bottom, in DOS.
        bottomBar = {
            MetroKeyBar(
                listOf(
                    MetroKey(2, stringResource(R.string.music_sort)) { sortOpen = true },
                    MetroKey(3, stringResource(R.string.metro_key_style)) { styleOpen = true },
                    MetroKey(5, stringResource(R.string.music_refresh), refresh),
                    MetroKey(9, stringResource(R.string.metro_key_setup)) { setupOpen = true },
                    MetroKey(10, stringResource(R.string.metro_key_quit), onBack),
                ),
            )
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
            when {
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
            onSignOut = { connection.signOut() },
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
                }
            },
            confirmButton = { MetroTextButton(onClick = { setupOpen = false }) { Text(stringResource(R.string.ok)) } },
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
private fun MusicTrackRow(track: SidecarTrack, addedBy: String?, size: String?) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (track.hasAudio) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
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
