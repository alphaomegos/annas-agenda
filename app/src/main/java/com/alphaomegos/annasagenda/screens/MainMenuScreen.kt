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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
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
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.util.BackupImportPayload
import com.alphaomegos.annasagenda.util.appLocale
import com.alphaomegos.annasagenda.util.readBackupImportPayload
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val payload = readBackupImportPayload(context, uri)

            val ok = when (payload) {
                is BackupImportPayload.LegacyJson -> {
                    vm.importBackupJson(payload.json)
                }

                is BackupImportPayload.ZipPackage -> {
                    vm.importBackupPackage(
                        appStateJson = payload.appStateJson,
                        coverEntries = payload.coverEntries
                    )
                }

                null -> false
            }

            Toast.makeText(
                context,
                when {
                    ok -> context.getString(R.string.toast_imported)
                    payload == null -> context.getString(R.string.toast_import_failed)
                    else -> context.getString(R.string.toast_invalid_backup)
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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
        langIconRes = langIconRes,
        menuEntries = menuEntries,
        menuOrderIds = state.mainMenuOrder,
        menuHiddenIds = state.mainMenuHiddenIds,
        onMenuOrderChange = vm::setMainMenuOrder,
        onHideMenuItem = vm::hideMainMenuItem,
        onShowAllMenuItems = vm::showAllMainMenuItems,
        onLanguage = onLanguage,
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
        )
    }
}

private fun applyMenuOrder(
    allItems: List<MenuEntry>,
    order: List<String>,
): List<MenuEntry> {
    if (order.isEmpty()) return allItems

    val byId = allItems.associateBy { it.id }
    val ordered = order.mapNotNull { byId[it] }
    val missing = allItems.filterNot { it.id in order.toSet() }
    return ordered + missing
}

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
internal fun MainMenuContent(
    langIconRes: Int,
    menuEntries: List<MenuEntry>,
    menuOrderIds: List<String>,
    menuHiddenIds: Set<String>,
    onMenuOrderChange: (List<String>) -> Unit,
    onHideMenuItem: (String) -> Unit,
    onShowAllMenuItems: () -> Unit,
    onLanguage: () -> Unit,
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
        applyMenuOrder(menuEntries, menuOrderIds)
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
        persistCurrentOrder()
    }

    Scaffold(
        topBar = {
            MainMenuTopBar(
                langIconRes = langIconRes,
                reorderMode = reorderMode,
                showShowAllButton = menuHiddenIds.isNotEmpty(),
                dataMenuExpanded = dataMenuExpanded,
                onLanguage = onLanguage,
                onOpenDataMenu = { dataMenuExpanded = true },
                onDismissDataMenu = { dataMenuExpanded = false },
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
                    items = orderedFromState
                    onShowAllMenuItems()
                },
                onFinishReorder = ::finishReorder
            )
        }
    ) { innerPadding ->
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        )
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
    reorderMode: Boolean,
    showShowAllButton: Boolean,
    dataMenuExpanded: Boolean,
    onLanguage: () -> Unit,
    onOpenDataMenu: () -> Unit,
    onDismissDataMenu: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    onShowAll: () -> Unit,
    onFinishReorder: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            if (reorderMode) {
                TextButton(
                    onClick = onShowAll,
                    enabled = showShowAllButton
                ) {
                    Text(stringResource(R.string.main_menu_show_all))
                }

                IconButton(onClick = onFinishReorder) {
                    Icon(Icons.Default.Check, contentDescription = null)
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
                    Modifier.pointerInput(item.id) {
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
                                            canHideItems
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
                                            onDragStart(index)
                                            return@detectDragGestures
                                        }
                                    }
                                }

                                val axis = lockedAxis ?: return@detectDragGestures
                                when (axis) {
                                    MenuGestureAxis.Horizontal -> {
                                        change.consume()
                                        if (!canHideItems) return@detectDragGestures
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_title)) },
        text = { Text(stringResource(R.string.reset_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun MenuRowCard(
    iconRes: Int,
    title: String,
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )

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
                            cd = "Move menu item up"
                        )
                    }

                    Box(modifier = Modifier.alpha(if (canMoveDown) 1f else 0.35f)) {
                        TinyIconButton(
                            onClick = {
                                if (canMoveDown) onMoveDown()
                            },
                            icon = Icons.Default.KeyboardArrowDown,
                            cd = "Move menu item down"
                        )
                    }

                    Box(modifier = Modifier.alpha(if (canHide) 1f else 0.35f)) {
                        TinyIconButton(
                            onClick = {
                                if (canHide) onHide()
                            },
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            cd = "Hide menu item"
                        )
                    }
                }
            }

            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(MENU_ICON_SIZE_DP.dp)
            )
        }
    }
}