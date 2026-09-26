package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.TaskSuggestion

/**
 * The few things the user has written before that look like what they are
 * writing now.
 *
 * Shows nothing at all when there is nothing to show — not an empty box, not a
 * heading with a gap under it. This sits directly under the field being typed
 * into, so anything it reserves pushes the rest of the form around on every
 * keystroke.
 */
@Composable
internal fun TaskSuggestionList(
    suggestions: List<TaskSuggestion>,
    onPick: (TaskSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.task_suggestions_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        suggestions.forEach { suggestion ->
            Surface(
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(suggestion) },
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = suggestion.description,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // The number of parts is the reason to tap this rather
                    // than finish typing, so it is worth a line of its own.
                    if (suggestion.subtaskDescriptions.isNotEmpty()) {
                        Text(
                            text = context.resources.getQuantityString(
                                R.plurals.task_suggestion_parts,
                                suggestion.subtaskDescriptions.size,
                                suggestion.subtaskDescriptions.size,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
