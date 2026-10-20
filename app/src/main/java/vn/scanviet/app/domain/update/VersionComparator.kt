package vn.scanviet.app.domain.update

/**
 * So sánh số phiên bản dạng `major.minor.patch` (có thể thiếu phần).
 *
 * Vì sao không dùng so sánh chuỗi: `"0.10.0" > "0.9.0"` là đúng theo nghĩa phiên bản nhưng
 * SAI khi so chuỗi (`'1' < '9'`). So chuỗi sẽ khiến ứng dụng coi bản 0.10.0 là cũ hơn 0.9.0
 * và không bao giờ báo cập nhật — lỗi âm thầm, người dùng chỉ phát hiện khi đã lâu không
 * nhận được bản sửa lỗi.
 *
 * Quy tắc:
 * - Bỏ tiền tố `v` hoặc `V`: `"v1.2.0"` bằng `"1.2.0"`.
 * - Chỉ so phần số, dừng ở ký tự không phải chữ số.
 * - Hậu tố như `-beta`, `-rc1` bị bỏ qua khi so sánh; phiên bản phát hành chính thức luôn
 *   được coi là mới hơn cùng số đó có hậu tố. Điều này đủ cho MVP; nếu sau này cần so sánh
 *   pre-release đầy đủ thì thay bằng thư viện chuyên dụng.
 * - Phần thiếu được coi là 0: `"1.2"` bằng `"1.2.0"`.
 */
object VersionComparator {

    /** @return số dương nếu [a] mới hơn [b], âm nếu cũ hơn, 0 nếu bằng nhau. */
    fun compare(a: String, b: String): Int {
        val left = parse(a)
        val right = parse(b)
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) return l - r
        }
        return 0
    }

    /** @return true nếu [candidate] mới hơn [current]. */
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    /**
     * Tách phần số của phiên bản.
     *
     * Bỏ tiền tố `v`/`V` trước, rồi cắt tại ký tự đầu tiên không thuộc `0-9` hoặc `.` để bỏ
     * hậu tố (`1.2.0-beta` -> `1.2.0`).
     *
     * Vì sao bỏ tiền tố ở ĐÂY chứ không chỉ ở nơi đọc dữ liệu: nhãn phát hành dạng `v1.2.0` là
     * quy ước rất phổ biến, và tiền tố có thể đến từ nguồn khác trong tương lai (máy chủ riêng,
     * dịch vụ khác) mà không ai nhớ bỏ. Nếu để lọt, `takeWhile` dừng ngay ở ký tự `v`, cả chuỗi
     * bị coi là phiên bản 0, và ứng dụng **im lặng** báo "đã là bản mới nhất" — không có lỗi,
     * không có log, người dùng chỉ thấy mãi không có bản cập nhật.
     *
     * Chuỗi rỗng hoặc không có số trả về danh sách rỗng, tức phiên bản 0.
     */
    private fun parse(version: String): List<Int> {
        val withoutPrefix = version.trim().removePrefix("v").removePrefix("V")
        val numeric = withoutPrefix.takeWhile { it.isDigit() || it == '.' }
        if (numeric.isEmpty()) return emptyList()
        return numeric.split('.')
            .map { part -> part.toIntOrNull() ?: 0 }
    }
}
