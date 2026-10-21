package vn.scanviet.app.domain

/**
 * Vì sao không nhận dạng được chữ cho bất kỳ trang nào của một tài liệu.
 *
 * **Vì sao cần phân biệt ba nguyên nhân này.** Ở tầng gọi, cả ba đều trông giống hệt nhau:
 * "0 trang nhận dạng thành công". Nhưng chúng đòi người dùng làm ba việc hoàn toàn khác nhau,
 * và gộp chúng lại thành một câu "Lỗi nhận dạng" chung (như bản trước) khiến người dùng không
 * biết phải làm gì:
 *
 * | Lý do | Nguyên nhân thật | Người dùng nên làm |
 * |---|---|---|
 * | [NO_TEXT_FOUND] | Ảnh mờ, thiếu sáng, hoặc trang trắng | Quét lại trang cho rõ hơn |
 * | [PAGE_UNREADABLE] | File ảnh thiếu hoặc hỏng trên máy | Quét lại tài liệu |
 * | [ENGINE_UNAVAILABLE] | Không khởi tạo được engine nhận dạng | Giải phóng dung lượng, thử lại |
 *
 * **Quy tắc riêng tư:** enum này không mang theo đường dẫn, tên file, hay nội dung tài liệu.
 * Nó chỉ nói *loại* vấn đề, không nói *nội dung* của tài liệu nào.
 */
enum class OcrFailureReason {
    /** Ảnh đọc được, engine chạy xong, nhưng không tìm thấy dòng chữ nào. */
    NO_TEXT_FOUND,

    /** Không đọc được file ảnh của trang (file thiếu, hỏng, hoặc không giải mã được). */
    PAGE_UNREADABLE,

    /** Engine không khởi tạo được — thiếu model, model hỏng, hoặc hết bộ nhớ. Thử lại có thể thành công. */
    ENGINE_UNAVAILABLE,
}
