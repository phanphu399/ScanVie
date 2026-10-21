package vn.scanviet.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.scanviet.app.R
import vn.scanviet.app.core.format.Formatters

/**
 * Băng nhắc cập nhật ở màn hình Thư viện.
 *
 * Vì sao cần: khối cập nhật trong Cài đặt chỉ có ích với người **tự đi tìm** nó. Phần lớn người
 * dùng không bao giờ mở Cài đặt, nên một bản sửa lỗi có thể nằm im trên GitHub hàng tháng trời.
 * Băng này là cách duy nhất để họ biết.
 *
 * Bốn quyết định về giao diện:
 *
 * 1. **Không phải hộp thoại.** Người dùng vừa mở ứng dụng để quét tài liệu. Chặn họ bằng một
 *    hộp thoại phải xử lý trước khi làm được việc là hành vi bị Google Play đánh giá thấp và
 *    gây khó chịu. Một băng nằm trong luồng nội dung là đủ.
 *
 * 2. **Chỉ chỗ cho hai hành động: cập nhật, hoặc bỏ qua.** Không có "để sau" riêng vì đóng
 *    băng đã chính là để sau.
 *
 * 3. **Luôn hiện bản đang ở đâu.** Người dùng cần biết bản mới là bao nhiêu và nặng thế nào
 *    trước khi quyết định tải — nhất là khi đang dùng dữ liệu di động.
 *
 * 4. **Không có trạng thái lỗi ở đây.** Kiểm tra tự động khi thất bại thì im lặng; nếu băng
 *    hiện được thì việc kiểm tra đã thành công. Lỗi chỉ hiện trong Cài đặt, nơi người dùng chủ
 *    động yêu cầu kiểm tra.
 */
@Composable
fun UpdateBanner(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is UpdateUiState.Available -> BannerCard(modifier = modifier) {
            BannerContent(
                title = stringResource(R.string.update_banner_title),
                message = bannerMessage(state),
                actionLabel = stringResource(R.string.update_download_button),
                onAction = onUpdate,
                onDismiss = onDismiss,
            )
        }

        // Đã bấm cập nhật: giữ băng lại để tiến trình không biến mất, nhưng bỏ nút đóng —
        // huỷ giữa chừng sẽ để lại file APK dở dang trong bộ nhớ đệm.
        is UpdateUiState.Downloading -> BannerCard(modifier = modifier) {
            BannerDownloading(state)
        }

        // Đã tải xong nhưng chưa được phép cài. Người dùng vừa bị đưa sang màn hình hệ thống;
        // giữ băng để họ biết phải quay lại đây bấm tiếp.
        is UpdateUiState.InstallPermissionRequired -> BannerCard(modifier = modifier) {
            BannerContent(
                title = stringResource(R.string.update_banner_title),
                message = stringResource(R.string.update_permission_needed),
                actionLabel = stringResource(R.string.update_download_button),
                onAction = onUpdate,
                onDismiss = null,
            )
        }

        // Các trạng thái còn lại không cần chiếm chỗ trên màn hình chính.
        else -> Unit
    }
}

/** Ví dụ: "Có bản cập nhật 0.6.0 (44,9 MB)". */
@Composable
private fun bannerMessage(state: UpdateUiState.Available): String =
    if (state.release.sizeBytes > 0L) {
        stringResource(
            R.string.update_banner_message,
            state.release.versionName,
            Formatters.fileSize(state.release.sizeBytes),
        )
    } else {
        stringResource(R.string.update_banner_message_no_size, state.release.versionName)
    }

@Composable
private fun BannerCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        content()
    }
}

@Composable
private fun BannerContent(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onAction) {
                    Text(text = actionLabel)
                }
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.update_banner_dismiss))
                    }
                }
            }
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(R.string.update_banner_dismiss),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun BannerDownloading(state: UpdateUiState.Downloading) {
    // Đọc vào biến cục bộ: `percent` là thuộc tính có getter tự viết nên Kotlin không cho
    // smart-cast khi dùng trực tiếp trong `if`.
    val percent = state.percent
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = if (percent != null) {
                stringResource(R.string.update_downloading, percent)
            } else {
                stringResource(R.string.update_downloading_unknown)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.size(8.dp))
        if (percent != null) {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
