package com.mipatrimonio.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mipatrimonio.app.ui.components.SectionCard
import com.mipatrimonio.app.ui.navigation.MenuLocation
import com.mipatrimonio.app.ui.navigation.SecondaryMenuDestination
import com.mipatrimonio.app.ui.theme.MiPatrimonioTheme

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    MoreContent(onNavigate = onNavigate)
}

@Composable
private fun MoreContent(onNavigate: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SectionCard {
                SecondaryMenuDestination.at(MenuLocation.MAS).forEach { destination ->
                    MoreLink(
                        destination = destination,
                        onClick = { onNavigate(destination.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreLink(
    destination: SecondaryMenuDestination,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(destination.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(destination.title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF15201C)
@Composable
private fun MoreDarkPreview() {
    MiPatrimonioTheme {
        MoreContent(onNavigate = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun MoreLightPreview() {
    MiPatrimonioTheme(modoOscuro = false) {
        MoreContent(onNavigate = {})
    }
}
