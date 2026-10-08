package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.Item

object NotificationHelper {

    const val CHANNEL_ID = "qiimo_quiz_channel"
    const val CHANNEL_NAME = "Qiimo Quiz Ogeysiisyada"
    const val NOTIFICATION_ID = 1001

    const val EXTRA_OPEN_QUIZ = "extra_open_quiz"
    const val EXTRA_OPEN_EXAM = "extra_open_exam"
    const val EXTRA_ITEM_ID = "extra_item_id"
    const val NOTIFICATION_REMINDER_ID = 1002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = "Ogeysiisyada su'aalaha qiimaha dawooyinka"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showQuizNotification(context: Context, item: Item) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_QUIZ, true)
            putExtra(EXTRA_ITEM_ID, item.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Qiimo Quiz: ${item.name}")
            .setContentText("Ma taqaanaa qiimaha ${item.name}? Taabo si aad uga jawaabto.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Qiimaha ${item.name} waa immisa marka macaamiil laga iibinayo? Taabo halkan si aad u xifdiso qiimaha.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Handled if permission not yet granted
        }
    }

    /**
     * Requirement: "Maanta weli ma jawaabin, 20 suaal ayaa kuu sugaya."
     * Tapping opens Exam mode. None during quiet hours.
     */
    fun showDailyReminderNotification(context: Context) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_EXAM, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Qiimo Quiz")
            .setContentText("Maanta weli ma jawaabin, 20 suaal ayaa kuu sugaya.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Maanta weli ma jawaabin, 20 suaal ayaa kuu sugaya. Taabo si aad imtixaanka u gasho oo aad u xifdiso qiimaha.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(NOTIFICATION_REMINDER_ID, builder.build())
        } catch (_: SecurityException) {
            // Handled if permission not yet granted
        }
    }
}
