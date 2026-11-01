@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.alcint.pargelium.player

import android.content.ContentUris
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.SaavnApi
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop

@Composable
fun PlayerBackground(
    playerBackdrop: LayerBackdrop, // Type depends on miuix blur implementation
    backgroundState: String,
    blurRadius: Dp,
    isVideo: Boolean,
    canvasUri: Uri?,
    showLyrics: Boolean,
    track: AudioTrack,
    dynamicSeedColor: Color,
    isPlaying: Boolean,
    imageLoader: ImageLoader,
    appContext: android.content.Context
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(playerBackdrop)
    ) {
        val bgModifier = if (blurRadius > 0.dp && !isVideo) {
            Modifier.fillMaxSize().blur(blurRadius)
        } else {
            Modifier.fillMaxSize()
        }

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
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
                                }
                            }
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(appContext).data(canvasUri).build(),
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = bgModifier
                            )
                        }
                    }
                }
                "cover" -> {
                    val saavnTrack = SaavnApi.trackCache[track.id]
                    val artModel: Any? = when {
                        saavnTrack != null && saavnTrack.coverUrl.isNotEmpty() -> saavnTrack.coverUrl
                        track.albumId != 0L -> ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), track.albumId)
                        else -> null
                    }
                    if (artModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(appContext).data(artModel).build(),
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = bgModifier
                        )
                    } else {
                        AuroraBackground(seedColor = dynamicSeedColor, isPlaying = isPlaying, modifier = bgModifier)
                    }
                }
                "aurora" -> {
                    AuroraBackground(seedColor = dynamicSeedColor, isPlaying = isPlaying, modifier = bgModifier)
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.4f),
                    0.15f to Color.Transparent,
                    0.6f to Color.Black.copy(alpha = 0.4f),
                    1.0f to Color.Black.copy(alpha = 0.8f)
                )
            )
        )
    }
}