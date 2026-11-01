package com.mipatrimonio.app.data

import com.mipatrimonio.app.testutil.SettingsStoreRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsRepositoryAutomationAlertsTest {
    @get:Rule val settingsRule = SettingsStoreRule()

    @Test fun `avisos de automatizacion estan activos por defecto y persisten`() = runTest {
        assertTrue(settingsRule.repository.settings.first().pendingAutomationAlertsEnabled)

        settingsRule.repository.setPendingAutomationAlertsEnabled(false)

        assertFalse(settingsRule.reopen().settings.first().pendingAutomationAlertsEnabled)
    }
}
