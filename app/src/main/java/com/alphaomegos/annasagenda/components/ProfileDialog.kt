package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.ProfileSex
import com.alphaomegos.annasagenda.support.PROFILE_BIRTH_YEARS
import com.alphaomegos.annasagenda.support.STEPS_HEIGHT_RANGE_CM

/**
 * The profile (28.10): sex, height and year of birth — one set for the whole
 * app, opened from Settings and from anthropometry's ⚙ alike. The height is
 * the one steps already asked for. Empty fields are left unset.
 */
@Composable
internal fun ProfileDialog(
    sex: ProfileSex?,
    heightCm: Int?,
    birthYear: Int?,
    onSave: (ProfileSex?, Int?, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickedSex by rememberSaveable { mutableStateOf(sex) }
    var height by rememberSaveable { mutableStateOf(heightCm?.toString() ?: "") }
    var year by rememberSaveable { mutableStateOf(birthYear?.toString() ?: "") }
    val heightValue = height.toIntOrNull()
    val yearValue = year.toIntOrNull()
    val heightOk = height.isEmpty() || (heightValue != null && heightValue in STEPS_HEIGHT_RANGE_CM)
    val yearOk = year.isEmpty() || (yearValue != null && yearValue in PROFILE_BIRTH_YEARS)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileSex.entries.forEach { s ->
                        FilterChip(
                            selected = pickedSex == s,
                            onClick = { pickedSex = if (pickedSex == s) null else s },
                            label = { Text(stringResource(if (s == ProfileSex.FEMALE) R.string.profile_female else R.string.profile_male)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.steps_height_label)) },
                    singleLine = true,
                    isError = !heightOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.profile_birth_year)) },
                    singleLine = true,
                    isError = !yearOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = heightOk && yearOk, onClick = { onSave(pickedSex, heightValue, yearValue) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The profile's summary: «Female · 168 cm · 1990», what is set of it; null when nothing is (Settings, the calorimeter). */
@Composable
internal fun profileSummary(sex: ProfileSex?, heightCm: Int?, birthYear: Int?): String? {
    val parts = listOfNotNull(
        sex?.let { stringResource(if (it == ProfileSex.FEMALE) R.string.profile_female else R.string.profile_male) },
        heightCm?.let { stringResource(R.string.profile_height_cm, it) },
        birthYear?.toString(),
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
