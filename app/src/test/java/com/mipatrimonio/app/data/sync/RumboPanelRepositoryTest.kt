package com.mipatrimonio.app.data.sync

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RumboPanelRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `construye la ruta exacta con usuario y perfil`() {
        assertEquals(
            "/storage/v1/object/authenticated/paneles/user-1/profile-2.html",
            rumboPanelPath("user-1", "profile-2"),
        )
    }

    @Test fun `respuesta 200 guarda html y metadatos`() = runTest {
        val root = temporaryFolder.newFolder("no_backup")
        val repository = repository(root) {
            SyncHttpResponse(200, "<html>panel</html>", mapOf(
                "Content-Type" to listOf("text/html; charset=utf-8"),
                "Last-Modified" to listOf("Wed, 28 Oct 2026 10:00:00 GMT"),
            ))
        }

        val result = repository.download() as RumboPanelDownload.Available

        assertEquals("<html>panel</html>", result.panel.html)
        assertEquals("Wed, 28 Oct 2026 10:00:00 GMT", result.panel.uploadedAt)
        assertEquals(1234L, result.panel.downloadedAt)
        assertEquals("<html>panel</html>", File(root, "panel_rumbo/profile-2.html").readText())
        assertTrue(File(root, "panel_rumbo/profile-2.metadata.json").readText().contains("1234"))
    }

    @Test fun `html servido como texto plano se acepta por su contenido`() = runTest {
        val root = temporaryFolder.newFolder("plain")
        val html = "﻿\n<!DOCTYPE html><html><body>panel</body></html>"
        val result = repository(root) {
            SyncHttpResponse(200, html, mapOf("content-type" to listOf("text/plain; charset=utf-8")))
        }.download() as RumboPanelDownload.Available

        assertEquals(html, result.panel.html)
    }

    @Test fun `400 y 404 se consideran no publicado`() = runTest {
        for (status in listOf(400, 404)) {
            assertEquals(RumboPanelDownload.NotPublished, repository(temporaryFolder.newFolder()) {
                SyncHttpResponse(status, "")
            }.download())
        }
    }

    @Test fun `sin red devuelve la cache existente`() = runTest {
        val root = temporaryFolder.newFolder("offline")
        RumboPanelCache(root).write("profile-2", "<html>cache</html>", "date", 1L)
        val result = repository(root) { throw SyncConnectionException("Sin conexión") }.download()
            as RumboPanelDownload.Available

        assertEquals("<html>cache</html>", result.panel.html)
        assertTrue(result.offline)
        assertFalse(result.changed)
    }

    @Test fun `respuesta demasiado grande no modifica la cache`() = runTest {
        val root = temporaryFolder.newFolder("large")
        RumboPanelCache(root).write("profile-2", "<html>anterior</html>", "old", 1L)
        val repository = repository(root) {
            SyncHttpResponse(200, "x".repeat(20 * 1024 * 1024 + 1), mapOf("content-type" to listOf("text/html")))
        }

        val error = runCatching { repository.download() }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("20 MB"))
        assertEquals("<html>anterior</html>", RumboPanelCache(root).read("profile-2")?.html)
    }

    @Test fun `respuesta que no es html no modifica la cache`() = runTest {
        val root = temporaryFolder.newFolder("json")
        RumboPanelCache(root).write("profile-2", "<html>anterior</html>", "old", 1L)
        val error = runCatching {
            repository(root) { SyncHttpResponse(200, "{}", mapOf("content-type" to listOf("application/json"))) }.download()
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("no es HTML"))
        assertEquals("<html>anterior</html>", RumboPanelCache(root).read("profile-2")?.html)
    }

    private fun repository(root: File, remote: suspend (String) -> SyncHttpResponse) = RumboPanelRepository(
        profileIdProvider = { "profile-2" },
        hideAmountsProvider = { false },
        remote = remote,
        noBackupFilesDir = root,
        clock = { 1234L },
    )
}
