package com.alcint.pargelium

import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcint.pargelium.liquid.InnerShadow
import com.alcint.pargelium.liquid.LiquidGlassIconButton
import com.alcint.pargelium.liquid.innerShadow
import com.alcint.pargelium.liquid.lens
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource

private val CapsuleHighlight = Highlight(
    width = 1.2.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.20f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.4f, -0.1f),
            color = Color.White,
            intensity = 1f
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.4f),
            color = Color.White,
            intensity = 0.35f
        ),
        dualPeak = true
    )
)

@Composable
fun FloatingActionRow(
    onRefresh: () -> Unit,
    onShuffleAll: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.padding(end = 8.dp)) {
            SmallActionBtn(Icons.Default.Shuffle, onClick = onShuffleAll)
        }

        Box(modifier = Modifier.padding(end = 16.dp)) {
            SmallActionBtn(Icons.Default.MoreVert) { showMenu = true }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Обновить медиатеку") },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = {
                        showMenu = false
                        onRefresh()
                    }
                )
            }
        }
    }
}

@Composable
fun SmallActionBtn(icon: ImageVector, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.size(40.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MiniPlayer(
    track: AudioTrack,
    isPlaying: Boolean,
    progress: Float,
    albumArt: Uri?,
    backdrop: Backdrop,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInDark = isSystemInDarkTheme()
    val capsuleShape = RoundedCornerShape(26.dp)
    val accentColor = MaterialTheme.colorScheme.primary

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "miniPlayerScale"
    )

    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.90f,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 300f),
        label = "coverScale"
    )

    val coverElevation by animateDpAsState(
        targetValue = if (isPlaying) 8.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 300f),
        label = "coverElevation"
    )

    val saavnTrack = SaavnApi.trackCache[track.id]
    val actualArt: Uri? = if (saavnTrack != null && saavnTrack.coverUrl.isNotEmpty()) {
        Uri.parse(saavnTrack.coverUrl)
    } else {
        albumArt
    }

    val rimBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isInDark) 0.35f else 0.55f),
            Color.White.copy(alpha = if (isInDark) 0.05f else 0.12f)
        )
    )

    val containerSurface = MaterialTheme.colorScheme.surfaceContainer.copy(
        alpha = if (isInDark) 0.40f else 0.55f
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(68.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 14.dp,
                shape = capsuleShape,
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(capsuleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { capsuleShape },
                effects = {
                    blur(20.dp.toPx(), 20.dp.toPx())
                    lens(
                        refractionHeight = 22.dp.toPx(),
                        refractionAmount = 18.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = 0.02f
                    )
                },
                highlight = { CapsuleHighlight },
                onDrawSurface = { drawRect(containerSurface) }
            )
            .innerShadow(capsuleShape) {
                InnerShadow.Default.copy(
                    radius = 8.dp,
                    color = (if (isInDark) Color.White else Color.Black).copy(alpha = 0.12f)
                )
            }
            .border(width = 1.dp, brush = rimBrush, shape = capsuleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .graphicsLayer {
                        scaleX = coverScale
                        scaleY = coverScale
                    }
                    .shadow(
                        elevation = coverElevation,
                        shape = RoundedCornerShape(14.dp),
                        spotColor = accentColor.copy(alpha = 0.45f)
                    )
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (actualArt != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(actualArt)
                            .size(120)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassIconButton(
                    onClick = onPlayPause,
                    backdrop = backdrop,
                    size = 40.dp
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                LiquidGlassIconButton(
                    onClick = onSkipNext,
                    backdrop = backdrop,
                    size = 40.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 3.dp)
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (isInDark) 0.12f else 0.20f))
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.7f),
                                    accentColor
                                )
                            )
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingPillIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    ContainedLoadingIndicator(
        modifier = modifier.size(56.dp),
        indicatorColor = color,
        containerColor = trackColor
    )
}