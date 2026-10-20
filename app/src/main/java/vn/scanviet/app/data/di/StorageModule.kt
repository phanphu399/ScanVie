package vn.scanviet.app.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.scanviet.app.data.file.LocalDocumentFileStorage
import vn.scanviet.app.data.image.AndroidPageImageLoader
import vn.scanviet.app.data.scan.ContentUriPageImageReader
import vn.scanviet.app.data.settings.settingsDataStore
import vn.scanviet.app.domain.image.PageImageLoader
import vn.scanviet.app.domain.scan.PageImageReader
import vn.scanviet.app.domain.storage.DocumentFileStorage

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindDocumentFileStorage(impl: LocalDocumentFileStorage): DocumentFileStorage

    /** Đọc ảnh từ content:// do trình quét trả về. Cần ContentResolver nên nằm ở tầng data. */
    @Binds
    @Singleton
    abstract fun bindPageImageReader(impl: ContentUriPageImageReader): PageImageReader

    /** Tạo ảnh thu nhỏ cho danh sách. Cần BitmapFactory nên nằm ở tầng data. */
    @Binds
    @Singleton
    abstract fun bindThumbnailLoader(impl: AndroidPageImageLoader): PageImageLoader

    companion object {
        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.settingsDataStore
    }
}
