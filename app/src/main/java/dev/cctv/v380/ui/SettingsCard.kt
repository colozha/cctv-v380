package dev.cctv.v380.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.cctv.v380.R

@Composable
internal fun SettingsCard(profileId: String, title: String, icon: ImageVector, summary: String? = null,
    canCollapse: Boolean = true, status: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(profileId, title) { mutableStateOf(false) }
    val expanded = open || !canCollapse
    val stateDescription = stringResource(if (expanded) R.string.section_expanded else R.string.section_collapsed)
    val clickLabel = stringResource(if (expanded) R.string.close_section else R.string.open_section, localizedCopy(title))
    CameraPanel {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { this.stateDescription = stateDescription }
            .clickable(enabled = canCollapse, role = Role.Button,
                onClickLabel = clickLabel) { open = !open },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!expanded && summary != null) Text(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status()
        if (expanded) content()
    }
}

@Composable
internal fun SettingsNotice(message: String, error: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = if (error) colors.errorContainer else colors.primaryContainer,
        contentColor = if (error) colors.onErrorContainer else colors.onPrimaryContainer,
        shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {
            if (error) Icon(Icons.Default.Warning, null, Modifier.size(22.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun SettingChoices(label: String, value: Int, choices: List<Pair<Int, String>>, enabled: Boolean,
    change: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (id, name) ->
            val selected = value == id
            val colors = MaterialTheme.colorScheme
            Surface(shape = RoundedCornerShape(50), color = colors.surfaceContainer,
                contentColor = if (!enabled) colors.onSurface.copy(alpha = .38f) else if (selected) colors.primary else colors.onSurface,
                border = BorderStroke(1.dp, if (selected && enabled) colors.primary else colors.outlineVariant)) {
                Row(Modifier.heightIn(min = 48.dp).selectable(selected, enabled = enabled,
                    role = Role.RadioButton, onClick = { change(id) }).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected, null, enabled = enabled, modifier = Modifier.size(24.dp))
                    Text(name, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun SettingsValue(label: String, value: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
