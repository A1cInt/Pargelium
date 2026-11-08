@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.alcint.pargelium.onboarding

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R
import com.alcint.pargelium.audio.JamesDspController
import java.util.Locale

@Composable
fun OnboardingScreen(
    onThemePreviewChanged: (Int) -> Unit = {},
    onComplete: () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val detectedLanguage = remember { OnboardingCatalog.detectDeviceLanguage() }
    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    var selectedLangTag by rememberSaveable {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val initial = if (!currentLocales.isEmpty) {
            currentLocales[0]?.toLanguageTag() ?: detectedLanguage.tag
        } else {
            detectedLanguage.tag
        }
        mutableStateOf(initial)
    }

    // Мгновенная локализация превью без пересоздания Activity (0 мс фризов при клике на язык!)
    val (localizedContext, localizedConfig) = remember(baseContext, baseConfig, selectedLangTag) {
        val locale = Locale.forLanguageTag(selectedLangTag)
        val config = Configuration(baseConfig).apply {
            setLocale(locale)
        }
        baseContext.createConfigurationContext(config) to config
    }

    var selectedUiStyle by rememberSaveable { mutableIntStateOf(PrefsManager.getNavBarStyle()) }
    var selectedThemeMode by rememberSaveable { mutableIntStateOf(0) }
    var autoDetectHeadphones by rememberSaveable { mutableStateOf(PrefsManager.getAutoDetectHeadphones()) }
    var showTrackInfoBar by rememberSaveable { mutableStateOf(PrefsManager.getShowTrackInfoBar()) }
    var jamesDspEnabled by rememberSaveable { mutableStateOf(PrefsManager.getEnabled()) }

    val expressiveColorScheme = rememberPargeliumExpressiveColorScheme(selectedThemeMode)

    var allFilesGranted by remember { mutableStateOf(OnboardingCatalog.isAllFilesGranted()) }
    var mediaGranted by remember { mutableStateOf(OnboardingCatalog.isMediaPermissionGranted(baseContext)) }
    var bluetoothGranted by remember { mutableStateOf(OnboardingCatalog.isBluetoothPermissionGranted(baseContext)) }

    fun refreshPermissions() {
        allFilesGranted = OnboardingCatalog.isAllFilesGranted()
        mediaGranted = OnboardingCatalog.isMediaPermissionGranted(baseContext)
        bluetoothGranted = OnboardingCatalog.isBluetoothPermissionGranted(baseContext)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissions()
    }

    val mediaPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissions()
    }

    val btPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissions()
    }

    BackHandler(enabled = currentStep > 0) {
        currentStep--
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig
    ) {
        MaterialExpressiveTheme(
            colorScheme = expressiveColorScheme,
            motionScheme = MotionScheme.expressive()
        ) {
            val stepTitles = listOf(
                stringResource(id = R.string.onboarding_step_lang_title),
                stringResource(id = R.string.onboarding_step_style_title),
                stringResource(id = R.string.onboarding_step_theme_title),
                stringResource(id = R.string.onboarding_step_perm_title)
            )

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = stringResource(id = R.string.onboarding_welcome_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stepTitles.getOrElse(currentStep) { "" },
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0..3) {
                                    val isCurrent = i == currentStep
                                    val isDone = i < currentStep
                                    val pillWidth by animateDpAsState(
                                        targetValue = if (isCurrent) 28.dp else 10.dp,
                                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                                        label = "pillW"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .height(10.dp)
                                            .width(pillWidth)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isCurrent -> MaterialTheme.colorScheme.primary
                                                    isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(240, easing = FastOutSlowInEasing)
                            val fadeSpec = tween<Float>(200)
                            if (targetState > initialState) {
                                (slideInHorizontally(slideSpec) { w -> w / 3 } + fadeIn(fadeSpec))
                                    .togetherWith(slideOutHorizontally(slideSpec) { w -> -w / 4 } + fadeOut(fadeSpec))
                            } else {
                                (slideInHorizontally(slideSpec) { w -> -w / 4 } + fadeIn(fadeSpec))
                                    .togetherWith(slideOutHorizontally(slideSpec) { w -> w / 3 } + fadeOut(fadeSpec))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = "onboardingStepContent"
                    ) { step ->
                        when (step) {
                            0 -> LanguageStepContent(
                                detectedLanguage = detectedLanguage,
                                selectedLanguageTag = selectedLangTag,
                                onSelectLanguage = { lang ->
                                    selectedLangTag = lang.tag
                                },
                                onConfirmDetectedAndContinue = {
                                    currentStep = 1
                                }
                            )
                            1 -> StyleStepContent(
                                selectedStyle = selectedUiStyle,
                                onSelectStyle = { style ->
                                    selectedUiStyle = style
                                    PrefsManager.saveNavBarStyle(style)
                                    PrefsManager.savePlayerButtonStyle(style)
                                }
                            )
                            2 -> ThemeAndAudioStepContent(
                                selectedTheme = selectedThemeMode,
                                onSelectTheme = { mode ->
                                    selectedThemeMode = mode
                                    PrefsManager.saveThemeMode(mode)
                                    onThemePreviewChanged(mode)
                                },
                                autoDetectHeadphones = autoDetectHeadphones,
                                onToggleAutoDetectHeadphones = { enabled ->
                                    autoDetectHeadphones = enabled
                                    PrefsManager.saveAutoDetectHeadphones(enabled)
                                },
                                showTrackInfoBar = showTrackInfoBar,
                                onToggleTrackInfoBar = { enabled ->
                                    showTrackInfoBar = enabled
                                    PrefsManager.saveShowTrackInfoBar(enabled)
                                },
                                jamesDspEnabled = jamesDspEnabled,
                                onToggleJamesDsp = { enabled ->
                                    jamesDspEnabled = enabled
                                    PrefsManager.saveEnabled(enabled)
                                    JamesDspController.setMasterEnabled(enabled)
                                }
                            )
                            else -> PermissionsStepContent(
                                allFilesGranted = allFilesGranted,
                                mediaGranted = mediaGranted,
                                bluetoothGranted = bluetoothGranted,
                                onRequestAllFiles = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${baseContext.packageName}")
                                            }
                                            allFilesLauncher.launch(intent)
                                        } catch (_: Exception) {
                                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            allFilesLauncher.launch(intent)
                                        }
                                    }
                                },
                                onRequestMedia = {
                                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        arrayOf(
                                            Manifest.permission.READ_MEDIA_AUDIO,
                                            Manifest.permission.READ_MEDIA_IMAGES,
                                            Manifest.permission.READ_MEDIA_VIDEO
                                        )
                                    } else {
                                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    mediaPermissionsLauncher.launch(perms)
                                },
                                onRequestBluetooth = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        btPermissionsLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.BLUETOOTH_CONNECT,
                                                Manifest.permission.BLUETOOTH_SCAN
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStep > 0) {
                            OutlinedButton(
                                onClick = { currentStep-- },
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.action_back), fontWeight = FontWeight.ExtraBold)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                if (currentStep < 3) {
                                    currentStep++
                                } else {
                                    PrefsManager.saveNavBarStyle(selectedUiStyle)
                                    PrefsManager.savePlayerButtonStyle(selectedUiStyle)
                                    PrefsManager.saveThemeMode(selectedThemeMode)
                                    PrefsManager.saveAutoDetectHeadphones(autoDetectHeadphones)
                                    PrefsManager.saveShowTrackInfoBar(showTrackInfoBar)
                                    PrefsManager.saveEnabled(jamesDspEnabled)
                                    PrefsManager.saveOnboardingCompleted(true)

                                    val currentAppLocales = AppCompatDelegate.getApplicationLocales()
                                    val currentTag = if (!currentAppLocales.isEmpty) currentAppLocales[0]?.toLanguageTag() else null
                                    if (!selectedLangTag.equals(currentTag, ignoreCase = true) &&
                                        !selectedLangTag.equals(detectedLanguage.tag, ignoreCase = true)
                                    ) {
                                        AppCompatDelegate.setApplicationLocales(
                                            LocaleListCompat.forLanguageTags(selectedLangTag)
                                        )
                                    }
                                    onComplete()
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    id = if (currentStep < 3) R.string.onboarding_btn_next else R.string.onboarding_btn_finish
                                ),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (currentStep < 3) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}