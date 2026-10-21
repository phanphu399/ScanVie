package vn.scanviet.app.domain.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra quy tắc quyết định tự kiểm tra cập nhật.
 *
 * Lý do đáng test kỹ: mọi lỗi ở đây đều **im lặng**. Viết ngược điều kiện thì ứng dụng hoặc
 * gọi mạng mỗi lần mở (tốn pin, dễ bị GitHub chặn), hoặc không bao giờ kiểm tra nữa và người
 * dùng mãi không nhận được bản sửa lỗi. Không exception, không log — chỉ có hành vi sai.
 */
class UpdateCheckScheduleTest {

    private val day: Long = UpdateCheckSchedule.DEFAULT_INTERVAL_MILLIS

    @Test
    fun `chua tung kiem tra thi kiem tra ngay`() {
        assertTrue(UpdateCheckSchedule.shouldCheck(lastCheckAt = 0L, now = 1_700_000_000_000L))
    }

    @Test
    fun `moc thoi gian am cung coi nhu chua kiem tra`() {
        // Dữ liệu cũ hoặc file hỏng có thể để lại giá trị âm. Không được coi là "vừa mới kiểm
        // tra xong" rồi bỏ qua mãi mãi.
        assertTrue(UpdateCheckSchedule.shouldCheck(lastCheckAt = -1L, now = 1_700_000_000_000L))
    }

    @Test
    fun `vua kiem tra xong thi khong kiem tra lai`() {
        val now = 1_700_000_000_000L

        assertFalse(UpdateCheckSchedule.shouldCheck(lastCheckAt = now, now = now))
    }

    @Test
    fun `chua du mot ngay thi khong kiem tra lai`() {
        val now = 1_700_000_000_000L
        val lastCheck = now - (day - 1)

        assertFalse(UpdateCheckSchedule.shouldCheck(lastCheckAt = lastCheck, now = now))
    }

    @Test
    fun `du mot ngay thi kiem tra`() {
        val now = 1_700_000_000_000L

        assertTrue(UpdateCheckSchedule.shouldCheck(lastCheckAt = now - day, now = now))
    }

    @Test
    fun `qua mot ngay thi kiem tra`() {
        val now = 1_700_000_000_000L

        assertTrue(UpdateCheckSchedule.shouldCheck(lastCheckAt = now - day - 1, now = now))
    }

    @Test
    fun `moc thoi gian o tuong lai thi kiem tra lai thay vi ket vinh vien`() {
        // Người dùng chỉnh đồng hồ máy lùi lại, hoặc đổi múi giờ. Mốc cũ nằm ở tương lai nên
        // hiệu số âm. Nếu chỉ so `elapsed >= interval` thì ứng dụng không bao giờ kiểm tra nữa
        // cho tới khi đồng hồ vượt qua mốc cũ — có máy kẹt như vậy vĩnh viễn.
        val now = 1_700_000_000_000L
        val lastCheck = now + day * 365

        assertTrue(UpdateCheckSchedule.shouldCheck(lastCheckAt = lastCheck, now = now))
    }

    @Test
    fun `dung khoang cho tuy chinh cung tinh la da toi han`() {
        assertTrue(
            UpdateCheckSchedule.shouldCheck(
                lastCheckAt = 1_000L,
                now = 1_000L + 60_000L,
                intervalMillis = 60_000L,
            ),
        )
    }

    @Test
    fun `khoang cho mac dinh la mot ngay`() {
        assertTrue(UpdateCheckSchedule.DEFAULT_INTERVAL_MILLIS == 86_400_000L)
    }
}
