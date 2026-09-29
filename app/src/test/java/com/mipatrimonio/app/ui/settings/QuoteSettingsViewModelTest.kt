package com.mipatrimonio.app.ui.settings

import com.mipatrimonio.app.data.quotes.InMemorySecretStore
import com.mipatrimonio.app.testutil.SettingsStoreRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuoteSettingsViewModelTest {
    @get:Rule val settingsRule = SettingsStoreRule()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `guardar y borrar solo expone estado configurada`() = runTest {
        val secrets = InMemorySecretStore()
        val viewModel = SettingsViewModel(settingsRule.repository, secrets)
        assertFalse(viewModel.uiState.first { !it.isLoading }.twelveDataConfigured)
        viewModel.saveTwelveDataKey("secret-never-in-state")
        val configured = viewModel.uiState.first { it.twelveDataConfigured }
        assertTrue(configured.toString().contains("secret-never-in-state").not())
        viewModel.deleteTwelveDataKey()
        assertFalse(viewModel.uiState.first { !it.twelveDataConfigured }.twelveDataConfigured)
        viewModel.saveOpenFigiKey("openfigi-secret")
        val openFigi = viewModel.uiState.first { it.openFigiConfigured }
        assertTrue(openFigi.toString().contains("openfigi-secret").not())
        viewModel.deleteOpenFigiKey()
        assertFalse(viewModel.uiState.first { !it.openFigiConfigured }.openFigiConfigured)
        viewModel.saveEodhdKey("eodhd-secret-never-in-state")
        val eodhd = viewModel.uiState.first { it.eodhdConfigured }
        assertTrue(eodhd.toString().contains("eodhd-secret-never-in-state").not())
        viewModel.deleteEodhdKey()
        assertFalse(viewModel.uiState.first { !it.eodhdConfigured }.eodhdConfigured)
    }
}
