package dev.cctv.v380.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.cctv.v380.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSettingsScreen(
    language: AppLanguage,
    theme: AppTheme,
    onLanguageSelected: (AppLanguage) -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.app_settings_title), fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            },
        )
    }) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).widthIn(max = 680.dp)
                .verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.app_settings_heading),
                Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.app_settings_description),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PreferenceCard(
                title = stringResource(R.string.language),
                description = stringResource(R.string.language_description),
                icon = WifiIcon,
            ) {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceOption(
                        label = stringResource(R.string.language_english),
                        selected = language == AppLanguage.ENGLISH,
                        modifier = Modifier.weight(1f),
                        onClick = { onLanguageSelected(AppLanguage.ENGLISH) },
                    )
                    PreferenceOption(
                        label = stringResource(R.string.language_indonesian),
                        selected = language == AppLanguage.INDONESIAN,
                        modifier = Modifier.weight(1f),
                        onClick = { onLanguageSelected(AppLanguage.INDONESIAN) },
                    )
                }
            }
            PreferenceCard(
                title = stringResource(R.string.theme),
                description = stringResource(R.string.theme_description),
                icon = Icons.Default.Settings,
            ) {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreferenceOption(
                        label = stringResource(R.string.theme_light),
                        selected = theme == AppTheme.LIGHT,
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeSelected(AppTheme.LIGHT) },
                    )
                    PreferenceOption(
                        label = stringResource(R.string.theme_dark),
                        selected = theme == AppTheme.DARK,
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeSelected(AppTheme.DARK) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PreferenceCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionIcon(icon)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun PreferenceOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier.selectable(selected, role = Role.RadioButton, onClick = onClick).heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick = null)
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

