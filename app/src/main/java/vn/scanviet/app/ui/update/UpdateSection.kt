package vn.scanviet.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.scanviet.app.R
import vn.scanviet.app.core.format.Formatters
import vn.scanviet.app.domain.model.AppRelease
import vn.scanviet.app.ui.components.SectionHeader

/**
 * Khối "Cập nhật" trong màn hình Cài đặt.
 *
 * Tách khỏi [vn.scanviet.app.ui.settings.SettingsScreen] vì khối này có nhiều trạng thái
 * riêng và sẽ còn phát triển (kênh thử nghiệm, xem lịch sử phiên bản…). Giữ nó trong file
 * Cài đặt sẽ khiến màn hình đó khó đọc.
 */
@Composable
fun UpdateSection(
    state: UpdateUiState,
    autoCheckEnabled: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
    onInstallHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionHeader(text = stringResource(R.string.settings_section_updates))
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            AutoCheckRow(
                checked = autoCheckEnabled,
                onCheckedChange = onAutoCheckChange,
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (state) {
                UpdateUiState.Idle -> CheckButton(onClick = onCheckNow)

                UpdateUiState.Checking -> CheckingRow()

                UpdateUiState.UpToDate -> {
                    Text(
                        text = stringResource(R.string.update_up_to_date),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CheckButton(onClick = onCheckNow)
                }

                is UpdateUiState.Available -> AvailableBlock(
                    release = state.release,
                    onDownload = onDownload,
                )

                is UpdateUiState.BannerDismissed -> AvailableBlock(
                    release = state.release,
                    onDownload = onDownload,
                )

                is UpdateUiState.Downloading -> DownloadingBlock(state)

                is UpdateUiState.InstallPermissionRequired -> {
                    Text(
                        text = stringResource(R.string.update_permission_needed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onDownload) {
                        Text(text = stringResource(R.string.update_download_button))
                    }
                }

                UpdateUiState.Installed -> {
                    Text(
                        text = stringResource(R.string.update_installed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onInstallHandled) {
                        Text(text = stringResource(R.string.update_done_button))
                    }
                }

                UpdateUiState.CheckFailed -> StatusMessage(
                    message = stringResource(R.string.update_check_failed),
                    onRetry = onCheckNow,
                )

                UpdateUiState.DownloadFailed -> StatusMessage(
                    // Tải lại sẽ thử lại đúng file đó, nên nhãn nút là "Kiểm tra cập nhật"
                    // để người dùng về lại trạng thái có bản mới rồi bấm tải.
                    message = stringResource(R.string.update_download_failed),
                    onRetry = onCheckNow,
                )

                UpdateUiState.Unsupported -> StatusMessage(
                    message = stringResource(R.string.update_unsupported),
                    onRetry = onCheckNow,
                )
            }
        }
    }
}

@Composable
private fun AutoCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_check_updates_label),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_check_updates_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CheckButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(text = stringResource(R.string.update_check_button))
    }
}

@Composable
private fun CheckingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.update_checking),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AvailableBlock(release: AppRelease, onDownload: () -> Unit) {
    Text(
        text = stringResource(R.string.update_available_title, release.versionName),
        style = MaterialTheme.typography.titleSmall,
    )
    if (release.sizeBytes > 0L) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.update_available_size,
                Formatters.fileSize(release.sizeBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (release.releaseNotes.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.update_notes_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = release.releaseNotes,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(onClick = onDownload) {
        Text(text = stringResource(R.string.update_download_button))
    }
}

@Composable
private fun DownloadingBlock(state: UpdateUiState.Downloading) {
    val percent = state.percent
    Text(
        text = if (percent != null) {
            stringResource(R.string.update_downloading, percent)
        } else {
            stringResource(R.string.update_downloading_unknown)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (percent != null) {
        // Tiến trình xác định được: hiện thanh chạy để người dùng thấy còn bao lâu.
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        // Máy chủ không cho biết tổng dung lượng: thanh chạy vô định, trung thực hơn là bịa số.
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatusMessage(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        CheckButton(onClick = onRetry)
    }
}
