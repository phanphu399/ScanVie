package vn.scanviet.app.testing

import java.io.OutputStream
import vn.scanviet.app.domain.export.PdfExporter
import vn.scanviet.app.domain.export.PdfPageSource

/**
 * Bộ sinh PDF giả: ghi lại đúng những gì use case yêu cầu, không sinh PDF thật.
 *
 * Nhờ nó, test use case xuất PDF chạy trên JVM thuần — không cần Robolectric, không cần
 * `android.graphics.pdf.PdfDocument`. Đây chính là lợi ích của việc tách port.
 */
class FakePdfExporter : PdfExporter {

    /** Các trang được yêu cầu vẽ, theo đúng thứ tự use case truyền xuống. */
    val exportedPages = mutableListOf<List<PdfPageSource>>()

    /** Số lần `export` được gọi. */
    val callCount: Int get() = exportedPages.size

    /** Bật lên để mô phỏng lỗi ghi PDF giữa chừng. */
    var failWith: Throwable? = null

    override suspend fun export(output: OutputStream, pages: List<PdfPageSource>) {
        exportedPages += pages
        failWith?.let { throw it }
    }

    /** Các trang của lần gọi gần nhất, tiện cho việc đọc test. */
    fun lastPages(): List<PdfPageSource> = exportedPages.last()
}
