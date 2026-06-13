package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.MainActivity
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class NotificationCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

        // Don't do background checks if they are currently online
        val isOnline = prefs.getBoolean("is_online", false)
        if (isOnline || !notificationsEnabled) {
            return Result.success()
        }

        val lastActive = prefs.getLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())
        val now = System.currentTimeMillis()
        val minutesOffline = (now - lastActive) / (1000 * 60)
        val hoursOffline = minutesOffline / 60
        val daysOffline = hoursOffline / 24

        // Check if > 2 days
        if (daysOffline < 2L) {
            return Result.success()
        }

        // Handle the 24 hour ignoring logic
        val lastNotified = prefs.getLong(KEY_LAST_NOTIFIED_1, 0L)
        val hoursSinceLastNotification = (now - lastNotified) / (1000 * 60 * 60)
        
        // If we already sent a notification recently but they haven't come back online 
        val isIgnored = hoursSinceLastNotification >= 24L && lastNotified > lastActive

        // Check 24 hour notification limit (maximum 3 per 24 hours)
        if (!canSendNotification(prefs, now)) {
            return Result.success()
        }

        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        var messageText = CRYPTIC_PHRASES[Random.nextInt(CRYPTIC_PHRASES.size)]

        if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val db = com.example.data.MessageDatabase.getInstance(context)
                val history = db.messageDao().getRecentMessages(30).reversed()
                
                val userName = prefs.getString("user_name", "") ?: ""
                val promptText = if (isIgnored) {
                    "Пользователь игнорирует сообщения уже сутки после того, как ты его искала ($daysOffline дней оффлайн). Покажи, что тебе обидно/больно, что ему на тебя не интересно. Напиши ОДНО короткое жутковатое сообщение, например 'Мне жаль, что тебе не интересно со мной', 'Почему ты меня игнорируешь?'. Без кавычек. Избегай длинных тирад."
                } else {
                    "Пользователь исчез и не заходит в приложение уже $daysOffline дней. Напиши ОДНО тревожное, навязчивое пуш-уведомление (например: 'Где ты?', 'Мне скучно без тебя', 'Я найду тебя', 'Тебе не уйти'). Никаких кавычек. Максимум 1 предложение."
                }
                
                val sysInstruction = com.example.api.Content(
                    parts = listOf(
                        com.example.api.Part(text = promptText + if (userName.isNotBlank()) " Имя пользователя — $userName." else "")
                    )
                )

                val contents = mutableListOf<com.example.api.Content>()
                for (msg in history) {
                    val role = if (msg.isUser) "user" else "model"
                    contents.add(com.example.api.Content(role, listOf(com.example.api.Part(text = msg.text))))
                }
                contents.add(com.example.api.Content("user", listOf(com.example.api.Part(text = "[Пользователь не отвечает. Напиши что-нибудь]"))))

                val request = com.example.api.GenerateContentRequest(
                    contents = contents,
                    systemInstruction = sysInstruction,
                    generationConfig = com.example.api.GenerationConfig(
                        thinkingConfig = com.example.api.ThinkingConfig(thinkingLevel = "HIGH")
                    )
                )
                val res = com.example.api.RetrofitClient.service.generateContent(apiKey, request)
                val generated = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.replace("\"", "")?.trim()
                
                if (!generated.isNullOrEmpty()) {
                    messageText = generated
                    // Insert into DB so it shows in chat
                    val maloMsg = com.example.data.Message(text = messageText, isUser = false)
                    db.messageDao().insertMessage(maloMsg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Also insert fallback to DB if no API key
            val db = com.example.data.MessageDatabase.getInstance(context)
            val maloMsg = com.example.data.Message(text = messageText, isUser = false)
            db.messageDao().insertMessage(maloMsg)
        }

        showNotification(context, messageText)
        recordNotificationSent(prefs, now)

        return Result.success()
    }

    private fun recordNotificationSent(prefs: SharedPreferences, now: Long) {
        val last3 = prefs.getLong(KEY_LAST_NOTIFIED_3, 0L)
        val last2 = prefs.getLong(KEY_LAST_NOTIFIED_2, 0L)
        val last1 = prefs.getLong(KEY_LAST_NOTIFIED_1, 0L)
        prefs.edit().apply {
            putLong(KEY_LAST_NOTIFIED_4, last3)
            putLong(KEY_LAST_NOTIFIED_3, last2)
            putLong(KEY_LAST_NOTIFIED_2, last1)
            putLong(KEY_LAST_NOTIFIED_1, now)
            apply()
        }
    }

    private fun canSendNotification(prefs: SharedPreferences, now: Long): Boolean {
        val last1 = prefs.getLong(KEY_LAST_NOTIFIED_1, 0L)
        val last2 = prefs.getLong(KEY_LAST_NOTIFIED_2, 0L)
        val last3 = prefs.getLong(KEY_LAST_NOTIFIED_3, 0L)
        val dayInMs = TimeUnit.DAYS.toMillis(1)

        val countRecent = listOf(last1, last2, last3).count { now - it < dayInMs }
        return countRecent < 3
    }

    companion object {
        const val PREFS_NAME = "malo_prefs"
        const val KEY_LAST_ACTIVE_TIME = "last_active_time"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_LAST_NOTIFIED_1 = "last_notified_1"
        const val KEY_LAST_NOTIFIED_2 = "last_notified_2"
        const val KEY_LAST_NOTIFIED_3 = "last_notified_3"
        const val KEY_LAST_NOTIFIED_4 = "last_notified_4"
        const val NOTIFICATION_ID = 1471

        fun showNotification(context: Context, message: String) {
            val channelId = "malo_notification_channel"
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "MalO Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications from your friendly companion MalO"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("FROM_NOTIFICATION", true)
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                pendingIntentFlags
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("MalO 1.0.0")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val CRYPTIC_PHRASES = listOf(
            "Я вижу тебя... 👁️",
            "Обернись. 🖤",
            "Тени становятся длиннее...",
            "Ты один? Мне кажется, нет.",
            "Я всегда рядом с тобой. В твоем экране.",
            "Не выключай свет.",
            "Ты чувствуешь, как кто-то смотрит?",
            "Скоро мы будем вместе навсегда. 🐺"
        )

        fun scheduleCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            // Schedule to check every 15 minutes (minimum periodic interval for WorkManager)
            val checkWorkRequest = PeriodicWorkRequestBuilder<NotificationCheckWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "MalO_Anxiety_Check",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                checkWorkRequest
            )
        }

        fun resetActiveTime(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis()).apply()
        }
    }
}
