package com.example.diarytripeljump

import com.example.diarytripeljump.ui.components.ConfirmHost
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.diarytripeljump.ui.theme.DiaryTripelJumpTheme

class MainActivity : ComponentActivity() {
    private val app: TripleJumpApplication get() = application as TripleJumpApplication

    private val viewModel: DiaryViewModel by viewModels {
        DiaryViewModelFactory(app.repository, app.preferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Отдельный поток только для темы: изменения статистики не перерисовывают всё дерево
            val darkTheme by viewModel.isDarkTheme.collectAsState()
            val customPalette by viewModel.customPalette.collectAsState()

            // Иконки системных панелей подстраиваются под тему приложения, а не системы
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { darkTheme }
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = LIGHT_SCRIM,
                        darkScrim = DARK_SCRIM,
                        detectDarkMode = { darkTheme }
                    )
                )
                onDispose { }
            }

            DiaryTripelJumpTheme(darkTheme = darkTheme, customPalette = customPalette) {
                // одно окно подтверждения для всех удалений и изменений
                ConfirmHost {
                    MainScreen(
                        viewModel = viewModel,
                        isDemo = app.isDemo,
                        onSwitchDiary = ::switchDiary
                    )
                }
            }
        }
    }

    /**
     * Переключение «Демо / Мои данные» без перезапуска процесса: подключается другая база,
     * старая ViewModel удаляется (её потоки отменяются), экран пересоздаётся с новой.
     */
    fun switchDiary(demo: Boolean) {
        if (app.isDemo == demo) return
        app.preferences.useDemoData = demo
        app.openDiary(demo)
        viewModelStore.clear()
        recreate()
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
