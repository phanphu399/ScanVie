package vn.scanviet.app.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra phép chuyển hộp bao pixel -> toạ độ chuẩn hoá.
 *
 * **Vì sao bộ test này quan trọng:** `AndroidPdfExporter` chỉ vẽ lớp chữ ẩn cho những dòng có
 * `hasUsableBox == true`, tức `bottom > top`. Một phiên bản trước của mã nguồn trả
 * `bottom = 0f` cho mọi dòng, nên **toàn bộ chữ biến mất khỏi PDF** mà không có lỗi nào được
 * báo. Đó là lỗi im lặng tệ nhất: người dùng mở PDF, bôi đen không được, và không có gì chỉ ra
 * nguyên nhân.
 *
 * Bộ test này khoá chặt hành vi đúng để lỗi đó không quay lại.
 */
class NormalizedOcrLineTest {

    @Test
    fun `toa do duoc chuan hoa dung theo kich thuoc anh`() {
        // Hộp bao ở góc trên-trái, rộng 1/4 và cao 1/10 trang.
        val line = normalizedOcrLine(
            box = intArrayOf(0, 0, 250, 175),
            text = "Hóa đơn",
            imageWidth = 1000,
            imageHeight = 1750,
        )

        assertNotNull(line)
        assertEquals("Hóa đơn", line!!.text)
        assertEquals(0.0f, line.left, TOLERANCE)
        assertEquals(0.0f, line.top, TOLERANCE)
        assertEquals(0.25f, line.right, TOLERANCE)
        assertEquals(0.1f, line.bottom, TOLERANCE)
    }

    @Test
    fun `hop bao co toa do goc khac 0 duoc tinh dung`() {
        // Điểm mấu chốt: `right` và `bottom` phải là x+w và y+h, KHÔNG phải w và h.
        // Nếu nhầm, chữ ẩn sẽ bị vẽ lệch về góc trên-trái của trang.
        val line = normalizedOcrLine(
            box = intArrayOf(200, 350, 400, 70),
            text = "Tiếng Việt",
            imageWidth = 1000,
            imageHeight = 1000,
        )

        assertNotNull(line)
        assertEquals(0.2f, line!!.left, TOLERANCE)
        assertEquals(0.35f, line.top, TOLERANCE)
        assertEquals(0.6f, line.right, TOLERANCE)
        assertEquals(0.42f, line.bottom, TOLERANCE)
    }

    @Test
    fun `moi dong hop le deu co hop bao dung duoc cho lop chu an trong PDF`() {
        // Đây là tính chất mà PDF exporter phụ thuộc. Test theo nhiều hộp bao khác nhau để
        // chắc chắn không có nhánh nào trả về hộp bao suy biến.
        val boxes = listOf(
            intArrayOf(0, 0, 1, 1),
            intArrayOf(10, 20, 300, 40),
            intArrayOf(500, 900, 499, 100),
            intArrayOf(999, 1749, 1, 1),
        )

        boxes.forEach { box ->
            val line = normalizedOcrLine(box, "chữ", imageWidth = 1000, imageHeight = 1750)
            assertNotNull("Hộp bao ${box.toList()} phải dùng được", line)
            assertTrue(
                "Hộp bao ${box.toList()} phải có bottom > top, nếu không chữ sẽ mất khỏi PDF",
                line!!.hasUsableBox,
            )
        }
    }

    @Test
    fun `chu rong bi loai`() {
        assertNull(normalizedOcrLine(intArrayOf(0, 0, 10, 10), "", 100, 100))
        assertNull(normalizedOcrLine(intArrayOf(0, 0, 10, 10), "   ", 100, 100))
        assertNull(normalizedOcrLine(intArrayOf(0, 0, 10, 10), "\n\t", 100, 100))
    }

    @Test
    fun `chu duoc cat khoang trang thua`() {
        val line = normalizedOcrLine(intArrayOf(0, 0, 10, 10), "  Xin chào  ", 100, 100)

        assertNotNull(line)
        // Khoảng trắng đầu/cuối không mang thông tin và làm lệch chiều rộng khi vẽ chữ ẩn.
        assertEquals("Xin chào", line!!.text)
    }

    @Test
    fun `hop bao rong hoac am bi loai`() {
        // Tesseract trả hộp 0x0 cho phần tử nó nhận ra là chữ nhưng không định vị được.
        // Lưu chúng lại sẽ tạo ra chữ ẩn chồng lên nhau ở góc trang.
        assertNull(normalizedOcrLine(intArrayOf(10, 10, 0, 5), "chữ", 100, 100))
        assertNull(normalizedOcrLine(intArrayOf(10, 10, 5, 0), "chữ", 100, 100))
        assertNull(normalizedOcrLine(intArrayOf(10, 10, 0, 0), "chữ", 100, 100))
        assertNull(normalizedOcrLine(intArrayOf(10, 10, -5, 5), "chữ", 100, 100))
    }

    @Test
    fun `hop bao sai dinh dang bi loai chu khong gay loi`() {
        // Phòng thủ trước dữ liệu bất thường từ tầng native: đọc chỉ số ngoài mảng sẽ ném
        // IndexOutOfBoundsException và làm hỏng cả lần nhận dạng.
        assertNull(normalizedOcrLine(null, "chữ", 100, 100))
        assertNull(normalizedOcrLine(intArrayOf(), "chữ", 100, 100))
        assertNull(normalizedOcrLine(intArrayOf(1, 2), "chữ", 100, 100))
        assertNull(normalizedOcrLine(intArrayOf(1, 2, 3), "chữ", 100, 100))
    }

    @Test
    fun `kich thuoc anh khong hop le bi loai`() {
        // Chia cho 0 sẽ cho Infinity, và toạ độ Infinity làm `textScaleX` trong PDF exporter
        // nhận giá trị vô lý. Loại ngay từ đầu thay vì để lỗi lan ra xa.
        assertNull(normalizedOcrLine(intArrayOf(1, 1, 1, 1), "chữ", 0, 100))
        assertNull(normalizedOcrLine(intArrayOf(1, 1, 1, 1), "chữ", 100, 0))
        assertNull(normalizedOcrLine(intArrayOf(1, 1, 1, 1), "chữ", -1, 100))
    }

    @Test
    fun `hop bao nho ra ngoai anh duoc kep ve trong khoang 0 den 1`() {
        // Tesseract đôi khi trả hộp bao nhô ra ngoài ảnh. Toạ độ phải nằm trong 0..1 vì
        // chúng được nhân với kích thước trang PDF.
        val line = normalizedOcrLine(
            box = intArrayOf(-20, -20, 120, 120),
            text = "chữ",
            imageWidth = 100,
            imageHeight = 100,
        )

        assertNotNull(line)
        assertEquals(0.0f, line!!.left, TOLERANCE)
        assertEquals(0.0f, line.top, TOLERANCE)
        assertEquals(1.0f, line.right, TOLERANCE)
        assertEquals(1.0f, line.bottom, TOLERANCE)
    }

    @Test
    fun `hop bao nam hoan toan ngoai anh bi loai`() {
        // Sau khi kẹp, hộp này suy biến (left == right), nên phải bị loại thay vì lưu lại
        // một dòng không vẽ được.
        val line = normalizedOcrLine(
            box = intArrayOf(200, 200, 50, 50),
            text = "chữ",
            imageWidth = 100,
            imageHeight = 100,
        )

        assertNull(line)
    }

    @Test
    fun `hop bao cham mep phai va day anh khong bi mat chu`() {
        // Trường hợp biên quan trọng: dòng chữ ở cuối trang, chạm hẳn mép phải và mép dưới.
        val line = normalizedOcrLine(
            box = intArrayOf(900, 1700, 100, 50),
            text = "Hết",
            imageWidth = 1000,
            imageHeight = 1750,
        )

        assertNotNull(line)
        assertTrue(line!!.hasUsableBox)
        assertEquals(1.0f, line.right, TOLERANCE)
        assertEquals(1.0f, line.bottom, TOLERANCE)
    }

    @Test
    fun `hop bao suy bien sau khi kep thi bi loai`() {
        // Đây chính là dạng lỗi đã từng xảy ra: một dòng có `bottom == top` sẽ bị
        // `AndroidPdfExporter` bỏ qua. Hàm phải chủ động loại nó, để database không chứa
        // những dòng chắc chắn không dùng được.
        val degenerate = OcrLine(text = "chữ", left = 0f, top = 0f, right = 1f, bottom = 0f)
        assertFalse(degenerate.hasUsableBox)
    }

    private companion object {
        /** Sai số cho phép khi so số thực, đủ nhỏ để bắt lỗi tính toán thật. */
        const val TOLERANCE = 0.0001f
    }
}
