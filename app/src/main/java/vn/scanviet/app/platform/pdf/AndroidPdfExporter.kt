package vn.scanviet.app.platform.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.domain.export.PdfExporter
import vn.scanviet.app.domain.export.PdfPageSource
import vn.scanviet.app.domain.export.PdfTextLine
import vn.scanviet.app.domain.export.invisibleTextPlacement

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

                // Lớp chữ ẩn vẽ SAU ảnh, trên cùng hệ toạ độ đích, nên không bị ảnh che.
                // Phải nằm ngoài khối save/rotate vì toạ độ chữ đã theo ảnh SAU khi xoay.
                drawInvisibleTextLayer(
                    canvas = canvas,
                    lines = source.textLines,
                    target = RectF(left, top, left + drawWidth, top + drawHeight),
                )
            } finally {
                finishPage(page)
            }
        } finally {
            // Giải phóng pixel ngay: chỉ giữ một trang trong RAM tại một thời điểm.
            bitmap.recycle()
        }
    }

    /**
     * Vẽ lớp chữ CHÌM lên ảnh để PDF tìm kiếm và copy được.
     *
     * Mục tiêu của phép hiệu chỉnh ở đây: khi người dùng bôi đen vùng chữ trong trình đọc PDF,
     * ô chọn phải trùng khít với hàng chữ NHÌN THẤY trên ảnh. Lệch lên/xuống vài phần trăm
     * chiều cao trang là đủ để ô chọn nhảy sang dòng khác — hỏng đúng thứ tính năng này sinh ra
     * để làm. Vì vậy toàn bộ hình học được suy từ chính số đo font (`FontMetrics`) thay vì các
     * tỉ lệ đoán trước.
     *
     * Bốn điều chỉnh, mỗi cái để tránh một lỗi có thật:
     *
     * 1. **Alpha = 1 chứ không phải 0.** `PdfDocument` BỎ QUA hoàn toàn lệnh vẽ có alpha 0
     *    khi ghi PDF — lớp chữ sẽ biến mất và tính năng copy hỏng trong im lặng. Alpha 1
     *    là mức trong suốt tối thiểu mà lệnh vẽ vẫn được ghi lại.
     *
     * 2. **Cỡ chữ suy từ số đo font, không từ một tỉ lệ cố định.** Với cỡ chữ `s`, khoảng cách
     *    từ đường chân chữ lên đỉnh chữ có dấu (`-ascent`) và khoảng cách từ đường chân chữ
     *    xuống đáy chữ có chân (`descent`) là số đo thật của font. Chiều cao hình bao nhận dạng
     *    được chính là `(-ascent + descent)`, nên `s = boxHeight / (ascentRatio + descentRatio)`
     *    là cỡ chữ ĐÚNG cho ô đó — không cần hệ số 1.3 đoán trước.
     *
     * 3. **Đường chân chữ đặt chính giữa khoảng chênh của hai số đo.** Sau khi chọn `s` theo (2),
     *    khối chữ vẽ ra vẫn có thể thấp hơn hình bao (font hệ thống và font tài liệu khác nhau).
     *    Dời baseline xuống đúng nửa phần chênh khiến khối chữ nằm CÂN trong ô thay vì dồn lên
     *    trên — đây là nguyên nhân chính làm chữ hiện ra lệch so với hàng chữ trong ảnh.
     *
     * 4. **`textScaleX` kéo chữ vừa khít chiều rộng hình bao.** Font hệ thống không cùng số đo
     *    với font của tài liệu gốc; không bù lại thì vị trí chọn chữ trôi dần về cuối dòng.
     *    Đo chiều rộng thật của chuỗi rồi chia cho chiều rộng cần có là cách hiệu chỉnh chính
     *    xác. Chiều rộng đo ở đây lấy TRƯỚC khi kéo giãn, nên hai phép hiệu chỉnh độc lập nhau.
     */
    private fun drawInvisibleTextLayer(canvas: Canvas, lines: List<PdfTextLine>, target: RectF) {
        if (lines.isEmpty()) return
        val textWidth = target.width()
        val textHeight = target.height()
        if (textWidth <= 0f || textHeight <= 0f) return

        // Một đối tượng Paint dùng lại cho mọi dòng: tạo mới mỗi dòng là cấp phát vô ích.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            // Xem chú thích (1) ở trên: 0 sẽ khiến PdfDocument bỏ luôn lệnh vẽ.
            alpha = INVISIBLE_TEXT_ALPHA
            typeface = Typeface.DEFAULT
            // Số đo font KHÔNG phụ thuộc cỡ chữ theo tỉ lệ tuyến tính, nên chỉ cần đo một lần
            // ở cỡ chữ 1 rồi dùng lại cho mọi dòng — thay vì đo lại từng dòng.
            textSize = METRICS_PROBE_SIZE_PT
        }
        val probe = paint.fontMetrics

        lines.forEach { line ->
            if (line.text.isBlank()) return@forEach
            val placement = invisibleTextPlacement(
                left = line.left,
                top = line.top,
                right = line.right,
                bottom = line.bottom,
                targetLeft = target.left,
                targetTop = target.top,
                targetWidth = textWidth,
                targetHeight = textHeight,
                ascentRatio = probe.ascent,
                descentRatio = probe.descent,
                minTextSize = MIN_TEXT_SIZE_PT,
                maxTextSize = MAX_TEXT_SIZE_PT,
            ) ?: return@forEach

            paint.textSize = placement.textSize
            val measured = paint.measureText(line.text)
            if (measured <= 0f) return@forEach
            // Xem chú thích (4): kéo giãn chữ cho khớp bề rộng nhận dạng được.
            val boxWidth = (line.right - line.left) * textWidth
            paint.textScaleX = (boxWidth / measured).coerceIn(MIN_TEXT_SCALE_X, MAX_TEXT_SCALE_X)

            canvas.drawText(line.text, placement.x, placement.baselineY, paint)
        }

        // Trả `textScaleX` về mặc định: Paint là đối tượng dùng chung, để lại giá trị kéo giãn
        // của dòng cuối sẽ ảnh hưởng nếu sau này có ai tái sử dụng nó.
        paint.textScaleX = 1f
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

        /**
         * Độ trong suốt của lớp chữ. 1/255 là mức nhỏ nhất mà `PdfDocument` vẫn ghi lệnh vẽ
         * xuống file (alpha 0 bị bỏ qua hoàn toàn). Trên màn hình gần như không thấy.
         */
        const val INVISIBLE_TEXT_ALPHA = 1

        /**
         * Cỡ chữ tối thiểu. Hình bao cao dưới 1pt gần như chắc chắn là dòng rác; vẫn vẽ ở 1pt
         * để dòng đó còn copy được thay vì bị bỏ.
         */
        const val MIN_TEXT_SIZE_PT = 1f

        /**
         * Chặn trên cỡ chữ. Hình bao bị nhận dạng sai thành một khối lớn sẽ đòi cỡ chữ khổng
         * lồ, kéo theo lệnh vẽ chữ tràn ra ngoài trang.
         */
        const val MAX_TEXT_SIZE_PT = 120f

        /**
         * Cỡ chữ dùng để ĐỌC số đo font. `FontMetrics` tỉ lệ tuyến tính theo cỡ chữ nên giá trị
         * này chỉ cần khác 0; đọc ở 1pt rồi suy ra là đủ.
         */
        const val METRICS_PROBE_SIZE_PT = 1f

        /** Chặn kéo giãn vô lý khi hình bao bị nhận dạng sai (quá hẹp hoặc quá rộng). */
        const val MIN_TEXT_SCALE_X = 0.05f
        const val MAX_TEXT_SCALE_X = 20f

        private val PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
    }
}
