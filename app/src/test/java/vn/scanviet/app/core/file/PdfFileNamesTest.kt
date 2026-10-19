package vn.scanviet.app.core.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tên file PDF là dữ liệu do người dùng nhập đi thẳng vào hệ thống file, nên đây là
 * ranh giới bảo mật: một tên chứa `/` hay `..` có thể ghi ra ngoài thư mục đích.
 */
class PdfFileNamesTest {

    @Test
    fun `giữ nguyên tên đã sạch và thêm đuôi pdf`() {
        assertEquals("hoa-don-dien.pdf", PdfFileNames.forTitle("Hóa đơn điện"))
    }

    @Test
    fun `bỏ dấu tiếng Việt`() {
        assertEquals("bao-cao-tai-chinh.pdf", PdfFileNames.forTitle("Báo cáo tài chính"))
    }

    @Test
    fun `chữ đ được đổi thành d chứ không bị xoá`() {
        assertEquals("don-xin-viec.pdf", PdfFileNames.forTitle("Đơn xin việc"))
    }

    @Test
    fun `dấu phân cách đường dẫn bị loại bỏ`() {
        val name = PdfFileNames.forTitle("../../etc/passwd")

        assertTrue("Không được còn dấu chéo: $name", !name.contains('/'))
        assertTrue("Không được còn dấu chéo ngược: $name", !name.contains('\\'))
        assertTrue("Không được còn '..': $name", !name.contains(".."))
    }

    @Test
    fun `ký tự bị hệ điều hành cấm bị loại bỏ`() {
        val name = PdfFileNames.forTitle("""hop dong: 2026 * "ban chinh" <ky> |x| ?""")

        listOf(':', '*', '"', '<', '>', '|', '?').forEach { illegal ->
            assertTrue("Ký tự $illegal vẫn còn trong $name", !name.contains(illegal))
        }
    }

    @Test
    fun `tên rỗng hoặc toàn ký tự lạ vẫn cho ra tên file dùng được`() {
        assertEquals("tai-lieu.pdf", PdfFileNames.forTitle(""))
        assertEquals("tai-lieu.pdf", PdfFileNames.forTitle("   "))
        assertEquals("tai-lieu.pdf", PdfFileNames.forTitle("///"))
        assertEquals("tai-lieu.pdf", PdfFileNames.forTitle("!!!???"))
    }

    @Test
    fun `tên rất dài bị cắt ngắn để không vượt giới hạn tên file`() {
        val name = PdfFileNames.forTitle("a".repeat(500))

        assertTrue("Tên file quá dài: ${name.length}", name.length <= 70)
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun `luôn kết thúc bằng đuôi pdf`() {
        listOf("Hóa đơn", "", "..", "tài liệu 2026").forEach { title ->
            assertTrue(PdfFileNames.forTitle(title).endsWith(".pdf"))
        }
    }

    @Test
    fun `khoảng trắng và ký tự đặc biệt không tạo ra dấu gạch thừa ở hai đầu`() {
        val name = PdfFileNames.forTitle("  -- Hóa đơn --  ")

        assertTrue("Có gạch thừa ở đầu: $name", !name.startsWith("-"))
        assertTrue("Có gạch thừa ở cuối: $name", !name.removeSuffix(".pdf").endsWith("-"))
    }
}
