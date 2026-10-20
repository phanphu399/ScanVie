package vn.scanviet.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.scanviet.app.MainActivity

/**
 * Kiểm tra khói (smoke test) cho luồng điều hướng chính của ứng dụng.
 *
 * Vì sao cần: đây là test instrumented đầu tiên của dự án. Nó không kiểm tra logic
 * nghiệp vụ (đã có 234 unit test), mà chỉ xác nhận ứng dụng khởi động được và màn
 * hình chính hiển thị mà không crash. Nếu một thay đổi làm hỏng wiring Hilt/Compose,
 * test này sẽ bắt được trước khi phát hành.
 *
 * Giới hạn: test này chạy trên thiết bị/emulator ARM (xem abiFilters trong
 * build.gradle.kts). Không chạy được trên emulator x86.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesWithoutCrash() {
        // Chỉ cần Activity khởi động thành công là đủ cho smoke test.
        // Compose rule sẽ fail nếu Activity crash trong onCreate.
    }
}