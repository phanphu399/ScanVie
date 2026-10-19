package vn.scanviet.app.domain.usecase

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.domain.EmptyDocumentException
import vn.scanviet.app.domain.InvalidDocumentTitleException
import vn.scanviet.app.domain.TitleValidationError
import vn.scanviet.app.domain.UnreadableScanPageException
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.data.file.LocalDocumentFileStorage
import vn.scanviet.app.testing.FakeDocumentRepository
import vn.scanviet.app.testing.FakePageImageReader
import vn.scanviet.app.testing.TestDispatcherProvider

/**
 * Kiểm tra bước "nhập ảnh đã quét vào ứng dụng" — bước dễ hỏng nhất của luồng quét.
 *
 * Dùng file THẬT (qua `LocalDocumentFileStorage` trên filesDir của Robolectric) vì phần
 * lớn giá trị của test nằm ở câu hỏi: khi lỗi xảy ra giữa đường thì có còn file rác không?
 * Repository được thay bằng bản giả để có thể ép lỗi ghi database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImportScannedDocumentUseCaseTest {

    private lateinit var storage: DocumentFileStorage
    private lateinit var repository: FakeDocumentRepository
    private lateinit var reader: FakePageImageReader
    private lateinit var useCase: ImportScannedDocumentUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = LocalDocumentFileStorage(context, TestDispatcherProvider())
        repository = FakeDocumentRepository()
        reader = FakePageImageReader()
        useCase = ImportScannedDocumentUseCase(
            imageReader = reader,
            storage = storage,
            createDocument = CreateDocumentUseCase(repository),
        )
    }

    @Test
    fun `hai trang thì ghi đủ hai file và đủ hai bản ghi trang`() = runTest {
        reader.put(PAGE_ONE_URI, byteArrayOf(1, 2, 3), width = 1240, height = 1754)
        reader.put(PAGE_TWO_URI, byteArrayOf(4, 5), width = 1000, height = 1400)

        val document = useCase("Hóa đơn điện", listOf(PAGE_ONE_URI, PAGE_TWO_URI))

        assertEquals("Hóa đơn điện", document.title)
        assertEquals(2, document.pageCount)

        // Đường dẫn phải là đường dẫn TƯƠNG ĐỐI, đánh số từ 1.
        val pages = repository.getPages(document.id)
        assertEquals(listOf(1, 2), pages.map { it.pageIndex })
        assertEquals(
            listOf("documents/${document.id}/page_0001.jpg", "documents/${document.id}/page_0002.jpg"),
            pages.map { it.imageFile },
        )
        assertTrue(storage.exists(pages[0].imageFile))
        assertTrue(storage.exists(pages[1].imageFile))

        // Kích thước ảnh phải được giữ lại để vẽ PDF đúng tỉ lệ ở bước sau.
        assertEquals(1240, pages[0].width)
        assertEquals(1754, pages[0].height)
        assertEquals(1000, pages[1].width)
    }

    @Test
    fun `nội dung file ghi xuống đúng bytes đọc được từ trình quét`() = runTest {
        val bytes = byteArrayOf(9, 8, 7, 6)
        reader.put(PAGE_ONE_URI, bytes)

        val document = useCase("Tài liệu", listOf(PAGE_ONE_URI))

        val page = repository.getPages(document.id).single()
        assertTrue(storage.resolve(page.imageFile).readBytes().contentEquals(bytes))
    }

    @Test
    fun `thứ tự trang theo đúng thứ tự URI truyền vào`() = runTest {
        reader.put(PAGE_ONE_URI, byteArrayOf(1))
        reader.put(PAGE_TWO_URI, byteArrayOf(2))

        // Truyền ngược thứ tự: người dùng đã sắp xếp lại ở màn hình xem lại.
        useCase("Tài liệu", listOf(PAGE_TWO_URI, PAGE_ONE_URI))

        assertEquals(listOf(PAGE_TWO_URI, PAGE_ONE_URI), reader.readUris)
    }

    @Test
    fun `lỗi ghi database thì xoá sạch file vừa ghi, không để lại rác`() = runTest {
        reader.put(PAGE_ONE_URI, byteArrayOf(1, 2))
        reader.put(PAGE_TWO_URI, byteArrayOf(3, 4))
        repository.failCreate = true

        val result = runCatching { useCase("Tài liệu", listOf(PAGE_ONE_URI, PAGE_TWO_URI)) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(
            "Thư mục tài liệu phải được xoá khi ghi database thất bại",
            storage.listDocumentDirectories().isEmpty(),
        )
    }

    @Test
    fun `một trang không đọc được thì báo lỗi riêng và dọn file đã ghi`() = runTest {
        reader.put(PAGE_ONE_URI, byteArrayOf(1, 2))
        // PAGE_TWO_URI cố ý không được đăng ký -> reader trả null.

        val result = runCatching { useCase("Tài liệu", listOf(PAGE_ONE_URI, PAGE_TWO_URI)) }

        assertTrue(result.exceptionOrNull() is UnreadableScanPageException)
        assertTrue(storage.listDocumentDirectories().isEmpty())
        assertEquals(0, repository.getDocuments().size)
    }

    @Test
    fun `lỗi đọc ảnh không mang theo URI để không lộ đường dẫn vào log`() = runTest {
        val result = runCatching { useCase("Tài liệu", listOf(PAGE_ONE_URI)) }

        val exception = result.exceptionOrNull()
        assertTrue(exception is UnreadableScanPageException)
        assertEquals(null, exception?.message)
    }

    @Test
    fun `không có trang nào thì từ chối ngay và không tạo thư mục`() = runTest {
        val result = runCatching { useCase("Tài liệu", emptyList()) }

        assertTrue(result.exceptionOrNull() is EmptyDocumentException)
        assertTrue(storage.listDocumentDirectories().isEmpty())
    }

    @Test
    fun `tên rỗng thì từ chối trước khi ghi bất cứ file nào`() = runTest {
        reader.put(PAGE_ONE_URI, byteArrayOf(1))

        val result = runCatching { useCase("   ", listOf(PAGE_ONE_URI)) }

        assertTrue(result.exceptionOrNull() is InvalidDocumentTitleException)
        assertEquals(
            TitleValidationError.EMPTY,
            (result.exceptionOrNull() as InvalidDocumentTitleException).error,
        )
        // Điểm quan trọng: chưa đọc ảnh, chưa ghi file, chưa tạo thư mục.
        assertEquals(emptyList<String>(), reader.readUris)
        assertTrue(storage.listDocumentDirectories().isEmpty())
    }

    @Test
    fun `tên quá dài thì cũng bị từ chối trước khi ghi file`() = runTest {
        reader.put(PAGE_ONE_URI, byteArrayOf(1))

        val result = runCatching { useCase("a".repeat(200), listOf(PAGE_ONE_URI)) }

        assertEquals(
            TitleValidationError.TOO_LONG,
            (result.exceptionOrNull() as InvalidDocumentTitleException).error,
        )
        assertTrue(storage.listDocumentDirectories().isEmpty())
    }

    private companion object {
        const val PAGE_ONE_URI = "content://scanner.tmp/page-1"
        const val PAGE_TWO_URI = "content://scanner.tmp/page-2"
    }
}
