package vn.scanviet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Trạng thái "đang tải" dùng chung cho mọi màn hình danh sách.
 *
 * Vì sao tách thành component dùng chung chứ không để riêng từng màn hình: trước đây
 * màn hình thư viện hiện "vòng xoay + chữ" còn màn hình thùng rác hiện một khoảng
 * trắng. Cùng một tình huống (đang đọc dữ liệu) mà hai màn hình cư xử khác nhau khiến
 * ứng dụng trông thiếu nhất quán — và khoảng trắng còn bị hiểu nhầm là màn hình lỗi.
 */
@Composable
fun LoadingContent(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
