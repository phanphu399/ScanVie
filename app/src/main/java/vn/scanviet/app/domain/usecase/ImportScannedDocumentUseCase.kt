package vn.scanviet.app.domain.usecase

import javax.inject.Inject
import vn.scanviet.app.domain.DocumentId
import vn.scanviet.app.domain.DocumentRules
import vn.scanviet.app.domain.EmptyDocumentException
import vn.scanviet.app.domain.InvalidDocumentTitleException
import vn.scanviet.app.domain.UnreadableScanPageException
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.scan.PageImageReader
import vn.scanviet.app.domain.storage.DocumentFileStorage

/**
 * Nhập các trang vừa quét vào ứng dụng.
 *
 * Đây là bước dễ hỏng nhất của luồng quét, nên thứ tự công việc được chốt như sau:
 *
 * 1. Kiểm tra tên và số trang TRƯỚC khi ghi bất cứ thứ gì (không tạo rác rồi phải dọn).
 * 2. Sinh [DocumentId] và tạo thư mục riêng cho tài liệu.
 * 3. Với từng URI: đọc ảnh -> ghi vào vùng riêng của app -> thu thập metadata.
 * 4. Chỉ khi MỌI ảnh đã ghi xong mới ghi bản ghi vào Room.
 * 5. Nếu bất kỳ bước nào lỗi: xoá thư mục vừa tạo rồi mới ném lỗi ra ngoài.
 *
 * Vì sao phải sao chép ngay: URI của trình quét là tệp TẠM trong vùng của Play services,
 * có thể bị xoá bất cứ lúc nào. Bản sao trong `filesDir/documents/<uuid>/` mới là
 * "nguồn sự thật" của ứng dụng.
 *
 * Bộ nhớ: chỉ giữ ảnh của MỘT trang tại một thời điểm, nên số trang không làm tăng RAM.
 */
class ImportScannedDocumentUseCase @Inject constructor(
    private val imageReader: PageImageReader,
    private val storage: DocumentFileStorage,
    private val createDocument: CreateDocumentUseCase,
) {

    suspend operator fun invoke(title: String, sourceUris: List<String>): Document {
        if (sourceUris.isEmpty()) throw EmptyDocumentException()
        DocumentRules.titleError(title)?.let { throw InvalidDocumentTitleException(it) }

        val documentId = DocumentId.new()
        return try {
            storage.createDocumentDirectory(documentId)
            val pages = sourceUris.mapIndexed { index, sourceUri ->
                val pageIndex = index + 1
                val image = imageReader.read(sourceUri) ?: throw UnreadableScanPageException()
                val relativePath = storage.writePageImage(documentId, pageIndex, image.bytes)
                NewDocumentPage(
                    relativePath = relativePath,
                    width = image.width,
                    height = image.height,
                    sizeBytes = image.bytes.size.toLong(),
                )
            }
            createDocument(documentId = documentId, title = title, pages = pages)
        } catch (throwable: Throwable) {
            // Không để lại thư mục/ảnh mồ côi khi việc ghi Room thất bại.
            runCatching { storage.deleteDocumentDirectory(documentId) }
            throw throwable
        }
    }
}
