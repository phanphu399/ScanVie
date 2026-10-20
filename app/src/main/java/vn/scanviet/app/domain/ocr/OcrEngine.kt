package vn.scanviet.app.domain.ocr

import java.io.File

/**
 * Cổng (port) nhận dạng chữ trên ảnh — "OCR".
 *
 * Vì sao là cổng ở domain: use case nhận dạng phải điều phối nhiều trang, cập nhật
 * database và xử lý lỗi từng trang, mà không cần biết `com.google.mlkit`. Cài đặt thật
 * nằm ở `platform/ocr/MlKitOcrEngine`; cài đặt giả trong test chạy được trên JVM thuần.
 *
 * Quy tắc riêng tư của cổng này: ảnh chỉ được ĐỌC tại chỗ trên máy. Không cài đặt nào
 * được phép gửi ảnh hay văn bản nhận dạng ra khỏi thiết bị.
 *
 * Hợp đồng:
 * - Trả về `null` khi ảnh không đọc được (file hỏng, thiếu). Lỗi của MỘT trang không
 *   được làm hỏng cả tài liệu — bên gọi quyết định cách xử lý.
 * - Nhận [File] (không phải Android `Uri`) để domain không phụ thuộc Android.
 */
interface OcrEngine {

    /**
     * Nhận dạng chữ trong [imageFile].
     *
     * @param rotationDegrees góc xoay của ảnh theo metadata trang (0/90/180/270). Phải
     *   áp dụng TRƯỚC khi nhận dạng, nếu không chữ sẽ nằm sai hướng và kết quả vô nghĩa.
     * @return chữ nhận dạng được, hoặc `null` nếu không đọc được ảnh.
     */
    suspend fun recognize(imageFile: File, rotationDegrees: Int = 0): OcrPageResult?
}
