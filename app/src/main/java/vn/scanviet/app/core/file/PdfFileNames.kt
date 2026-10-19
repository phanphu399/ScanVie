package vn.scanviet.app.core.file

import java.text.Normalizer
import java.util.Locale

/**
 * Sinh tên file PDF từ tên tài liệu.
 *
 * Vì sao không dùng thẳng tên tài liệu: tên tài liệu do người dùng nhập, có thể chứa
 * `/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`, ký tự điều khiển, hoặc dài 120 ký tự.
 * Dùng thẳng sẽ tạo ra tên file hỏng hoặc đường dẫn thoát ra ngoài thư mục đích.
 *
 * Vì sao bỏ dấu tiếng Việt: tên file có dấu gây lỗi hiển thị trên một số ứng dụng nhận
 * file và một số hệ thống lưu trữ. Bản không dấu vẫn đọc được với người dùng Việt Nam,
 * và đây chỉ là tên file gợi ý — người dùng đổi được ở hộp thoại lưu.
 *
 * Kết quả luôn kết thúc bằng `.pdf` và không bao giờ rỗng.
 */
object PdfFileNames {

    private const val EXTENSION = ".pdf"

    /** Tên file dự phòng khi tên tài liệu không còn ký tự dùng được nào. */
    private const val FALLBACK_STEM = "tai-lieu"

    /** Đủ dài để nhận ra tài liệu, đủ ngắn để không vượt giới hạn tên file của hệ điều hành. */
    private const val MAX_STEM_LENGTH = 60

    private val ILLEGAL_CHARACTERS = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    fun forTitle(title: String): String {
        val withoutAccents = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')

        val slug = withoutAccents
            .lowercase(Locale.ROOT)
            .replace(ILLEGAL_CHARACTERS, " ")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX_STEM_LENGTH)
            .trim('-')

        return (slug.ifEmpty { FALLBACK_STEM }) + EXTENSION
    }
}
