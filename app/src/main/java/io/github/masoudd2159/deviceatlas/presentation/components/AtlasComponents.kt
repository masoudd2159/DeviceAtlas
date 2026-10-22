package io.github.masoudd2159.deviceatlas.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import io.github.masoudd2159.deviceatlas.R
import io.github.masoudd2159.deviceatlas.presentation.theme.*

@Composable
fun ResponsiveContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FoundationSpacing.maxContentWidth).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(FoundationSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(FoundationSpacing.content),
            content = content,
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}

@Composable
fun InformationCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = FoundationSpacing.cardElevation,
    ) {
        Column(Modifier.padding(FoundationSpacing.content),
            verticalArrangement = Arrangement.spacedBy(FoundationSpacing.content), content = content)
    }
}

@Composable
fun InformationRow(label: String, value: String, technical: Boolean = false) {
    // A column remains readable at 200% fonts and with long translated labels.
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = if (technical) FontFamily.Monospace else FontFamily.Default,
            textDirection = if (technical) TextDirection.Ltr else TextDirection.Content,
        ))
    }
}

@Composable
fun StatusIndicator(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val (container, content) = statusColors(tone)
    Surface(modifier, shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(text, Modifier.padding(horizontal = FoundationSpacing.medium, vertical = FoundationSpacing.small),
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AvailabilityMessage(description: String) {
    InformationCard {
        StatusIndicator(stringResource(R.string.not_available), StatusTone.UNAVAILABLE)
        Text(description, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ActionRow(title: String, summary: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.heightIn(min = FoundationSpacing.touchTarget).padding(FoundationSpacing.content),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FoundationSpacing.content)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_forward), contentDescription = null,
                modifier = Modifier.size(FoundationSpacing.icon))
        }
    }
}

@Composable
fun SelectionGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), content = content)
}

@Composable
fun SettingsChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = FoundationSpacing.touchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(FoundationSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FoundationSpacing.medium),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PreferenceError(onRetry: () -> Unit) {
    InformationCard {
        Text(stringResource(R.string.preference_error), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(title = { Text(title, Modifier.semantics { heading() }) }, navigationIcon = {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
        }
    })
}

@Preview(name = "Components English", showBackground = true)
@Preview(name = "Components Persian", locale = "fa", showBackground = true)
@Preview(name = "Components large font", fontScale = 2f, showBackground = true)
@Composable
private fun ComponentsPreview() {
    DeviceAtlasTheme(dynamicColor = false) {
        ResponsiveContent {
            SectionHeader(stringResource(R.string.explore))
            AvailabilityMessage(stringResource(R.string.device_description))
            ActionRow(stringResource(R.string.language), stringResource(R.string.follow_system), {})
            InformationCard { InformationRow(stringResource(R.string.computation_core), stringResource(R.string.rust), technical = true) }
            SelectionGroup { SettingsChoice(stringResource(R.string.follow_system), true, {}) }
        }
    }
}
