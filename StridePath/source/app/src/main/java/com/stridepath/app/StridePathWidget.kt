package com.stridepath.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class StridePathWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    companion object {
        fun requestPin(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 26) return false
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, StridePathWidget::class.java), null, null)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StridePathWidget::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val store = AppStore(context)
            val profile = store.loadProfile()
            val views = RemoteViews(context.packageName, R.layout.widget_stridepath)
            val openApp = PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            if (profile == null) {
                views.setTextViewText(R.id.widget_title, "PIP • READY")
                views.setTextViewText(R.id.widget_steps, "Start your campaign")
                views.setProgressBar(R.id.widget_progress, 100, 0, false)
                views.setTextViewText(R.id.widget_details, "Tap to set up StridePath")
                views.setTextViewText(R.id.widget_message, "I’ll keep your daily quests nearby.")
            } else {
                val plan = GoalCalculator.calculate(profile)
                val stats = GameEngine.playerStats(store, profile, plan)
                val steps = store.dailySteps()
                val calories = store.loadFood().sumOf { it.calories }
                val water = store.waterFlOz()
                val progress = ((steps.toFloat() / plan.balancedStepTarget.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
                views.setTextViewText(R.id.widget_title, "${store.buddyName().uppercase()} • LEVEL ${stats.level}")
                views.setTextViewText(R.id.widget_steps, "${"%,d".format(steps)} / ${"%,d".format(plan.balancedStepTarget)} steps")
                views.setProgressBar(R.id.widget_progress, 100, progress, false)
                views.setTextViewText(R.id.widget_details, "${"%,d".format(calories)} Cal • $water fl oz • ${stats.xp} XP")
                views.setTextViewText(R.id.widget_message, GameEngine.buddyMessages(store, profile, plan).first())
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}
