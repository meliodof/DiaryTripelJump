package com.example.diarytripeljump

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.diarytripeljump.data.WidgetSnapshot
import com.example.diarytripeljump.ui.components.pluralRu

/**
 * Виджет на рабочий стол: дни до ближайшего старта, прогноз и рекорд в тройном.
 * Виджет не читает базу: приложение сохраняет готовые данные ([DiaryWidget.save]) при каждом их изменении,
 * а дни до старта считаются заново при каждом обновлении (раз в час), поэтому отсчёт верен и без открытия приложения.
 */
class DiaryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, DiaryWidget.views(context)) }
    }
}

object DiaryWidget {
    private const val PREFS = "widget_prefs"
    private const val KEY_NAME = "start_name"
    private const val KEY_DATE = "start_date"
    private const val KEY_FORECAST = "forecast"
    private const val KEY_RECORD = "record"

    /** Сохраняет данные своего дневника (не демо) и обновляет виджеты, если что-то изменилось. */
    fun save(context: Context, snapshot: WidgetSnapshot) {
        if (snapshot == load(context)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, snapshot.startName)
            .putLong(KEY_DATE, snapshot.startDate ?: -1L)
            .putString(KEY_FORECAST, snapshot.forecast)
            .putString(KEY_RECORD, snapshot.record)
            .apply()
        refresh(context)
    }

    fun load(context: Context): WidgetSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetSnapshot(
            startName = p.getString(KEY_NAME, null),
            startDate = p.getLong(KEY_DATE, -1L).takeIf { it >= 0 },
            forecast = p.getString(KEY_FORECAST, null),
            record = p.getString(KEY_RECORD, null)
        )
    }

    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, DiaryWidgetProvider::class.java))
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    fun views(context: Context): RemoteViews {
        val s = load(context)
        val days = s.daysLeft(System.currentTimeMillis())
        val v = RemoteViews(context.packageName, R.layout.widget_diary)
        if (days != null) {
            v.setTextViewText(R.id.widget_label, context.getString(R.string.widget_until_start))
            v.setViewVisibility(R.id.widget_days_row, View.VISIBLE)
            v.setTextViewText(R.id.widget_days, if (days == 0) "Сегодня" else days.toString())
            v.setTextViewText(R.id.widget_days_word, if (days == 0) "" else pluralRu(days, "день", "дня", "дней"))
            v.setTextViewText(R.id.widget_start, s.startName.orEmpty())
            v.setViewVisibility(R.id.widget_start, View.VISIBLE)
        } else {
            v.setTextViewText(R.id.widget_label, "Ближайших стартов нет")
            v.setViewVisibility(R.id.widget_days_row, View.GONE)
            v.setViewVisibility(R.id.widget_start, View.GONE)
        }
        v.setTextViewText(R.id.widget_forecast, s.forecast?.let { "Прогноз ≈ $it" } ?: "Прогноза пока нет")
        v.setTextViewText(R.id.widget_record, s.record?.let { "Рекорд в тройном: $it" } ?: "")
        val open = PendingIntent.getActivity(
            context, 0,
            // виджет открывает сразу обзор аналитики: подводку, радар, карту сезона
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_ROUTE, Routes.ANALYTICS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        v.setOnClickPendingIntent(R.id.widget_root, open)
        return v
    }
}
