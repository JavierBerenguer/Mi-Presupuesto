package com.mipatrimonio.app.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mipatrimonio.app.R
import com.mipatrimonio.app.domain.model.AssetType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabelsTest {
    @Test
    fun `prestamo p2p tiene etiqueta en espanol`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(R.string.common_asset_prestamo_p2p, AssetType.PRESTAMO_P2P.labelResource())
        assertEquals("Préstamo P2P", context.getString(AssetType.PRESTAMO_P2P.labelResource()))
    }
}
