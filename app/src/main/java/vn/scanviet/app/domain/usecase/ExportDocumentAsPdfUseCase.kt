package vn.scanviet.app.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import vn.scanviet.app.domain.DocumentNotFoundException
import vn.scanviet.app.domain.EmptyDocumentException
import vn.scanviet.app.domain.PageImageMissingException
import vn.scanviet.app.domain.export.DocumentOutputTarget
import vn.scanviet.app.domain.export.PdfExporter
import vn.scanviet.app.domain.export.PdfOutputTarget
import vn.scanviet.app.domain.export.PdfPageSource
import vn.scanviet.app.domain.export.PdfTextLine
import vn.scanviet.app.domain.model.DocumentPage
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.storage.DocumentFileStorage

/**
 * Xuất một tài liệu đã lưu thành PDF nhiều trang, kèm lớp chữ ẩn copy được.
 *
 * Bốn điểm nghiệp vụ đáng chú ý:
 *
 * 1. **Thứ tự trang theo `pageIndex`, không theo thứ tự trả về.** Trang 1 phải là trang 1
 *    trong PDF dù Room trả về thế nào. Bỏ qua việc sắp xếp là lỗi khiến người dùng in ra
 *    một hợp đồng bị đảo trang.
 *
 * 2. **Thiếu ảnh là lỗi, không bỏ qua.** Nếu một trang mất file ảnh trên đĩa thì
 *    [storage.resolve] ném lỗi và việc xuất thất bại toàn bộ. Cố ý vậy: lặng lẽ bỏ qua trang
 *    thiếu sẽ tạo ra một PDF "thành công" nhưng thiếu trang — tai hoạ với người dùng ký
 *    hợp đồng hay nộp hồ sơ, và còn tệ hơn nhiều so với một thông báo lỗi rõ ràng.
 *
 * 3. **Chữ đã nhận dạng được ghép vào đúng trang và đúng thứ tự dòng.** Lớp chữ ẩn phải
 *    nằm khớp với ảnh; gán nhầm chữ sang trang khác sẽ khiến người dùng copy ra nội dung
 *    không thuộc trang đó — kiểu lỗi rất khó phát hiện bằng mắt.
 *
 * 4. **Trang chưa nhận dạng vẫn xuất bình thường, chỉ thiếu lớp chữ.** Xuất PDF không được
 *    phụ thuộc vào việc OCR đã chạy: người dùng có quyền chỉ muốn lưu ảnh scan.
 *
 * Việc đóng stream thuộc về use case (`.use { }`), không thuộc [PdfExporter].
 */
class ExportDocumentAsPdfUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val storage: DocumentFileStorage,
    private val outputTarget: DocumentOutputTarget,
    private val pdfExporter: PdfExporter,
) {

    suspend operator fun invoke(documentId: String, output: PdfOutputTarget) {
        // Kiểm tra tài liệu có tồn tại TRƯỚC khi mở stream: tránh tạo file trống cho tài
        // liệu đã bị xoá vĩnh viễn giữa lúc người dùng đang bấm nút.
        repository.observeDocument(documentId).first()
            ?: throw DocumentNotFoundException(documentId)

        val pages = repository.observePages(documentId).first()
            .sortedBy { page -> page.pageIndex }
        if (pages.isEmpty()) throw EmptyDocumentException()

        val ocrByPage = repository.observeOcrText(documentId).first()
            .associateBy { text -> text.pageIndex }

        val sources = pages.map { page ->
            page.toSource(documentId, ocrByPage[page.pageIndex]?.lines.orEmpty().map { line ->
                PdfTextLine(
                    text = line.text,
                    left = line.left,
                    top = line.top,
                    right = line.right,
                    bottom = line.bottom,
                )
            })
        }

        outputTarget.open(output).use { stream ->
            pdfExporter.export(stream, sources)
        }
    }

    private suspend fun DocumentPage.toSource(
        documentId: String,
        textLines: List<PdfTextLine>,
    ): PdfPageSource {
        if (!storage.exists(imageFile)) {
            // Không ném kèm đường dẫn hay tên file: thông điệp lỗi phải an toàn khi hiển thị.
            throw PageImageMissingException(documentId = documentId, pageIndex = pageIndex)
        }
        return PdfPageSource(
            imageFile = storage.resolve(imageFile),
            rotationDegrees = rotationDegrees,
            textLines = textLines,
        )
    }
}
