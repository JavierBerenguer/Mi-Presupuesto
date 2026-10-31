package com.mipatrimonio.app.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.data.repository.NewMerchantAlert
import com.mipatrimonio.app.data.repository.PendingAutomationAlert
import com.mipatrimonio.app.testutil.SettingsStoreRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PendingAutomationNotifierTest {
    @get:Rule val settingsRule = SettingsStoreRule()

    @Test fun `texto omite ceros usa plurales y separa recuentos`() {
        fun text(structures: Int, merchants: Int) = formatPendingAutomationAlert(
            structures, merchants,
            "%d notificación sin estructura", "%d notificaciones sin estructura",
            "%d comercio nuevo", "%d comercios nuevos", " · ",
        )
        assertEquals("1 notificación sin estructura", text(1, 0))
        assertEquals("2 notificaciones sin estructura · 1 comercio nuevo", text(2, 1))
        assertEquals("3 comercios nuevos", text(0, 3))
        assertEquals("", text(0, 0))
    }

    @Test @Config(sdk = [34])
    fun `no publica sin permiso`() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val context: Context = application
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS),
        )
        val notifier = PendingAutomationNotifier(context, settingsRule.repository)

        notifier.publish(PendingAutomationAlert(1, 0))

        assertTrue(shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }

    @Test @Config(sdk = [32])
    fun `preferencia desactivada cancela la notificacion visible`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val notifier = PendingAutomationNotifier(context, settingsRule.repository)
        notifier.createChannel()
        notifier.publish(PendingAutomationAlert(1, 0))
        assertEquals(1, manager.allNotifications.size)

        notifier.publish(PendingAutomationAlert(0, 0))
        assertTrue(manager.allNotifications.isEmpty())
        notifier.publish(PendingAutomationAlert(0, 1))
        assertEquals(1, manager.allNotifications.size)

        settingsRule.repository.setPendingAutomationAlertsEnabled(false)
        notifier.publish(PendingAutomationAlert(1, 0))

        assertTrue(manager.allNotifications.isEmpty())
    }

    @Test @Config(sdk = [32])
    fun `comercio nuevo nombra la regla general y ofrece seguir usandola`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val notifier = PendingAutomationNotifier(context, settingsRule.repository)
        notifier.createChannel()

        notifier.publish(PendingAutomationAlert(0, 1, NewMerchantAlert("r1", "McDonald's", "Gasto con tarjeta", "s1", "mcdonald's")))

        val notification = manager.allNotifications.single()
        assertEquals("Nuevo comercio: McDonald's", notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals("Regla general: Gasto con tarjeta", notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
        assertEquals("Seguir usando la regla general", notification.actions.single().title.toString())
        assertTrue(manager.getNotification(newMerchantNotificationId("s1", "mcdonald's")) != null)
    }
}
