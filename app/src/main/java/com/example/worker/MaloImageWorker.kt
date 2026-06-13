package com.example.worker

import android.content.Context
import androidx.work.*
import com.example.data.Message
import com.example.data.MessageDatabase
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MaloImageWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences(NotificationCheckWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean(NotificationCheckWorker.KEY_NOTIFICATIONS_ENABLED, true)

        if (!notificationsEnabled) return Result.success()

        val glitchSeed = Random.nextInt(1, 1000)
        
        val db = MessageDatabase.getInstance(context)
        val msg = Message(
            text = "...",
            isUser = false,
            fileType = "malo_glitch_image",
            fileName = "malo_${glitchSeed}"
        )
        db.messageDao().insertMessage(msg)
        
        NotificationCheckWorker.showNotification(context, "🖼️ Новое изображение получено...")

        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaloImageWorker>(4, TimeUnit.HOURS)
                .setInitialDelay(Random.nextLong(1, 4), TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "MaloImageWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
