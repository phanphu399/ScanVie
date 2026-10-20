package vn.scanviet.app.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.data.file.LocalDocumentFileStorage
import vn.scanviet.app.data.local.ScanVietDatabase
import vn.scanviet.app.domain.DocumentNotFoundException
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.model.OcrStatus
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.testing.FakeTimeProvider
import vn.scanviet.app.testing.TestDispatcherProvider

/**
 * Test tầng lưu trữ thật: Room chạy trong bộ nhớ + file thật trong filesDir của Robolectric.
 * Đây là loại test bắt được lỗi SQL / mapping / tính nhất quán giữa database và file,
 * những thứ mà test với repository giả không thể phát hiện.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OfflineDocumentRepositoryTest {

    private lateinit var database: ScanVietDatabase
    private lateinit var storage: DocumentFileStorage
    private lateinit var repository: DocumentRepository
    private lateinit var timeProvider: FakeTimeProvider
    private val dispatchers = TestDispatcherProvider()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ScanVietDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storage = LocalDocumentFileStorage(context, dispatchers)
        timeProvider = FakeTimeProvider()
        repository = OfflineDocumentRepository(
            database = database,
            documentDao = database.documentDao(),
            ocrDao = database.ocrDao(),
            fileStorage = storage,
            timeProvider = timeProvider,
            dispatchers = dispatchers,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createDocumentWithRealFiles(
        documentId: String,
        title: String,
        pageCount: Int = 2,
    ) {
        storage.createDocumentDirectory(documentId)
        val pages = (1..pageCount).map { pageIndex ->
            val bytes = "ảnh giả $pageIndex".toByteArray()
            val relativePath = storage.writePageImage(documentId, pageIndex, bytes)
            NewDocumentPage(
                relativePath = relativePath,
                width = 100,
                height = 200,
                sizeBytes = bytes.size.toLong(),
            )
        }
        repository.createDocument(documentId = documentId, title = title, pages = pages)
    }

    @Test
    fun `tạo tài liệu ghi cả bản ghi và các trang`() = runTest {
        createDocumentWithRealFiles("doc-1", "Hóa đơn điện", pageCount = 2)

        val document = repository.getDocument("doc-1")
        assertEquals("Hóa đơn điện", document?.title)
        assertEquals(2, document?.pageCount)
        assertEquals(OcrStatus.NONE, document?.ocrStatus)
        assertNull(document?.deletedAtMillis)

        val pages = repository.getPages("doc-1")
        assertEquals(listOf(1, 2), pages.map { it.pageIndex })
        assertEquals("documents/doc-1/page_0001.jpg", pages.first().imageFile)
        assertTrue(storage.exists(pages.first().imageFile))
    }

    @Test
    fun `dung lượng tài liệu bằng tổng dung lượng các trang`() = runTest {
        createDocumentWithRealFiles("doc-1", "Biên lai", pageCount = 2)

        val expected = repository.getPages("doc-1").sumOf { page -> storage.resolve(page.imageFile).length() }
        assertEquals(expected, repository.getDocument("doc-1")?.sizeBytes)
    }

    @Test
    fun `tìm kiếm không dấu vẫn tìm được tài liệu có dấu`() = runTest {
        createDocumentWithRealFiles("doc-1", "Hóa đơn điện")
        createDocumentWithRealFiles("doc-2", "Hợp đồng thuê nhà")

        assertEquals(1, repository.observeDocuments("hoa don").first().size)
        assertEquals(1, repository.observeDocuments("HOA DON").first().size)
        assertEquals(1, repository.observeDocuments("hop dong").first().size)
        assertEquals("Hóa đơn điện", repository.observeDocuments("hoa don").first().first().title)
    }

    @Test
    fun `tìm kiếm với chuỗi rỗng trả về tất cả`() = runTest {
        createDocumentWithRealFiles("doc-1", "Hóa đơn điện")
        createDocumentWithRealFiles("doc-2", "Hợp đồng thuê nhà")

        assertEquals(2, repository.observeDocuments("").first().size)
    }

    @Test
    fun `ký tự đặc biệt của LIKE không bị coi là wildcard`() = runTest {
        createDocumentWithRealFiles("doc-1", "Giảm 50%")
        createDocumentWithRealFiles("doc-2", "Hợp đồng thuê nhà")

        // Nếu không thoát ký tự, "%" sẽ khớp mọi tài liệu.
        val result = repository.observeDocuments("%").first()
        assertEquals(1, result.size)
        assertEquals("Giảm 50%", result.first().title)
    }

    @Test
    fun `danh sách sắp xếp theo thời gian cập nhật mới nhất trước`() = runTest {
        createDocumentWithRealFiles("doc-1", "Tài liệu cũ")
        timeProvider.advanceBy(60_000)
        createDocumentWithRealFiles("doc-2", "Tài liệu mới")

        val documents = repository.observeDocuments("").first()
        assertEquals(listOf("doc-2", "doc-1"), documents.map { it.id })
    }

    @Test
    fun `đổi tên cập nhật cả tên hiển thị và cột tìm kiếm`() = runTest {
        createDocumentWithRealFiles("doc-1", "Tên cũ")
        timeProvider.advanceBy(10_000)

        repository.renameDocument("doc-1", "Hóa đơn mới")

        val document = repository.getDocument("doc-1")
        assertEquals("Hóa đơn mới", document?.title)
        assertEquals(timeProvider.currentTimeMillis, document?.updatedAtMillis)
        assertEquals(1, repository.observeDocuments("hoa don moi").first().size)
        assertTrue(repository.observeDocuments("ten cu").first().isEmpty())
    }

    @Test
    fun `đổi tên tài liệu không tồn tại báo lỗi rõ ràng`() = runTest {
        try {
            repository.renameDocument("không-có", "Tên mới")
            fail("Đáng lẽ phải ném DocumentNotFoundException")
        } catch (expected: DocumentNotFoundException) {
            assertEquals("không-có", expected.documentId)
        }
    }

    @Test
    fun `xoá mềm ẩn khỏi danh sách và hiện trong thùng rác`() = runTest {
        createDocumentWithRealFiles("doc-1", "Hóa đơn điện")

        repository.moveToTrash("doc-1")

        assertTrue(repository.observeDocuments("").first().isEmpty())
        assertEquals(1, repository.observeTrashedDocuments().first().size)
        assertTrue(storage.exists("documents/doc-1/page_0001.jpg"))
    }

    @Test
    fun `khôi phục từ thùng rác trả tài liệu về danh sách`() = runTest {
        createDocumentWithRealFiles("doc-1", "Hóa đơn điện")
        repository.moveToTrash("doc-1")

        repository.restoreFromTrash("doc-1")

        assertEquals(1, repository.observeDocuments("").first().size)
        assertTrue(repository.observeTrashedDocuments().first().isEmpty())
    }

    @Test
    fun `xoá vĩnh viễn xoá cả bản ghi, các trang và file`() = runTest {
        createDocumentWithRealFiles("doc-1", "Hóa đơn điện")

        repository.deleteForever("doc-1")

        assertNull(repository.getDocument("doc-1"))
        assertTrue(repository.getPages("doc-1").isEmpty())
        assertFalse(storage.exists("documents/doc-1/page_0001.jpg"))
    }

    @Test
    fun `dọn thùng rác quá hạn chỉ xoá tài liệu cũ`() = runTest {
        createDocumentWithRealFiles("doc-1", "Tài liệu cũ")
        createDocumentWithRealFiles("doc-2", "Tài liệu mới")
        repository.moveToTrash("doc-1")
        repository.moveToTrash("doc-2")

        timeProvider.advanceBy(10L * FakeTimeProvider.ONE_DAY_MILLIS)
        repository.restoreFromTrash("doc-2")
        repository.moveToTrash("doc-2")

        // doc-1 bị xoá cách đây 10 ngày, doc-2 vừa bị xoá lại nên còn hạn.
        val cutoff = timeProvider.currentTimeMillis - 5L * FakeTimeProvider.ONE_DAY_MILLIS
        val purged = repository.purgeTrashOlderThan(cutoff)

        assertEquals(1, purged)
        assertNull(repository.getDocument("doc-1"))
        assertFalse(storage.exists("documents/doc-1/page_0001.jpg"))
        assertEquals("Tài liệu mới", repository.getDocument("doc-2")?.title)
    }

    @Test
    fun `xác định được trang thiếu file ảnh`() = runTest {
        createDocumentWithRealFiles("doc-1", "Hóa đơn điện", pageCount = 2)
        assertEquals(emptySet<Int>(), repository.missingPageIndices("doc-1"))

        // Mô phỏng file bị mất (người dùng xoá bằng công cụ khác, hoặc khôi phục dữ liệu lỗi).
        storage.resolve("documents/doc-1/page_0002.jpg").delete()

        assertEquals(setOf(2), repository.missingPageIndices("doc-1"))
    }

    @Test
    fun `trang của tài liệu được đọc theo đúng thứ tự`() = runTest {
        createDocumentWithRealFiles("doc-1", "Tài liệu 3 trang", pageCount = 3)

        assertEquals(listOf(1, 2, 3), repository.getPages("doc-1").map { it.pageIndex })
    }

    @Test
    fun `tổng dung lượng chỉ tính tài liệu chưa xoá`() = runTest {
        createDocumentWithRealFiles("doc-1", "Hóa đơn điện")
        createDocumentWithRealFiles("doc-2", "Hợp đồng")

        val firstSize = repository.getDocument("doc-1")!!.sizeBytes
        val secondSize = repository.getDocument("doc-2")!!.sizeBytes

        val beforeTrash = repository.observeActiveSizeBytes().first()
        repository.moveToTrash("doc-1")
        val afterTrash = repository.observeActiveSizeBytes().first()

        assertEquals(firstSize + secondSize, beforeTrash)
        assertEquals(secondSize, afterTrash)
    }

    @Test
    fun `danh sách id gồm cả tài liệu trong thùng rác`() = runTest {
        createDocumentWithRealFiles("doc-1", "Hóa đơn điện")
        repository.moveToTrash("doc-1")

        assertEquals(setOf("doc-1"), repository.getAllDocumentIds())
    }
}
