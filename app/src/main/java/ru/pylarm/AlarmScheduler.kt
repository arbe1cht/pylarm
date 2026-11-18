package ru.pylarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AlarmScheduler {
    fun schedule(context: Context, alarm: Alarm): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            return false
        }
        val triggerAt = nextOccurrence(alarm) ?: return false
        try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(context, alarm.id)
            )
        } catch (_: SecurityException) {
            return false
        }
        return true
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(context, id))
    }

    fun rescheduleAll(context: Context) {
        AlarmStore.get(context).filter { it.enabled }.forEach { schedule(context, it) }
    }

    private fun pendingIntent(context: Context, id: Long) = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun nextOccurrence(alarm: Alarm): Long? {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, alarm.second)
            set(Calendar.MILLISECOND, alarm.millisecond)
        }
        if (alarm.date != null) {
            val date = runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(alarm.date)
            }.getOrNull() ?: return null
            val day = Calendar.getInstance().apply { time = date }
            calendar.set(Calendar.YEAR, day.get(Calendar.YEAR))
            calendar.set(Calendar.MONTH, day.get(Calendar.MONTH))
            calendar.set(Calendar.DAY_OF_MONTH, day.get(Calendar.DAY_OF_MONTH))
            return calendar.timeInMillis.takeIf { it > now }
        }
        if (alarm.weekdays.isEmpty()) return calendar.timeInMillis.takeIf { it > now }

        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, alarm.second)
                set(Calendar.MILLISECOND, alarm.millisecond)
            }
            if (candidate.get(Calendar.DAY_OF_WEEK) in alarm.weekdays && candidate.timeInMillis > now) {
                return candidate.timeInMillis
            }
        }
        return null
    }
}
