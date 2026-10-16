package com.alphaomegos.annasagenda.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.SidecarAvatarStores
import com.alphaomegos.annasagenda.app.SidecarStatus
import com.alphaomegos.annasagenda.support.SidecarSignInState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The person's own picture from the sidecar for [status] (16.10): from the
 * phone once fetched, small enough for an icon. Null while there is none,
 * nobody is in, or it is on its way.
 */
@Composable
internal fun rememberSidecarAvatar(status: SidecarStatus): ImageBitmap? {
    val context = LocalContext.current
    val avatarId = status.avatarId?.takeIf { status.account != null }
    val avatar by produceState<ImageBitmap?>(initialValue = null, avatarId) {
        value = avatarId?.let { id ->
            SidecarAvatarStores.of(context).bytes(id)?.let { bytes -> withContext(Dispatchers.Default) { sidecarAvatarBitmap(bytes) } }
        }
    }
    return avatar
}

/** [bytes] decoded no larger than needed for a picture [side] pixels across; null for what is no picture. */
internal fun sidecarAvatarBitmap(bytes: ByteArray, side: Int = 192): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= side && bounds.outHeight / (sample * 2) >= side) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/** oursidecar's own green (its logo). */
private val SidecarGreen = Color(0xFF607055)

/**
 * The oursidecar account as a picture (16.10, Eduard): nobody in — his cat
 * looking out of the sidecar's "O"; somebody in — their own picture from the
 * sidecar, round, or the cat with a green dot while there is none; the
 * session ended — the same picture with a red ring and a "!" on it.
 */
@Composable
internal fun SidecarAccountIcon(
    state: SidecarSignInState,
    avatar: ImageBitmap?,
    contentDescription: String,
    modifier: Modifier = Modifier.size(30.dp),
) {
    val red = MaterialTheme.colorScheme.error
    val face = if (state == SidecarSignInState.SIGNED_OUT) null else avatar
    val ring = if (state == SidecarSignInState.ATTENTION) Modifier.border(2.dp, red, CircleShape) else Modifier
    Box(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (face != null) {
            Image(
                face,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape).then(ring),
            )
        } else {
            Image(
                painterResource(R.drawable.ic_sidecar_signed_out),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().then(ring),
            )
        }
        when (state) {
            SidecarSignInState.ATTENTION -> Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(13.dp)
                    .background(red, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = MaterialTheme.colorScheme.onError, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            SidecarSignInState.SIGNED_IN -> if (face == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .background(SidecarGreen, CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            } else {
                Unit
            }
            SidecarSignInState.SIGNED_OUT -> Unit
        }
    }
}
