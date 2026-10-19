package vn.scanviet.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Thông báo ngắn gửi từ ViewModel lên màn hình (snackbar).
 * ViewModel không giữ Context nên chỉ truyền mã chuỗi + tham số.
 */
data class UiMessage(
    @StringRes val resId: Int,
    val formatArgs: List<Any> = emptyList(),
)

fun Throwable.toUiMessage(): UiMessage = UiMessage(
    resId = toUiError().messageRes(),
    formatArgs = toUiError().messageArgs(),
)

fun UiError.toUiMessage(): UiMessage = UiMessage(resId = messageRes(), formatArgs = messageArgs())

@Composable
fun UiMessage.asString(): String = stringResource(resId, *formatArgs.toTypedArray())
