package com.example.diarytripeljump.data

import android.content.Context

/** Настройки приложения, которые должны переживать перезапуск (хранятся в diary_prefs). */
class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var isDarkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    /** Своя расцветка; null — стандартные цвета. */
    var customPalette: CustomPalette?
        get() = CustomPalette.decode(prefs.getString(KEY_PALETTE, null))
        set(value) = prefs.edit().putString(KEY_PALETTE, value?.encode()).apply()

    /** Выбранные показатели и оформление экрана визуализации. */
    var visualization: VisualizationConfig
        get() = VisualizationConfig.decode(prefs.getString(KEY_VISUALIZATION, null))
        set(value) = prefs.edit().putString(KEY_VISUALIZATION, value.encode()).apply()

    /** Обучение пройдено или пропущено: при следующем входе не показывается. */
    var tutorialSeen: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL, false)
        set(value) = prefs.edit().putBoolean(KEY_TUTORIAL, value).apply()

    /** Идёт обучение на демо-дневнике: переживает перезапуск экрана при переключении дневника. */
    var tutorialActive: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_ACTIVE, false)
        set(value) { prefs.edit().putBoolean(KEY_TUTORIAL_ACTIVE, value).commit() }

    var tutorialStep: Int
        get() = prefs.getInt(KEY_TUTORIAL_STEP, 0)
        set(value) = prefs.edit().putInt(KEY_TUTORIAL_STEP, value).apply()

    /** После обучения вернуться к своему дневнику: обучение запускали из него. */
    var tutorialReturnToOwn: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_RETURN, false)
        set(value) { prefs.edit().putBoolean(KEY_TUTORIAL_RETURN, value).commit() }

    /** Удалённые рекорды из тренировок: ключ и значение на момент удаления. */
    var hiddenRecords: Map<String, Double>
        get() = prefs.getString(KEY_HIDDEN, null).orEmpty().split("\u001E").mapNotNull { part ->
            val (key, value) = part.split("\u001F").takeIf { it.size == 2 } ?: return@mapNotNull null
            value.toDoubleOrNull()?.let { key to it }
        }.toMap()
        set(value) = prefs.edit().putString(KEY_HIDDEN, value.entries.joinToString("\u001E") { "${it.key}\u001F${it.value}" }).apply()

    /** Скрытые советы на главной (по id, в порядке скрытия). */
    var dismissedAdvice: Set<String>
        get() = prefs.getString(KEY_ADVICE, null).orEmpty().split("\u001E").filter { it.isNotEmpty() }.toCollection(LinkedHashSet())
        set(value) = prefs.edit().putString(KEY_ADVICE, value.joinToString("\u001E")).apply()

    /** Показатели, по которым пользователь выбрал прогноз: id и через сколько недель (0 — к ближайшему старту). */
    var pinnedForecasts: List<Pair<String, Int>>
        get() = prefs.getString(KEY_FORECASTS, null).orEmpty().split("\u001E").mapNotNull { part ->
            val (id, weeks) = part.split("\u001F").takeIf { it.size == 2 } ?: return@mapNotNull null
            weeks.toIntOrNull()?.let { id to it }
        }
        set(value) = prefs.edit().putString(KEY_FORECASTS, value.joinToString("\u001E") { "${it.first}\u001F${it.second}" }).apply()

    /** Версия набора демо-данных, которым заполнена демо-база; при новой версии база создаётся заново. */
    var demoSeedVersion: Int
        get() = prefs.getInt(KEY_DEMO_SEED, 0)
        set(value) = prefs.edit().putInt(KEY_DEMO_SEED, value).apply()

    /** true — открыт демо-дневник (отдельная база), false — дневник пользователя. */
    var useDemoData: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        // commit, а не apply: сразу после записи процесс может быть перезапущен (восстановление копии)
        set(value) { prefs.edit().putBoolean(KEY_DEMO, value).commit() }

    companion object {
        const val FILE_NAME = "diary_prefs"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_DEMO = "use_demo_data"
        private const val KEY_PALETTE = "custom_palette"
        private const val KEY_VISUALIZATION = "visualization"
        private const val KEY_TUTORIAL = "tutorial_seen"
        private const val KEY_TUTORIAL_ACTIVE = "tutorial_active"
        private const val KEY_TUTORIAL_STEP = "tutorial_step"
        private const val KEY_TUTORIAL_RETURN = "tutorial_return"
        private const val KEY_HIDDEN = "hidden_records"
        private const val KEY_ADVICE = "dismissed_advice"
        private const val KEY_DEMO_SEED = "demo_seed_version"
        private const val KEY_FORECASTS = "pinned_forecasts"
    }
}
