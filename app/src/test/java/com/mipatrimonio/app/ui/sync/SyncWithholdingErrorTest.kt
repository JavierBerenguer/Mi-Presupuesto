package com.mipatrimonio.app.ui.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWithholdingErrorTest {
    @Test fun `detecta rechazo por columna de retencion ausente`() {
        assertTrue(isMissingWithholdingColumnError("PGRST204: Could not find the 'retencion' column in the schema cache"))
        assertFalse(isMissingWithholdingColumnError("El servidor rechazó los datos (409)"))
        assertFalse(isMissingWithholdingColumnError(null))
    }
}
