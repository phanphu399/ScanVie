package vn.scanviet.app.data.export

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.domain.export.PdfOutputTarget
import vn.scanviet.app.testing.TestDispatcherProvider

/**
 * Kiểm tra việc mở luồng ghi cho hai loại đích.
 *
 * Trọng tâm là hành vi ghi đè: `"w"` để lại đuôi file cũ nên PDF hỏng, `"wt"` mới cắt bớt.
 * Test này ghi hai lần liên tiếp vào cùng một file để phát hiện đúng lỗi đó.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ContentUriDocumentOutputTargetTest {

    private lateinit var context: Context
    private lateinit var target: ContentUriDocumentOutputTarget

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        target = ContentUriDocumentOutputTarget(context, TestDispatcherProvider())
    }

    @Test
    fun `ghi vào file nháp thì tạo file thật với đúng nội dung`() = runTest {
        val file = java.io.File(context.cacheDir, "hop-dong.pdf")

        target.open(PdfOutputTarget.LocalFile(file)).use { stream ->
            stream.write("nội dung PDF".toByteArray())
        }

        assertTrue(file.exists())
        assertEquals("nội dung PDF", file.readText())
    }

    @Test
    fun `ghi đè file nháp ngắn hơn thì không còn đuôi của nội dung cũ`() = runTest {
        val file = java.io.File(context.cacheDir, "de-ghi-de.pdf")
        target.open(PdfOutputTarget.LocalFile(file)).use { it.write("nội dung rất dài ban đầu".toByteArray()) }

        target.open(PdfOutputTarget.LocalFile(file)).use { it.write("ngắn".toByteArray()) }

        assertEquals(
            "Đuôi của nội dung cũ phải bị cắt, nếu không PDF sẽ hỏng",
            "ngắn",
            file.readText(),
        )
    }

    @Test
    fun `tạo thư mục cha nếu file nháp nằm trong thư mục chưa có`() = runTest {
        val nested = java.io.File(context.cacheDir, "scan_scratch/con/thu-muc.pdf")

        target.open(PdfOutputTarget.LocalFile(nested)).use { it.write(byteArrayOf(1, 2)) }

        assertTrue("File phải được tạo cùng thư mục cha", nested.exists())
    }

    @Test
    fun `URI không hợp lệ thì báo lỗi rõ ràng thay vì ghi bừa`() = runTest {
        val result = runCatching { target.open(PdfOutputTarget.SafUri("khong-phai-uri")) }

        val failure = result.exceptionOrNull()
        assertTrue("Phải là IOException: $failure", failure is IOException)
    }

    @Test
    fun `URI trỏ tới nơi không mở được thì báo lỗi`() = runTest {
        // ContentResolver của Robolectric không có provider nào đăng ký cho authority này.
        val result = runCatching {
            target.open(PdfOutputTarget.SafUri("content://khong-ton-tai/file.pdf"))
        }

        assertTrue(result.exceptionOrNull() is IOException)
    }
}
