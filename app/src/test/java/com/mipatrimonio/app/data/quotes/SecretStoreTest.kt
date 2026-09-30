package com.mipatrimonio.app.data.quotes

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretStoreTest {
    @Test fun `memoria guarda informa y borra sin incluir valor en representacion`() = runTest {
        val store = InMemorySecretStore()
        store.put(SecretStore.TWELVE_DATA_KEY, "super-secret")
        assertTrue(store.isConfigured(SecretStore.TWELVE_DATA_KEY))
        assertFalse(store.toString().contains("super-secret"))
        store.remove(SecretStore.TWELVE_DATA_KEY)
        assertNull(store.get(SecretStore.TWELVE_DATA_KEY))
    }
}
