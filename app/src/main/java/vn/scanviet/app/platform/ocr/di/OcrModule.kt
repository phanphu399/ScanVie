package vn.scanviet.app.platform.ocr.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.scanviet.app.domain.ocr.MlKitOcrEnginePort
import vn.scanviet.app.domain.ocr.TessdataProvisioner
import vn.scanviet.app.domain.ocr.TesseractOcrEnginePort
import vn.scanviet.app.platform.ocr.AssetTessdataProvisioner
import vn.scanviet.app.platform.ocr.MlKitOcrEngine
import vn.scanviet.app.platform.ocr.TesseractOcrEngine

/**
 * Nối các cổng nhận dạng chữ với cài đặt thật.
 *
 * Nhờ module này, `domain/` và `ui/` chỉ biết các interface (`OcrEngine`,
 * `TesseractOcrEnginePort`, `TessdataProvisioner`). Muốn đổi hay thêm engine khác
 * (PaddleOCR/ONNX, nhận dạng trên máy chủ do người dùng tự dựng…) chỉ cần sửa ở đây và
 * trong `OcrEngineProvider` — không đụng tới Room, ViewModel hay PDF exporter.
 *
 * **Vì sao là `abstract class` với `@Binds` chứ không phải `object` với `@Provides`:**
 * `@Binds` không sinh mã, chỉ là khai báo ánh xạ interface -> cài đặt, nên Hilt dựng đối
 * tượng đúng một lần theo `@Singleton` của chính lớp cài đặt. `@Provides` sẽ gọi hàm mỗi
 * lần có yêu cầu, trừ khi tự khai báo `@Singleton` ở đây — dễ quên và nhân đôi instance
 * Tesseract, mỗi instance giữ thêm 12 MB model trong RAM.
 *
 * Cả ba cài đặt đều nhận `@Inject constructor`, nên Hilt tự dựng được mà không cần tham số.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    /** Engine tiếng Việt (mặc định). Xem `OcrEngineKind`. */
    @Binds
    @Singleton
    abstract fun bindTesseractOcrEngine(impl: TesseractOcrEngine): TesseractOcrEnginePort

    /** Engine cho tài liệu tiếng Anh. KHÔNG hỗ trợ tiếng Việt có dấu. */
    @Binds
    @Singleton
    abstract fun bindMlKitOcrEngine(impl: MlKitOcrEngine): MlKitOcrEnginePort

    /** Giải nén file model Tesseract từ `assets/` ra vùng riêng của ứng dụng. */
    @Binds
    @Singleton
    abstract fun bindTessdataProvisioner(impl: AssetTessdataProvisioner): TessdataProvisioner
}
