package ru.pylarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val alarm = AlarmStore.get(context).firstOrNull { it.id == id } ?: return
        if (!alarm.enabled) return

        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(AlarmRingingService.EXTRA_RINGTONE, alarm.ringtoneUri)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
        else context.startService(serviceIntent)

        if (alarm.date != null || alarm.weekdays.isEmpty()) {
            AlarmStore.save(context, AlarmStore.get(context).filterNot { it.id == id })
        } else {
            AlarmScheduler.schedule(context, alarm)
        }
    }

    companion object {
        const val EXTRA_ID = "alarm_id"
    }
}
