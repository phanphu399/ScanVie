package vn.scanviet.app.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.scanviet.app.data.repository.OfflineDocumentRepository
import vn.scanviet.app.data.settings.DataStoreSettingsRepository
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: OfflineDocumentRepository): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
