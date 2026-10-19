package vn.scanviet.app.data.dev

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.core.format.Formatters
import vn.scanviet.app.core.time.TimeProvider
import vn.scanviet.app.domain.DocumentId
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.domain.usecase.CreateDocumentUseCase

/**
 * CÔNG CỤ PHÁT TRIỂN — không phải tính năng người dùng.
 *
 * Tạo một tài liệu 2 trang "thật": ảnh JPEG thật trong filesDir + bản ghi Room,
 * để kiểm tra toàn bộ đường đi lưu trữ trước khi có chức năng quét (Giai đoạn 2).
 * Chỉ được gọi từ nhánh `if (BuildConfig.DEBUG)`; ở bản release R8 loại bỏ nhánh chết
 * nên class này không tồn tại trong APK phát hành.
 */
@Singleton
class SampleDocumentFactory @Inject constructor(
    private val storage: DocumentFileStorage,
    private val createDocument: CreateDocumentUseCase,
    private val dispatchers: DispatcherProvider,
    private val timeProvider: TimeProvider,
) {

    suspend fun createTwoPageDocument(): Document {
        val documentId = DocumentId.new()
        return try {
            storage.createDocumentDirectory(documentId)
            val pages = (FIRST_PAGE_INDEX..PAGE_COUNT).map { pageIndex ->
                val jpeg = renderPage(pageIndex)
                val relativePath = storage.writePageImage(documentId, pageIndex, jpeg)
                NewDocumentPage(
                    relativePath = relativePath,
                    width = PAGE_WIDTH,
                    height = PAGE_HEIGHT,
                    sizeBytes = jpeg.size.toLong(),
                )
            }
            createDocument(
                documentId = documentId,
                title = "Tài liệu mẫu ${Formatters.dateTime(timeProvider.now())}",
                pages = pages,
            )
        } catch (throwable: Throwable) {
            // Không để lại file rác nếu bước ghi database thất bại.
            runCatching { storage.deleteDocumentDirectory(documentId) }
            throw throwable
        }
    }

    private suspend fun renderPage(pageIndex: Int): ByteArray = withContext(dispatchers.default) {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            drawHeader(canvas, pageIndex)
            drawBodyLines(canvas)
            drawFooter(canvas, pageIndex)
            bitmap.toJpeg()
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawHeader(canvas: Canvas, pageIndex: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 40, 70)
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("TÀI LIỆU MẪU SCANVIET", MARGIN, MARGIN + 80f, paint)
        paint.apply {
            textSize = 48f
            typeface = Typeface.DEFAULT
            color = Color.rgb(90, 100, 110)
        }
        canvas.drawText("Trang $pageIndex / $PAGE_COUNT", MARGIN, MARGIN + 160f, paint)
    }

    private fun drawBodyLines(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(150, 160, 170)
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 40, 40)
            textSize = 44f
        }
        var y = MARGIN + 300f
        val lineText = "Dòng chữ tiếng Việt để kiểm tra hiển thị: Hóa đơn điện, hợp đồng, bài học."
        canvas.drawText(lineText, MARGIN, y, textPaint)
        y += 60f
        repeat(LINE_COUNT) {
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)
            y += 70f
        }
    }

    private fun drawFooter(canvas: Canvas, pageIndex: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 130, 140)
            textSize = 36f
        }
        canvas.drawText(
            "Tạo bởi công cụ thử nghiệm · trang $pageIndex",
            MARGIN,
            PAGE_HEIGHT - MARGIN,
            paint,
        )
    }

    private fun Bitmap.toJpeg(): ByteArray {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        return output.toByteArray()
    }

    private companion object {
        const val PAGE_WIDTH = 1240
        const val PAGE_HEIGHT = 1754
        const val MARGIN = 110f
        const val LINE_COUNT = 14
        const val JPEG_QUALITY = 85
        const val PAGE_COUNT = 2
        const val FIRST_PAGE_INDEX = 1
    }
}
