package vn.scanviet.app.platform.ocr.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.scanviet.app.domain.ocr.OcrEngine
import vn.scanviet.app.platform.ocr.MlKitOcrEngine

/**
 * Nối cổng nhận dạng chữ với cài đặt ML Kit.
 *
 * Nhờ module này, use case và tầng ui chỉ biết [OcrEngine]. Muốn đổi sang engine khác
 * (Tesseract, nhận dạng trên máy chủ do người dùng tự dựng…) chỉ cần đổi ở đây.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: MlKitOcrEngine): OcrEngine
}
