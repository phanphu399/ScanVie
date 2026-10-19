package vn.scanviet.app.platform.pdf

import android.app.Application
import android.graphics.pdf.PdfDocument
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tài liệu hoá một GIỚI HẠN của bộ test, bằng thực nghiệm.
 *
 * Robolectric KHÔNG chạy được `android.graphics.pdf.PdfDocument`. Bằng chứng đã đo được:
 * `PdfDocument()` rồi `startPage(...)` ném `IllegalStateException: document is closed!`
 * ngay ở lần gọi đầu tiên. Robolectric không có shadow cho `PdfDocument` (đã kiểm tra
 * `shadows-framework-4.14.1.jar`: không có lớp `ShadowPdfDocument`), nên lời gọi rơi xuống
 * implementation thật và thất bại vì thiếu môi trường native.
 *
 * Hệ quả cho dự án:
 * - [AndroidPdfExporter] KHÔNG thể kiểm thử bằng unit test. Việc nó sinh ra PDF hợp lệ chỉ
 *   được xác nhận trên thiết bị thật (MVP_SPEC.md §6.3, mục "xuất PDF").
 * - Phần logic đáng kiểm thử của việc xuất PDF — thứ tự trang, xử lý trang thiếu ảnh, đóng
 *   stream, từ chối tài liệu rỗng — nằm ở
 *   [vn.scanviet.app.domain.usecase.ExportDocumentAsPdfUseCase] và ĐÃ được kiểm thử đầy đủ
 *   trên JVM thuần nhờ tách cổng [vn.scanviet.app.domain.export.PdfExporter].
 *
 * Test này giữ lại như một cái khoá: nó khẳng định giới hạn đó là sự thật. Nếu một ngày
 * Robolectric hỗ trợ `PdfDocument`, test này sẽ đỏ và đó chính là tín hiệu để viết test thật
 * cho exporter (bỏ `try/catch` bên dưới đi).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RobolectricPdfLimitationTest {

    @Test
    fun `Robolectric khong chay duoc PdfDocument nen exporter phai kiem tra tren thiet bi`() {
        val unsupported = runCatching {
            val document = PdfDocument()
            try {
                document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            } finally {
                document.close()
            }
        }.exceptionOrNull()

        assertTrue(
            "Robolectric đã chạy được PdfDocument. Hãy viết test thật cho AndroidPdfExporter " +
                "và xoá test giới hạn này.",
            unsupported is IllegalStateException,
        )
    }
}
