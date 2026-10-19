package vn.scanviet.app.data.maintenance

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.data.file.LocalDocumentFileStorage
import vn.scanviet.app.data.local.ScanVietDatabase
import vn.scanviet.app.data.repository.OfflineDocumentRepository
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.testing.FakeTimeProvider
import vn.scanviet.app.testing.TestDispatcherProvider

/**
 * Kiểm tra tính nhất quán giữa database và file — nhóm lỗi nguy hiểm nhất của ứng dụng
 * quét tài liệu: danh sách hiển thị tài liệu nhưng ảnh đã biến mất, hoặc file còn mà
 * không còn bản ghi nào.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DocumentStorageMaintenanceTest {

    private lateinit var database: ScanVietDatabase
    private lateinit var storage: DocumentFileStorage
    private lateinit var repository: DocumentRepository
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var maintenance: DocumentStorageMaintenance
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
            fileStorage = storage,
            timeProvider = timeProvider,
            dispatchers = dispatchers,
        )
        maintenance = DocumentStorageMaintenance(
            repository = repository,
            storage = storage,
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createDocument(documentId: String, title: String) {
        storage.createDocumentDirectory(documentId)
        val bytes = ByteArray(64) { it.toByte() }
        val relativePath = storage.writePageImage(documentId, 1, bytes)
        repository.createDocument(
            documentId = documentId,
            title = title,
            pages = listOf(
                NewDocumentPage(relativePath = relativePath, width = 10, height = 10, sizeBytes = 64),
            ),
        )
    }

    @Test
    fun `thư mục không còn bản ghi nào bị xoá`() = runTest {
        createDocument("doc-1", "Hóa đơn điện")
        storage.createDocumentDirectory("thư-mục-mồ-côi")
        storage.writePageImage("thư-mục-mồ-côi", 1, ByteArray(8))

        val result = maintenance.run()

        assertEquals(1, result.orphanDirectoriesDeleted)
        assertFalse(storage.listDocumentDirectories().contains("thư-mục-mồ-côi"))
        assertTrue(storage.exists("documents/doc-1/page_0001.jpg"))
    }

    @Test
    fun `thư mục của tài liệu trong thùng rác vẫn được giữ`() = runTest {
        createDocument("doc-1", "Hóa đơn điện")
        repository.moveToTrash("doc-1")

        val result = maintenance.run()

        assertEquals(0, result.orphanDirectoriesDeleted)
        assertTrue(storage.exists("documents/doc-1/page_0001.jpg"))
    }

    @Test
    fun `tài liệu mất toàn bộ file ảnh được chuyển vào thùng rác chứ không xoá thẳng`() = runTest {
        createDocument("doc-1", "Hóa đơn điện")
        storage.deleteDocumentDirectory("doc-1")

        val result = maintenance.run()

        assertEquals(1, result.documentsMovedToTrash)
        assertNotNull(repository.getDocument("doc-1")?.deletedAtMillis)
        assertEquals(1, repository.observeTrashedDocuments().first().size)
        // Bản ghi vẫn còn để người dùng quyết định khôi phục hay xoá vĩnh viễn.
        assertEquals("Hóa đơn điện", repository.getDocument("doc-1")?.title)
    }

    @Test
    fun `tài liệu còn file ảnh không bị chuyển vào thùng rác`() = runTest {
        createDocument("doc-1", "Hóa đơn điện")

        val result = maintenance.run()

        assertEquals(0, result.documentsMovedToTrash)
        assertTrue(repository.observeTrashedDocuments().first().isEmpty())
    }

    @Test
    fun `dọn tài liệu trong thùng rác quá 30 ngày và xoá luôn file`() = runTest {
        createDocument("doc-1", "Hóa đơn cũ")
        repository.moveToTrash("doc-1")
        timeProvider.advanceBy(31L * FakeTimeProvider.ONE_DAY_MILLIS)

        val result = maintenance.run()

        assertEquals(1, result.purgedDocuments)
        assertNull(repository.getDocument("doc-1"))
        assertFalse(storage.exists("documents/doc-1/page_0001.jpg"))
    }

    @Test
    fun `tài liệu mới xoá trong thùng rác chưa bị dọn`() = runTest {
        createDocument("doc-1", "Hóa đơn mới")
        repository.moveToTrash("doc-1")
        timeProvider.advanceBy(3L * FakeTimeProvider.ONE_DAY_MILLIS)

        val result = maintenance.run()

        assertEquals(0, result.purgedDocuments)
        assertEquals(1, repository.observeTrashedDocuments().first().size)
    }

    @Test
    fun `chạy dọn dẹp nhiều lần không gây lỗi`() = runTest {
        createDocument("doc-1", "Hóa đơn điện")
        storage.deleteDocumentDirectory("doc-1")

        maintenance.run()
        val secondRun = maintenance.run()

        assertEquals(0, secondRun.orphanDirectoriesDeleted)
        assertEquals(0, secondRun.documentsMovedToTrash)
    }
}
