package vn.scanviet.app.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import vn.scanviet.app.core.DefaultDispatcherProvider
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.core.time.SystemTimeProvider
import vn.scanviet.app.core.time.TimeProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class CoroutinesModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
