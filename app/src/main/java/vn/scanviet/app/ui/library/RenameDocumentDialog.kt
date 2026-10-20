package vn.scanviet.app.ui.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import vn.scanviet.app.R
import vn.scanviet.app.domain.DocumentRules
import vn.scanviet.app.domain.TitleValidationError

/**
 * Hộp thoại đổi tên. Kiểm tra ngay khi người dùng gõ bằng đúng quy tắc của domain
 * ([DocumentRules]) nên không thể lưu một tên không hợp lệ.
 *
 * `rememberSaveable(initialTitle)` — khoá theo tên ban đầu là điều bắt buộc, không
 * phải chi tiết vụn: Compose tái dùng composable giữa hai tài liệu khác nhau. Nếu chỉ
 * `rememberSaveable` trơn, đổi tên tài liệu A rồi mở hộp thoại của tài liệu B trong
 * cùng một màn hình cha, ô nhập sẽ giữ TÊN CŨ của A. Khoá theo [initialTitle] buộc
 * trạng thái được tạo lại đúng khi mở cho tài liệu khác.
 */
@Composable
fun RenameDocumentDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    val validationError = DocumentRules.titleError(title)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = stringResource(R.string.rename_dialog_label)) },
                isError = validationError != null,
                supportingText = validationError?.let { error ->
                    { Text(text = error.message()) }
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                enabled = validationError == null,
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun TitleValidationError.message(): String = when (this) {
    TitleValidationError.EMPTY -> stringResource(R.string.rename_error_empty)
    TitleValidationError.TOO_LONG -> stringResource(
        R.string.rename_error_too_long,
        DocumentRules.MAX_TITLE_LENGTH,
    )
}
