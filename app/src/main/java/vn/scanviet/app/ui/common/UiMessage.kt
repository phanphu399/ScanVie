package vn.scanviet.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vn.scanviet.app.domain.PageImageMissingException

/**
 * Thông báo ngắn gửi từ ViewModel lên màn hình (snackbar).
 * ViewModel không giữ Context nên chỉ truyền mã chuỗi + tham số.
 */
data class UiMessage(
    @StringRes val resId: Int,
    val formatArgs: List<Any> = emptyList(),
)

fun Throwable.toUiMessage(): UiMessage {
    val error = toUiError()
    return UiMessage(
        resId = error.messageRes(),
        formatArgs = messageArgs(error),
    )
}

fun UiError.toUiMessage(): UiMessage = UiMessage(resId = messageRes(), formatArgs = messageArgs())

/**
 * Tham số cho chuỗi có placeholder.
 *
 * `toUiError()` đã làm mất thông tin chi tiết (nó chỉ trả về một mã enum), nên với những
 * lỗi cần con số cụ thể — ví dụ "Trang 3 bị thiếu file ảnh" — phải đọc từ chính exception.
 * Đây là lý do hàm nhận cả [Throwable] gốc thay vì chỉ [UiError].
 */
private fun Throwable.messageArgs(error: UiError): List<Any> = when {
    error == UiError.PAGE_IMAGE_MISSING && this is PageImageMissingException -> listOf(pageIndex)
    else -> error.messageArgs()
}

@Composable
fun UiMessage.asString(): String = stringResource(resId, *formatArgs.toTypedArray())
