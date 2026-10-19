package vn.scanviet.app.data.file

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider

/**
 * Cài đặt thật: file nằm trong filesDir/documents/<documentId>/page_XXXX.jpg
 *
 * Vì sao nằm trong filesDir (bộ nhớ riêng của app):
 * - Ứng dụng khác không đọc được.
 * - Không cần xin quyền bộ nhớ ngoài.
 * - Gỡ ứng dụng là xoá sạch, đúng kỳ vọng của người dùng về dữ liệu riêng tư.
 */
@Singleton
class LocalDocumentFileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : DocumentFileStorage {

    private val documentsRoot: File
        get() = File(context.filesDir, DOCUMENTS_DIR)

    private val scratchRoot: File
        get() = File(context.cacheDir, SCRATCH_DIR)

    override fun relativePathFor(documentId: String, pageIndex: Int): String {
        requireSafeSegment(documentId)
        return "$DOCUMENTS_DIR/$documentId/${pageFileName(pageIndex)}"
    }

    override suspend fun createDocumentDirectory(documentId: String): File = withContext(dispatchers.io) {
        requireSafeSegment(documentId)
        val directory = File(documentsRoot, documentId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Không tạo được thư mục tài liệu")
        }
        directory
    }

    override suspend fun writePageImage(
        documentId: String,
        pageIndex: Int,
        jpegBytes: ByteArray,
    ): String = withContext(dispatchers.io) {
        require(jpegBytes.isNotEmpty()) { "Ảnh trang rỗng" }
        val directory = createDocumentDirectory(documentId)
        val target = File(directory, pageFileName(pageIndex))
        // Ghi ra file tạm rồi đổi tên: tránh file dở dang nếu tiến trình bị dừng giữa chừng.
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeBytes(jpegBytes)
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IOException("Không ghi đè được ảnh trang")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Không hoàn tất được việc ghi ảnh trang")
        }
        relativePathFor(documentId, pageIndex)
    }

    override suspend fun resolve(relativePath: String): File = withContext(dispatchers.io) {
        val base = context.filesDir.canonicalFile
        val candidate = File(context.filesDir, relativePath).canonicalFile
        val basePath = base.path + File.separator
        if (!candidate.path.startsWith(basePath)) {
            // Chặn đường dẫn kiểu "../../" thoát khỏi vùng dữ liệu của ứng dụng.
            throw IllegalArgumentException("Đường dẫn không hợp lệ")
        }
        candidate
    }

    override suspend fun exists(relativePath: String): Boolean = withContext(dispatchers.io) {
        runCatching { resolve(relativePath).isFile }.getOrDefault(false)
    }

    override suspend fun documentBytes(documentId: String): Long = withContext(dispatchers.io) {
        val directory = File(documentsRoot, documentId)
        directory.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    override suspend fun deleteDocumentDirectory(documentId: String) = withContext(dispatchers.io) {
        val directory = File(documentsRoot, documentId)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IOException("Không xoá được thư mục tài liệu")
        }
    }

    override suspend fun listDocumentDirectories(): List<String> = withContext(dispatchers.io) {
        documentsRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()
    }

    override suspend fun cleanScratchSpace() = withContext(dispatchers.io) {
        if (scratchRoot.exists() && !scratchRoot.deleteRecursively()) {
            throw IOException("Không dọn được vùng nháp")
        }
    }

    private fun pageFileName(pageIndex: Int): String =
        String.format(Locale.US, PAGE_FILE_FORMAT, pageIndex)

    private fun requireSafeSegment(segment: String) {
        require(
            segment.isNotBlank() &&
                segment != "." &&
                segment != ".." &&
                !segment.contains('/') &&
                !segment.contains('\\'),
        ) { "Định danh tài liệu không hợp lệ" }
    }

    companion object {
        const val DOCUMENTS_DIR = "documents"
        const val SCRATCH_DIR = "scan_scratch"

        /** page_0001.jpg — 4 chữ số để sắp xếp theo tên file trùng thứ tự trang. */
        private const val PAGE_FILE_FORMAT = "page_%04d.jpg"
    }
}
