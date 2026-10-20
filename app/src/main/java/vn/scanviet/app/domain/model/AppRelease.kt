package vn.scanviet.app.domain.model

/**
 * Một bản phát hành mới của ứng dụng, lấy từ nguồn kiểm tra cập nhật.
 *
 * @param versionName phiên bản dạng người đọc, ví dụ "0.4.0".
 * @param downloadUrl nơi tải APK. Chỉ dùng khi người dùng bấm cập nhật.
 * @param sizeBytes dung lượng file, dùng để hiển thị trước khi tải và cảnh báo khi dùng mạng di động.
 * @param releaseNotes mô tả ngắn, có thể rỗng nếu nguồn không cung cấp.
 */
data class AppRelease(
    val versionName: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0L,
    val releaseNotes: String = "",
)

/**
 * Kết quả kiểm tra cập nhật.
 *
 * Vì sao tách [UpToDate] khỏi [Available] thay vì trả về `AppRelease?`: màn hình cần phân biệt
 * "đã là bản mới nhất" với "kiểm tra thất bại". Nếu gộp chung thành `null`, người dùng bấm kiểm
 * tra khi mất mạng sẽ thấy thông báo "bạn đang dùng bản mới nhất" — sai sự thật và khiến họ
 * không thử lại nữa.
 */
sealed interface UpdateCheckResult {
    /** Đang dùng bản mới nhất. */
    data object UpToDate : UpdateCheckResult

    /** Có bản mới hơn. */
    data class Available(val release: AppRelease) : UpdateCheckResult
}
