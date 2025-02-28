package com.test.testapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

private const val UPDATE_INTERVAL_MILLIS = 10000L

class ForegroundService : Service() {

    // Имя для SharedPreferences и ключ для сохранения времени последнего запуска
    // Не уверен, что это нужно в задаче, но если сервис перезапустится то, чтобы не ждать заново интервал
    // мы сможем восстановить время последнего показа активити
    private val prefsName = "my_service_prefs"
    private val lastLaunchKey = "lastActivityLaunchTime"

    private val handler = Handler(Looper.getMainLooper())

    private val runnable = object : Runnable {
        override fun run() {
            Log.d(
                "MyForegroundService",
                "Перезапуск активити каждые $UPDATE_INTERVAL_MILLIS мс!"
            )
            launchMainActivity()
            updateLastActivityLaunchTime()
            handler.postDelayed(this, UPDATE_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        initForegroundNotification()

        // Вот эта часть тоже, не уверен, нужно ли для данной задачи, но такой сценарий стоит предусмотреть
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val lastLaunchTime = prefs.getLong(lastLaunchKey, 0L)
        // Вычисляем задержку: если Activity запускалось недавно, подождать оставшееся время.
        val initialDelay = if (lastLaunchTime != 0L) {
            val elapsed = System.currentTimeMillis() - lastLaunchTime
            if (elapsed < UPDATE_INTERVAL_MILLIS) UPDATE_INTERVAL_MILLIS - elapsed else 0L
        } else {
            0L
        }
        handler.postDelayed(runnable, initialDelay)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initForegroundNotification() {
        val channelId = "foreground_service_channel"
        val channelName = "My Foreground Service"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        // Отключаем клик по уведомлению
        val emptyIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_transparent)
            .setContentTitle("")
            .setContentText("")
            .setOngoing(true)                             // Делает уведомление несвайпаемым
            .setPriority(NotificationCompat.PRIORITY_LOW) // Низкий приоритет
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)                         // Не скрывается после клика
            .setContentIntent(emptyIntent)                // Некликабельное
            .build()

        startForeground(1, notification)
    }

    private fun launchMainActivity() {
        val activityIntent = Intent(this@ForegroundService, AddActivity::class.java)
            .apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
        startActivity(activityIntent)
    }

    private fun updateLastActivityLaunchTime() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        prefs.edit().putLong(lastLaunchKey, System.currentTimeMillis()).apply()
    }
}
