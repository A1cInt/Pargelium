@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.alcint.pargelium.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcint.pargelium.MorphingPillIndicator
import com.alcint.pargelium.R
import com.alcint.pargelium.liquid.LiquidGlassIconButton
import com.alcint.pargelium.liquid.LiquidGlassPlayPauseButton
import com.alcint.pargelium.liquid.LiquidGlassSwitch
import com.alcint.pargelium.material.ExpressiveIconButton
import com.alcint.pargelium.material.ExpressivePlayButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@Composable
fun LanguageStepContent(
    detectedLanguage: OnboardingLanguage,
    selectedLanguageTag: String,
    onSelectLanguage: (OnboardingLanguage) -> Unit,
    onConfirmDetectedAndContinue: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val motionScheme = MaterialTheme.motionScheme

    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            OnboardingCatalog.languages
        } else {
            val q = searchQuery.trim().lowercase()
            OnboardingCatalog.languages.filter {
                it.nativeName.lowercase().contains(q) ||
                        it.englishName.lowercase().contains(q) ||
                        it.tag.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(30.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = detectedLanguage.flagEmoji, fontSize = 32.sp)
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Translate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.onboarding_lang_detected_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    id = R.string.onboarding_lang_question_fmt,
                                    detectedLanguage.nativeName
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSelectLanguage(detectedLanguage)
                            onConfirmDetectedAndContinue()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                id = R.string.onboarding_lang_confirm_btn_fmt,
                                "${detectedLanguage.flagEmoji} ${detectedLanguage.nativeName}"
                            ),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(id = R.string.onboarding_lang_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filteredLanguages, key = { it.tag }) { lang ->
                val isSelected = selectedLanguageTag.equals(lang.tag, ignoreCase = true)
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.97f else 1f,
                    animationSpec = motionScheme.fastSpatialSpec(),
                    label = "langScale"
                )
                val cornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 26.dp else 18.dp,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "langCorner"
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "langBg"
                )

                Surface(
                    onClick = { onSelectLanguage(lang) },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(cornerRadius),
                    color = bgColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .border(
                            width = if (isSelected) 1.5.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(cornerRadius)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(text = lang.flagEmoji, fontSize = 26.sp)
                            Column {
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = "${lang.englishName} • ${lang.tag.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        if (isSelected) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StyleStepContent(
    selectedStyle: Int,
    onSelectStyle: (Int) -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val styleCards = listOf(
        Triple(
            0,
            stringResource(id = R.string.onboarding_style_liquid_title),
            stringResource(id = R.string.onboarding_style_liquid_desc)
        ),
        Triple(
            1,
            stringResource(id = R.string.onboarding_style_m3e_title),
            stringResource(id = R.string.onboarding_style_m3e_desc)
        ),
        Triple(
            2,
            stringResource(id = R.string.onboarding_style_classic_title),
            stringResource(id = R.string.onboarding_style_classic_desc)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(id = R.string.onboarding_style_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        styleCards.forEach { (styleId, title, desc) ->
            val isSelected = selectedStyle == styleId
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.96f else 1f,
                animationSpec = motionScheme.fastSpatialSpec(),
                label = "styleScale"
            )
            val cornerSize by animateDpAsState(
                targetValue = if (isSelected) 32.dp else 24.dp,
                animationSpec = motionScheme.defaultSpatialSpec(),
                label = "styleCorner"
            )

            var previewPlaying by remember { mutableStateOf(true) }
            var previewSwitch by remember { mutableStateOf(true) }
            val liquidPreviewBackdrop = rememberLayerBackdrop()
            val primaryColor = MaterialTheme.colorScheme.primary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary

            Surface(
                onClick = { onSelectStyle(styleId) },
                interactionSource = interactionSource,
                shape = RoundedCornerShape(cornerSize),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        },
                        shape = RoundedCornerShape(cornerSize)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectStyle(styleId) }
                        )
                    }

                    // Настоящее интерактивное превью компонентов каждого стиля
                    when (styleId) {
                        0 -> {
                            // 0: НАСТОЯЩИЕ компоненты из пакета com.alcint.pargelium.liquid поверх LayerBackdrop
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(92.dp)
                                    .clip(RoundedCornerShape(24.dp))
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .layerBackdrop(liquidPreviewBackdrop)
                                ) {
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF14111F),
                                                Color(0xFF231934),
                                                Color(0xFF0E1D2A)
                                            )
                                        )
                                    )
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(primaryColor.copy(alpha = 0.85f), Color.Transparent),
                                            center = Offset(size.width * 0.28f, size.height * 0.5f),
                                            radius = size.height * 0.8f
                                        ),
                                        center = Offset(size.width * 0.28f, size.height * 0.5f),
                                        radius = size.height * 0.8f
                                    )
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(tertiaryColor.copy(alpha = 0.8f), Color.Transparent),
                                            center = Offset(size.width * 0.72f, size.height * 0.5f),
                                            radius = size.height * 0.75f
                                        ),
                                        center = Offset(size.width * 0.72f, size.height * 0.5f),
                                        radius = size.height * 0.75f
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LiquidGlassIconButton(
                                        onClick = { onSelectStyle(0) },
                                        backdrop = liquidPreviewBackdrop,
                                        size = 48.dp,
                                        activeColor = Color.White
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    LiquidGlassPlayPauseButton(
                                        isPlaying = previewPlaying,
                                        onClick = {
                                            previewPlaying = !previewPlaying
                                            onSelectStyle(0)
                                        },
                                        accentColor = primaryColor,
                                        backdrop = liquidPreviewBackdrop,
                                        size = 64.dp
                                    )

                                    LiquidGlassIconButton(
                                        onClick = { onSelectStyle(0) },
                                        backdrop = liquidPreviewBackdrop,
                                        size = 48.dp,
                                        activeColor = Color.White
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    LiquidGlassSwitch(
                                        checked = previewSwitch,
                                        onCheckedChange = {
                                            previewSwitch = it
                                            onSelectStyle(0)
                                        }
                                    )
                                }
                            }
                        }

                        1 -> {
                            // 1: НАСТОЯЩИЕ компоненты Material 3 Expressive из com.alcint.pargelium.material
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(92.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ExpressiveIconButton(
                                        icon = Icons.Default.SkipPrevious,
                                        onClick = { onSelectStyle(1) },
                                        size = 48.dp,
                                        iconSize = 24.dp
                                    )

                                    ExpressivePlayButton(
                                        isPlaying = previewPlaying,
                                        onClick = {
                                            previewPlaying = !previewPlaying
                                            onSelectStyle(1)
                                        },
                                        size = 64.dp,
                                        iconSize = 32.dp
                                    )

                                    ExpressiveIconButton(
                                        icon = Icons.Default.SkipNext,
                                        onClick = { onSelectStyle(1) },
                                        size = 48.dp,
                                        iconSize = 24.dp
                                    )

                                    Box(
                                        modifier = Modifier.size(42.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MorphingPillIndicator()
                                    }
                                }
                            }
                        }

                        else -> {
                            // 2: Классические кнопки и минималистичная капсула
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(92.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { onSelectStyle(2) },
                                        shape = CircleShape,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = null)
                                    }

                                    FilledIconButton(
                                        onClick = {
                                            previewPlaying = !previewPlaying
                                            onSelectStyle(2)
                                        },
                                        shape = CircleShape,
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (previewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    FilledTonalIconButton(
                                        onClick = { onSelectStyle(2) },
                                        shape = CircleShape,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.SkipNext, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeAndAudioStepContent(
    selectedTheme: Int,
    onSelectTheme: (Int) -> Unit,
    autoDetectHeadphones: Boolean,
    onToggleAutoDetectHeadphones: (Boolean) -> Unit,
    showTrackInfoBar: Boolean,
    onToggleTrackInfoBar: (Boolean) -> Unit,
    jamesDspEnabled: Boolean,
    onToggleJamesDsp: (Boolean) -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(id = R.string.onboarding_theme_section_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        val rows = OnboardingCatalog.themes.chunked(2)
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { theme ->
                    val isSelected = selectedTheme == theme.id
                    val cornerRadius by animateDpAsState(
                        targetValue = if (isSelected) 28.dp else 20.dp,
                        animationSpec = motionScheme.defaultSpatialSpec(),
                        label = "themeCorner"
                    )
                    Surface(
                        onClick = { onSelectTheme(theme.id) },
                        shape = RoundedCornerShape(cornerRadius),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                },
                                shape = RoundedCornerShape(cornerRadius)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = theme.iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    theme.swatches.forEach { swatch ->
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
                                                .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                        )
                                    }
                                }
                            }
                            Column {
                                Text(
                                    text = stringResource(id = theme.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(id = theme.descRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(id = R.string.onboarding_audio_features_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(id = R.string.sound_effects_title), fontWeight = FontWeight.ExtraBold)
                    },
                    supportingContent = {
                        Text(stringResource(id = R.string.signal_path_engine_desc))
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        LiquidGlassSwitch(
                            checked = jamesDspEnabled,
                            onCheckedChange = onToggleJamesDsp
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    headlineContent = {
                        Text(stringResource(id = R.string.integration_auto_detect), fontWeight = FontWeight.ExtraBold)
                    },
                    supportingContent = {
                        Text(stringResource(id = R.string.integration_auto_detect_desc))
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    trailingContent = {
                        LiquidGlassSwitch(
                            checked = autoDetectHeadphones,
                            onCheckedChange = onToggleAutoDetectHeadphones
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    headlineContent = {
                        Text(stringResource(id = R.string.settings_track_info), fontWeight = FontWeight.ExtraBold)
                    },
                    supportingContent = {
                        Text(stringResource(id = R.string.settings_track_info_desc))
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    },
                    trailingContent = {
                        LiquidGlassSwitch(
                            checked = showTrackInfoBar,
                            onCheckedChange = onToggleTrackInfoBar
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun PermissionsStepContent(
    allFilesGranted: Boolean,
    mediaGranted: Boolean,
    bluetoothGranted: Boolean,
    onRequestAllFiles: () -> Unit,
    onRequestMedia: () -> Unit,
    onRequestBluetooth: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(id = R.string.onboarding_perm_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PermissionStatusCard(
            title = stringResource(id = R.string.onboarding_perm_all_files_title),
            description = stringResource(id = R.string.onboarding_perm_all_files_desc),
            icon = Icons.Rounded.FolderSpecial,
            accentColor = MaterialTheme.colorScheme.primary,
            isGranted = allFilesGranted,
            onRequest = onRequestAllFiles
        )

        PermissionStatusCard(
            title = stringResource(id = R.string.onboarding_perm_media_title),
            description = stringResource(id = R.string.onboarding_perm_media_desc),
            icon = Icons.Rounded.LibraryMusic,
            accentColor = MaterialTheme.colorScheme.secondary,
            isGranted = mediaGranted,
            onRequest = onRequestMedia
        )

        PermissionStatusCard(
            title = stringResource(id = R.string.onboarding_perm_bt_title),
            description = stringResource(id = R.string.onboarding_perm_bt_desc),
            icon = Icons.Rounded.Bluetooth,
            accentColor = MaterialTheme.colorScheme.tertiary,
            isGranted = bluetoothGranted,
            onRequest = onRequestBluetooth
        )
    }
}

@Composable
private fun PermissionStatusCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    val grantedColor = Color(0xFF00C853)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isGranted) {
                    grantedColor.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = (if (isGranted) grantedColor else accentColor).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Rounded.CheckCircle else icon,
                            contentDescription = null,
                            tint = if (isGranted) grantedColor else accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isGranted) {
                        grantedColor.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    }
                ) {
                    Text(
                        text = stringResource(
                            id = if (isGranted) R.string.onboarding_perm_status_granted else R.string.onboarding_perm_status_required
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isGranted) grantedColor else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted) {
                FilledTonalButton(
                    onClick = onRequest,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.onboarding_perm_grant_btn),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}