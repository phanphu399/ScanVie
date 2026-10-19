package vn.scanviet.app.testing

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import vn.scanviet.app.domain.export.DocumentOutputTarget
import vn.scanviet.app.domain.export.PdfOutputTarget

/**
 * Đích ghi giả: giữ nội dung ghi vào một mảng trong bộ nhớ thay vì file thật.
 *
 * Ghi nhận thêm hai điều mà test cần kiểm chứng:
 * - [openCount]: use case có mở đúng MỘT lần không (mở hai lần nghĩa là ghi đè lẫn nhau).
 * - [closedStreams]: stream có được use case đóng không (rò rỉ file descriptor là lỗi thật,
 *   không phải chuyện hình thức).
 */
class FakeDocumentOutputTarget : DocumentOutputTarget {

    val writtenBytes = ByteArrayOutputStream()

    var openCount: Int = 0
        private set

    var lastTarget: PdfOutputTarget? = null
        private set

    /** Bật lên để mô phỏng việc không mở được đích ghi (hết chỗ, URI bị thu hồi). */
    var failOpenWith: Throwable? = null

    /** Theo dõi stream nào đã bị đóng. */
    val closedStreams = mutableListOf<OutputStream>()

    override suspend fun open(target: PdfOutputTarget): OutputStream {
        openCount += 1
        lastTarget = target
        failOpenWith?.let { throw it }
        // Ghi ra file thật khi đích là file trong vùng nháp, giống hệt hành vi của
        // ContentUriDocumentOutputTarget. Nhờ vậy test màn hình chi tiết kiểm tra được
        // câu hỏi có thật: "file PDF chia sẻ có tồn tại trên đĩa không?".
        return when (target) {
            is PdfOutputTarget.LocalFile -> TrackingOutputStream(
                underlying = target.file.outputStream(),
            )

            is PdfOutputTarget.SafUri -> TrackingOutputStream(underlying = writtenBytes)
        }
    }

    private inner class TrackingOutputStream(
        private val underlying: OutputStream,
    ) : OutputStream() {
        override fun write(b: Int) {
            underlying.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            underlying.write(b, off, len)
        }

        override fun close() {
            closedStreams += this
            underlying.close()
        }
    }
}
