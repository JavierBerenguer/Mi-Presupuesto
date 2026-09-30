package com.mipatrimonio.app.ui.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryIconPickerViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `busca por nombre espanol sin depender de tildes`() {
        val viewModel = CategoryIconPickerViewModel()
        viewModel.setQuery("optica")
        assertEquals(listOf("glasses"), viewModel.state.value.filtered.map { it.key })
    }

    @Test fun `seleccion invalida vuelve a icono generico`() {
        val viewModel = CategoryIconPickerViewModel()
        viewModel.select("car")
        assertEquals("car", viewModel.state.value.selectedKey)
        viewModel.select("desconocido")
        assertNull(viewModel.state.value.selectedKey)
        assertTrue(viewModel.state.value.filtered.isNotEmpty())
    }
}
