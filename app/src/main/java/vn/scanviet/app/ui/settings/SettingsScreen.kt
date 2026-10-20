package vn.scanviet.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.scanviet.app.BuildConfig
import vn.scanviet.app.R
import vn.scanviet.app.core.format.Formatters
import vn.scanviet.app.domain.model.ThemePreference
import vn.scanviet.app.ui.components.SectionHeader
import vn.scanviet.app.ui.update.UpdateSection
import vn.scanviet.app.ui.update.UpdateViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(text = stringResource(R.string.settings_section_appearance))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.selectableGroup()) {
                    ThemeOption(
                        label = stringResource(R.string.settings_theme_system),
                        selected = uiState.settings.theme == ThemePreference.SYSTEM,
                        onSelect = { viewModel.onThemeChange(ThemePreference.SYSTEM) },
                    )
                    ThemeOption(
                        label = stringResource(R.string.settings_theme_light),
                        selected = uiState.settings.theme == ThemePreference.LIGHT,
                        onSelect = { viewModel.onThemeChange(ThemePreference.LIGHT) },
                    )
                    ThemeOption(
                        label = stringResource(R.string.settings_theme_dark),
                        selected = uiState.settings.theme == ThemePreference.DARK,
                        onSelect = { viewModel.onThemeChange(ThemePreference.DARK) },
                    )
                }
            }

            SectionHeader(text = stringResource(R.string.settings_section_scan))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SwitchRow(
                        label = stringResource(R.string.settings_auto_ocr_label),
                        supportingText = stringResource(R.string.settings_auto_ocr_hint),
                        checked = uiState.settings.autoOcr,
                        onCheckedChange = viewModel::onAutoOcrChange,
                    )
                    SwitchRow(
                        label = stringResource(R.string.settings_keep_original_label),
                        supportingText = null,
                        checked = uiState.settings.keepOriginalImage,
                        onCheckedChange = viewModel::onKeepOriginalImageChange,
                    )
                }
            }

            SectionHeader(text = stringResource(R.string.settings_section_data))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_storage_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Formatters.fileSize(uiState.storageBytes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onOpenTrash) {
                        Text(text = stringResource(R.string.settings_open_trash))
                    }
                    Text(
                        text = stringResource(R.string.settings_backup_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader(text = stringResource(R.string.settings_section_about))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            UpdateSection(
                state = updateState,
                autoCheckEnabled = uiState.settings.checkForUpdates,
                onAutoCheckChange = viewModel::onCheckForUpdatesChange,
                onCheckNow = updateViewModel::check,
                onDownload = updateViewModel::downloadAndInstall,
                onInstallHandled = updateViewModel::onInstallHandled,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Tự kiểm tra khi mở màn hình Cài đặt, nhưng chỉ khi người dùng còn bật tính năng này.
    // Dùng LaunchedEffect với khoá là lựa chọn hiện tại: bật lại trong lúc đang ở màn hình
    // cũng kích hoạt kiểm tra ngay, còn tắt thì không gọi mạng.
    LaunchedEffect(uiState.settings.checkForUpdates) {
        if (uiState.settings.checkForUpdates) updateViewModel.check()
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    HorizontalDivider()
}

@Composable
private fun SwitchRow(
    label: String,
    supportingText: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
