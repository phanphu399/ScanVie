package vn.scanviet.app.domain.update

/**
 * Quyết định có nên tự kiểm tra cập nhật lúc này hay không.
 *
 * Vì sao cần một lớp riêng chỉ để so sánh hai con số: đây là chỗ dễ sai theo cách **im lặng**.
 * Nếu điều kiện viết ngược, ứng dụng kiểm tra mỗi lần mở (tốn pin, dễ bị GitHub chặn), hoặc
 * ngược lại **không bao giờ** kiểm tra và người dùng mãi không nhận được bản sửa lỗi. Cả hai
 * lỗi đều không ném ra exception, không có log — chỉ có hành vi sai. Tách thành hàm thuần cho
 * phép test bằng JVM thường, không cần thiết bị.
 *
 * Quy tắc:
 * - [lastCheckAt] <= 0: chưa từng kiểm tra -> kiểm tra.
 * - [lastCheckAt] ở **tương lai** (đồng hồ máy bị chỉnh ngược, hoặc người dùng đổi múi giờ
 *   thủ công): coi như đã quá hạn. Nếu không xử lý riêng trường hợp này, hiệu số âm sẽ luôn
 *   nhỏ hơn khoảng chờ và ứng dụng **im lặng không kiểm tra nữa** cho tới khi đồng hồ vượt qua
 *   mốc cũ — có máy bị kẹt vĩnh viễn như vậy.
 * - Đúng bằng khoảng chờ: kiểm tra (dùng `>=`, không phải `>`).
 */
object UpdateCheckSchedule {

    /** 24 giờ. Đủ thưa để không phiền, đủ dày để bản sửa lỗi tới tay trong vòng một ngày. */
    const val DEFAULT_INTERVAL_MILLIS: Long = 24L * 60L * 60L * 1000L

    /**
     * @param lastCheckAt thời điểm kiểm tra gần nhất (`System.currentTimeMillis()`), 0 nếu chưa từng.
     * @param now thời điểm hiện tại.
     * @param intervalMillis khoảng chờ tối thiểu giữa hai lần tự kiểm tra.
     * @return true nếu nên tự kiểm tra.
     */
    fun shouldCheck(
        lastCheckAt: Long,
        now: Long,
        intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    ): Boolean {
        if (lastCheckAt <= 0L) return true
        val elapsed = now - lastCheckAt
        // Đồng hồ lùi: tin lần kiểm tra hiện tại hơn là tin mốc thời gian vô lý trong tương lai.
        if (elapsed < 0L) return true
        return elapsed >= intervalMillis
    }
}
