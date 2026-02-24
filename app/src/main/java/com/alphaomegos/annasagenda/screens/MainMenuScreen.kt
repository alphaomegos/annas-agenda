package com.alphaomegos.annasagenda.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppViewModel
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.components.MenuTile
import com.alphaomegos.annasagenda.util.appLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable


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

    MainMenuContent(
        langIconRes = langIconRes,
        onLanguage = onLanguage,
        onCalendar = onCalendar,
        onNewTask = onNewTask,
        onSomeday = onSomeday,
        onRecurring = onRecurring,
        onAnthropometry = onAnthropometry,
        onCalorimeter = onCalorimeter,
        onRunning = onRunning,
        onExport = { exportLauncher.launch("annasagenda-backup.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
        onResetConfirmed = {
            vm.resetAllData()
            Toast.makeText(ctx, ctx.getString(R.string.toast_reset_done), Toast.LENGTH_SHORT).show()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainMenuContent(
    langIconRes: Int,
    onLanguage: () -> Unit,
    onCalendar: () -> Unit,
    onNewTask: () -> Unit,
    onSomeday: () -> Unit,
    onRecurring: () -> Unit,
    onAnthropometry: () -> Unit,
    onCalorimeter: () -> Unit,
    onRunning: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onResetConfirmed: () -> Unit,
) {
    var dataMenuExpanded by remember { mutableStateOf(false) }
    val confirmReset = rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
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
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuTile(
                    iconRes = R.drawable.ic_menu_calendar,
                    title = stringResource(R.string.calendar),
                    onClick = onCalendar,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_menu_new_task,
                    title = stringResource(R.string.create_task),
                    onClick = onNewTask,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuTile(
                    iconRes = R.drawable.ic_menu_someday,
                    title = stringResource(R.string.someday_title),
                    onClick = onSomeday,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_menu_recurring,
                    title = stringResource(R.string.recurring_tasks_title),
                    onClick = onRecurring,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuTile(
                    iconRes = R.drawable.ic_menu_anthropometry,
                    title = stringResource(R.string.anthropometry_title),
                    onClick = onAnthropometry,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_menu_calorimeter,
                    title = stringResource(R.string.calorimeter_title),
                    onClick = onCalorimeter,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuTile(
                    iconRes = R.drawable.ic_menu_running,
                    title = stringResource(R.string.running_title),
                    onClick = onRunning,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_menu_soon,
                    title = stringResource(R.string.coming_soon),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f).fillMaxHeight()
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
