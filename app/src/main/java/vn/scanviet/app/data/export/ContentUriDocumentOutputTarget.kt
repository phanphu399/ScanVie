package vn.scanviet.app.data.export

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.domain.export.DocumentOutputTarget
import vn.scanviet.app.domain.export.PdfOutputTarget

/**
 * Mở luồng ghi cho hai loại đích: URI do người dùng chọn qua SAF, và file tạm trong cacheDir.
 *
 * Vì sao `mode = "wt"` chứ không phải `"w"`: `"w"` ghi đè từ đầu nhưng KHÔNG cắt bớt phần
 * còn lại. Xuất một PDF 5 trang lên chỗ file 20 trang cũ sẽ để lại đuôi file cũ — file kết
 * quả là một PDF hỏng không mở được. `"wt"` (write + truncate) mới là hành vi đúng.
 *
 * Vì sao `LocalFile` không cần kiểm tra đường dẫn: `File` đến từ
 * [vn.scanviet.app.domain.storage.DocumentFileStorage.createScratchFile], nơi đã chặn dấu
 * phân cách. Lớp này không nhận chuỗi đường dẫn từ UI.
 */
@Singleton
class ContentUriDocumentOutputTarget @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : DocumentOutputTarget {

    override suspend fun open(target: PdfOutputTarget): OutputStream = withContext(dispatchers.io) {
        when (target) {
            is PdfOutputTarget.SafUri -> openSaf(target.uri)
            is PdfOutputTarget.LocalFile -> openLocalFile(target.file)
        }
    }

    private fun openSaf(uri: String): OutputStream {
        val parsed = runCatching { android.net.Uri.parse(uri) }.getOrNull()
            ?: throw IOException("Địa chỉ lưu file không hợp lệ")
        return context.contentResolver.openOutputStream(parsed, "wt")
            ?: throw IOException("Không mở được nơi lưu file")
    }

    private fun openLocalFile(file: File): OutputStream {
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("Không tạo được thư mục tạm")
        }
        return FileOutputStream(file)
    }
}
