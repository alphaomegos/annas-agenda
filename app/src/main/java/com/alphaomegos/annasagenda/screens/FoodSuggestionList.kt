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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.FoodSuggestion
import com.alphaomegos.annasagenda.R

/**
 * The few meals already eaten that look like what is being typed.
 *
 * The second line is the whole reason to tap rather than keep typing: the
 * portion last eaten and what it was worth. A suggestion without one is still
 * offered — the name and the calories are worth having on their own — it just
 * cannot be re-priced afterwards.
 */
@Composable
internal fun FoodSuggestionList(
    suggestions: List<FoodSuggestion>,
    onPick: (FoodSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.calorimeter_suggestions_title),
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
                        text = suggestion.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (suggestion.amount != null && suggestion.unit != null) {
                            stringResource(
                                R.string.calorimeter_suggestion_portion,
                                suggestion.amount,
                                suggestion.unit,
                                suggestion.kcal,
                            )
                        } else {
                            stringResource(
                                R.string.calorimeter_suggestion_no_portion,
                                suggestion.kcal,
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
