package com.mipatrimonio.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mipatrimonio.app.MainActivity
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.repository.PendingAutomationAlert
import com.mipatrimonio.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class PendingAutomationNotifier(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.auto_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.auto_notification_channel_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun publish(alert: PendingAutomationAlert) {
        if (!settings.settings.first().pendingAutomationAlertsEnabled) {
            cancel()
            return
        }
        if (alert.pendingStructureCount == 0 && alert.newMerchantCount == 0) {
            cancel()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val text = formatPendingAutomationAlert(
            pendingStructureCount = alert.pendingStructureCount,
            newMerchantCount = alert.newMerchantCount,
            structureSingular = context.getString(R.string.auto_notification_structure_one),
            structurePlural = context.getString(R.string.auto_notification_structure_other),
            merchantSingular = context.getString(R.string.auto_notification_merchant_one),
            merchantPlural = context.getString(R.string.auto_notification_merchant_other),
            separator = context.getString(R.string.auto_notification_separator),
        )
        val initialTab = if (alert.pendingStructureCount > 0) {
            MainActivity.TAB_PENDING
        } else {
            MainActivity.TAB_NEW_MERCHANTS
        }
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_AUTOMATION)
            .putExtra(MainActivity.EXTRA_AUTOMATION_TAB, initialTab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.auto_notification_title))
            .setContentText(context.getString(R.string.auto_notification_public_text))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.auto_notification_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "automation_pending"
        const val NOTIFICATION_ID = 6_901
        private const val REQUEST_CODE = 6_901
    }
}

fun formatPendingAutomationAlert(
    pendingStructureCount: Int,
    newMerchantCount: Int,
    structureSingular: String,
    structurePlural: String,
    merchantSingular: String,
    merchantPlural: String,
    separator: String,
): String = buildList {
    if (pendingStructureCount > 0) {
        add((if (pendingStructureCount == 1) structureSingular else structurePlural).format(pendingStructureCount))
    }
    if (newMerchantCount > 0) {
        add((if (newMerchantCount == 1) merchantSingular else merchantPlural).format(newMerchantCount))
    }
}.joinToString(separator)
