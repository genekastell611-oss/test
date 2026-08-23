package com.stridepath.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

object ReminderScheduler {
    private const val WATER_REQUEST = 611
    private const val QUEST_REQUEST = 621
    private const val MEAL_REQUEST = 631
    private const val WEIGH_REQUEST = 641

    fun scheduleAll(context: Context, store: AppStore) {
        if (store.waterReminderEnabled()) scheduleWater(context, store.waterReminderHours()) else cancel(context, WATER_REQUEST, WaterReminderReceiver::class.java)
        if (store.questReminderEnabled()) scheduleDaily(context, QUEST_REQUEST, QuestReminderReceiver::class.java, 9, 0) else cancel(context, QUEST_REQUEST, QuestReminderReceiver::class.java)
        if (store.mealReminderEnabled()) scheduleDaily(context, MEAL_REQUEST, MealReminderReceiver::class.java, 18, 30) else cancel(context, MEAL_REQUEST, MealReminderReceiver::class.java)
        if (store.weighInReminderEnabled()) scheduleWeekly(context, WEIGH_REQUEST, WeighInReminderReceiver::class.java, Calendar.SUNDAY, 9, 30) else cancel(context, WEIGH_REQUEST, WeighInReminderReceiver::class.java)
    }

    fun scheduleWater(context: Context, hours: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = receiverPending(context, WATER_REQUEST, WaterReminderReceiver::class.java)
        val interval = hours.coerceIn(1, 6) * 60L * 60L * 1000L
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval, interval, pending)
    }

    private fun scheduleDaily(context: Context, requestCode: Int, receiver: Class<out BroadcastReceiver>, hour: Int, minute: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = nextCalendar(hour, minute)
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, AlarmManager.INTERVAL_DAY, receiverPending(context, requestCode, receiver))
    }

    private fun scheduleWeekly(context: Context, requestCode: Int, receiver: Class<out BroadcastReceiver>, day: Int, hour: Int, minute: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.WEEK_OF_YEAR, 1)
        }
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, 7L * AlarmManager.INTERVAL_DAY, receiverPending(context, requestCode, receiver))
    }

    private fun nextCalendar(hour: Int, minute: Int) = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }

    private fun receiverPending(context: Context, requestCode: Int, receiver: Class<out BroadcastReceiver>): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode, Intent(context, receiver), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cancel(context: Context, requestCode: Int, receiver: Class<out BroadcastReceiver>) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(receiverPending(context, requestCode, receiver))
    }
}

fun sendQuestNotification(context: Context, id: Int, title: String, text: String, channel: String = "quests") {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelName = when (channel) {
            "hydration" -> "Hydration quests"
            "achievements" -> "Achievements"
            else -> "StridePath quests"
        }
        manager.createNotificationChannel(NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_DEFAULT))
    }
    val openIntent = PendingIntent.getActivity(
        context, id + 1000, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setAutoCancel(true)
        .setContentIntent(openIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    NotificationManagerCompat.from(context).notify(id, notification)
}

fun notifyNewAchievements(context: Context, store: AppStore, achievements: List<Achievement>) {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val alreadyNotified = store.notifiedAchievementIds()
    val newlyUnlocked = achievements.filter { it.unlocked && it.id !in alreadyNotified }
    newlyUnlocked.forEachIndexed { index, achievement ->
        sendQuestNotification(
            context,
            7000 + (achievement.id.hashCode() and 0x0fff) + index,
            "Achievement unlocked: ${achievement.title}",
            "${achievement.description} +${achievement.xp} XP",
            "achievements"
        )
    }
    store.markAchievementsNotified(newlyUnlocked.map { it.id }.toSet())
}

class WaterReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = AppStore(context)
        if (!store.waterReminderEnabled()) return
        val messages = listOf(
            "Hydration quest available: grab a few sips and earn potion progress. 💧",
            "Pip found a water potion. Inventory it now! 🧪",
            "Tiny side quest: refill your drink and keep the run going.",
            "HP refill station nearby — hydration break! 💙"
        )
        sendQuestNotification(context, 613, "Hydration side quest", messages[(System.currentTimeMillis() / 1000L % messages.size).toInt()], "hydration")
    }
}

class QuestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = AppStore(context)
        if (!store.questReminderEnabled()) return
        val profile = store.loadProfile() ?: return
        val plan = GoalCalculator.calculate(profile)
        val left = (plan.balancedStepTarget - store.dailySteps()).coerceAtLeast(0)
        sendQuestNotification(context, 623, "Daily quest board is live", if (left == 0) "Walking boss already defeated. Collect the rest of today’s quest XP!" else "$left steps remain on today’s walking boss. Small walks all count.")
    }
}

class MealReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = AppStore(context)
        if (!store.mealReminderEnabled()) return
        sendQuestNotification(context, 633, "Inventory check", "Log dinner or snacks so your nutrition HUD stays accurate. No judgment—just data.")
    }
}

class WeighInReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = AppStore(context)
        if (!store.weighInReminderEnabled()) return
        sendQuestNotification(context, 643, "Weekly checkpoint", "Optional weigh-in quest: save a checkpoint and compare actual vs projected progress.")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) ReminderScheduler.scheduleAll(context, AppStore(context))
    }
}
