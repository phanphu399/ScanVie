package vn.scanviet.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import vn.scanviet.app.domain.model.ThemePreference

/**
 * Chủ đề của ứng dụng.
 *
 * Không dùng màu động (dynamic color): giữ giao diện nhất quán giữa các máy để ảnh
 * chụp màn hình trên Google Play trông đồng nhất.
 *
 * **Vì sao phải đặt lại màu biểu tượng thanh trạng thái ở đây:** theme XML chỉ khai
 * `windowLightStatusBar=true` (biểu tượng màu TỐI) vì nó không biết người dùng chọn
 * chế độ nào — lựa chọn đó nằm trong DataStore. Khi bật chế độ tối, nền thanh trạng
 * thái chuyển sang đen nhưng biểu tượng vẫn màu tối, khiến giờ/pin gần như vô hình.
 * Gọi `isAppearanceLightStatusBars` ngay trong composition sửa được điều đó, và chạy
 * lại mỗi khi `darkTheme` đổi nên đổi chế độ trong lúc dùng vẫn đúng ngay.
 */
@Composable
fun ScanVietTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // `darkTheme` = true -> thanh trạng thái tối -> cần biểu tượng SÁNG.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ScanVietTypography,
        content = content,
    )
}
