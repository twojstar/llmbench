package com.twojstar.llmbench.ui.screens

import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.twojstar.llmbench.data.streambench.StreambenchPlaybackService
import com.twojstar.llmbench.data.streambench.StreambenchPlaybackState

@Composable
internal fun StreambenchMiniPlayer(
    applyNavigationBarInset: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playback by StreambenchPlaybackState.state.collectAsStateWithLifecycle()
    if (!playback.active) return

    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(
            context,
            ComponentName(context, StreambenchPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        var disposed = false
        future.addListener(
            {
                if (!disposed) {
                    controller = runCatching { future.get() }.getOrNull()
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            disposed = true
            controller = null
            MediaController.releaseFuture(future)
        }
    }

    val surfaceModifier = if (applyNavigationBarInset) {
        modifier.windowInsetsPadding(WindowInsets.navigationBars)
    } else {
        modifier
    }

    Surface(
        tonalElevation = 3.dp,
        modifier = surfaceModifier
            .fillMaxWidth()
            .testTag("streambench_mini_player")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null)
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    playback.title.ifBlank { "Streambench" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                playback.group.takeIf(String::isNotBlank)?.let { group ->
                    Text(
                        group,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = {
                    controller?.let { activeController ->
                        if (playback.isPlaying) activeController.pause() else activeController.play()
                    }
                },
                enabled = controller != null,
                modifier = Modifier.testTag("streambench_mini_player_toggle")
            ) {
                Icon(
                    if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playback.isPlaying) "Pause Streambench" else "Resume Streambench"
                )
            }
            IconButton(
                onClick = { StreambenchPlaybackService.stop(context) },
                modifier = Modifier.testTag("streambench_mini_player_stop")
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop Streambench")
            }
        }
    }
}
