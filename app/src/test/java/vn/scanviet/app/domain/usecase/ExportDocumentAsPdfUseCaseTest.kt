package vn.scanviet.app.domain.usecase

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.data.file.LocalDocumentFileStorage
import vn.scanviet.app.domain.DocumentNotFoundException
import vn.scanviet.app.domain.EmptyDocumentException
import vn.scanviet.app.domain.PageImageMissingException
import vn.scanviet.app.domain.export.PdfOutputTarget
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.testing.FakeDocumentOutputTarget
import vn.scanviet.app.testing.FakeDocumentRepository
import vn.scanviet.app.testing.FakePdfExporter
import vn.scanviet.app.testing.TestDispatcherProvider

/**
 * Kiểm tra việc xuất tài liệu thành PDF.
 *
 * Dùng file THẬT qua [LocalDocumentFileStorage] trên filesDir của Robolectric, vì phần
 * quan trọng nhất của use case này là quan hệ giữa database và file trên đĩa: trang nào
 * có thật, trang nào đã mất. Bộ sinh PDF và đích ghi được thay bằng bản giả để test chạy
 * trên JVM thuần.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExportDocumentAsPdfUseCaseTest {

    private lateinit var storage: DocumentFileStorage
    private lateinit var repository: FakeDocumentRepository
    private lateinit var output: FakeDocumentOutputTarget
    private lateinit var exporter: FakePdfExporter
    private lateinit var useCase: ExportDocumentAsPdfUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = LocalDocumentFileStorage(context, TestDispatcherProvider())
        repository = FakeDocumentRepository()
        output = FakeDocumentOutputTarget()
        exporter = FakePdfExporter()
        useCase = ExportDocumentAsPdfUseCase(
            repository = repository,
            storage = storage,
            outputTarget = output,
            pdfExporter = exporter,
        )
    }

    @Test
    fun `xuất hai trang thì exporter nhận đúng hai nguồn ảnh`() = runTest {
        seedDocument(pageCount = 2)

        useCase(DOC_ID, TARGET)

        assertEquals(1, exporter.callCount)
        assertEquals(2, exporter.lastPages().size)
        assertEquals(listOf(PAGE_ONE, PAGE_TWO), exporter.lastPages().map { it.imageFile.name })
    }

    @Test
    fun `trang được sắp theo pageIndex chứ không theo thứ tự repository trả về`() = runTest {
        // Mô phỏng database lệch thứ tự: trang thứ nhất trong danh sách mang pageIndex 3,
        // trang thứ ba mang pageIndex 1. Đây là tình huống có thật khi một bản ghi bị sửa
        // tay hoặc bị đánh số lại. Nếu use case tin vào thứ tự trả về, PDF in ra sẽ đảo
        // trang — lỗi nghiêm trọng với hợp đồng hay hồ sơ.
        repository.pageIndexOverride = { position -> 3 - position }
        seedDocument(
            pages = listOf(
                NewDocumentPage(relativePath = path(3), width = 100, height = 100, sizeBytes = 1),
                NewDocumentPage(relativePath = path(2), width = 100, height = 100, sizeBytes = 1),
                NewDocumentPage(relativePath = path(1), width = 100, height = 100, sizeBytes = 1),
            ),
        )

        useCase(DOC_ID, TARGET)

        assertEquals(
            listOf(PAGE_ONE, PAGE_TWO, PAGE_THREE),
            exporter.lastPages().map { it.imageFile.name },
        )
    }

    @Test
    fun `góc xoay của trang được truyền xuống exporter`() = runTest {
        // Luồng quét hiện tại luôn tạo 0 độ, nhưng bỏ qua tham số này sẽ là lỗi âm thầm
        // nếu sau này có bước tự xoay ảnh.
        seedDocument(pageCount = 1, rotationDegrees = 90)

        useCase(DOC_ID, TARGET)

        assertEquals(90, exporter.lastPages().single().rotationDegrees)
    }

    @Test
    fun `thiếu file ảnh thì báo lỗi kèm số trang, không xuất PDF thiếu trang`() = runTest {
        seedDocument(pageCount = 2)
        storage.resolve(path(2)).delete()

        val result = runCatching { useCase(DOC_ID, TARGET) }

        val failure = result.exceptionOrNull()
        assertTrue("Phải là PageImageMissingException: $failure", failure is PageImageMissingException)
        assertEquals(2, (failure as PageImageMissingException).pageIndex)
        assertEquals(0, exporter.callCount)
    }

    @Test
    fun `lỗi thiếu ảnh không mang theo đường dẫn file`() = runTest {
        seedDocument(pageCount = 1)
        storage.resolve(path(1)).delete()

        val failure = runCatching { useCase(DOC_ID, TARGET) }.exceptionOrNull()

        assertTrue(failure is PageImageMissingException)
        // Quy tắc riêng tư: tên file và đường dẫn không được rơi vào thông báo lỗi.
        assertNull(failure?.message)
    }

    @Test
    fun `tài liệu không tồn tại thì từ chối trước khi mở đích ghi`() = runTest {
        val result = runCatching { useCase("khong-co", TARGET) }

        assertTrue(result.exceptionOrNull() is DocumentNotFoundException)
        // Điểm quan trọng: chưa mở stream, nên không tạo ra file rỗng cho người dùng.
        assertEquals(0, output.openCount)
    }

    @Test
    fun `tài liệu không có trang nào thì từ chối`() = runTest {
        seedDocument(pageCount = 0)

        val result = runCatching { useCase(DOC_ID, TARGET) }

        assertTrue(result.exceptionOrNull() is EmptyDocumentException)
        assertEquals(0, output.openCount)
    }

    @Test
    fun `stream được đóng sau khi xuất xong`() = runTest {
        seedDocument(pageCount = 1)

        useCase(DOC_ID, TARGET)

        assertEquals(1, output.openCount)
        assertEquals("Stream phải được đóng đúng một lần", 1, output.closedStreams.size)
    }

    @Test
    fun `stream vẫn được đóng khi exporter ném lỗi giữa chừng`() = runTest {
        seedDocument(pageCount = 1)
        exporter.failWith = IOException("đĩa đầy")

        val result = runCatching { useCase(DOC_ID, TARGET) }

        assertTrue(result.exceptionOrNull() is IOException)
        // Rò rỉ file descriptor là lỗi thật, xảy ra đúng ở nhánh lỗi này.
        assertEquals("Stream phải được đóng cả khi lỗi", 1, output.closedStreams.size)
    }

    @Test
    fun `đích ghi được mở đúng một lần cho cả tài liệu nhiều trang`() = runTest {
        seedDocument(pageCount = 3)

        useCase(DOC_ID, TARGET)

        // Nếu mỗi trang mở lại một stream thì các trang sẽ ghi đè lẫn nhau, chỉ còn trang cuối.
        assertEquals(1, output.openCount)
    }

    @Test
    fun `đích ghi nhận đúng đối tượng đích mà UI yêu cầu`() = runTest {
        seedDocument(pageCount = 1)
        val target = PdfOutputTarget.SafUri("content://nguoi-dung/chon.pdf")

        useCase(DOC_ID, target)

        assertEquals(target, output.lastTarget)
    }

    /** Tạo tài liệu thật trong database giả, kèm file ảnh thật trên đĩa. */
    private suspend fun seedDocument(
        pageCount: Int = 0,
        rotationDegrees: Int = 0,
        pages: List<NewDocumentPage>? = null,
    ) {
        val pageList = pages ?: (1..pageCount).map { index ->
            NewDocumentPage(
                relativePath = path(index),
                width = 1240,
                height = 1754,
                sizeBytes = 1,
                rotationDegrees = rotationDegrees,
            )
        }
        repository.createDocument(DOC_ID, TITLE, pageList)
        pageList.forEach { page ->
            storage.writePageImage(
                documentId = DOC_ID,
                pageIndex = page.relativePath.pageNumber(),
                jpegBytes = byteArrayOf(1, 2, 3),
            )
        }
    }

    /** page_0007.jpg -> 7 */
    private fun String.pageNumber(): Int =
        substringAfterLast('_').substringBefore('.').toInt()

    private fun path(index: Int): String = "documents/$DOC_ID/page_%04d.jpg".format(index)

    private companion object {
        const val DOC_ID = "doc-export"
        const val TITLE = "Hợp đồng thuê nhà"
        const val PAGE_ONE = "page_0001.jpg"
        const val PAGE_TWO = "page_0002.jpg"
        const val PAGE_THREE = "page_0003.jpg"

        /** Đích ghi dùng chung: mọi thứ nằm trong bộ nhớ nên đích nào cũng như nhau. */
        val TARGET: PdfOutputTarget = PdfOutputTarget.SafUri("content://nguoi-dung/chon.pdf")
    }
}
