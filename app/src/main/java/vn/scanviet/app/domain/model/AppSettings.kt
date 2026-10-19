package vn.scanviet.app.domain.model

/** Cài đặt người dùng. MVP không có tài khoản nên không có hồ sơ cá nhân. */
data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val autoOcr: Boolean = true,
    val keepOriginalImage: Boolean = false,
)
