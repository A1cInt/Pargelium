@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.alcint.pargelium.player

import android.content.Context
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import java.io.File

private object VideoCacheHolder {
    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache? {
        return try {
            cache ?: synchronized(this) {
                cache ?: SimpleCache(
                    File(context.cacheDir, "canvas_video_cache"),
                    LeastRecentlyUsedCacheEvictor(80L * 1024L * 1024L),
                    StandaloneDatabaseProvider(context)
                ).also { cache = it }
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun VideoBackground(
    videoUri: Uri,
    isPlaying: Boolean = true
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLifecycleResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var isFirstFrameRendered by remember(videoUri) { mutableStateOf(false) }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 1f else 0f,
        animationSpec = tween(350),
        label = "videoFadeIn"
    )

    val isLocalFile = remember(videoUri) {
        videoUri.scheme == "file" || videoUri.scheme == "content"
    }

    val exoPlayer = remember(appContext, isLocalFile) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(10000)

        val upstreamFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
        val simpleCache = if (!isLocalFile) VideoCacheHolder.get(appContext) else null
        val dataSourceFactory = if (simpleCache != null) {
            CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstreamFactory
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 4000, 500, 1000)
            .build()

        ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                // Ограничиваем HLS-потоки Apple Music до 720p / 30 FPS / 1.8 Мбит/с для устранения нагрева декодера
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(720, 1280)
                    .setMaxVideoFrameRate(30)
                    .setMaxVideoBitrate(1_800_000)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
    }

    LaunchedEffect(videoUri) {
        isFirstFrameRendered = false
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        val shouldPlay = isPlaying && isLifecycleResumed
        exoPlayer.playWhenReady = shouldPlay
        if (shouldPlay) exoPlayer.play()
    }

    LaunchedEffect(isPlaying, isLifecycleResumed) {
        val shouldPlay = isPlaying && isLifecycleResumed
        exoPlayer.playWhenReady = shouldPlay
        if (shouldPlay) {
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            isLifecycleResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.clearVideoSurface()
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            val aspectRatioFrame = AspectRatioFrameLayout(ctx).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }

            val textureView = TextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                // isOpaque = true отключает дорогое попиксельное альфа-смешивание 1080x2400 на GPU
                isOpaque = true
            }

            aspectRatioFrame.addView(textureView)
            exoPlayer.setVideoTextureView(textureView)

            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        val ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                        aspectRatioFrame.setAspectRatio(ratio)
                        isFirstFrameRendered = true
                    }
                }

                override fun onRenderedFirstFrame() {
                    isFirstFrameRendered = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && exoPlayer.videoSize.width > 0) {
                        isFirstFrameRendered = true
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    isFirstFrameRendered = false
                }
            }
            aspectRatioFrame.tag = listener
            exoPlayer.addListener(listener)
            aspectRatioFrame
        },
        onRelease = { view ->
            (view.tag as? Player.Listener)?.let { exoPlayer.removeListener(it) }
            exoPlayer.clearVideoSurface()
        },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { alpha = videoAlpha }
    )
}