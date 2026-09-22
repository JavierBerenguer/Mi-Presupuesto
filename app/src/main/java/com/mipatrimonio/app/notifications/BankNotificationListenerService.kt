package com.mipatrimonio.app.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mipatrimonio.app.MiPatrimonioApplication
import com.mipatrimonio.app.domain.notifications.BankNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BankNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val notification = bankNotificationFromRaw(
            packageName = packageName,
            title = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE),
            text = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT),
            postTime = sbn.postTime,
        )
        val repository = (application as MiPatrimonioApplication).container.notifications
        serviceScope.launch {
            runCatching {
                repository.ensureKnown(packageName)
                repository.ingest(notification)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

fun bankNotificationFromRaw(
    packageName: String,
    title: CharSequence?,
    text: CharSequence?,
    postTime: Long,
): BankNotification = BankNotification(
    packageName = packageName,
    title = title?.toString().orEmpty(),
    text = text?.toString().orEmpty(),
    postedAt = postTime,
)
