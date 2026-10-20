package vn.scanviet.app.domain.ocr

/**
 * Các engine nhận dạng chữ mà ứng dụng hỗ trợ.
 *
 * Vì sao cần khái niệm "chọn engine" thay vì chỉ dùng một engine: hai engine mạnh ở hai
 * việc khác nhau, và không engine nào thắng ở mọi trường hợp.
 *
 * | Engine | Tiếng Việt có dấu | Tiếng Anh in rõ | Ghi chú |
 * |---|---|---|---|
 * | [TESSERACT] | **Có** | Khá | Mặc định. Có `vie.traineddata` chính thức. |
 * | [ML_KIT] | **KHÔNG** | Tốt hơn | Mô hình nơ-ron; giữ lại cho tài liệu tiếng Anh. |
 *
 * Đây là kiến thức đã kiểm chứng, không phải phỏng đoán: ML Kit Text Recognition v2 chỉ
 * hỗ trợ 5 hệ chữ (Latin, Chinese, Devanagari, Japanese, Korean) và mô hình đóng gói thật
 * là `Latn_ctc`, nó bỏ dấu tiếng Việt. Xem `MVP_SPEC.md` §2.5.
 *
 * Người dùng chọn trong Cài đặt; giá trị mặc định là [TESSERACT] vì đa số người dùng
 * mục tiêu của ứng dụng quét tài liệu tiếng Việt.
 */
enum class OcrEngineKind(
    /** Giá trị lưu trong DataStore. Không dùng `ordinal` — đổi thứ tự enum sẽ phá dữ liệu cũ. */
    val storageValue: String,
) {
    TESSERACT("tesseract"),
    ML_KIT("ml_kit"),
    ;

    companion object {
        /** Mặc định cho người dùng mới: Tesseract, vì nó đọc được tiếng Việt có dấu. */
        val DEFAULT = TESSERACT

        /**
         * Đọc giá trị từ DataStore.
         *
         * Trả về [DEFAULT] khi giá trị `null`, rỗng, hoặc không nhận ra. KHÔNG ném lỗi:
         * một giá trị lạ trong cài đặt (do bản cũ ghi vào, hoặc dữ liệu hỏng) không được
         * phép làm ứng dụng không mở được. Rơi về mặc định là hành vi đúng — tệ nhất là
         * người dùng phải chọn lại.
         */
        fun fromStorage(value: String?): OcrEngineKind =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * Mức chất lượng của file model Tesseract.
 *
 * Vì sao đóng gói CẢ HAI thay vì chỉ một: hai bản model là một đánh đổi thật mà chỉ
 * người dùng biết câu trả lời đúng.
 *
 * | Model | Dung lượng | Tốc độ | Chất lượng |
 * |---|---|---|---|
 * | [FAST] | 0,51 MB | Nhanh | Thấp hơn, nhưng VẪN giữ dấu tiếng Việt |
 * | [BEST] | 11,86 MB | Chậm hơn nhiều | Cao nhất với chữ nhỏ / ảnh chụp không lý tưởng |
 *
 * Cả hai đều là bản chính thức của dự án Tesseract. Chỉ **một** file được giải nén ra
 * vùng riêng của app tại một thời điểm (xem `TessdataProvisioner`), nên dung lượng trên
 * máy không phải tổng của hai bản — nhưng dung lượng APK thì có, vì cả hai đều nằm trong
 * `assets/`.
 *
 * Mặc định là [BEST]: nhận dạng chữ chính xác là điểm khác biệt của ứng dụng, và một
 * tài liệu 20 trang chạy thêm vài giây là cái giá chấp nhận được.
 */
enum class OcrModelQuality(
    /** Giá trị lưu trong DataStore. */
    val storageValue: String,
    /**
     * Tên file trong `assets/tessdata/`.
     *
     * Vì sao KHÔNG đặt tên file là `vie.traineddata` cho cả hai: chúng phải cùng tồn tại
     * trong thư mục `assets/`, mà `assets/` không cho hai file trùng tên.
     */
    val assetName: String,
    /** Tên file khi đã giải nén ra vùng riêng của app. Tesseract tìm theo tên này. */
    val trainedDataFileName: String,
) {
    FAST(
        storageValue = "fast",
        assetName = "vie_fast.traineddata",
        trainedDataFileName = "vie.traineddata",
    ),
    BEST(
        storageValue = "best",
        assetName = "vie_best.traineddata",
        trainedDataFileName = "vie.traineddata",
    ),
    ;

    companion object {
        val DEFAULT = BEST

        /** Đọc giá trị từ DataStore; rơi về [DEFAULT] khi không nhận ra. Xem [OcrEngineKind.fromStorage]. */
        fun fromStorage(value: String?): OcrModelQuality =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
