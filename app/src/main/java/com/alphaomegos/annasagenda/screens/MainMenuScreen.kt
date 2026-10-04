package com.alphaomegos.annasagenda.screens

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

private const val MENU_REORDER_HOLD_MS = 3000L
private const val MENU_ROW_MIN_HEIGHT_DP = 80
private const val MENU_ICON_SIZE_DP = 64

private enum class MenuGestureAxis {
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
        onMediaLibrary = onMediaLibrary
    )

    MainMenuContent(
        today = today,
        langIconRes = langIconRes,
        undoneLampIconRes = lampIconRes,
        menuEntries = menuEntries,
        menuOrderIds = state.mainMenuOrder,
        menuHiddenIds = state.mainMenuHiddenIds,
        onMenuOrderChange = vm::setMainMenuOrder,
        onHideMenuItem = vm::hideMainMenuItem,
        onShowAllMenuItems = vm::showAllMainMenuItems,
        onLanguage = onLanguage,
        onSettings = onSettings,
        onUndone = onUndone,
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
        onMediaLibrary
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
            // Coming soon (0147). The pictures are the real ones, drawn grey
            // until the sections exist.
            MenuEntry("metro", R.drawable.ic_menu_metro, R.string.menu_metro, onClick = {}, comingSoon = true),
            MenuEntry("travel", R.drawable.ic_menu_travel, R.string.menu_travel, onClick = {}, comingSoon = true),
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
    onHideMenuItem: (String) -> Unit,
    onShowAllMenuItems: () -> Unit,
    onLanguage: () -> Unit,
    // Defaulted so the tests that draw the menu need not know about it.
    onSettings: () -> Unit = {},
    onUndone: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onResetConfirmed: () -> Unit,
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
                val columns = mainMenuColumns(maxWidth.value.toInt())

                // Rearranging stays in whichever shape the menu is in. Until
                // 0130 the tiles handed over to the list for it, which on the
                // phone read as a jump to another screen at the very moment
                // something was being held.
                if (columns > 1) {
                    MainMenuTiles(
                        items = items,
                        columns = columns,
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

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@Composable
private fun MainMenuTopBar(
    langIconRes: Int,
    undoneLampIconRes: Int,
    reorderMode: Boolean,
    showShowAllButton: Boolean,
    dataMenuExpanded: Boolean,
    onUndone: () -> Unit,
    onLanguage: () -> Unit,
    onOpenDataMenu: () -> Unit,
    onDismissDataMenu: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    onShowAll: () -> Unit,
    onFinishReorder: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        navigationIcon = {
            IconButton(onClick = onUndone) {
                Icon(
                    painter = painterResource(undoneLampIconRes),
                    contentDescription = stringResource(R.string.undone_lamp_open),
                    tint = Color.Unspecified
                )
            }
        },
        actions = {
            if (reorderMode) {
                TextButton(
                    onClick = onShowAll,
                    enabled = showShowAllButton
                ) {
                    Text(stringResource(R.string.main_menu_show_all))
                }

                IconButton(onClick = onFinishReorder) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.finish_reordering)
                    )
                }
            } else {
                IconButton(onClick = onLanguage) {
                    Icon(
                        painter = painterResource(langIconRes),
                        contentDescription = stringResource(R.string.choose_language),
                        tint = Color.Unspecified
                    )
                }

                IconButton(onClick = onOpenDataMenu) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.data_menu)
                    )
                }

                DropdownMenu(
                    expanded = dataMenuExpanded,
                    onDismissRequest = onDismissDataMenu
                ) {
                    // Above the divider: how the app looks. Below it: what
                    // happens to the data. The button is labelled "Data", but
                    // it is the only place in the app that holds settings at
                    // all, and a second overflow button beside it would be
                    // worse than one menu with a line across it.
                    // Every setting lives on one screen now (04.10).
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_title)) },
                        onClick = onSettings
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_backup_json)) },
                        onClick = onExport
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_backup_json)) },
                        onClick = onImport
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reset_data_menu)) },
                        onClick = onReset
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainMenuList(
    items: List<MenuEntry>,
    reorderMode: Boolean,
    canHideItems: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    draggingIndex: Int,
    draggingOffsetY: Float,
    onStartReorder: () -> Unit,
    onDragStart: (Int) -> Unit,
    onDragOffsetChange: (Float) -> Unit,
    onMoveItem: (from: Int, to: Int, dragCompensation: Float) -> Unit,
    onStepMoveItem: (from: Int, to: Int) -> Unit,
    onHideItem: (String) -> Unit,
    onStopDragging: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestDraggingIndex by androidx.compose.runtime.rememberUpdatedState(draggingIndex)
    val latestDraggingOffsetY by androidx.compose.runtime.rememberUpdatedState(draggingOffsetY)

    var swipingItemId by remember { mutableStateOf<String?>(null) }
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    val axisLockThresholdPx = with(LocalDensity.current) { 12.dp.toPx() }
    val hideThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.id }
        ) { index, item ->
            val isDragging = index == draggingIndex
            val isSwiping = swipingItemId == item.id
            // The gesture below is keyed on the item, not on its place, so it
            // outlives a move by the arrows: read the place and the rule as
            // they are now, not as they were when the gesture started (04.10).
            val currentIndex by rememberUpdatedState(index)
            val currentCanHide by rememberUpdatedState(canHideItems)
            val rowTranslationX = if (isSwiping) swipeOffsetX else 0f

            val baseModifier = Modifier
                .animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = IntOffset.VisibilityThreshold
                    )
                )
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) draggingOffsetY else 0f
                }

            val tapAndHoldModifier =
                if (!reorderMode) {
                    // The taps come through a gesture detector, which TalkBack
                    // cannot press; the same two actions are named for it here.
                    Modifier.semantics(mergeDescendants = true) {
                        role = Role.Button
                        this.onClick(label = null) { item.onClick(); true }
                        this.onLongClick(label = null) { onStartReorder(); true }
                    }.pointerInput(item.id) {
                        detectTapGestures(
                            onTap = { item.onClick() },
                            onPress = {
                                val released = withTimeoutOrNull(MENU_REORDER_HOLD_MS) {
                                    tryAwaitRelease()
                                }
                                if (released == null) {
                                    onStartReorder()
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }

            val gestureModifier =
                if (reorderMode) {
                    Modifier.pointerInput(item.id, true) {
                        var lockedAxis: MenuGestureAxis? = null

                        detectDragGestures(
                            onDragStart = {
                                lockedAxis = null
                                swipingItemId = null
                                swipeOffsetX = 0f
                            },
                            onDragCancel = {
                                when (lockedAxis) {
                                    MenuGestureAxis.Vertical -> {
                                        onStopDragging()
                                    }
                                    MenuGestureAxis.Horizontal,
                                    null -> {
                                        swipingItemId = null
                                        swipeOffsetX = 0f
                                    }
                                }
                                lockedAxis = null
                            },
                            onDragEnd = {
                                when (lockedAxis) {
                                    MenuGestureAxis.Vertical -> {
                                        onStopDragging()
                                    }
                                    MenuGestureAxis.Horizontal -> {
                                        if (
                                            swipingItemId == item.id &&
                                            swipeOffsetX <= -hideThresholdPx &&
                                            currentCanHide
                                        ) {
                                            swipingItemId = null
                                            swipeOffsetX = 0f
                                            onHideItem(item.id)
                                        } else {
                                            swipingItemId = null
                                            swipeOffsetX = 0f
                                        }
                                    }
                                    null -> {
                                        swipingItemId = null
                                        swipeOffsetX = 0f
                                    }
                                }
                                lockedAxis = null
                            },
                            onDrag = { change, dragAmount ->
                                if (lockedAxis == null) {
                                    val absX = abs(dragAmount.x)
                                    val absY = abs(dragAmount.y)

                                    if (absX < axisLockThresholdPx && absY < axisLockThresholdPx) {
                                        return@detectDragGestures
                                    }

                                    lockedAxis =
                                        if (absX > absY) {
                                            MenuGestureAxis.Horizontal
                                        } else {
                                            MenuGestureAxis.Vertical
                                        }

                                    val axis = lockedAxis ?: return@detectDragGestures
                                    when (axis) {
                                        MenuGestureAxis.Horizontal -> {
                                            swipingItemId = item.id
                                            swipeOffsetX = 0f
                                        }
                                        MenuGestureAxis.Vertical -> {
                                            swipingItemId = null
                                            swipeOffsetX = 0f
                                            onDragStart(currentIndex)
                                            return@detectDragGestures
                                        }
                                    }
                                }

                                val axis = lockedAxis ?: return@detectDragGestures
                                when (axis) {
                                    MenuGestureAxis.Horizontal -> {
                                        change.consume()
                                        if (!currentCanHide) return@detectDragGestures
                                        swipingItemId = item.id
                                        swipeOffsetX = min(0f, swipeOffsetX + dragAmount.x)
                                    }

                                    MenuGestureAxis.Vertical -> {
                                        change.consume()

                                        val activeIndex = latestDraggingIndex
                                        if (activeIndex < 0) return@detectDragGestures

                                        val updatedOffsetY = latestDraggingOffsetY + dragAmount.y
                                        onDragOffsetChange(dragAmount.y)

                                        val visible = listState.layoutInfo.visibleItemsInfo
                                        val draggedInfo = visible.firstOrNull { it.index == activeIndex }
                                            ?: return@detectDragGestures

                                        val draggedMiddle =
                                            draggedInfo.offset + updatedOffsetY + draggedInfo.size / 2f

                                        val target = visible.firstOrNull { info ->
                                            info.index != activeIndex &&
                                                    draggedMiddle >= info.offset &&
                                                    draggedMiddle <= info.offset + info.size
                                        } ?: return@detectDragGestures

                                        val to = target.index
                                        if (activeIndex == to) return@detectDragGestures

                                        val dragCompensation =
                                            (draggedInfo.offset - target.offset).toFloat()

                                        onMoveItem(activeIndex, to, dragCompensation)
                                    }
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }

            Box(
                modifier = baseModifier.fillMaxWidth()
            ) {
                if (reorderMode && isSwiping && rowTranslationX < 0f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MENU_ROW_MIN_HEIGHT_DP.dp)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = stringResource(R.string.main_menu_hide),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                MenuRowCard(
                    iconRes = item.iconRes,
                    title = stringResource(item.titleRes),
                    comingSoon = item.comingSoon,
                    reorderMode = reorderMode,
                    isDragging = isDragging,
                    canMoveUp = index > 0,
                    canMoveDown = index < items.lastIndex,
                    canHide = canHideItems,
                    onMoveUp = {
                        if (index > 0) {
                            onStepMoveItem(index, index - 1)
                        }
                    },
                    onMoveDown = {
                        if (index < items.lastIndex) {
                            onStepMoveItem(index, index + 1)
                        }
                    },
                    onHide = {
                        if (canHideItems) {
                            onHideItem(item.id)
                        }
                    },
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = rowTranslationX
                        }
                        .then(tapAndHoldModifier)
                        .then(gestureModifier)
                )
            }
        }
    }
}

@Composable
private fun ResetDataDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!open) return

    ConfirmDialog(
        titleRes = R.string.reset_title,
        textRes = R.string.reset_text,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun MenuRowCard(
    iconRes: Int,
    title: String,
    comingSoon: Boolean,
    reorderMode: Boolean,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canHide: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)

    val containerColor = when {
        isDragging -> MaterialTheme.colorScheme.secondaryContainer
        reorderMode -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    val elevation = when {
        isDragging -> 12.dp
        reorderMode -> 6.dp
        else -> 2.dp
    }

    ElevatedCard(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MENU_ROW_MIN_HEIGHT_DP.dp)
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                .alpha(if (reorderMode) 0.98f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (comingSoon) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                if (comingSoon) {
                    Text(
                        text = stringResource(R.string.menu_coming_soon),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (reorderMode) {
                Row(
                    modifier = Modifier.padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(modifier = Modifier.alpha(if (canMoveUp) 1f else 0.35f)) {
                        TinyIconButton(
                            onClick = {
                                if (canMoveUp) onMoveUp()
                            },
                            icon = Icons.Default.KeyboardArrowUp,
                            cd = stringResource(R.string.move_menu_item_up)
                        )
                    }

                    Box(modifier = Modifier.alpha(if (canMoveDown) 1f else 0.35f)) {
                        TinyIconButton(
                            onClick = {
                                if (canMoveDown) onMoveDown()
                            },
                            icon = Icons.Default.KeyboardArrowDown,
                            cd = stringResource(R.string.move_menu_item_down)
                        )
                    }

                    Box(modifier = Modifier.alpha(if (canHide) 1f else 0.35f)) {
                        TinyIconButton(
                            onClick = {
                                if (canHide) onHide()
                            },
                            // Was a back arrow, which says "go back", not
                            // "hide this".
                            icon = Icons.Default.VisibilityOff,
                            cd = stringResource(R.string.hide_menu_item)
                        )
                    }
                }
            }

            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = if (comingSoon) comingSoonIconFilter else null,
                modifier = Modifier
                    .size(MENU_ICON_SIZE_DP.dp)
                    .alpha(if (comingSoon) COMING_SOON_ICON_ALPHA else 1f)
            )
        }
    }
}