@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.alcint.pargelium.service

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.alcint.pargelium.CustomAudioProcessor
import com.alcint.pargelium.SaavnApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.ArrayList

data class PlayerComponents(val player: ExoPlayer, val mediaSourceFactory: DefaultMediaSourceFactory)

object PlayerFactory {
    private val SAAVN_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    )

    fun create(context: Context, customProcessor: CustomAudioProcessor): PlayerComponents {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(50000, 50000, 2500, 5000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>
            ) {
                val myAudioSink = DefaultAudioSink.Builder()
                    .setAudioProcessors(arrayOf(customProcessor))
                    .setEnableFloatOutput(false)
                    .build()

                val renderer = MediaCodecAudioRenderer(
                    context, mediaCodecSelector, enableDecoderFallback,
                    eventHandler, eventListener, myAudioSink
                )
                out.add(renderer)
            }
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val baseDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(baseDataSourceFactory) { dataSpec ->
            val uri = dataSpec.uri

            if (uri.scheme == "saavn") {
                val trackId = uri.schemeSpecificPart.toLongOrNull() ?: return@Factory dataSpec
                try {
                    val streamUri = runBlocking(Dispatchers.IO) { SaavnApi.getStreamUri(trackId) }
                    return@Factory dataSpec.buildUpon()
                        .setUri(streamUri)
                        .setHttpRequestHeaders(SAAVN_HEADERS)
                        .build()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            dataSpec
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(resolvingDataSourceFactory)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        return PlayerComponents(player, mediaSourceFactory)
    }
}