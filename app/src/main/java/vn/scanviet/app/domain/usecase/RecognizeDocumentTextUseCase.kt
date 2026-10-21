package vn.scanviet.app.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import vn.scanviet.app.domain.DocumentNotFoundException
import vn.scanviet.app.domain.OcrFailedException
import vn.scanviet.app.domain.OcrFailureReason
import vn.scanviet.app.domain.model.OcrStatus
import vn.scanviet.app.domain.model.OcrText
import vn.scanviet.app.domain.model.StoredOcrLine
import vn.scanviet.app.domain.ocr.OcrEngineProvider
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.repository.SettingsRepository
import vn.scanviet.app.domain.storage.DocumentFileStorage

/**
 * Nhận dạng chữ cho toàn bộ trang của một tài liệu và lưu kết quả lại.
 *
 * Năm quyết định nghiệp vụ, mỗi cái vì một lý do cụ thể:
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
 * 5. **Engine được chọn theo CÀI ĐẶT tại thời điểm chạy**, không cố định lúc khởi tạo.
 *    Người dùng đổi engine trong Cài đặt rồi bấm nhận dạng lại thì phải dùng engine mới
 *    ngay, không cần tắt mở ứng dụng.
 *
 * Ảnh chỉ được đọc tại chỗ trên máy; không có lời gọi mạng nào trong luồng này.
 */
class RecognizeDocumentTextUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val storage: DocumentFileStorage,
    private val settingsRepository: SettingsRepository,
    private val engineProvider: OcrEngineProvider,
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

        // Lấy engine TRƯỚC khi bật trạng thái RUNNING. Nếu model chưa cài được (hết chỗ,
        // asset thiếu), lỗi được ném ra ở đây — khi đó tài liệu chưa bị đánh dấu RUNNING,
        // nên giao diện không kẹt ở trạng thái "đang nhận dạng".
        //
        // `TessdataInstallException` được ném tiếp chứ KHÔNG nuốt thành `null`: đây là lỗi
        // cài đặt (thử lại được, và người dùng cần biết), khác hẳn với "ảnh không có chữ".
        val settings = settingsRepository.settings.first()
        val engine = engineProvider.forKind(
            kind = settings.ocrEngineKind,
            quality = settings.ocrModelQuality,
        )

        val results = mutableListOf<OcrText>()
        var newlyRecognized = 0

        // Đếm lý do thất bại để thông báo cho người dùng biết PHẢI LÀM GÌ. Trước đây mọi
        // trường hợp đều thành một câu "Lỗi nhận dạng" chung, nên người dùng không phân biệt
        // được "ảnh mờ quá" (chụp lại) với "không đọc được file" (lỗi hệ thống).
        var unreadablePages = 0
        var engineFailedPages = 0

        /** Số trang engine chạy xong nhưng không tìm thấy dòng chữ nào. */
        var emptyPages = 0

        repository.setOcrStatus(documentId, OcrStatus.RUNNING)

        for (page in pages.sortedBy { it.pageIndex }) {
            val existing = alreadyRecognized[page.pageIndex]
            if (existing != null) {
                // Giữ nguyên kết quả cũ để bước ghi đè bên dưới không xoá mất chữ đã có.
                results += existing
                continue
            }
            if (!storage.exists(page.imageFile)) {
                unreadablePages++
                continue
            }
            val file = runCatching { storage.resolve(page.imageFile) }.getOrNull()
            if (file == null) {
                unreadablePages++
                continue
            }
            // Lỗi của MỘT trang bị bỏ qua (quyết định 2 ở trên), nhưng lỗi cài đặt model đã
            // được xử lý trước vòng lặp nên không bị che ở đây.
            //
            // Phân biệt hai kiểu thất bại: engine trả `null` nghĩa là KHÔNG ĐỌC ĐƯỢC ẢNH
            // hoặc KHÔNG KHỞI TẠO ĐƯỢC engine; engine ném lỗi cũng vậy. Còn engine trả về
            // kết quả với 0 dòng nghĩa là ẢNH KHÔNG CÓ CHỮ — hai việc khác hẳn nhau.
            val recognized = runCatching { engine.recognize(file, page.rotationDegrees) }
            if (recognized.isFailure) {
                engineFailedPages++
                continue
            }
            val pageResult = recognized.getOrNull()
            if (pageResult == null) {
                engineFailedPages++
                continue
            }

            val lines = pageResult.lines.map { line ->
                StoredOcrLine(
                    text = line.text,
                    left = line.left,
                    top = line.top,
                    right = line.right,
                    bottom = line.bottom,
                )
            }
            // Trang chạy xong nhưng KHÔNG có dòng chữ nào vẫn được lưu (để lần sau không nhận
            // dạng lại trang đó — xem `OcrPageEntity`), nhưng phải đếm riêng. Nếu không đếm,
            // ta không phân biệt được "cả tài liệu không có chữ" với "engine hỏng".
            if (lines.isEmpty()) emptyPages++

            results += OcrText(
                documentId = documentId,
                pageIndex = page.pageIndex,
                lines = lines,
                imageWidth = pageResult.width,
                imageHeight = pageResult.height,
            )
            newlyRecognized++
        }

        // Điều kiện phải là CÓ ÍT NHẤT MỘT DÒNG CHỮ, không phải "có ít nhất một trang".
        //
        // Đây là lỗi thật đã bị bắt bởi test: bản trước kiểm tra `results.isEmpty()`. Khi
        // engine chạy xong mà ảnh không có chữ, `results` chứa một `OcrText` với `lines` rỗng
        // — tức `results` KHÔNG rỗng. Hệ quả: tài liệu được đánh dấu `DONE` dù không có một
        // chữ nào, người dùng thấy "Đã có chữ", mở ra thì trống, và PDF xuất ra thiếu lớp chữ
        // mà không có cảnh báo nào.
        val totalLines = results.sumOf { it.lines.size }
        if (totalLines == 0) {
            repository.setOcrStatus(documentId, OcrStatus.FAILED)
            throw OcrFailedException(
                documentId = documentId,
                failedPageCount = pages.size,
                reason = when {
                    // Không đọc được ảnh nhiều hơn hẳn -> vấn đề file, không phải nội dung ảnh.
                    unreadablePages > 0 && unreadablePages >= engineFailedPages ->
                        OcrFailureReason.PAGE_UNREADABLE

                    // Engine trả `null`/ném lỗi cho mọi trang -> engine không dùng được.
                    engineFailedPages > 0 -> OcrFailureReason.ENGINE_UNAVAILABLE

                    // Còn lại: engine chạy được, ảnh đọc được, nhưng không có chữ nào.
                    else -> OcrFailureReason.NO_TEXT_FOUND
                },
            )
        }

        repository.replaceOcrText(documentId, results)
        repository.setOcrStatus(documentId, OcrStatus.DONE)
        return newlyRecognized
    }
}
