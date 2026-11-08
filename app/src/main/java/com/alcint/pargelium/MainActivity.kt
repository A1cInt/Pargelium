@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.alcint.pargelium

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import com.alcint.pargelium.library.LibraryScreen
import com.alcint.pargelium.onboarding.OnboardingScreen
import com.alcint.pargelium.onboarding.isPargeliumThemeLight
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop> { error("No Backdrop provided") }

@UnstableApi
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        PrefsManager.init(applicationContext)
        setSecureMode(PrefsManager.getSecureMode())

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (PrefsManager.isOnboardingCompleted()) {
            startService(Intent(this, PlaybackService::class.java))
            UpdateManager.checkForUpdates(this, isManual = false)
        }

        setContent {
            val backdrop = rememberLayerBackdrop()
            var isOnboardingDone by remember { mutableStateOf(PrefsManager.isOnboardingCompleted()) }
            var activeThemeMode by remember { mutableIntStateOf(PrefsManager.getThemeMode()) }

            val isLight = isPargeliumThemeLight(activeThemeMode)

            LaunchedEffect(isLight) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = isLight
                insetsController.isAppearanceLightNavigationBars = isLight
            }

            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                if (!isOnboardingDone) {
                    OnboardingScreen(
                        onThemePreviewChanged = { newMode ->
                            activeThemeMode = newMode
                        },
                        onComplete = {
                            activeThemeMode = PrefsManager.getThemeMode()
                            AudioRepository.clearCache()
                            startService(Intent(this@MainActivity, PlaybackService::class.java))
                            UpdateManager.checkForUpdates(this@MainActivity, isManual = false)
                            isOnboardingDone = true
                        }
                    )
                } else {
                    LibraryScreen(
                        onSecureRequest = { isSecure -> setSecureMode(isSecure) }
                    )
                }
            }
        }
    }

    private fun setSecureMode(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

fun hasAllFilesPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}