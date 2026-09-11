package com.twojstar.llmbench.data.streambench

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.twojstar.llmbench.MainActivity
import okhttp3.OkHttpClient

/** Private Media3 session for explicit Streambench playback started from LlmBench UI. */
@OptIn(UnstableApi::class)
class StreambenchPlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val requestPolicy = StreambenchRemoteRequestInterceptor()
        val streamClient = OkHttpClient.Builder()
            .dns(StreambenchPublicDns())
            .addInterceptor(requestPolicy)
            .addNetworkInterceptor(requestPolicy)
            .followRedirects(true)
            .followSslRedirects(false)
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(streamClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val createdPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = createdPlayer

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, createdPlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY) {
            playFromIntent(intent)
        }
        return result
    }

    private fun playFromIntent(intent: Intent) {
        val activePlayer = player ?: return
        val rawUrl = intent.getStringExtra(EXTRA_URL) ?: return
        val url = StreambenchM3uParser.validateRemotePlaybackUrl(rawUrl) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val group = intent.getStringExtra(EXTRA_GROUP).orEmpty()

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title.ifBlank { DEFAULT_TITLE })
        if (group.isNotBlank()) {
            metadataBuilder.setArtist(group)
        }

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadataBuilder.build())
            .build()
        activePlayer.setMediaItem(mediaItem)
        activePlayer.prepare()
        activePlayer.play()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PLAY = "com.twojstar.llmbench.streambench.PLAY"
        private const val EXTRA_URL = "stream_url"
        private const val EXTRA_TITLE = "stream_title"
        private const val EXTRA_GROUP = "stream_group"
        private const val DEFAULT_TITLE = "Streambench"

        fun play(context: Context, request: StreambenchPlaybackRequest) {
            context.startService(
                Intent(context, StreambenchPlaybackService::class.java)
                    .setAction(ACTION_PLAY)
                    .putExtra(EXTRA_URL, request.url)
                    .putExtra(EXTRA_TITLE, request.title)
                    .putExtra(EXTRA_GROUP, request.group)
            )
        }
    }
}
