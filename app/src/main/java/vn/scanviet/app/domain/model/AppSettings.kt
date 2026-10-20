package vn.scanviet.app.domain.model

import vn.scanviet.app.domain.ocr.OcrEngineKind
import vn.scanviet.app.domain.ocr.OcrModelQuality

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

    /**
     * Engine nhận dạng chữ người dùng chọn.
     *
     * Mặc định [OcrEngineKind.TESSERACT]: đây là engine duy nhất đọc được **tiếng Việt có
     * dấu**. ML Kit Text Recognition v2 chỉ hỗ trợ 5 hệ chữ và tiếng Việt không nằm trong đó
     * — xem `MVP_SPEC.md` §2.5. Giữ ML Kit làm lựa chọn vì nó mạnh hơn với tài liệu tiếng Anh.
     */
    val ocrEngineKind: OcrEngineKind = OcrEngineKind.DEFAULT,

    /**
     * Chất lượng file model của Tesseract (chỉ có tác dụng khi [ocrEngineKind] là Tesseract).
     *
     * Mặc định [OcrModelQuality.BEST] vì độ chính xác là điểm khác biệt của ứng dụng. Người
     * dùng máy yếu hoặc cần nhanh chọn được bản `fast`.
     */
    val ocrModelQuality: OcrModelQuality = OcrModelQuality.DEFAULT,
)
