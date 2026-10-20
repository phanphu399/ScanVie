package vn.scanviet.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Bảng màu ScanViet — tông xanh tài liệu, cam cho hành động.
//
// Vì sao phải khai ĐỦ các vai trò (không chỉ primary/secondary/tertiary):
// khi chỉ khai ba vai trò đó, Material 3 lấp phần còn lại bằng bảng màu MẶC ĐỊNH
// của nó — vốn có tông TÍM. Kết quả là bật chế độ tối thì ứng dụng đổi hẳn sang
// tông tím, mất bản sắc thương hiệu, trong khi chế độ sáng vẫn xanh. Đây là lỗi
// thật đã tồn tại ở Giai đoạn 2, sửa ở Giai đoạn B.
//
// Vì sao khai hai bảng riêng cho sáng/tối thay vì dùng Material Theme Builder:
// ứng dụng phải cố định màu (không dùng dynamic color) để ảnh chụp màn hình trên
// Google Play trông đồng nhất giữa các máy — xem Theme.kt.
// ---------------------------------------------------------------------------

// Trắng và các màu chữ đặt TRÊN nền đậm. Khai thành hằng số có tên thay vì rải
// `Color.White` khắp nơi để khi cần đổi (ví dụ sang trắng ngà) chỉ sửa một chỗ.
internal val Color_White = Color(0xFFFFFFFF)
internal val Color_OnBlue80 = Color(0xFF00325A)
internal val Color_OnBlueGrey80 = Color(0xFF24313C)
internal val Color_OnOrange80 = Color(0xFF552100)

// --- Chế độ SÁNG ---
internal val Blue40 = Color(0xFF1B5E9B)
internal val BlueGrey40 = Color(0xFF4A5A6A)
internal val Orange40 = Color(0xFFB4551A)

// Nền và bề mặt của chế độ sáng. Không dùng trắng tuyệt đối (#FFFFFF) cho nền:
// trên màn hình AMOLED, nền trắng tuyệt đối cạnh thẻ trắng làm mất ranh giới giữa
// thẻ và nền. Một chút xám xanh (#F6F8FB) giữ được chiều sâu mà vẫn sáng.
internal val SurfaceLight = Color(0xFFF6F8FB)
internal val OnSurfaceLight = Color(0xFF191C1E)
internal val SurfaceVariantLight = Color(0xFFDDE3EA)
internal val OnSurfaceVariantLight = Color(0xFF41474D)
internal val OutlineLight = Color(0xFF71787E)
internal val OutlineVariantLight = Color(0xFFC1C7CE)
internal val ErrorLight = Color(0xFFBA1A1A)
internal val OnErrorLight = Color(0xFFFFFFFF)
internal val ErrorContainerLight = Color(0xFFFFDAD6)
internal val OnErrorContainerLight = Color(0xFF410002)
internal val PrimaryContainerLight = Color(0xFFD1E4FF)
internal val OnPrimaryContainerLight = Color(0xFF001D36)

// --- Chế độ TỐI ---
internal val Blue80 = Color(0xFF9FC9F0)
internal val BlueGrey80 = Color(0xFFC2D0DC)
internal val Orange80 = Color(0xFFFFB68A)

// Nền tối dùng xanh đen (#101418) chứ không dùng xám trung tính: giữ được tông
// xanh của thương hiệu trong khi vẫn đủ tối để tiết kiệm pin trên AMOLED.
internal val SurfaceDark = Color(0xFF101418)
internal val OnSurfaceDark = Color(0xFFE1E3E6)
internal val SurfaceVariantDark = Color(0xFF41474D)
internal val OnSurfaceVariantDark = Color(0xFFC1C7CE)
internal val OutlineDark = Color(0xFF8B9198)
internal val OutlineVariantDark = Color(0xFF41474D)
internal val ErrorDark = Color(0xFFFFB4AB)
internal val OnErrorDark = Color(0xFF690005)
internal val ErrorContainerDark = Color(0xFF93000A)
internal val OnErrorContainerDark = Color(0xFFFFDAD6)
internal val PrimaryContainerDark = Color(0xFF00497D)
internal val OnPrimaryContainerDark = Color(0xFFD1E4FF)

// Bề mặt nổi (thẻ) của chế độ tối: sáng hơn nền một bậc để thẻ tách khỏi nền,
// đúng quy ước của Material 3 (elevation thể hiện bằng độ sáng, không bằng bóng).
internal val SurfaceContainerDark = Color(0xFF1C2024)
internal val SurfaceContainerHighDark = Color(0xFF262A2F)
