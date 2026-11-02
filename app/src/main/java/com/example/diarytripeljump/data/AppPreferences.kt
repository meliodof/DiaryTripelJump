package com.example.diarytripeljump.data

import android.content.Context

/** Настройки приложения, которые должны переживать перезапуск (хранятся в diary_prefs). */
class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var isDarkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    /** true — открыт демо-дневник (отдельная база), false — дневник пользователя. */
    var useDemoData: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        // commit, а не apply: сразу после записи процесс может быть перезапущен (восстановление копии)
        set(value) { prefs.edit().putBoolean(KEY_DEMO, value).commit() }

    companion object {
        const val FILE_NAME = "diary_prefs"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_DEMO = "use_demo_data"
    }
}
