package com.alphaomegos.annasagenda.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.app.AppIcons
import com.alphaomegos.annasagenda.app.SidecarConnections

/**
 * The profile as a picture in a top bar (02.11, Eduard): the person's own picture from
 * oursidecar, as on the main screen, or a silhouette when there is none (not signed in,
 * or no picture set). A tap opens the profile — sex, height, year of birth.
 */
@Composable
internal fun ProfileAvatarButton(onClick: () -> Unit) {
    val context = LocalContext.current
    val sidecar = remember(context) { SidecarConnections.of(context) }
    val status by sidecar.status.collectAsStateWithLifecycle()
    val avatar = rememberSidecarAvatar(status)
    val name = stringResource(R.string.profile_title)
    IconButton(onClick = onClick) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(28.dp).clip(CircleShape),
            )
        } else {
            Icon(AppIcons.profile, contentDescription = name)
        }
    }
}
