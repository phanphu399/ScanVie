package vn.scanviet.app.core.text

import java.text.Normalizer
import java.util.Locale

/**
 * Chuẩn hoá tiếng Việt để tìm kiếm không cần dấu.
 *
 * Vì sao cần: SQLite `LIKE` so khớp theo ký tự, nên "hoa don" sẽ không khớp "Hóa đơn".
 * Cách xử lý: lưu thêm một cột đã chuẩn hoá (`titleSearch`) và tìm trên cột đó.
 * Bản gốc vẫn được giữ nguyên để hiển thị.
 */
object VnTextNormalizer {

    private val COMBINING_MARKS = "\\p{Mn}+".toRegex()
    private val WHITESPACE = "\\s+".toRegex()

    /**
     * "Hóa Đơn Điện" -> "hoa don dien"
     */
    fun normalize(input: String): String {
        val lowered = input.lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
        val withoutMarks = decomposed.replace(COMBINING_MARKS, "")
        val withoutDStroke = withoutMarks.replace('đ', 'd')
        return withoutDStroke.replace(WHITESPACE, " ").trim()
    }

    /**
     * Thoát các ký tự đặc biệt của `LIKE` để người dùng gõ "%" hoặc "_" không bị coi là wildcard.
     * Phải dùng kèm `ESCAPE '\'` trong câu truy vấn SQL.
     */
    fun escapeLikePattern(input: String): String = input
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    /** Chuẩn hoá rồi thoát ký tự đặc biệt: dùng ngay cho tham số truy vấn tìm kiếm. */
    fun normalizeForQuery(input: String): String = escapeLikePattern(normalize(input))
}
