package com.example.diarytripeljump

import com.example.diarytripeljump.ui.components.ConfirmHost
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.diarytripeljump.ui.theme.DiaryTripelJumpTheme

class MainActivity : ComponentActivity() {
    private val app: TripleJumpApplication get() = application as TripleJumpApplication

    private val viewModel: DiaryViewModel by viewModels {
        DiaryViewModelFactory(app.repository, app.preferences)
    }

    /** Раздел, который нужно открыть по нажатию на виджет; сбрасывается после перехода. */
    private val openRoute = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { openRoute.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
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

            // виджет показывает только свой дневник: в демо-режиме его данные не меняются
            LaunchedEffect(app.isDemo) {
                if (!app.isDemo) viewModel.widgetSnapshot.collect { DiaryWidget.save(applicationContext, it) }
            }

            DiaryTripelJumpTheme(darkTheme = darkTheme, customPalette = customPalette) {
                // одно окно подтверждения для всех удалений и изменений
                ConfirmHost {
                    MainScreen(
                        viewModel = viewModel,
                        isDemo = app.isDemo,
                        onSwitchDiary = ::switchDiary,
                        openRoute = openRoute.value,
                        onRouteOpened = { openRoute.value = null }
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

    companion object {
        /** Раздел, который открыть при запуске (из виджета). */
        const val EXTRA_ROUTE = "open_route"
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
