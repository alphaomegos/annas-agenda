package com.alphaomegos.annasagenda.components

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.Subtask
import com.alphaomegos.annasagenda.model.Task
import com.alphaomegos.annasagenda.support.formatTaskTime

@Composable
internal fun DateTaskRow(
    task: Task,
    subtasks: List<Subtask>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleDone: () -> Unit,
    onCycleColor: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    // A task with a time is placed by its time, and the first untimed task
    // sits under the timed ones; their arrows are grey (canMoveTask).
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
) {
    val markerShape = RoundedCornerShape(10.dp)
    val markerAlpha = 0.18f

    val taskBg = task.colorArgb
        ?.toInt()
        ?.let { Color(it).copy(alpha = markerAlpha) }
        ?: Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(taskBg, markerShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val totalSubs = subtasks.size
        val doneSubs = if (totalSubs == 0) 0 else subtasks.count { it.isDone }

        TextButton(
            onClick = onToggleExpand,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            modifier = Modifier.width(26.dp)
        ) {
            Text(if (isExpanded) "▼" else "▶")
        }

        if (totalSubs == 0 || task.isDone) {
            Checkbox(
                checked = task.isDone,
                onCheckedChange = { onToggleDone() }
            )
        } else {
            SubtaskProgressToggle(
                done = doneSubs,
                total = totalSubs,
                onClick = onToggleDone
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // The description opens the editor. The colour is cycled by the narrow
        // strip at the end of the row — see the Box after the buttons below.
        //
        // This Box used to carry a colour-cycling click of its own, and it
        // could never fire: the Text inside fills it completely and takes
        // every tap. Nothing changes by removing it except that the row stops
        // claiming to handle a gesture it does not.
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        ) {
            val deco = if (task.isDone) TextDecoration.LineThrough else null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val time = task.time
                if (time != null) {
                    Text(
                        text = formatTaskTime(time),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Text(
                    text = task.description,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(textDecoration = deco)
                )
            }
        }

        Box(modifier = Modifier.alpha(if (canMoveUp) 1f else 0.35f)) {
            TinyIconButton(
                onClick = { if (canMoveUp) onMoveUp() },
                icon = Icons.Default.KeyboardArrowUp,
                cd = stringResource(R.string.move_task_up)
            )
        }
        Box(modifier = Modifier.alpha(if (canMoveDown) 1f else 0.35f)) {
            TinyIconButton(
                onClick = { if (canMoveDown) onMoveDown() },
                icon = Icons.Default.KeyboardArrowDown,
                cd = stringResource(R.string.move_task_down)
            )
        }
        TinyIconButton(
            onClick = onMove,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            cd = stringResource(R.string.move_task)
        )
        TinyIconButton(
            onClick = onCopy,
            icon = Icons.Default.ContentCopy,
            cd = stringResource(R.string.copy_task)
        )

        Box(
            modifier = Modifier
                .width(22.dp)
                .height(36.dp)
                .clickable { onCycleColor() }
        )
    }
}

@Composable
internal fun DateSubtaskRow(
    subtask: Subtask,
    onToggleDone: () -> Unit,
    onCycleColor: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
) {
    val markerShape = RoundedCornerShape(10.dp)
    val markerAlpha = 0.18f

    val subBg = subtask.colorArgb
        ?.toInt()
        ?.let { Color(it).copy(alpha = markerAlpha) }
        ?: Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp)
            .background(subBg, markerShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = subtask.isDone,
            onCheckedChange = { onToggleDone() }
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Same as the task row above: the tap belongs to the Text, and the
        // colour lives on the strip at the end.
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        ) {
            val deco = if (subtask.isDone) TextDecoration.LineThrough else null
            Text(
                text = subtask.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit() },
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = deco)
            )
        }

        TinyIconButton(
            onClick = onMoveUp,
            icon = Icons.Default.KeyboardArrowUp,
            cd = stringResource(R.string.move_subtask_up)
        )
        TinyIconButton(
            onClick = onMoveDown,
            icon = Icons.Default.KeyboardArrowDown,
            cd = stringResource(R.string.move_subtask_down)
        )
        TinyIconButton(
            onClick = onMove,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            cd = stringResource(R.string.move_subtask)
        )
        TinyIconButton(
            onClick = onCopy,
            icon = Icons.Default.ContentCopy,
            cd = stringResource(R.string.copy_subtask)
        )

        Box(
            modifier = Modifier
                .width(22.dp)
                .height(36.dp)
                .clickable { onCycleColor() }
        )
    }
}

@Composable
private fun SubtaskProgressToggle(
    done: Int,
    total: Int,
    onClick: () -> Unit,
) {
    val progress = if (total <= 0) 0f else done.toFloat() / total.toFloat()

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.matchParentSize(),
            color = ProgressIndicatorDefaults.circularColor,
            strokeWidth = 3.dp,
            trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
            strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
        )
        Text(
            text = "$done/$total",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
/** One task's row, in the day screen's new look (27.10) or as it was. Same arguments either way. */
@Composable
internal fun TaskRowCall(
    newLook: Boolean,
    task: Task,
    subtasks: List<Subtask>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleDone: () -> Unit,
    onCycleColor: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
) {
    if (newLook) {
        NewDateTaskRow(task, subtasks, isExpanded, onToggleExpand, onToggleDone, onCycleColor, onEdit, onMoveUp, onMoveDown, onMove, onCopy, canMoveUp, canMoveDown)
    } else {
        DateTaskRow(task, subtasks, isExpanded, onToggleExpand, onToggleDone, onCycleColor, onEdit, onMoveUp, onMoveDown, onMove, onCopy, canMoveUp, canMoveDown)
    }
}

/**
 * The day's rows on one surface in the new look (27.10, the brief: rows
 * grounded in the page, not cards floating in space) — a soft container with
 * rounded corners and hairlines between rows; as they were otherwise.
 */
@Composable
internal fun TaskListSurface(newLook: Boolean, content: @Composable () -> Unit) {
    if (!newLook) {
        content()
        return
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

/**
 * A task's row in the new look (27.10, Eduard's brief): the triangle that
 * opens the subtasks, then the tick; the time and the title, wrapping as
 * words do; up and down as plain icons, the rarer moving and copying under
 * ⋮; and at the very edge a thin strip in the colour the next tap gives —
 * the next one, not the current, which the row's tint already shows. Done
 * tasks are ticked, faded and struck through.
 */
@Composable
internal fun NewDateTaskRow(
    task: Task,
    subtasks: List<Subtask>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleDone: () -> Unit,
    onCycleColor: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
) {
    val tint = task.colorArgb?.toInt()?.let { Color(it).copy(alpha = 0.22f) } ?: Color.Transparent
    val next = nextPaletteColor(task.colorArgb)?.toInt()?.let { Color(it) } ?: MaterialTheme.colorScheme.outlineVariant
    var menuOpen by rememberSaveable(task.id) { mutableStateOf(false) }
    val done = task.isDone
    val textColor = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(tint),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onToggleExpand,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.width(32.dp),
        ) {
            Text(if (isExpanded) "▼" else "▶", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val totalSubs = subtasks.size
        if (totalSubs == 0 || done) {
            Checkbox(checked = done, onCheckedChange = { onToggleDone() })
        } else {
            SubtaskProgressToggle(done = subtasks.count { it.isDone }, total = totalSubs, onClick = onToggleDone)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onEdit() }
                .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            task.time?.let { time ->
                Text(
                    text = formatTaskTime(time),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (done) textColor else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
                ),
                color = textColor,
                modifier = Modifier.weight(1f),
            )
        }
        IconButton(onClick = { if (canMoveUp) onMoveUp() }, enabled = canMoveUp, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_task_up), modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = { if (canMoveDown) onMoveDown() }, enabled = canMoveDown, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.move_task_down), modifier = Modifier.size(22.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.task_more_actions), modifier = Modifier.size(22.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_task)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    onClick = { menuOpen = false; onMove() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_task)) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = { menuOpen = false; onCopy() },
                )
            }
        }
        // The strip: 5 dp of colour, in a 16 dp target at the row's full height.
        val stripName = stringResource(R.string.task_next_colour)
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .clickable(onClickLabel = stripName) { onCycleColor() }
                .semantics { contentDescription = stripName },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(next))
        }
    }
}

/** One subtask's row, in the day screen's new look (27.10) or as it was. */
@Composable
internal fun SubtaskRowCall(
    newLook: Boolean,
    subtask: Subtask,
    onToggleDone: () -> Unit,
    onCycleColor: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
) {
    if (newLook) NewDateSubtaskRow(subtask, onToggleDone, onCycleColor, onEdit, onMoveUp, onMoveDown, onMove, onCopy)
    else DateSubtaskRow(subtask, onToggleDone, onCycleColor, onEdit, onMoveUp, onMoveDown, onMove, onCopy)
}

/**
 * A subtask in the new look (27.10): inside the same surface as its task,
 * set in under the task's tick, a size smaller — otherwise as a task's row:
 * the tick, the title, ↑ ↓, ⋮ for moving and copying, the next colour's strip.
 */
@Composable
internal fun NewDateSubtaskRow(
    subtask: Subtask,
    onToggleDone: () -> Unit,
    onCycleColor: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
) {
    val tint = subtask.colorArgb?.toInt()?.let { Color(it).copy(alpha = 0.16f) } ?: Color.Transparent
    val next = nextPaletteColor(subtask.colorArgb)?.toInt()?.let { Color(it) } ?: MaterialTheme.colorScheme.outlineVariant
    var menuOpen by rememberSaveable(subtask.id) { mutableStateOf(false) }
    val done = subtask.isDone
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(tint)
            .padding(start = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = { onToggleDone() })
        Text(
            text = subtask.description,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (done) TextDecoration.LineThrough else null,
                lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
            ),
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .clickable { onEdit() }
                .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        )
        IconButton(onClick = onMoveUp, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_task_up), modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onMoveDown, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.move_task_down), modifier = Modifier.size(20.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.task_more_actions), modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_task)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    onClick = { menuOpen = false; onMove() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_task)) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = { menuOpen = false; onCopy() },
                )
            }
        }
        val stripName = stringResource(R.string.task_next_colour)
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .clickable(onClickLabel = stripName) { onCycleColor() }
                .semantics { contentDescription = stripName },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(next))
        }
    }
}
