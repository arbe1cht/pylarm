package ru.pylarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder

class AlarmRingingService : Service() {
    private var ringtone: Ringtone? = null
    private val handler = android.os.Handler(mainLooper)
    private val legacyLoop = object : Runnable {
        override fun run() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && ringtone?.isPlaying != true) {
                ringtone?.play()
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val id = intent?.getLongExtra(AlarmReceiver.EXTRA_ID, -1L) ?: -1L
        val uri = intent?.getStringExtra(EXTRA_RINGTONE)?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(id))
        ringtone?.stop()
        ringtone = RingtoneManager.getRingtone(this, uri)
            ?: RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        ringtone?.let {
            it.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
            it.play()
        }
        handler.removeCallbacks(legacyLoop)
        handler.postDelayed(legacyLoop, 1_000L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(legacyLoop)
        ringtone?.stop()
        ringtone = null
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Будильник",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Звонок будильника"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(id: Long): Notification {
        val challengeIntent = Intent(this, ChallengeActivity::class.java)
            .putExtra(AlarmReceiver.EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fullScreen = PendingIntent.getActivity(
            this, 0, challengeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Пора просыпаться")
            .setContentText("Решите 10 примеров, чтобы выключить будильник")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_RINGTONE = "ringtone_uri"
        const val ACTION_STOP = "ru.pylarm.STOP_ALARM"
        private const val CHANNEL_ID = "alarm_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
