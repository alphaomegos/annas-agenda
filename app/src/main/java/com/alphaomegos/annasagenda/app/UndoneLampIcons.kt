package com.alphaomegos.annasagenda

import androidx.annotation.DrawableRes

/**
 * The picture for a lamp, in the theme the app is currently wearing.
 *
 * Two drawings per lamp, chosen here rather than by a `-night` resource
 * folder, and that is not a preference. A resource qualifier follows the
 * **phone's** night setting; this app has a theme setting of its own, which
 * the user can put at odds with the phone deliberately. Dark app on a light
 * phone would get the light lamps, which is exactly the shape of the bug 0088
 * fixed for the buttons that said "OK" in the phone's language.
 *
 * The light drawings are the originals. The dark ones exist because the grey
 * lamp in particular was unreadable: its glass is nearly white, so on a dark
 * screen the lamp the user had switched off was the brightest thing on it.
 */
@DrawableRes
internal fun undoneLampIconRes(lamp: UndoneLamp, dark: Boolean): Int = when (lamp) {
    UndoneLamp.GREEN ->
        if (dark) R.drawable.ic_undone_lamp_green_dark else R.drawable.ic_undone_lamp_green

    UndoneLamp.GRAY ->
        if (dark) R.drawable.ic_undone_lamp_gray_dark else R.drawable.ic_undone_lamp_gray

    UndoneLamp.RED ->
        if (dark) R.drawable.ic_undone_lamp_red_dark else R.drawable.ic_undone_lamp_red
}
