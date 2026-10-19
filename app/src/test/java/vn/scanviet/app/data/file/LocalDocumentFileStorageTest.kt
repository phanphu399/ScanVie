package vn.scanviet.app.data.file

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.testing.TestDispatcherProvider
import vn.scanviet.app.domain.storage.DocumentFileStorage

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalDocumentFileStorageTest {

    private lateinit var context: Context
    private lateinit var storage: DocumentFileStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = LocalDocumentFileStorage(context, TestDispatcherProvider())
    }

    @Test
    fun `đường dẫn tương đối có dạng documents id page`() {
        assertEquals(
            "documents/doc-1/page_0001.jpg",
            storage.relativePathFor("doc-1", 1),
        )
        assertEquals(
            "documents/doc-1/page_0012.jpg",
            storage.relativePathFor("doc-1", 12),
        )
    }

    @Test
    fun `ghi ảnh trang tạo file thật trong filesDir`() = runTest {
        val bytes = "nội dung ảnh giả".toByteArray()

        val relativePath = storage.writePageImage("doc-1", 1, bytes)

        assertEquals("documents/doc-1/page_0001.jpg", relativePath)
        val file = storage.resolve(relativePath)
        assertTrue(file.isFile)
        assertEquals(bytes.size.toLong(), file.length())
        assertTrue(file.absolutePath.startsWith(context.filesDir.absolutePath))
    }

    @Test
    fun `ghi lại cùng trang sẽ ghi đè chứ không nhân đôi file`() = runTest {
        storage.writePageImage("doc-1", 1, "phiên bản 1 dài hơn".toByteArray())
        storage.writePageImage("doc-1", 1, "v2".toByteArray())

        val directory = File(context.filesDir, "documents/doc-1")
        assertEquals(listOf("page_0001.jpg"), directory.listFiles()!!.map { it.name }.sorted())
        assertEquals(2L, storage.documentBytes("doc-1"))
    }

    @Test
    fun `exist trả về false với file không tồn tại`() = runTest {
        assertFalse(storage.exists("documents/doc-1/page_0001.jpg"))
    }

    @Test
    fun `chặn đường dẫn thoát khỏi vùng dữ liệu của app`() = runTest {
        val dangerousPaths = listOf(
            "../ngoài.jpg",
            "documents/../../ngoài.jpg",
        )
        dangerousPaths.forEach { dangerous ->
            try {
                storage.resolve(dangerous)
                fail("Đáng lẽ phải từ chối đường dẫn: $dangerous")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.isNotEmpty())
            }
        }
    }

    @Test
    fun `chặn id tài liệu không an toàn`() {
        val dangerousIds = listOf("", "  ", ".", "..", "a/b", "a\\b")
        dangerousIds.forEach { dangerous ->
            try {
                storage.relativePathFor(dangerous, 1)
                fail("Đáng lẽ phải từ chối id: $dangerous")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.isNotEmpty())
            }
        }
    }

    @Test
    fun `xoá thư mục tài liệu xoá toàn bộ ảnh của tài liệu đó`() = runTest {
        storage.writePageImage("doc-1", 1, byteArrayOf(1))
        storage.writePageImage("doc-1", 2, byteArrayOf(2))
        storage.writePageImage("doc-2", 1, byteArrayOf(3))

        storage.deleteDocumentDirectory("doc-1")

        assertFalse(storage.exists("documents/doc-1/page_0001.jpg"))
        assertTrue(storage.exists("documents/doc-2/page_0001.jpg"))
    }

    @Test
    fun `liệt kê được các thư mục tài liệu đang có`() = runTest {
        storage.createDocumentDirectory("doc-1")
        storage.createDocumentDirectory("doc-2")

        assertEquals(setOf("doc-1", "doc-2"), storage.listDocumentDirectories().toSet())
    }

    @Test
    fun `dung lượng tài liệu là tổng dung lượng các file trong thư mục`() = runTest {
        storage.writePageImage("doc-1", 1, ByteArray(100))
        storage.writePageImage("doc-1", 2, ByteArray(250))

        assertEquals(350L, storage.documentBytes("doc-1"))
    }

    @Test
    fun `dung lượng tài liệu không tồn tại là 0`() = runTest {
        assertEquals(0L, storage.documentBytes("không-có"))
    }

    @Test
    fun `dọn vùng nháp xoá thư mục cache scan_scratch`() = runTest {
        val scratch = File(context.cacheDir, LocalDocumentFileStorage.SCRATCH_DIR)
        scratch.mkdirs()
        File(scratch, "tạm.jpg").writeBytes(ByteArray(10))
        assertTrue(scratch.exists())

        storage.cleanScratchSpace()

        assertFalse(scratch.exists())
    }

    @Test
    fun `ghi ảnh rỗng bị từ chối`() = runTest {
        try {
            storage.writePageImage("doc-1", 1, ByteArray(0))
            fail("Đáng lẽ phải từ chối ảnh rỗng")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.isNotEmpty())
        }
    }
}
