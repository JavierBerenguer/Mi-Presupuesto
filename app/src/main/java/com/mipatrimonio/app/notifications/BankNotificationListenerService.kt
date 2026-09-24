package com.mipatrimonio.app.notifications

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mipatrimonio.app.MiPatrimonioApplication
import com.mipatrimonio.app.domain.notifications.BankNotification
import com.mipatrimonio.app.domain.notifications.NotificationFields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BankNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (!shouldProcessBankNotification(packageName, this.packageName, sbn.notification.flags)) return

        val extras = sbn.notification.extras
        val notification = bankNotificationFromRaw(
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE),
            titleBig = extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            text = extras.getCharSequence(Notification.EXTRA_TEXT),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.asList().orEmpty(),
            messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.mapNotNull { (it as? Bundle)?.getCharSequence("text") }
                .orEmpty(),
            ticker = sbn.notification.tickerText,
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
    titleBig: CharSequence? = null,
    bigText: CharSequence? = null,
    subText: CharSequence? = null,
    textLines: List<CharSequence> = emptyList(),
    messages: List<CharSequence> = emptyList(),
    ticker: CharSequence? = null,
): BankNotification = BankNotification(
    packageName = packageName,
    title = title.asTrimmedString().ifEmpty { titleBig.asTrimmedString() },
    text = composeNotificationText(text, bigText, subText, textLines, messages, ticker),
    postedAt = postTime,
    fields = NotificationFields(
        hadTitle = title.hasText() || titleBig.hasText(),
        hadText = text.hasText(),
        hadBigText = bigText.hasText(),
        hadSubText = subText.hasText(),
        hadTextLines = textLines.any { it.hasText() },
        hadMessages = messages.any { it.hasText() },
        hadTicker = ticker.hasText(),
    ),
)

internal fun shouldProcessBankNotification(packageName: String, ownPackageName: String, flags: Int): Boolean =
    packageName != ownPackageName && flags and Notification.FLAG_GROUP_SUMMARY == 0

private fun composeNotificationText(
    text: CharSequence?,
    bigText: CharSequence?,
    subText: CharSequence?,
    textLines: List<CharSequence>,
    messages: List<CharSequence>,
    ticker: CharSequence?,
): String {
    val regular = text.asTrimmedString()
    val expanded = bigText.asTrimmedString()
    val primary = expanded.takeIf { it.isNotEmpty() && (regular.isEmpty() || it.length > regular.length) }
        ?: regular
    val fragments = mutableListOf<String>()
    fun addFragment(raw: CharSequence?) {
        val candidate = raw.asTrimmedString()
        if (candidate.isEmpty() || fragments.any { it.contains(candidate, ignoreCase = true) }) return
        fragments.removeAll { candidate.contains(it, ignoreCase = true) }
        fragments += candidate
    }
    addFragment(primary)
    addFragment(subText)
    textLines.forEach(::addFragment)
    messages.forEach(::addFragment)
    addFragment(ticker)
    return fragments.joinToString("\n")
}

private fun CharSequence?.asTrimmedString(): String = this?.toString()?.trim().orEmpty()

private fun CharSequence?.hasText(): Boolean = this?.toString()?.isNotBlank() == true
