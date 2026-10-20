package vn.scanviet.app.domain.model

/** Cài đặt người dùng. MVP không có tài khoản nên không có hồ sơ cá nhân. */
data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val autoOcr: Boolean = true,
    val keepOriginalImage: Boolean = false,

    /**
     * Tự kiểm tra bản cập nhật khi mở ứng dụng.
     *
     * Mặc định BẬT, nhưng đây là một lời gọi mạng duy nhất tới GitHub để đọc một file JSON nhỏ.
     * Không gửi kèm dữ liệu tài liệu hay thông tin nhận dạng nào. Người dùng tắt được trong
     * Cài đặt vì đây là quyền riêng tư của họ, không phải lựa chọn của nhà phát triển.
     */
    val checkForUpdates: Boolean = true,
)
