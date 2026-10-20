package vn.scanviet.app.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import vn.scanviet.app.domain.DocumentNotFoundException
import vn.scanviet.app.domain.OcrFailedException
import vn.scanviet.app.domain.model.OcrStatus
import vn.scanviet.app.domain.model.OcrText
import vn.scanviet.app.domain.model.StoredOcrLine
import vn.scanviet.app.domain.ocr.OcrEngine
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.storage.DocumentFileStorage

/**
 * Nhận dạng chữ cho toàn bộ trang của một tài liệu và lưu kết quả lại.
 *
 * Bốn quyết định nghiệp vụ, mỗi cái vì một lý do cụ thể:
 *
 * 1. **Bỏ qua trang đã nhận dạng rồi.** Chạy lại OCR trên trang đã có chữ là tốn thời gian
 *    vô ích: cùng một ảnh cho cùng một kết quả. Chỉ những trang còn thiếu mới được xử lý,
 *    nên một tài liệu 20 trang bị hỏng ở trang 15 không phải chạy lại 14 trang đầu.
 *
 * 2. **Một trang lỗi không làm hỏng cả tài liệu.** Trang không đọc được sẽ bị bỏ qua và
 *    ghi nhận; các trang còn lại vẫn được lưu. Người dùng mất chữ của một trang còn hơn
 *    mất chữ của tất cả.
 *
 * 3. **Nếu KHÔNG trang nào nhận dạng được thì đó là lỗi**, không phải thành công rỗng.
 *    Trả về thành công với nội dung rỗng khiến người dùng tưởng tài liệu không có chữ.
 *
 * 4. **Trạng thái được ghi ngay cả khi lỗi** (`FAILED`). Nhờ vậy danh sách tài liệu phản
 *    ánh đúng: người dùng thấy "Nhận dạng chữ bị lỗi" và biết cần thử lại, thay vì thấy
 *    "Chưa nhận dạng chữ" như thể chưa từng bấm gì.
 *
 * Ảnh chỉ được đọc tại chỗ trên máy; không có lời gọi mạng nào trong luồng này.
 */
class RecognizeDocumentTextUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val storage: DocumentFileStorage,
    private val ocrEngine: OcrEngine,
) {

    /** @return số trang ĐÃ nhận dạng thêm được trong lần chạy này (không tính trang có sẵn). */
    suspend operator fun invoke(documentId: String): Int {
        // Kiểm tra tài liệu còn tồn tại TRƯỚC khi làm gì: tránh bật trạng thái RUNNING cho
        // một bản ghi đã bị xoá ở màn hình khác.
        repository.observeDocument(documentId).first()
            ?: throw DocumentNotFoundException(documentId)

        val pages = repository.observePages(documentId).first()
        if (pages.isEmpty()) return 0

        val alreadyRecognized = repository.observeOcrText(documentId).first()
            .associateBy { it.pageIndex }

        val results = mutableListOf<OcrText>()
        var newlyRecognized = 0

        repository.setOcrStatus(documentId, OcrStatus.RUNNING)

        for (page in pages.sortedBy { it.pageIndex }) {
            val existing = alreadyRecognized[page.pageIndex]
            if (existing != null) {
                // Giữ nguyên kết quả cũ để bước ghi đè bên dưới không xoá mất chữ đã có.
                results += existing
                continue
            }
            if (!storage.exists(page.imageFile)) continue
            val file = runCatching { storage.resolve(page.imageFile) }.getOrNull() ?: continue
            val recognized = runCatching { ocrEngine.recognize(file, page.rotationDegrees) }
                .getOrNull() ?: continue

            results += OcrText(
                documentId = documentId,
                pageIndex = page.pageIndex,
                lines = recognized.lines.map { line ->
                    StoredOcrLine(
                        text = line.text,
                        left = line.left,
                        top = line.top,
                        right = line.right,
                        bottom = line.bottom,
                    )
                },
                imageWidth = recognized.width,
                imageHeight = recognized.height,
            )
            newlyRecognized++
        }

        if (results.isEmpty()) {
            repository.setOcrStatus(documentId, OcrStatus.FAILED)
            throw OcrFailedException(documentId = documentId, failedPageCount = pages.size)
        }

        repository.replaceOcrText(documentId, results)
        repository.setOcrStatus(documentId, OcrStatus.DONE)
        return newlyRecognized
    }
}
