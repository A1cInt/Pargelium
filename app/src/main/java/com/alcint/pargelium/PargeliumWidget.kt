package com.alcint.pargelium

import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File

class PargeliumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PargeliumWidget()
}

class PargeliumWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val title = PrefsManager.getWidgetTitle()
            val artist = PrefsManager.getWidgetArtist()
            val isPlaying = PrefsManager.getWidgetIsPlaying()

            val coverFile = File(context.cacheDir, "widget_cover.png")
            val bitmap = if (coverFile.exists()) BitmapFactory.decodeFile(coverFile.absolutePath) else null

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_bg))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bitmap != null) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = null,
                            modifier = GlanceModifier.size(56.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(12.dp))
                    }

                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = title,
                            style = TextStyle(
                                color = androidx.glance.unit.ColorProvider(Color.White),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = artist,
                            style = TextStyle(
                                color = androidx.glance.unit.ColorProvider(Color.White.copy(alpha = 0.7f)),
                                fontSize = 14.sp
                            ),
                            maxLines = 1
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_skip_previous),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(androidx.glance.unit.ColorProvider(Color.White)),
                            modifier = GlanceModifier.size(32.dp).clickable(actionRunCallback<WidgetPrevAction>())
                        )
                        Spacer(modifier = GlanceModifier.width(12.dp))
                        Image(
                            provider = ImageProvider(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(androidx.glance.unit.ColorProvider(Color.White)),
                            modifier = GlanceModifier.size(38.dp).clickable(actionRunCallback<WidgetPlayAction>())
                        )
                        Spacer(modifier = GlanceModifier.width(12.dp))
                        Image(
                            provider = ImageProvider(R.drawable.ic_skip_next),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(androidx.glance.unit.ColorProvider(Color.White)),
                            modifier = GlanceModifier.size(32.dp).clickable(actionRunCallback<WidgetNextAction>())
                        )
                    }
                }
            }
        }
    }
}

class WidgetPlayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        sendCommand(context, "PLAY_PAUSE")
    }
}

class WidgetNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        sendCommand(context, "NEXT")
    }
}

class WidgetPrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        sendCommand(context, "PREV")
    }
}

private fun sendCommand(context: Context, command: String) {
    Handler(Looper.getMainLooper()).post {
        val appContext = context.applicationContext
        val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                when (command) {
                    "PLAY_PAUSE" -> if (controller.isPlaying) controller.pause() else controller.play()
                    "NEXT" -> controller.seekToNext()
                    "PREV" -> controller.seekToPrevious()
                }
                controller.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, { it.run() })
    }
}