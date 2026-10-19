package vn.scanviet.app.platform.pdf.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.scanviet.app.domain.export.PdfExporter
import vn.scanviet.app.platform.pdf.AndroidPdfExporter

/**
 * Nối cổng sinh PDF với cài đặt dùng API hệ thống.
 *
 * Nhờ module này, tầng `ui` và use case chỉ biết [PdfExporter]. Muốn thay bằng thư viện
 * ngoài (iText, PDFBox) hay chèn lớp văn bản OCR ở Giai đoạn 3 thì chỉ cần đổi ở đây.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PdfModule {

    @Binds
    @Singleton
    abstract fun bindPdfExporter(impl: AndroidPdfExporter): PdfExporter
}
