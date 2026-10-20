package vn.scanviet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Sắc thái của trạng thái rỗng.
 *
 * Vì sao cần phân biệt: trước đây mọi trạng thái đều tô biểu tượng bằng màu `primary`
 * (xanh). Một biểu tượng CẢNH BÁO màu xanh "tích cực" gửi sai tín hiệu — người dùng
 * đọc màu trước khi đọc chữ. Màu đỏ dành riêng cho lỗi giúp phân biệt "chưa có gì"
 * (bình thường) với "có gì đó hỏng" (cần xử lý) chỉ bằng một cái liếc mắt.
 */
enum class EmptyStateTone {
    /** Trạng thái bình thường: chưa có tài liệu, tìm không thấy kết quả. */
    NEUTRAL,

    /** Có lỗi thật: đọc dữ liệu thất bại, trình quét không dùng được. */
    ERROR,
}

/**
 * Trạng thái rỗng / trạng thái lỗi, dùng chung cho mọi màn hình danh sách.
 * Có thể kèm một nút hành động (ví dụ "Thử lại").
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    tone: EmptyStateTone = EmptyStateTone.NEUTRAL,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = when (tone) {
                EmptyStateTone.NEUTRAL -> MaterialTheme.colorScheme.primary
                EmptyStateTone.ERROR -> MaterialTheme.colorScheme.error
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            // Nút ĐẦY (Button) chứ không phải chữ trần (TextButton): ở trạng thái lỗi,
            // hành động khắc phục là việc chính người dùng cần làm, nên phải nổi bật
            // hơn phần chữ giải thích phía trên.
            Button(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}
