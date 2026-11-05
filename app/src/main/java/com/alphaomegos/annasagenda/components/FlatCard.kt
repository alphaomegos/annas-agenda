package com.alphaomegos.annasagenda.components

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** The new look's flat card (05.11): the surfaces' colour… */
@Composable
internal fun flatCardColors(): CardColors =
    CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

/** …and no shadow — at rest, pressed, focused, hovered, dragged or disabled. */
@Composable
internal fun flatCardElevation(): CardElevation =
    CardDefaults.elevatedCardElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
