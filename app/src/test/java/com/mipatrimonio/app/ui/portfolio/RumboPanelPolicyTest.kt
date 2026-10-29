package com.mipatrimonio.app.ui.portfolio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RumboPanelPolicyTest {
    @Test fun `bloquea cualquier url que pueda salir a la red`() {
        listOf(
            "http://example.com/a",
            "https://example.com/a",
            "ws://example.com/socket",
            "wss://example.com/socket",
            "ftp://example.com/file",
            "//example.com/scheme-relative",
        ).forEach { assertTrue(it, shouldBlockRumboPanelRequest(it)) }
        assertFalse(shouldBlockRumboPanelRequest("data:image/png;base64,AA"))
        assertFalse(shouldBlockRumboPanelRequest("about:blank"))
    }

    @Test fun `solo enlaces http y https se abren fuera`() {
        assertTrue(shouldOpenRumboPanelLinkExternally("http://example.com"))
        assertTrue(shouldOpenRumboPanelLinkExternally("https://example.com"))
        assertFalse(shouldOpenRumboPanelLinkExternally("file:///tmp/panel.html"))
        assertFalse(shouldOpenRumboPanelLinkExternally("javascript:alert(1)"))
    }
}
