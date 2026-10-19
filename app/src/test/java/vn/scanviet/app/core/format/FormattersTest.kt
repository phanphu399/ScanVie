package vn.scanviet.app.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.scanviet.app.testing.FakeTimeProvider

class FormattersTest {

    @Test
    fun `dung lượng nhỏ hiển thị theo byte`() {
        assertEquals("512 B", Formatters.fileSize(512))
    }

    @Test
    fun `dung lượng KB không có phần thập phân`() {
        assertEquals("1 KB", Formatters.fileSize(1024))
        assertEquals("500 KB", Formatters.fileSize(512_000))
    }

    @Test
    fun `dung lượng MB có một chữ số thập phân theo kiểu Việt Nam`() {
        // 1,2 MB — dấu phẩy thập phân của tiếng Việt
        assertEquals("1,2 MB", Formatters.fileSize(1_258_291))
    }

    @Test
    fun `dung lượng GB có hai chữ số thập phân`() {
        assertEquals("1,50 GB", Formatters.fileSize(1_610_612_736))
    }

    @Test
    fun `thời gian tương đối dưới một phút`() {
        val now = FakeTimeProvider.DEFAULT_TIME
        assertEquals("Vừa xong", Formatters.relativeTime(now, now))
        assertEquals("Vừa xong", Formatters.relativeTime(now - 59_000, now))
    }

    @Test
    fun `thời gian tương đối theo phút và giờ`() {
        val now = FakeTimeProvider.DEFAULT_TIME
        assertEquals("12 phút trước", Formatters.relativeTime(now - 12 * 60_000, now))
        assertEquals("3 giờ trước", Formatters.relativeTime(now - 3 * 3_600_000, now))
    }

    @Test
    fun `một ngày trước hiển thị Hôm qua`() {
        val now = FakeTimeProvider.DEFAULT_TIME
        assertEquals("Hôm qua", Formatters.relativeTime(now - FakeTimeProvider.ONE_DAY_MILLIS, now))
    }

    @Test
    fun `xa hơn một tuần hiển thị ngày cụ thể`() {
        val now = FakeTimeProvider.DEFAULT_TIME
        val old = now - 30L * FakeTimeProvider.ONE_DAY_MILLIS
        val formatted = Formatters.relativeTime(old, now)
        // Định dạng dd/MM/yyyy
        assert(formatted.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
            "Mong đợi ngày dạng dd/MM/yyyy nhưng nhận được: $formatted"
        }
    }

    @Test
    fun `thời gian trong tương lai không gây lỗi hiển thị`() {
        val now = FakeTimeProvider.DEFAULT_TIME
        assertEquals("Vừa xong", Formatters.relativeTime(now + 60_000, now))
    }
}
