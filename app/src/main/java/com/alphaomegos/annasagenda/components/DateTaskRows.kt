package com.alphaomegos.annasagenda.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.Subtask
import com.alphaomegos.annasagenda.Task

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

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
                .clickable { onCycleColor() }
        ) {
            val deco = if (task.isDone) TextDecoration.LineThrough else null
            Text(
                text = task.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit() },
                style = MaterialTheme.typography.bodyLarge.copy(textDecoration = deco)
            )
        }

        TinyIconButton(
            onClick = onMoveUp,
            icon = Icons.Default.KeyboardArrowUp,
            cd = "Move task up"
        )
        TinyIconButton(
            onClick = onMoveDown,
            icon = Icons.Default.KeyboardArrowDown,
            cd = "Move task down"
        )
        TinyIconButton(
            onClick = onMove,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            cd = "Move task"
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

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
                .clickable { onCycleColor() }
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
            cd = "Move subtask up"
        )
        TinyIconButton(
            onClick = onMoveDown,
            icon = Icons.Default.KeyboardArrowDown,
            cd = "Move subtask down"
        )
        TinyIconButton(
            onClick = onMove,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            cd = "Move subtask"
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