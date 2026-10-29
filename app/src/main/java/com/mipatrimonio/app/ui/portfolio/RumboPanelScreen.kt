package com.mipatrimonio.app.ui.portfolio

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipatrimonio.app.R
import com.mipatrimonio.app.data.sync.RumboPanelRepository
import com.mipatrimonio.app.ui.common.EmptyState
import com.mipatrimonio.app.ui.common.LoadingBox
import com.mipatrimonio.app.ui.common.appViewModel
import java.io.ByteArrayInputStream

@Composable
fun RumboPanelScreen(
    onOpenSync: () -> Unit,
    viewModel: RumboPanelViewModel = run {
        val context = LocalContext.current
        appViewModel { container ->
            RumboPanelViewModel(RumboPanelRepository(container.sync, container.settings, context.noBackupFilesDir))
        }
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (state.status) {
        RumboPanelStatus.LOADING -> LoadingBox()
        RumboPanelStatus.NO_CONFIGURATION -> EmptyState(
            Icons.Filled.ShowChart,
            stringResource(R.string.rumbo_panel_no_configuration),
            actionLabel = stringResource(R.string.rumbo_panel_open_sync),
            onAction = onOpenSync,
        )
        RumboPanelStatus.NOT_PUBLISHED -> EmptyState(
            Icons.Filled.ShowChart,
            stringResource(R.string.rumbo_panel_not_published),
            actionLabel = stringResource(R.string.rumbo_panel_reload),
            onAction = viewModel::reload,
        )
        RumboPanelStatus.UNAVAILABLE -> EmptyState(
            Icons.Filled.CloudOff,
            state.error ?: stringResource(R.string.rumbo_panel_unavailable),
            actionLabel = stringResource(R.string.rumbo_panel_retry),
            onAction = viewModel::reload,
        )
        RumboPanelStatus.READY -> Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.rumbo_panel_updated, state.panel?.uploadedAt.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::reload, enabled = !state.refreshing) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.rumbo_panel_reload))
                    }
                }
                if (state.offline) Text(
                    stringResource(R.string.rumbo_panel_offline),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            if (state.requiresReveal && !state.revealed) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.rumbo_panel_hidden_warning))
                    Button(onClick = viewModel::reveal) { Text(stringResource(R.string.rumbo_panel_show)) }
                }
            } else {
                state.panel?.let { panel -> IsolatedRumboWebView(panel.html, Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun IsolatedRumboWebView(html: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            if (viewContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
            WebView(viewContext).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.domStorageEnabled = false
                settings.setGeolocationEnabled(false)
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                        callback?.invoke(origin, false, false)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? =
                        blockedResponse().takeIf { shouldBlockRumboPanelRequest(request.url.toString()) }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                        handleNavigation(request.url.toString())

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean = handleNavigation(url)

                    private fun handleNavigation(url: String): Boolean {
                        if (shouldOpenRumboPanelLinkExternally(url)) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
    )
}

private fun blockedResponse() = WebResourceResponse(
    "text/plain",
    "utf-8",
    403,
    "Blocked",
    emptyMap(),
    ByteArrayInputStream(ByteArray(0)),
)
