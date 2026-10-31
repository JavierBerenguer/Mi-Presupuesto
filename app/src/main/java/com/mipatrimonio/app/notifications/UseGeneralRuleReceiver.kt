package com.mipatrimonio.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.mipatrimonio.app.MiPatrimonioApplication
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.notifications.NotificationRuleValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Botón «Seguir usando la regla general» del aviso de comercio nuevo: hace lo mismo que
 * Automatización › Comercios nuevos › «Usar siempre la general» y retira el aviso.
 */
class UseGeneralRuleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val recordId = intent.getStringExtra(EXTRA_RECORD_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val app = context.applicationContext as? MiPatrimonioApplication ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val ok = runCatching { app.container.notifications.createRuleForRecord(recordId, NotificationRuleValues()) }.isSuccess
            NotificationManagerCompat.from(context).cancel(notificationId)
            if (!ok) launch(Dispatchers.Main) {
                Toast.makeText(context, R.string.auto_notification_keep_general_error, Toast.LENGTH_LONG).show()
            }
            pending.finish()
        }
    }

    companion object {
        const val ACTION = "com.mipatrimonio.app.action.USE_GENERAL_RULE"
        const val EXTRA_RECORD_ID = "recordId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
