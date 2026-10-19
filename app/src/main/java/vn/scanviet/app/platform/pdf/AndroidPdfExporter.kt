package vn.scanviet.app.platform.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.domain.export.PdfExporter
import vn.scanviet.app.domain.export.PdfPageSource

/**
 * Sinh PDF bằng `android.graphics.pdf.PdfDocument` có sẵn trong Android.
 *
 * Vì sao dùng API hệ thống thay vì thư viện như iText/PDFBox: chỉ cần vẽ ảnh lên trang,
 * và giữ số dependency ở mức tối thiểu là yêu cầu của dự án. Bản thân `PdfDocument` là
 * API công khai, đã kiểm chứng bằng `javap` trên `android.jar` 35.
 *
 * Ba quyết định đáng giải trình:
 *
 * 1. **Khổ A4 (595×842 pt) và vừa-khít (contain-fit).** PDF A4 mở được ở mọi máy in và
 *    mọi phần mềm, còn ảnh được thu nhỏ để luôn nằm trọn trong trang và giữ đúng tỉ lệ.
 *    Ảnh rộng hơn cao (chụp ngang) sẽ tự đổi khổ trang thành ngang — PDF không có khái niệm
 *    "xoay 90 độ rồi co", nên đổi kích thước trang là cách thể hiện đúng nhất.
 *
 * 2. **Nền trắng được tô trước khi vẽ ảnh.** Ảnh JPEG không có kênh trong suốt, nhưng nếu
 *    sau này có ảnh PNG thì nền trong suốt sẽ thành đen trên máy in. Tô nền trắng là bảo hiểm
 *    rẻ tiền.
 *
 * 3. **Chỉ một ảnh nằm trong bộ nhớ tại một thời điểm.** Mỗi trang được giải mã, vẽ, rồi
 *    `recycle()` ngay. 20 trang A4 ở 300 DPI nếu giữ hết cùng lúc có thể ngốn hàng trăm MB.
 *
 * Giới hạn đã biết: ảnh được vẽ ở kích thước bitmap đang có, không tự nâng lên 300 DPI.
 * Muốn nét hơn thì trình quét phải xuất ảnh lớn hơn, không phải việc của lớp này.
 */
@Singleton
class AndroidPdfExporter @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : PdfExporter {

    override suspend fun export(output: OutputStream, pages: List<PdfPageSource>) {
        withContext(dispatchers.io) {
            val document = PdfDocument()
            try {
                pages.forEachIndexed { index, page ->
                    // Số trang trong PDF đánh từ 1.
                    document.addPage(page, pageNumber = index + 1)
                }
                document.writeTo(output)
            } finally {
                // close() giải phóng tài nguyên native; phải gọi cả khi vẽ lỗi giữa chừng.
                document.close()
            }
        }
    }

    private fun PdfDocument.addPage(source: PdfPageSource, pageNumber: Int) {
        val bitmap = decodeScaledBitmap(source)

        // Xoay 90/270 độ làm chiều rộng và chiều cao của ảnh đổi chỗ cho nhau, nên phải
        // quyết định khổ trang theo kích thước SAU khi xoay. Quên bước này là in ra một
        // trang dọc chứa ảnh ngang bị cắt cụt hai bên.
        val quarterTurn = source.rotationDegrees % 360 == 90 || source.rotationDegrees % 360 == 270
        val contentWidth = if (quarterTurn) bitmap.height else bitmap.width
        val contentHeight = if (quarterTurn) bitmap.width else bitmap.height

        // Khổ A4: trang ngang nếu nội dung ngang, trang dọc nếu nội dung dọc.
        val landscape = contentWidth > contentHeight
        val pageWidth = if (landscape) A4_HEIGHT_PT else A4_WIDTH_PT
        val pageHeight = if (landscape) A4_WIDTH_PT else A4_HEIGHT_PT

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber)
            .create()

        try {
            val page = startPage(pageInfo)
            try {
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                // Vẽ ảnh vào giữa trang, thu nhỏ vừa khít nhưng KHÔNG phóng to ảnh nhỏ
                // (phóng to chỉ làm mờ, không thêm thông tin).
                //
                // Khung đích được tính theo kích thước SAU khi xoay, để ảnh vẫn nằm trọn
                // trong trang. Nếu tính theo kích thước gốc thì ảnh xoay 90 độ sẽ tràn ra
                // ngoài mép giấy và bị cắt cụt.
                val scale = minOf(
                    pageWidth.toFloat() / contentWidth,
                    pageHeight.toFloat() / contentHeight,
                )
                val drawWidth = contentWidth * scale
                val drawHeight = contentHeight * scale
                val left = (pageWidth - drawWidth) / 2f
                val top = (pageHeight - drawHeight) / 2f

                canvas.save()
                // Xoay quanh tâm khung đích để ảnh không lệch ra ngoài trang.
                canvas.rotate(
                    source.rotationDegrees.toFloat(),
                    left + drawWidth / 2f,
                    top + drawHeight / 2f,
                )
                canvas.drawBitmap(
                    bitmap,
                    Rect(0, 0, bitmap.width, bitmap.height),
                    RectF(left, top, left + drawWidth, top + drawHeight),
                    PAINT,
                )
                canvas.restore()
            } finally {
                finishPage(page)
            }
        } finally {
            // Giải phóng pixel ngay: chỉ giữ một trang trong RAM tại một thời điểm.
            bitmap.recycle()
        }
    }

    /** `pageNumber` nội bộ do PdfDocument dùng để đánh số; ta tự đếm cho chắc chắn liên tục. */
    private var pageNumber = 1
        set(value) {
            field = value + 1
        }

    private fun decodeScaledBitmap(source: PdfPageSource): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.imageFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Không đọc được ảnh trang để xuất PDF")
        }

        val options = BitmapFactory.Options().apply {
            // Giảm mẫu nếu ảnh lớn hơn nhiều so với trang A4 ở ~200 DPI. Vừa đủ nét khi in,
            // vừa tránh nạp một bitmap 4000×3000 không cần thiết cho một trang giấy A4.
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(source.imageFile.absolutePath, options)
            ?: throw IOException("Không đọc được ảnh trang để xuất PDF")
        return bitmap
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_RENDER_WIDTH && height / (sample * 2) >= MAX_RENDER_HEIGHT) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        /** A4 ở 72 DPI: 210mm × 297mm. Đây là đơn vị điểm (pt) mà PdfDocument dùng. */
        const val A4_WIDTH_PT = 595
        const val A4_HEIGHT_PT = 842

        /**
         * Mức chi tiết tối đa khi vẽ: 1654×2339 px, tương đương A4 ở ~200 DPI.
         * Trên mức này mắt thường không phân biệt được khi đọc tài liệu, mà RAM tăng gấp đôi
         * mỗi lần nhân đôi cạnh.
         */
        const val MAX_RENDER_WIDTH = 1654
        const val MAX_RENDER_HEIGHT = 2339

        private val PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
    }
}
