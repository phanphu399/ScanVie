package vn.scanviet.app.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.scanviet.app.data.export.ContentUriDocumentOutputTarget
import vn.scanviet.app.domain.export.DocumentOutputTarget

/**
 * Nối cổng ghi file với cài đặt dùng `ContentResolver`/`FileOutputStream`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @Binds
    @Singleton
    abstract fun bindDocumentOutputTarget(impl: ContentUriDocumentOutputTarget): DocumentOutputTarget
}
