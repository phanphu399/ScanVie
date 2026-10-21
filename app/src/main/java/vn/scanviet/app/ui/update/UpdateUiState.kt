package vn.scanviet.app.ui.update

import vn.scanviet.app.domain.model.AppRelease

/**
 * Trạng thái của khối "Cập nhật ứng dụng".
 *
 * Vì sao tách thành nhiều trạng thái thay vì vài cờ boolean: màn hình cần hiển thị rất khác
 * nhau giữa "đang kiểm tra", "đã mới nhất", "có bản mới", "đang tải 40%" và "lỗi mạng". Dùng
 * cờ boolean dễ tạo ra tổ hợp vô nghĩa (vừa "có bản mới" vừa "đang tải" vừa "lỗi") mà không
 * ai biết hiển thị cái nào.
 */
sealed interface UpdateUiState {

    /** Chưa kiểm tra lần nào trong phiên này. */
    data object Idle : UpdateUiState

    /** Đang hỏi nguồn phát hành. */
    data object Checking : UpdateUiState

    /** Đang dùng bản mới nhất. */
    data object UpToDate : UpdateUiState

    /** Có bản mới, chờ người dùng quyết định. */
    data class Available(val release: AppRelease) : UpdateUiState

    /**
     * Người dùng đã đóng lời nhắc ở màn hình Thư viện.
     *
     * Vì sao giữ lại `release` thay vì quay về [Idle]: khối "Cập nhật" trong Cài đặt vẫn phải
     * hiện bản mới và nút tải. Nếu xoá mất thông tin bản phát hành, người dùng đóng lời nhắc
     * xong sẽ không còn cách nào tải bản mới.
     *
     * Vì sao không lưu xuống đĩa: đây là lựa chọn cho **phiên làm việc này**. Lần sau mở ứng
     * dụng, người dùng thấy lại lời nhắc — họ đã bỏ qua một lần chứ chưa từ chối cập nhật.
     */
    data class BannerDismissed(val release: AppRelease) : UpdateUiState

    /** Đang tải APK về máy. */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : UpdateUiState {

        /**
         * Phần trăm đã tải, hoặc `null` khi máy chủ không cho biết tổng dung lượng.
         *
         * Trả `null` thay vì một giá trị bịa: thanh tiến trình chạy mãi ở một mức sai còn khó
         * hiểu hơn một vòng xoay không xác định rõ ràng.
         */
        val percent: Int?
            get() = if (totalBytes > 0L) {
                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                null
            }
    }

    /** Tải xong, đã mở trình cài đặt của hệ thống. */
    data object Installed : UpdateUiState

    /**
     * Không kiểm tra được.
     *
     * Vì sao tách [CheckFailed] khỏi [DownloadFailed]: hai lỗi này cần hai hành động khác nhau.
     * Kiểm tra lỗi thì thử lại được ngay; tải lỗi giữa chừng thì nên nói rõ là chưa tải xong
     * để người dùng không tưởng ứng dụng đã hỏng.
     */
    data object CheckFailed : UpdateUiState

    data object DownloadFailed : UpdateUiState

    /** Bản phát hành có bản mới nhưng thiếu thông tin cần thiết để tải. */
    data object Unsupported : UpdateUiState

    /**
     * Đã tải xong nhưng hệ thống chưa cho phép cài đặt từ nguồn này.
     *
     * Vì sao cần trạng thái riêng thay vì quay về [Available]: người dùng vừa bị đưa sang màn
     * hình Cài đặt của hệ thống và quay lại. Nếu màn hình chỉ hiện lại "Có bản cập nhật" mà
     * không giải thích, họ sẽ tưởng nút tải bị hỏng. Một dòng chữ nói rõ cần bật quyền gì là
     * đủ để họ hiểu và bấm lại.
     */
    data class InstallPermissionRequired(val release: AppRelease) : UpdateUiState
}
