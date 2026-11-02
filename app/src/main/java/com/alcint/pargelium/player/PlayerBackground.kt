@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.alcint.pargelium.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.SaavnApi
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

private val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")

private val OVERLAY_GRADIENT_BRUSH = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.4f),
    0.15f to Color.Transparent,
    0.6f to Color.Black.copy(alpha = 0.4f),
    1.0f to Color.Black.copy(alpha = 0.8f)
)

private val LYRICS_DIM_COLOR = Color.Black.copy(alpha = 0.6f)

@Composable
fun PlayerBackground(
    playerBackdrop: LayerBackdrop,
    backgroundState: String,
    blurRadius: Dp,
    isVideo: Boolean,
    canvasUri: Uri?,
    showLyrics: Boolean,
    track: AudioTrack,
    dynamicSeedColor: Color,
    isPlaying: Boolean,
    imageLoader: ImageLoader,
    appContext: Context
) {
    val isBlurred = blurRadius > 0.dp && !isVideo
    val imageBlurModifier = remember(isBlurred, blurRadius) {
        if (isBlurred) {
            Modifier.fillMaxSize().blur(blurRadius)
        } else {
            Modifier.fillMaxSize()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(playerBackdrop)
    ) {
        Crossfade(
            targetState = backgroundState,
            animationSpec = tween(800),
            label = "bg_clear",
            modifier = Modifier.fillMaxSize()
        ) { state ->
            when (state) {
                "canvas" -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isVideo && canvasUri != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                VideoBackground(videoUri = canvasUri)
                                if (showLyrics) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(LYRICS_DIM_COLOR)
                                    )
                                }
                            }
                        } else {
                            val canvasRequest = remember(appContext, canvasUri, isBlurred) {
                                ImageRequest.Builder(appContext)
                                    .data(canvasUri)
                                    .apply {
                                        if (isBlurred) {
                                            size(512)
                                            precision(Precision.INEXACT)
                                        }
                                    }
                                    .crossfade(false)
                                    .build()
                            }
                            AsyncImage(
                                model = canvasRequest,
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = imageBlurModifier
                            )
                        }
                    }
                }
                "cover" -> {
                    val artModel = remember(track.id, track.albumId) {
                        val saavnTrack = SaavnApi.trackCache[track.id]
                        when {
                            saavnTrack != null && saavnTrack.coverUrl.isNotEmpty() -> saavnTrack.coverUrl
                            track.albumId != 0L -> ContentUris.withAppendedId(ALBUM_ART_BASE_URI, track.albumId)
                            else -> null
                        }
                    }
                    if (artModel != null) {
                        val coverRequest = remember(appContext, artModel, isBlurred) {
                            ImageRequest.Builder(appContext)
                                .data(artModel)
                                .apply {
                                    if (isBlurred) {
                                        size(512)
                                        precision(Precision.INEXACT)
                                    }
                                }
                                .crossfade(false)
                                .build()
                        }
                        AsyncImage(
                            model = coverRequest,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = imageBlurModifier
                        )
                    } else {
                        AuroraBackground(
                            seedColor = dynamicSeedColor,
                            isPlaying = isPlaying,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                "aurora" -> {
                    AuroraBackground(
                        seedColor = dynamicSeedColor,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OVERLAY_GRADIENT_BRUSH)
        )
    }
}