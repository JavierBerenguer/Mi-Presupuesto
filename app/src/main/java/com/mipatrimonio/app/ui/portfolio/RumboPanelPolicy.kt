package com.mipatrimonio.app.ui.portfolio

import java.net.URI

fun shouldBlockRumboPanelRequest(url: String): Boolean {
    if (url.startsWith("//")) return true
    val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
    return scheme != null && scheme !in setOf("about", "blob", "data")
}

fun shouldOpenRumboPanelLinkExternally(url: String): Boolean =
    runCatching { URI(url).scheme?.lowercase() in setOf("http", "https") }.getOrDefault(false)
