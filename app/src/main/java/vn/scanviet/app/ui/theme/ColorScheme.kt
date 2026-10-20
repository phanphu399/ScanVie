package vn.scanviet.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Bảng màu chế độ SÁNG.
 *
 * Khai đủ các vai trò chứ không chỉ `primary`/`secondary`/`tertiary` — xem ghi chú
 * ở [Color.kt]: thiếu vai trò nào thì Material lấp bằng màu mặc định (tông tím).
 */
internal val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color_White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = BlueGrey40,
    onSecondary = Color_White,
    tertiary = Orange40,
    onTertiary = Color_White,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    // Thẻ dùng nền sáng hơn nền chung để tách khỏi phông — cùng nguyên tắc với
    // chế độ tối, chỉ đảo chiều.
    surface = Color_White,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

/**
 * Bảng màu chế độ TỐI.
 *
 * `surfaceContainer*` là các bậc "nổi" của Material 3: thẻ dùng bậc cao hơn nền để
 * tách khỏi phông. Không khai thì Material dùng mặc định tím của nó.
 */
internal val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Color_OnBlue80,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = BlueGrey80,
    onSecondary = Color_OnBlueGrey80,
    tertiary = Orange80,
    onTertiary = Color_OnOrange80,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceContainerDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
)
