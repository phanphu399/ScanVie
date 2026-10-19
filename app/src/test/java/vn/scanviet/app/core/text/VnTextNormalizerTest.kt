package vn.scanviet.app.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class VnTextNormalizerTest {

    @Test
    fun `bỏ dấu tiếng Việt`() {
        assertEquals("hoa don dien", VnTextNormalizer.normalize("Hóa đơn điện"))
    }

    @Test
    fun `xử lý chữ đ và Đ`() {
        assertEquals("dang ky dat dai", VnTextNormalizer.normalize("Đăng ký đất đai"))
    }

    @Test
    fun `xử lý các nguyên âm có nhiều dấu`() {
        // Dễ -> de, hiểu -> hieu, ngỡ -> ngo, ngàng -> ngang
        assertEquals("de hieu ngo ngang", VnTextNormalizer.normalize("Dễ hiểu ngỡ ngàng"))
    }

    @Test
    fun `chuỗi đã ở dạng NFD vẫn chuẩn hoá đúng`() {
        val composed = "Hóa đơn"
        val decomposed = java.text.Normalizer.normalize(composed, java.text.Normalizer.Form.NFD)
        assertEquals(VnTextNormalizer.normalize(composed), VnTextNormalizer.normalize(decomposed))
    }

    @Test
    fun `gộp khoảng trắng thừa và cắt hai đầu`() {
        assertEquals("hoa don", VnTextNormalizer.normalize("   Hóa    đơn  "))
    }

    @Test
    fun `thoát ký tự đặc biệt của LIKE`() {
        assertEquals("50\\%", VnTextNormalizer.escapeLikePattern("50%"))
        assertEquals("a\\_b", VnTextNormalizer.escapeLikePattern("a_b"))
        assertEquals("c\\\\d", VnTextNormalizer.escapeLikePattern("c\\d"))
    }

    @Test
    fun `normalizeForQuery kết hợp chuẩn hoá và thoát ký tự`() {
        assertEquals("giam 50\\%", VnTextNormalizer.normalizeForQuery("Giảm 50%"))
    }

    @Test
    fun `chuỗi rỗng giữ nguyên rỗng`() {
        assertEquals("", VnTextNormalizer.normalize(""))
        assertEquals("", VnTextNormalizer.normalizeForQuery("   "))
    }
}
