package vn.scanviet.app.domain.ocr

import java.io.File

/**
 * Một engine nhận dạng chữ CỤ THỂ, đã sẵn sàng dùng.
 *
 * Đây là lớp con của [OcrEngine] thêm một thuộc tính: engine này là engine nào. Nhờ đó
 * `ui/` hiển thị được "đang dùng engine gì" mà không phải giữ một biến song song dễ lệch
 * khỏi thực tế.
 *
 * Vì sao tách khỏi [OcrEngine]: [OcrEngine] là hợp đồng tối thiểu mà use case cần. Thêm
 * `id` vào chính [OcrEngine] sẽ bắt mọi cài đặt giả trong test phải khai báo một thứ
 * chúng không dùng đến.
 */
interface IdentifiedOcrEngine : OcrEngine {
    val kind: OcrEngineKind
}

/**
 * Engine Tesseract, có thể cấu hình theo model ngôn ngữ.
 *
 * Vì sao cần interface riêng thay vì dùng thẳng lớp thật: `OcrEngineProvider` nằm ở
 * `domain/` nên **không được** import lớp đến từ Android/`platform/`. Interface này giữ
 * cho `domain/` không biết gì về `TessBaseAPI`, trong khi vẫn diễn đạt được ý "engine này
 * cần được trỏ tới thư mục model".
 *
 * [forModel] trả về một engine đã gắn với model cụ thể; gọi lại với thư mục khác sẽ khiến
 * lần nhận dạng sau dùng model mới. Nhờ vậy việc đổi chất lượng model trong Cài đặt chỉ
 * là gọi lại [forModel], không cần dựng lại đối tượng hay khởi động lại ứng dụng.
 */
interface TesseractOcrEnginePort : IdentifiedOcrEngine {
    /**
     * @param tessdataParentDir thư mục **cha** của `tessdata/`, tức thư mục truyền cho
     *   `TessBaseAPI.init()`.
     * @param quality dùng để biết nên khởi tạo lại engine khi chất lượng đổi. Không đổi
     *   model thì không cần khởi tạo lại — khởi tạo Tesseract tốn hàng trăm mili-giây.
     */
    fun forModel(tessdataParentDir: File, quality: OcrModelQuality): TesseractOcrEnginePort
}

/**
 * Engine ML Kit. Không có gì để cấu hình: model nằm sẵn trong APK.
 */
interface MlKitOcrEnginePort : IdentifiedOcrEngine
