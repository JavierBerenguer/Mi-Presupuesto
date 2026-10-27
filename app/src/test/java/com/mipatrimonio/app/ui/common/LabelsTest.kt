package com.mipatrimonio.app.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabelsTest {
    @Test
    fun `tipos de cuenta de broker conservan etiquetas en espanol`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("Cuenta de inversión", context.getString(R.string.common_account_inversion))
        assertEquals("Criptomonedas", context.getString(R.string.common_account_cripto))
    }
}
