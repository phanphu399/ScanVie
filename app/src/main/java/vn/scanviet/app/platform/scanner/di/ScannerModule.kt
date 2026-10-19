package vn.scanviet.app.platform.scanner.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.scanviet.app.platform.scanner.DocumentScannerEngine
import vn.scanviet.app.platform.scanner.MlKitDocumentScannerEngine

/**
 * Nối cổng quét với cài đặt ML Kit.
 *
 * Nhờ module này, tầng `ui` chỉ biết [DocumentScannerEngine]. Muốn thay SDK quét (ví dụ
 * tự dựng CameraX để không phụ thuộc Play services) thì chỉ cần thêm một cài đặt mới ở đây.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {

    @Binds
    @Singleton
    abstract fun bindDocumentScannerEngine(impl: MlKitDocumentScannerEngine): DocumentScannerEngine
}
