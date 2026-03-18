package com.alphaomegos.annasagenda.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
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
import com.alphaomegos.annasagenda.util.appLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val MENU_REORDER_HOLD_MS = 3000L

private const val MENU_ROW_MIN_HEIGHT_DP = 80
private const val MENU_ICON_SIZE_DP = 64

private data class MenuEntry(
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
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = vm.exportBackupJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("OutputStream is null")
                }.isSuccess
            }
            Toast.makeText(
                ctx,
                if (ok) ctx.getString(R.string.toast_exported) else ctx.getString(R.string.toast_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                }.getOrNull()
            }
            if (raw.isNullOrBlank()) {
                Toast.makeText(ctx, ctx.getString(R.string.toast_import_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val ok = vm.importBackupJson(raw)
            Toast.makeText(
                ctx,
                if (ok) ctx.getString(R.string.toast_imported) else ctx.getString(R.string.toast_invalid_backup),
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

    val st by vm.state.collectAsState()
    val menuOrderIds = st.mainMenuOrder

    MainMenuContent(
        langIconRes = langIconRes,
        menuOrderIds = menuOrderIds,
        onMenuOrderChange = { ids -> vm.setMainMenuOrder(ids) },
        onLanguage = onLanguage,
        onCalendar = onCalendar,
        onNewTask = onNewTask,
        onSomeday = onSomeday,
        onRecurring = onRecurring,
        onAnthropometry = onAnthropometry,
        onCalorimeter = onCalorimeter,
        onRunning = onRunning,
        onCounters = onCounters,
        onMediaLibrary = onMediaLibrary,
        onExport = { exportLauncher.launch("annasagenda-backup.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
        onResetConfirmed = {
            vm.resetAllData()
            Toast.makeText(ctx, ctx.getString(R.string.toast_reset_done), Toast.LENGTH_SHORT).show()
        }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MainMenuContent(
    langIconRes: Int,
    menuOrderIds: List<String>,
    onMenuOrderChange: (List<String>) -> Unit,
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
    onExport: () -> Unit,
    onImport: () -> Unit,
    onResetConfirmed: () -> Unit,
) {
    var dataMenuExpanded by remember { mutableStateOf(false) }
    val confirmReset = rememberSaveable { mutableStateOf(false) }
    var reorderMode by rememberSaveable { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    val allItems = listOf(
        MenuEntry("calendar", R.drawable.ic_menu_calendar, R.string.calendar, onCalendar),
        MenuEntry("new_task", R.drawable.ic_menu_new_task, R.string.create_task, onNewTask),
        MenuEntry("someday", R.drawable.ic_menu_someday, R.string.someday_title, onSomeday),
        MenuEntry("recurring", R.drawable.ic_menu_recurring, R.string.recurring_tasks_title, onRecurring),
        MenuEntry("anthropometry", R.drawable.ic_menu_anthropometry, R.string.anthropometry_title, onAnthropometry),
        MenuEntry("calorimeter", R.drawable.ic_menu_calorimeter, R.string.calorimeter_title, onCalorimeter),
        MenuEntry("running", R.drawable.ic_menu_running, R.string.running_title, onRunning),
        MenuEntry("counters", R.drawable.ic_menu_counters, R.string.counters_title, onCounters),
        MenuEntry("reading", R.drawable.ic_menu_reading, R.string.menu_reading, onMediaLibrary),    )

    fun applyOrder(order: List<String>): List<MenuEntry> {
        if (order.isEmpty()) return allItems
        val byId = allItems.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }
        val missing = allItems.filterNot { it.id in order.toSet() }
        return ordered + missing
    }

    val orderedFromState = remember(menuOrderIds) { applyOrder(menuOrderIds) }
    var items by remember { mutableStateOf(orderedFromState) }

    LaunchedEffect(menuOrderIds, reorderMode) {
        if (!reorderMode) items = applyOrder(menuOrderIds)
    }

    val draggingIndex = remember { mutableIntStateOf(-1) }
    val draggingOffsetY = remember { mutableFloatStateOf(0f) }

    fun persistOrderNow() {
        onMenuOrderChange(items.map { it.id })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (reorderMode) {
                        IconButton(onClick = {
                            reorderMode = false
                            draggingIndex.intValue = -1
                            draggingOffsetY.floatValue = 0f
                            persistOrderNow()
                        }) {
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

                        IconButton(onClick = { dataMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.data_menu))
                        }

                        DropdownMenu(
                            expanded = dataMenuExpanded,
                            onDismissRequest = { dataMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_backup_json)) },
                                onClick = {
                                    dataMenuExpanded = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_backup_json)) },
                                onClick = {
                                    dataMenuExpanded = false
                                    onImport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reset_data_menu)) },
                                onClick = {
                                    dataMenuExpanded = false
                                    confirmReset.value = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { _, it -> it.id }
            ) { index, item ->
                val isDragging = index == draggingIndex.intValue

                Modifier
                    .fillMaxWidth()
                val base = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = IntOffset.VisibilityThreshold
                        )
                )
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) draggingOffsetY.floatValue else 0f }

                val tapAndHold =
                    if (!reorderMode) {
                        Modifier.pointerInput(item.id, false) {
                            detectTapGestures(
                                onTap = {
                                    if (!reorderMode) item.onClick()
                                },
                                onPress = {
                                    val released = withTimeoutOrNull(MENU_REORDER_HOLD_MS) { tryAwaitRelease() }
                                    if (released == null) {
                                        reorderMode = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            )
                        }
                    } else Modifier

                val dragModifier =
                    if (reorderMode) {
                        Modifier.pointerInput(items, draggingIndex) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingIndex.intValue = index
                                    draggingOffsetY.floatValue = 0f
                                },
                                onDragCancel = {
                                    draggingIndex.intValue = -1
                                    draggingOffsetY.floatValue = 0f
                                    persistOrderNow()
                                },
                                onDragEnd = {
                                    draggingIndex.intValue = -1
                                    draggingOffsetY.floatValue = 0f
                                    persistOrderNow()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    draggingOffsetY.floatValue += dragAmount.y

                                    val layout = listState.layoutInfo
                                    val visible = layout.visibleItemsInfo
                                    val draggedInfo = visible.firstOrNull { it.index == draggingIndex.intValue }
                                        ?: return@detectDragGestures

                                    val draggedMiddle = draggedInfo.offset + draggingOffsetY.floatValue + draggedInfo.size / 2f
                                    val target = visible.firstOrNull { info ->
                                        info.index != draggingIndex.intValue &&
                                                draggedMiddle >= info.offset &&
                                                draggedMiddle <= info.offset + info.size
                                    } ?: return@detectDragGestures

                                    val from = draggingIndex.intValue
                                    val to = target.index
                                    if (from == to) return@detectDragGestures

                                    items = items.toMutableList().also { list ->
                                        val moved = list.removeAt(from)
                                        list.add(to, moved)
                                    }

                                    draggingIndex.intValue = to
                                    draggingOffsetY.floatValue += (draggedInfo.offset - target.offset).toFloat()
                                }
                            )
                        }
                    } else Modifier

                MenuRowCard(
                    iconRes = item.iconRes,
                    title = stringResource(item.titleRes),
                    reorderMode = reorderMode,
                    isDragging = isDragging,
                    modifier = base.then(tapAndHold).then(dragModifier)
                )
            }
        }
    }

    if (confirmReset.value) {
        AlertDialog(
            onDismissRequest = { confirmReset.value = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onResetConfirmed()
                    confirmReset.value = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun MenuRowCard(
    iconRes: Int,
    title: String,
    reorderMode: Boolean,
    isDragging: Boolean,
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

            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(MENU_ICON_SIZE_DP.dp)
            )
        }
    }
}