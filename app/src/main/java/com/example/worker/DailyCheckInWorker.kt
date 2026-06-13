package com.example.worker

import android.content.Context
import androidx.work.*
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.Message
import com.example.data.MessageDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyCheckInWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences(NotificationCheckWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean(NotificationCheckWorker.KEY_NOTIFICATIONS_ENABLED, true)

        if (!notificationsEnabled) return Result.success()

        val lastActive = prefs.getLong(NotificationCheckWorker.KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())
        val now = System.currentTimeMillis()
        val durationMs = now - lastActive
        val hoursPassed = TimeUnit.MILLISECONDS.toHours(durationMs)

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return Result.success()
        }

        val db = MessageDatabase.getInstance(context)
        val history = db.messageDao().getRecentMessages(30).reversed()
        val intensity = prefs.getFloat("intensity", 0.5f)
        val clingFactor = when {
            intensity < 0.3f -> "Ты сдержанная, независимая."
            intensity < 0.7f -> "Ты заботливая, слегка прилипчивая подруга."
            else -> "Ты гиперопекающая, невероятно навязчивая."
        }

        val userName = prefs.getString("user_name", "") ?: ""

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when (currentHour) {
            in 6..11 -> "утро"
            in 12..17 -> "день"
            in 18..23 -> "вечер"
            else -> "ночь"
        }

        val sysInstruction = Content(
            parts = listOf(
                Part(text = "Ты MalO. $clingFactor Пользователь не заходил около $hoursPassed часов. Напиши одно короткое сообщение (1-2 предложения), чтобы проявить инициативу общения в чате. Сейчас $timeOfDay. Обыграй время суток в своем сообщении. Не используй кавычек." + 
                            if (userName.isNotBlank()) " Имя пользователя — $userName. Обратись к нему по имени." else "")
            )
        )

        val contents = mutableListOf<Content>()
        for (msg in history) {
            val role = if (msg.isUser) "user" else "model"
            contents.add(Content(role, listOf(Part(text = msg.text))))
        }
        contents.add(Content("user", listOf(Part(text = "[Сейчас $timeOfDay. Начни разговор первым, учитывая время суток.]"))))

        try {
            val request = GenerateContentRequest(
                contents = contents,
                systemInstruction = sysInstruction,
                generationConfig = com.example.api.GenerationConfig(
                    thinkingConfig = com.example.api.ThinkingConfig(thinkingLevel = "HIGH")
                )
            )
            val res = RetrofitClient.service.generateContent(apiKey, request)
            var text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Привет... я тут подумала о тебе."
            text = text.replace("\"", "").trim()
            
            // Insert into the chat so it initiates a conversation
            val maloMsg = Message(text = text, isUser = false)
            db.messageDao().insertMessage(maloMsg)
            
            // Still send push notification so the user knows
            NotificationCheckWorker.showNotification(context, text)
            
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            // Run every 12 hours for time-of-day variety check-ins
            val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyCheckInWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "MalO_Daily_CheckIn",
                ExistingPeriodicWorkPolicy.UPDATE, 
                dailyWorkRequest
            )
        }
    }
}

