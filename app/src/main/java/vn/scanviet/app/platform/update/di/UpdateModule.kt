package vn.scanviet.app.platform.update.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import vn.scanviet.app.BuildConfig
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.domain.update.UpdateSource
import vn.scanviet.app.domain.usecase.CheckForUpdateUseCase
import vn.scanviet.app.platform.update.GitHubReleaseSource
import vn.scanviet.app.platform.update.HttpResponseReader
import vn.scanviet.app.platform.update.HttpUrlConnectionReader

/**
 * Nối cổng [UpdateSource] với nguồn GitHub Releases.
 *
 * Đổi nơi phát hành (sang máy chủ riêng, sang Firebase App Distribution, hoặc lên Google Play)
 * chỉ cần sửa duy nhất file này. Không lớp nào khác trong ứng dụng biết bản cập nhật đến từ đâu.
 *
 * Vì sao [BuildConfig] không được tiêm trực tiếp: `BuildConfig.RELEASE_REPOSITORY` là hằng số
 * sinh lúc biên dịch, không phải đối tượng có thể tiêm. Dùng `@Named` để truyền đúng phần cần
 * thiết, nhờ vậy [CheckForUpdateUseCase] vẫn nhận phiên bản qua constructor và test được.
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    /** Tên kho GitHub chứa bản phát hành. Đặt trong build.gradle.kts để đổi không cần sửa code. */
    @Provides
    @Named("releaseOwner")
    fun provideReleaseOwner(): String = BuildConfig.RELEASE_OWNER

    @Provides
    @Named("releaseRepository")
    fun provideReleaseRepository(): String = BuildConfig.RELEASE_REPOSITORY

    @Provides
    @Named("currentVersion")
    fun provideCurrentVersion(): String = BuildConfig.VERSION_NAME

    @Provides
    @Singleton
    fun provideHttpResponseReader(dispatchers: DispatcherProvider): HttpResponseReader =
        HttpUrlConnectionReader(dispatchers)

    @Provides
    @Singleton
    fun provideUpdateSource(
        httpReader: HttpResponseReader,
        @Named("releaseOwner") owner: String,
        @Named("releaseRepository") repository: String,
    ): UpdateSource = GitHubReleaseSource(
        httpReader = httpReader,
        owner = owner,
        repository = repository,
    )

    /**
     * Cấp phát use case bằng tay thay vì để Hilt tự dựng.
     *
     * Vì sao: [CheckForUpdateUseCase] nhận `currentVersion: String` trong constructor. Hilt không
     * biết lấy `String` nào, và nếu chỉ có `@Named("currentVersion")` thì vẫn thiếu annotation
     * trên tham số. Cấp phát tường minh ở đây khiến việc phiên bản đến từ đâu là rõ ràng, và
     * vẫn giữ được constructor của use case thuần Kotlin để test không cần Hilt.
     */
    @Provides
    @Singleton
    fun provideCheckForUpdateUseCase(
        updateSource: UpdateSource,
        @Named("currentVersion") currentVersion: String,
    ): CheckForUpdateUseCase = CheckForUpdateUseCase(
        updateSource = updateSource,
        currentVersion = currentVersion,
    )
}
