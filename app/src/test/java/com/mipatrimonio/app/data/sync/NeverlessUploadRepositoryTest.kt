package com.mipatrimonio.app.data.sync

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NeverlessUploadRepositoryTest {
    @Test fun `valida extracto completo con una fila`() {
        assertTrue(validateNeverlessCsv(validCsv().toByteArray()) is NeverlessCsvValidation.Valid)
    }

    @Test fun `acepta BOM UTF8`() {
        assertTrue(validateNeverlessCsv(("\uFEFF" + validCsv()).toByteArray()) is NeverlessCsvValidation.Valid)
    }

    @Test fun `acepta columnas en otro orden`() {
        val headers = requiredHeaders().reversed()
        val csv = "${headers.joinToString(",")}\n${headers.indices.joinToString(",") { "value" }}"

        assertTrue(validateNeverlessCsv(csv.toByteArray()) is NeverlessCsvValidation.Valid)
    }

    @Test fun `rechaza cabecera sin ID`() {
        val csv = requiredHeaders().filterNot { it == "ID" }.joinToString(",") + "\nrow"

        assertEquals(
            NeverlessCsvValidation.Invalid(NeverlessCsvValidation.Reason.NOT_NEVERLESS),
            validateNeverlessCsv(csv.toByteArray()),
        )
    }

    @Test fun `rechaza archivo con solo cabecera`() {
        assertEquals(
            NeverlessCsvValidation.Invalid(NeverlessCsvValidation.Reason.NO_DATA),
            validateNeverlessCsv(requiredHeaders().joinToString(",").toByteArray()),
        )
    }

    @Test fun `rechaza archivo mayor de dos megabytes`() {
        assertEquals(
            NeverlessCsvValidation.Invalid(NeverlessCsvValidation.Reason.TOO_LARGE),
            validateNeverlessCsv(ByteArray(NEVERLESS_MAX_BYTES + 1)),
        )
    }

    @Test fun `rechaza UTF8 mal formado`() {
        assertEquals(
            NeverlessCsvValidation.Invalid(NeverlessCsvValidation.Reason.NOT_UTF8),
            validateNeverlessCsv(byteArrayOf(0xC3.toByte(), 0x28)),
        )
    }

    @Test fun `sube a la ruta exacta con segmentos codificados y cabeceras`() = runTest {
        var captured: SyncHttpRequest? = null
        val repository = repository(userId = "usuario con espacio", profileId = "perfil/uno") { request ->
            captured = request
            SyncHttpResponse(200, "{}")
        }

        repository.upload(validCsv())

        assertEquals(
            "/storage/v1/object/importaciones/usuario%20con%20espacio/perfil%2Funo/neverless.csv",
            captured?.pathAndQuery,
        )
        assertEquals("POST", captured?.method)
        assertEquals("true", captured?.headers?.get("x-upsert"))
        assertEquals("text/csv", captured?.headers?.get("Content-Type"))
        assertEquals(validCsv(), captured?.body)
    }

    @Test fun `objeto presente devuelve pendiente con fecha`() = runTest {
        val repository = repository { request ->
            assertEquals("/storage/v1/object/list/importaciones", request.pathAndQuery)
            assertEquals("user-1/profile-2/", JSONObject(request.body.orEmpty()).getString("prefix"))
            SyncHttpResponse(200, """[{"name":"neverless.csv","updated_at":"2026-11-13T10:15:00Z"}]""")
        }

        assertEquals(
            NeverlessPendingState.Pending("2026-11-13T10:15:00Z"),
            repository.status(),
        )
    }

    @Test fun `lista vacia devuelve no pendiente`() = runTest {
        val repository = repository { SyncHttpResponse(200, "[]") }

        assertEquals(NeverlessPendingState.NotPending, repository.status())
    }

    @Test fun `sin sesion devuelve NoSession sin llamar a red`() = runTest {
        val repository = NeverlessUploadRepository(profileIdProvider = { "profile-2" }, sessionProvider = { null })

        assertEquals(NeverlessPendingState.NoSession, repository.status())
    }

    @Test fun `respuesta 401 se convierte en sesion caducada`() = runTest {
        val repository = repository { SyncHttpResponse(401, "") }

        assertTrue(runCatching { repository.status() }.exceptionOrNull() is SyncSessionExpiredException)
    }

    @Test fun `archivo invalido no llega a la red`() = runTest {
        var remoteCalled = false
        val repository = repository {
            remoteCalled = true
            SyncHttpResponse(200, "{}")
        }

        runCatching { repository.upload("no es un extracto") }

        assertTrue(!remoteCalled)
    }

    private fun repository(
        userId: String = "user-1",
        profileId: String = "profile-2",
        remote: suspend (SyncHttpRequest) -> SyncHttpResponse,
    ) = NeverlessUploadRepository(
        profileIdProvider = { profileId },
        sessionProvider = { NeverlessRemoteSession(userId, remote) },
    )

    private fun validCsv(): String = requiredHeaders().joinToString(",") + "\n" +
        requiredHeaders().indices.joinToString(",") { "value" }

    private fun requiredHeaders() = listOf(
        "Type", "Date", "Amount received", "Asset received", "Amount sent", "Asset sent",
        "Fee", "Asset of the fee", "Description", "USD price of asset received",
        "USD price of asset sent", "ID",
    )
}
