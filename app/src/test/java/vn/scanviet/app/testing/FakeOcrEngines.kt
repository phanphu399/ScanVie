package vn.scanviet.app.testing

import java.io.File
import vn.scanviet.app.domain.ocr.MlKitOcrEnginePort
import vn.scanviet.app.domain.ocr.OcrEngineKind
import vn.scanviet.app.domain.ocr.OcrModelQuality
import vn.scanviet.app.domain.ocr.TessdataInstallException
import vn.scanviet.app.domain.ocr.TessdataProvisioner
import vn.scanviet.app.domain.ocr.TesseractOcrEnginePort

/**
 * Engine Tesseract giả, để test `OcrEngineProvider` mà không cần thư viện native.
 *
 * Ghi lại model đã được trỏ tới, nhờ vậy test kiểm chứng được rằng đổi chất lượng model
 * thật sự dẫn tới việc gọi `forModel` với model mới.
 */
class FakeTesseractOcrEngine(
    private val delegate: FakeOcrEngine = FakeOcrEngine(),
) : TesseractOcrEnginePort {

    override val kind: OcrEngineKind = OcrEngineKind.TESSERACT

    /** Các cặp (thư mục model, chất lượng) đã được yêu cầu, theo thứ tự gọi. */
    val appliedModels: MutableList<Pair<String, OcrModelQuality>> = mutableListOf()

    override fun forModel(
        tessdataParentDir: File,
        quality: OcrModelQuality,
    ): TesseractOcrEnginePort = apply {
        appliedModels += tessdataParentDir.absolutePath to quality
    }

    override suspend fun recognize(imageFile: File, rotationDegrees: Int) =
        delegate.recognize(imageFile, rotationDegrees)
}

/** Engine ML Kit giả. Không có gì để cấu hình nên chỉ uỷ quyền cho [FakeOcrEngine]. */
class FakeMlKitOcrEngine(
    private val delegate: FakeOcrEngine = FakeOcrEngine(),
) : MlKitOcrEnginePort {

    override val kind: OcrEngineKind = OcrEngineKind.ML_KIT

    override suspend fun recognize(imageFile: File, rotationDegrees: Int) =
        delegate.recognize(imageFile, rotationDegrees)
}

/**
 * Cổng cài model giả.
 *
 * [failWith] cho phép mô phỏng lỗi cài đặt (hết chỗ, asset thiếu) để kiểm chứng rằng lỗi
 * đó được NÉM RA chứ không bị nuốt thành "ảnh không có chữ".
 */
class FakeTessdataProvisioner(
    private val modelDir: File,
) : TessdataProvisioner {

    var failWith: Throwable? = null

    /** Các chất lượng model đã được yêu cầu cài, theo thứ tự gọi. */
    val installRequests: MutableList<OcrModelQuality> = mutableListOf()

    override suspend fun ensureInstalled(quality: OcrModelQuality): File {
        installRequests += quality
        failWith?.let { throw it }
        return modelDir
    }

    override suspend fun installedModelSizeBytes(): Long? = null

    /** Tiện dụng: tạo lỗi cài đặt giả cho một chất lượng model. */
    fun fail(quality: OcrModelQuality = OcrModelQuality.BEST, cause: Throwable? = null) {
        failWith = TessdataInstallException(quality, cause)
    }
}
