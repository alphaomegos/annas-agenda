package com.alphaomegos.annasagenda.screens

import com.alphaomegos.annasagenda.model.MainMenuNarrowStyle
import com.alphaomegos.annasagenda.components.MenuTileLabel
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.alphaomegos.annasagenda.support.ImportMessage
import com.alphaomegos.annasagenda.support.ImportOutcome
import com.alphaomegos.annasagenda.support.importMessageFor
import com.alphaomegos.annasagenda.app.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.appIsDarkTheme
import com.alphaomegos.annasagenda.support.isAnnaDay
import com.alphaomegos.annasagenda.support.itemsInMenuOrder
import com.alphaomegos.annasagenda.support.mainMenuColumns
import com.alphaomegos.annasagenda.support.withItemMoved
import com.alphaomegos.annasagenda.support.undoneLampFor
import com.alphaomegos.annasagenda.app.undoneLampIconRes
import com.alphaomegos.annasagenda.components.ConfirmDialog
import com.alphaomegos.annasagenda.components.COMING_SOON_ICON_ALPHA
import com.alphaomegos.annasagenda.components.comingSoonIconFilter
import com.alphaomegos.annasagenda.util.BackupImportPayload
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.util.readBackupImportPayload
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.alphaomegos.annasagenda.components.TinyIconButton
import com.alphaomegos.annasagenda.components.SidecarAccountDialog
import com.alphaomegos.annasagenda.components.SidecarAccountIcon
import com.alphaomegos.annasagenda.components.rememberSidecarAvatar
import com.alphaomegos.annasagenda.app.sidecarSignIn
import com.alphaomegos.annasagenda.app.sidecarSignOut
import androidx.compose.ui.graphics.ImageBitmap
import com.alphaomegos.annasagenda.app.SidecarConnections
import com.alphaomegos.annasagenda.support.SidecarSignInState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal const val MENU_REORDER_HOLD_MS = 3000L

internal const val MENU_ROW_MIN_HEIGHT_DP = 80
internal const val MENU_ICON_SIZE_DP = 64

internal enum class MenuGestureAxis {
    Horizontal,
    Vertical,
}

internal data class MenuEntry(
    val id: String,
    val iconRes: Int,
    val titleRes: Int,
    val onClick: () -> Unit,
    // A section that exists only as a promise: grey, "Coming soon", a tap
    // does nothing. It can still be moved and hidden like any other.
    val comingSoon: Boolean = false,
    // What a grey one says under its name instead of "Coming soon" — music
    // before signing in (16.10): its tap asks to sign in.
    val greyNoteRes: Int? = null,
)

@Composable
fun MainMenuScreen(
    vm: AppViewModel,
    onLanguage: () -> Unit,
    onCalendar: () -> Unit,
    onNewTask: () -> Unit,
    onSomeday: () -> Unit,
    onRecurring: () -> Unit,
    onAnthropometry: () -> Unit,
    onCalorimeter: () -> Unit,
    onRunning: () -> Unit,
    onCounters: () -> Unit,
    onMediaLibrary: () -> Unit,
    onUndone: () -> Unit,
    onSettings: () -> Unit,
    onMetro: () -> Unit = {},
    onTravel: () -> Unit = {},
    onMusic: () -> Unit = {},
    onSidecarBackups: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val payload = readBackupImportPayload(context, uri)

            val outcome = when (payload) {
                is BackupImportPayload.LegacyJson -> {
                    vm.importBackupJson(payload.json)
                }

                is BackupImportPayload.ZipPackage -> {
                    vm.importBackupPackage(
                        appStateJson = payload.appStateJson,
                        coverEntries = payload.coverEntries
                    )
                }

                null -> ImportOutcome.Failed
            }

            val message = importMessageFor(
                payloadWasReadable = payload != null,
                outcome = outcome,
            )

            Toast.makeText(
                context,
                when (message) {
                    ImportMessage.IMPORTED -> context.getString(R.string.toast_imported)
                    ImportMessage.IMPORTED_WITHOUT_SOME_COVERS -> context.resources
                        .getQuantityString(
                            R.plurals.toast_imported_without_some_covers,
                            outcome.coversNotWritten,
                            outcome.coversNotWritten,
                        )
                    ImportMessage.COULD_NOT_READ_FILE -> context.getString(R.string.toast_import_failed)
                    ImportMessage.NOT_A_BACKUP -> context.getString(R.string.toast_invalid_backup)
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Read once. If the app is left open across midnight on the 28th the card
    // appears when the menu is next rebuilt rather than at the stroke of
    // twelve, which for a card that lives one day a year is fine.
    val today = remember { LocalDate.now() }

    val locale = appLocale()
    val langTag = remember(locale) {
        when (locale.language) {
            "sr" -> AppLanguages.SR_LATN
            "gil" -> "gil"
            "ru" -> "ru"
            else -> "en"
        }
    }
    val langIconRes = remember(langTag) {
        when (langTag) {
            "ru" -> R.drawable.ic_langflag_ru
            AppLanguages.SR_LATN -> R.drawable.ic_langflag_sr_latn
            "gil" -> R.drawable.ic_langflag_gil
            else -> R.drawable.ic_langflag_en
        }
    }

    val state by vm.state.collectAsState()

    // The oursidecar sign-in (16.10): one for the process, kept by the connection.
    val sidecar = remember(context) { SidecarConnections.of(context) }
    val sidecarStatus by sidecar.status.collectAsStateWithLifecycle()
    var sidecarDialogOpen by rememberSaveable { mutableStateOf(false) }
    // One instance, so the menu's entries are not built anew on every redraw for it.
    val openSidecar = remember { { sidecarDialogOpen = true } }

    // The lamp reads the same debts as the Undone screen, so it needs the same
    // horizon materialised. Without this it only went red for days the calendar
    // had already drawn.
    LaunchedEffect(state.undoneHorizonDays) {
        vm.ensureUndoneHorizonGenerated()
    }

    // Named apart from the function it calls: a local `val x = x(...)` is
    // legal Kotlin and reads like a mistake.
    val lampIconRes = undoneLampIconRes(
        lamp = undoneLampFor(
            muted = state.undoneLampMuted,
            hasDebt = vm.hasUndonePastTasks(),
        ),
        dark = appIsDarkTheme,
    )

    val menuEntries = rememberMainMenuEntries(
        onCalendar = onCalendar,
        onNewTask = onNewTask,
        onSomeday = onSomeday,
        onRecurring = onRecurring,
        onAnthropometry = onAnthropometry,
        onCalorimeter = onCalorimeter,
        onRunning = onRunning,
        onCounters = onCounters,
        onMediaLibrary = onMediaLibrary,
        onMetro = onMetro,
        onTravel = onTravel,
        // Music opens only signed in to oursidecar (16.10); grey before, and a tap asks to sign in.
        musicOpen = sidecarStatus.signIn(System.currentTimeMillis()) == SidecarSignInState.SIGNED_IN,
        onMusic = onMusic,
        onMusicClosed = openSidecar,
    )

    MainMenuContent(
        today = today,
        langIconRes = langIconRes,
        undoneLampIconRes = lampIconRes,
        menuEntries = menuEntries,
        menuOrderIds = state.mainMenuOrder,
        menuHiddenIds = state.mainMenuHiddenIds,
        narrowStyle = state.mainMenuNarrow,
        onMenuOrderChange = vm::setMainMenuOrder,
        onHideMenuItem = vm::hideMainMenuItem,
        onShowAllMenuItems = vm::showAllMainMenuItems,
        onLanguage = onLanguage,
        onSettings = onSettings,
        sidecarState = sidecarStatus.signIn(System.currentTimeMillis()),
        sidecarAvatar = rememberSidecarAvatar(sidecarStatus),
        onSidecar = openSidecar,
        onUndone = onUndone,
        onSidecarBackups = onSidecarBackups,
        onExport = {
            scope.launch {
                val ok = if (Build.VERSION.SDK_INT < 29) {
                    false
                } else {
                    runCatching {
                        vm.exportBackupToDocuments()
                    }.isSuccess
                }

                Toast.makeText(
                    context,
                    if (ok) context.getString(R.string.toast_exported)
                    else context.getString(R.string.toast_export_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onImport = {
            importLauncher.launch(
                arrayOf(
                    "application/zip",
                    "application/octet-stream",
                    "application/json",
                    "text/*"
                )
            )
        },
        onResetConfirmed = {
            vm.resetAllData()
            Toast.makeText(
                context,
                context.getString(R.string.toast_reset_done),
                Toast.LENGTH_SHORT
            ).show()
        }
    )

    if (sidecarDialogOpen) {
        SidecarAccountDialog(
            status = sidecarStatus,
            nowMillis = System.currentTimeMillis(),
            onSignIn = { server, email, password -> sidecarSignIn(context, server, email, password) },
            onSignOut = { sidecarSignOut(context) },
            onRefresh = { sidecar.refreshAccount() },
            onChooseSpace = { id -> scope.launch { sidecar.chooseSpace(id) } },
            onDismiss = { sidecarDialogOpen = false },
        )
    }
}

@Composable
private fun rememberMainMenuEntries(
    onCalendar: () -> Unit,
    onNewTask: () -> Unit,
    onSomeday: () -> Unit,
    onRecurring: () -> Unit,
    onAnthropometry: () -> Unit,
    onCalorimeter: () -> Unit,
    onRunning: () -> Unit,
    onCounters: () -> Unit,
    onMediaLibrary: () -> Unit,
    onMetro: () -> Unit,
    onTravel: () -> Unit,
    musicOpen: Boolean,
    onMusic: () -> Unit,
    onMusicClosed: () -> Unit,
): List<MenuEntry> {
    return remember(
        onCalendar,
        onNewTask,
        onSomeday,
        onRecurring,
        onAnthropometry,
        onCalorimeter,
        onRunning,
        onCounters,
        onMediaLibrary,
        onMetro,
        onTravel,
        musicOpen,
        onMusic,
        onMusicClosed,
    ) {
        listOf(
            MenuEntry("calendar", R.drawable.ic_menu_calendar, R.string.calendar, onCalendar),
            MenuEntry("new_task", R.drawable.ic_menu_new_task, R.string.create_task, onNewTask),
            MenuEntry("someday", R.drawable.ic_menu_someday, R.string.someday_title, onSomeday),
            MenuEntry("recurring", R.drawable.ic_menu_recurring, R.string.recurring_tasks_title, onRecurring),
            MenuEntry("anthropometry", R.drawable.ic_menu_anthropometry, R.string.anthropometry_title, onAnthropometry),
            MenuEntry("calorimeter", R.drawable.ic_menu_calorimeter, R.string.calorimeter_title, onCalorimeter),
            MenuEntry("running", R.drawable.ic_menu_running, R.string.running_title, onRunning),
            MenuEntry("counters", R.drawable.ic_menu_counters, R.string.counters_title, onCounters),
            MenuEntry("reading", R.drawable.ic_menu_reading, R.string.menu_reading, onMediaLibrary),
            MenuEntry("metro", R.drawable.ic_menu_metro, R.string.menu_metro, onMetro),
            MenuEntry("travel", R.drawable.ic_menu_travel, R.string.menu_travel, onTravel),
            // Eduard's two cats with a record; grey until signed in.
            MenuEntry(
                "music",
                R.drawable.ic_menu_music,
                R.string.menu_music,
                if (musicOpen) onMusic else onMusicClosed,
                comingSoon = !musicOpen,
                greyNoteRes = R.string.menu_music_sign_in,
            ),
        )
    }
}

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
internal fun MainMenuContent(
    today: LocalDate,
    langIconRes: Int,
    undoneLampIconRes: Int,
    menuEntries: List<MenuEntry>,
    menuOrderIds: List<String>,
    menuHiddenIds: Set<String>,
    onMenuOrderChange: (List<String>) -> Unit,
    narrowStyle: MainMenuNarrowStyle = MainMenuNarrowStyle.LIST,
    onHideMenuItem: (String) -> Unit,
    onShowAllMenuItems: () -> Unit,
    onLanguage: () -> Unit,
    // Defaulted so the tests that draw the menu need not know about it.
    onSettings: () -> Unit = {},
    sidecarState: SidecarSignInState = SidecarSignInState.SIGNED_OUT,
    sidecarAvatar: ImageBitmap? = null,
    onSidecar: () -> Unit = {},
    onUndone: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onResetConfirmed: () -> Unit,
    // The backups in oursidecar (16.10); defaulted, as onSettings.
    onSidecarBackups: () -> Unit = {},
) {
    var dataMenuExpanded by remember { mutableStateOf(false) }
    var reorderMode by rememberSaveable { mutableStateOf(false) }
    val confirmReset = rememberSaveable { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    val orderedFromState = remember(menuEntries, menuOrderIds) {
        itemsInMenuOrder(menuEntries, menuOrderIds) { it.id }
    }
    val visibleOrderedFromState = remember(orderedFromState, menuHiddenIds) {
        orderedFromState.filterNot { it.id in menuHiddenIds }
    }

    var items by remember { mutableStateOf(visibleOrderedFromState) }

    LaunchedEffect(visibleOrderedFromState, reorderMode) {
        if (!reorderMode) {
            items = visibleOrderedFromState
        }
    }

    val draggingIndex = remember { mutableIntStateOf(-1) }
    val draggingOffsetY = remember { mutableFloatStateOf(0f) }
    // Only the tiles move sideways; the list leaves this at zero.
    val draggingOffsetX = remember { mutableFloatStateOf(0f) }

    fun persistCurrentOrder() {
        val hiddenIdsInOrder = orderedFromState
            .map { it.id }
            .filter { it in menuHiddenIds }

        onMenuOrderChange((items.map { it.id } + hiddenIdsInOrder).distinct())
    }

    fun finishReorder() {
        reorderMode = false
        draggingIndex.intValue = -1
        draggingOffsetY.floatValue = 0f
        draggingOffsetX.floatValue = 0f
        persistCurrentOrder()
    }

    Scaffold(
        topBar = {
            MainMenuTopBar(
                langIconRes = langIconRes,
                undoneLampIconRes = undoneLampIconRes,
                reorderMode = reorderMode,
                showShowAllButton = menuHiddenIds.isNotEmpty(),
                dataMenuExpanded = dataMenuExpanded,
                sidecarState = sidecarState,
                sidecarAvatar = sidecarAvatar,
                onSidecar = onSidecar,
                onUndone = onUndone,
                onLanguage = onLanguage,
                onOpenDataMenu = { dataMenuExpanded = true },
                onDismissDataMenu = { dataMenuExpanded = false },
                onSettings = {
                    dataMenuExpanded = false
                    onSettings()
                },
                onExport = {
                    dataMenuExpanded = false
                    onExport()
                },
                onImport = {
                    dataMenuExpanded = false
                    onImport()
                },
                onSidecarBackups = {
                    dataMenuExpanded = false
                    onSidecarBackups()
                },
                onReset = {
                    dataMenuExpanded = false
                    confirmReset.value = true
                },
                onShowAll = {
                    draggingIndex.intValue = -1
                    draggingOffsetY.floatValue = 0f
                    draggingOffsetX.floatValue = 0f
                    items = orderedFromState
                    onShowAllMenuItems()
                },
                onFinishReorder = ::finishReorder
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isAnnaDay(today)) {
                AnnaDayCard()
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val wide = mainMenuColumns(maxWidth.value.toInt())
                // A narrow screen as «mini buttons» (25.10): two to a row, pictures only.
                // «Mini buttons» on any screen (28.10): two to a row when narrow,
                // as many as the tiles when wide; pictures only, no frames.
                val miniButtons = narrowStyle == MainMenuNarrowStyle.ICONS
                val columns = if (miniButtons && wide == 1) 2 else wide

                // Rearranging stays in whichever shape the menu is in. Until
                // 0130 the tiles handed over to the list for it, which on the
                // phone read as a jump to another screen at the very moment
                // something was being held.
                if (columns > 1) {
                    MainMenuTiles(
                        items = items,
                        columns = columns,
                        label = if (miniButtons) MenuTileLabel.IconOnly else MenuTileLabel.Small,
                        reorderMode = reorderMode,
                        canHideItems = items.size > 1,
                        draggingIndex = draggingIndex.intValue,
                        draggingOffset = Offset(draggingOffsetX.floatValue, draggingOffsetY.floatValue),
                        onStartReorder = { reorderMode = true },
                        onDragStart = { index ->
                            draggingIndex.intValue = index
                            draggingOffsetX.floatValue = 0f
                            draggingOffsetY.floatValue = 0f
                        },
                        onDrag = { delta ->
                            draggingOffsetX.floatValue += delta.x
                            draggingOffsetY.floatValue += delta.y
                        },
                        onMoveItem = { from, to, dragCompensation ->
                            items = items.withItemMoved(from, to)
                            draggingIndex.intValue = to
                            draggingOffsetX.floatValue += dragCompensation.x
                            draggingOffsetY.floatValue += dragCompensation.y
                        },
                        onHideItem = { id ->
                            draggingIndex.intValue = -1
                            draggingOffsetX.floatValue = 0f
                            draggingOffsetY.floatValue = 0f
                            items = items.filterNot { it.id == id }
                            onHideMenuItem(id)
                        },
                        onStopDragging = {
                            draggingIndex.intValue = -1
                            draggingOffsetX.floatValue = 0f
                            draggingOffsetY.floatValue = 0f
                            persistCurrentOrder()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {

                    MainMenuList(
                        items = items,
                        reorderMode = reorderMode,
                        canHideItems = items.size > 1,
                        listState = listState,
                        draggingIndex = draggingIndex.intValue,
                        draggingOffsetY = draggingOffsetY.floatValue,
                        onStartReorder = {
                            reorderMode = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragStart = { index ->
                            draggingIndex.intValue = index
                            draggingOffsetY.floatValue = 0f
                        },
                        onDragOffsetChange = { deltaY ->
                            draggingOffsetY.floatValue += deltaY
                        },
                        onMoveItem = { from, to, dragCompensation ->
                            items = items.toMutableList().also { list ->
                                val moved = list.removeAt(from)
                                list.add(to, moved)
                            }
                            draggingIndex.intValue = to
                            draggingOffsetY.floatValue += dragCompensation
                        },
                        onStepMoveItem = { from, to ->
                            if (from in items.indices && to in items.indices && from != to) {
                                items = items.toMutableList().also { list ->
                                    val moved = list.removeAt(from)
                                    list.add(to, moved)
                                }
                                draggingIndex.intValue = -1
                                draggingOffsetY.floatValue = 0f
                                // Written through at once, as a drag is when it
                                // ends: `items` is only a working copy, and an
                                // arrow tap kept until Done was lost to the
                                // first turn of the phone.
                                persistCurrentOrder()
                            }
                        },
                        onHideItem = { id ->
                            draggingIndex.intValue = -1
                            draggingOffsetY.floatValue = 0f
                            items = items.filterNot { it.id == id }
                            onHideMenuItem(id)
                        },
                        onStopDragging = {
                            draggingIndex.intValue = -1
                            draggingOffsetY.floatValue = 0f
                            persistCurrentOrder()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    ResetDataDialog(
        open = confirmReset.value,
        onDismiss = { confirmReset.value = false },
        onConfirm = {
            onResetConfirmed()
            confirmReset.value = false
        }
    )
}
