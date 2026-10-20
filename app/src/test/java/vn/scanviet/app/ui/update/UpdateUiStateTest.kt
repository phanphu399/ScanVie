package vn.scanviet.app.ui.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kiểm tra cách tính phần trăm đã tải.
 *
 * Lý do đáng test: khi máy chủ không gửi `Content-Length`, `totalBytes` bằng 0 và phép chia
 * sẽ ra `NaN`/vô cực nếu không chặn. Một thanh tiến trình hiển thị "50%" trong khi thực tế
 * chưa tải được gì là lỗi khiến người dùng mất niềm tin vào ứng dụng.
 */
class UpdateUiStateTest {

    @Test
    fun `tinh dung phan tram khi biet tong dung luong`() {
        val state = UpdateUiState.Downloading(downloadedBytes = 5_000, totalBytes = 10_000)

        assertEquals(50, state.percent)
    }

    @Test
    fun `chua tai duoc gi thi bang 0 phan tram`() {
        val state = UpdateUiState.Downloading(downloadedBytes = 0, totalBytes = 10_000)

        assertEquals(0, state.percent)
    }

    @Test
    fun `tai xong thi bang 100 phan tram`() {
        val state = UpdateUiState.Downloading(downloadedBytes = 10_000, totalBytes = 10_000)

        assertEquals(100, state.percent)
    }

    @Test
    fun `khong biet tong dung luong thi tra ve null`() {
        val state = UpdateUiState.Downloading(downloadedBytes = 4_096, totalBytes = 0)

        assertNull(state.percent)
    }

    @Test
    fun `khong bao gio vuot qua 100 phan tram`() {
        // Có máy chủ báo Content-Length thiếu; đọc được nhiều hơn số đã báo. Không được hiển
        // thị "140%" vì trông như ứng dụng bị lỗi.
        val state = UpdateUiState.Downloading(downloadedBytes = 14_000, totalBytes = 10_000)

        assertEquals(100, state.percent)
    }
}
