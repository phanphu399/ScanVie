package vn.scanviet.app.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.scanviet.app.data.local.ScanVietDatabase
import vn.scanviet.app.data.local.dao.DocumentDao
import vn.scanviet.app.data.local.dao.OcrDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScanVietDatabase =
        Room.databaseBuilder(context, ScanVietDatabase::class.java, ScanVietDatabase.NAME)
            // Cố ý KHÔNG dùng fallbackToDestructiveMigration:
            // xoá dữ liệu người dùng để "chữa" lỗi schema là hành vi không chấp nhận được.
            .addMigrations(ScanVietDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideDocumentDao(database: ScanVietDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideOcrDao(database: ScanVietDatabase): OcrDao = database.ocrDao()
}
