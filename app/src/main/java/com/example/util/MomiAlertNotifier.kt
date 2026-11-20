package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object MomiAlertNotifier {
    const val CHANNEL_ID = "momi_alerts_high"
    const val CHANNEL_NAME = "Momi Alerts & Messages"
    const val EXTRA_ALERT_ROUTE = "momi_alert_route"

    private val vibrationPattern = longArrayOf(0L, 180L, 90L, 180L)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts and notification sounds for new messages, activity, and gifts on Momi"
            enableLights(true)
            enableVibration(true)
            setVibrationPattern(vibrationPattern)
            setSound(soundUri, audioAttrs)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean {
        val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!permissionGranted) return false
        return runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)
    }

    fun playAlertSound(context: Context) {
        runCatching {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALL)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, soundUri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
        }
        triggerShortHapticAlert(context)
    }

    @SuppressLint("MissingPermission")
    private fun triggerShortHapticAlert(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(140L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(140L, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(140L)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun postAlertNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        route: String? = null,
        soundEnabled: Boolean = true
    ) {
        ensureChannel(context)
        if (soundEnabled) {
            playAlertSound(context)
        }
        if (!canPostNotifications(context)) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!route.isNullOrBlank()) {
                putExtra(EXTRA_ALERT_ROUTE, route)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { "Momi" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (soundEnabled) {
            builder
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(vibrationPattern)
        } else {
            builder.setSilent(true)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }
}
