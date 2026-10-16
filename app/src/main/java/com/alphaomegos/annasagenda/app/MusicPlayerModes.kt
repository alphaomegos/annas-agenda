package com.alphaomegos.annasagenda.app

import androidx.media3.common.Player
import com.alphaomegos.annasagenda.model.MusicRepeat

/** The player's own word for how it goes on, as this app keeps it (16.10). */
internal fun musicRepeatOf(repeatMode: Int): MusicRepeat = when (repeatMode) {
    Player.REPEAT_MODE_OFF -> MusicRepeat.OFF
    Player.REPEAT_MODE_ONE -> MusicRepeat.ONE
    else -> MusicRepeat.ALL
}

/** How the player is told to go on. */
internal fun musicPlayerRepeatMode(repeat: MusicRepeat): Int = when (repeat) {
    MusicRepeat.OFF -> Player.REPEAT_MODE_OFF
    MusicRepeat.ONE -> Player.REPEAT_MODE_ONE
    MusicRepeat.ALL -> Player.REPEAT_MODE_ALL
}
