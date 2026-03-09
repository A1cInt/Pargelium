package com.alcint.pargelium

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // 1. ИНИЦИАЛИЗАЦИЯ
        PrefsManager.init(this)

        PlaylistDatabase.init(this)

        // Применяем флаг безопасности
        setSecureMode(PrefsManager.getSecureMode())

        // 2. Настройка окон (Edge-to-Edge)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Делаем иконки белыми (так как фон темный)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        // 3. Запуск сервиса
        val intent = Intent(this, PlaybackService::class.java)
        startService(intent)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                LibraryScreen(
                    onSecureRequest = { isSecure -> setSecureMode(isSecure) }
                )
            }
        }
    }

    private fun setSecureMode(enabled: Boolean) {
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}