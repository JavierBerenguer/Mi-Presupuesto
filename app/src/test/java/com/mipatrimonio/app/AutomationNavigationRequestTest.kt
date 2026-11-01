package com.mipatrimonio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationNavigationRequestTest {
    @Test fun `extras validos abren automatizacion en la pestana indicada`() {
        assertEquals(
            AutomationNavigationRequest(MainActivity.TAB_PENDING),
            automationNavigationRequest(MainActivity.OPEN_AUTOMATION, MainActivity.TAB_PENDING),
        )
        assertEquals(
            AutomationNavigationRequest(MainActivity.TAB_NEW_MERCHANTS),
            automationNavigationRequest(MainActivity.OPEN_AUTOMATION, MainActivity.TAB_NEW_MERCHANTS),
        )
    }

    @Test fun `extras ausentes o desconocidos no navegan`() {
        assertNull(automationNavigationRequest(null, null))
        assertNull(automationNavigationRequest("unknown", MainActivity.TAB_PENDING))
        assertNull(automationNavigationRequest(MainActivity.OPEN_AUTOMATION, "unknown"))
    }
}
