package vn.scanviet.app.ui.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.scanviet.app.domain.model.AppRelease
import vn.scanviet.app.domain.model.AppSettings
import vn.scanviet.app.domain.model.UpdateCheckResult
import vn.scanviet.app.domain.update.UpdateCheckFailedException
import vn.scanviet.app.domain.update.UpdateCheckSchedule
import vn.scanviet.app.platform.update.ApkInstaller
import vn.scanviet.app.testing.FakeSettingsRepository

/**
 * Kiểm tra hành vi tự kiểm tra cập nhật của [UpdateViewModel].
 *
 * Đây là phần dễ sai nhất của tính năng cập nhật, và sai theo cách **im lặng**:
 *
 * - Nếu không giới hạn tần suất, ứng dụng gọi mạng mỗi lần người dùng mở app.
 * - Nếu giới hạn sai, người dùng **không bao giờ** thấy thông báo cập nhật — đúng lỗi mà tính
 *   năng này được viết ra để sửa.
 * - Nếu lỗi mạng của lần kiểm tra tự động không bị nuốt, người dùng đang mở app giữa lúc mất
 *   mạng sẽ thấy một thông báo lỗi họ không hề yêu cầu.
 *
 * Không dùng Hilt: các phụ thuộc đều là interface hoặc lớp thuần, dựng trực tiếp trong test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope chạy trên Dispatchers.Main, test phải thay bằng dispatcher điều khiển được.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Tự kiểm tra khi mở ứng dụng -------------------------------------------------------

    @Test
    fun `lan mo dau tien thi tu kiem tra`() = runTest {
        val settings = FakeSettingsRepository()
        val source = FakeUpdateSource(result = UpdateCheckResult.Available(release("0.6.0")))
        val viewModel = viewModel(settings, source)

        viewModel.autoCheckIfDue()
        advanceUntilIdle()

        assertEquals(1, source.callCount)
        assertTrue(viewModel.uiState.value is UpdateUiState.Available)
    }

    @Test
    fun `vua kiem tra xong thi khong goi mang lai`() = runTest {
        val now = 1_700_000_000_000L
        val settings = FakeSettingsRepository(
            AppSettings(lastUpdateCheckAt = now - 1000L),
        )
        val source = FakeUpdateSource()
        val viewModel = viewModel(settings, source, now = now)

        viewModel.autoCheckIfDue()
        advanceUntilIdle()

        assertEquals(0, source.callCount)
    }

    @Test
    fun `qua mot ngay thi kiem tra lai`() = runTest {
        val now = 1_700_000_000_000L
        val settings = FakeSettingsRepository(
            AppSettings(lastUpdateCheckAt = now - UpdateCheckSchedule.DEFAULT_INTERVAL_MILLIS),
        )
        val source = FakeUpdateSource()
        val viewModel = viewModel(settings, source, now = now)

        viewModel.autoCheckIfDue()
        advanceUntilIdle()

        assertEquals(1, source.callCount)
    }

    @Test
    fun `nguoi dung tat tu kiem tra thi khong goi mang`() = runTest {
        // Tắt trong Cài đặt là lựa chọn về quyền riêng tư, không phải gợi ý. Không được lờ đi.
        val settings = FakeSettingsRepository(AppSettings(checkForUpdates = false))
        val source = FakeUpdateSource()
        val viewModel = viewModel(settings, source)

        viewModel.autoCheckIfDue()
        advanceUntilIdle()

        assertEquals(0, source.callCount)
    }

    @Test
    fun `tu kiem tra chi chay mot lan trong cung phien`() = runTest {
        // Activity có thể vào foreground nhiều lần (xoay màn hình, quay lại từ màn hình khác).
        // Mỗi lần đều gọi autoCheckIfDue, nhưng chỉ lần đầu được phép hỏi mạng.
        val settings = FakeSettingsRepository()
        val source = FakeUpdateSource()
        val viewModel = viewModel(settings, source)

        viewModel.autoCheckIfDue()
        viewModel.autoCheckIfDue()
        viewModel.autoCheckIfDue()
        advanceUntilIdle()

        assertEquals(1, source.callCount)
    }

    @Test
    fun `kiem tra xong thi ghi lai moc thoi gian`() = runTest {
        val now = 1_700_000_000_000L
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings, FakeUpdateSource(), now = now)

        viewModel.autoCheckIfDue()
        advanceUntilIdle()

        // Không ghi mốc thì lần mở app kế tiếp lại gọi mạng — giới hạn 24 giờ thành vô nghĩa.
        assertEquals(now, settings.current.lastUpdateCheckAt)
    }

    @Test
    fun `kiem tra that bai van ghi moc de khong thu lai lien tuc`() = runTest {
        val now = 1_700_000_000_000L
        val settings = FakeSettingsRepository()
        val source = FakeUpdateSource(failure = UpdateCheckFailedException())
        val viewModel = viewModel(settings, source, now = now)

        viewModel.autoCheckIfDue()
        advanceUntilIdle()

        // Đang mất mạng mà thử lại mỗi lần mở app thì vừa vô ích vừa tốn pin.
        assertEquals(now, settings.current.lastUpdateCheckAt)
    }

    // ---- Im lặng khi lỗi -------------------------------------------------------------------

    @Test
    fun `tu kiem tra that bai thi im lang`() = runTest {
        val settings = FakeSettingsRepository()
        val source = FakeUpdateSource(failure = UpdateCheckFailedException())
        val viewModel = viewModel(settings, source)

        viewModel.autoCheckIfDue()
        advanceUntilIdle()

        // Người dùng không yêu cầu lần kiểm tra này. Báo lỗi mạng lúc họ vừa mở app chỉ gây
        // khó chịu và khiến họ tưởng ứng dụng hỏng.
        assertEquals(UpdateUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `tu kiem tra thay ban moi nhat thi khong hien gi`() = runTest {
        val settings = FakeSettingsRepository()
        val source = FakeUpdateSource(result = UpdateCheckResult.UpToDate)
        val viewModel = viewModel(settings, source)

        viewModel.autoCheckIfDue()
        advanceUntilIdle()

        // Không có gì để làm -> không chiếm chỗ trên màn hình chính.
        assertEquals(UpdateUiState.Idle, viewModel.uiState.value)
    }

    // ---- Kiểm tra do người dùng bấm --------------------------------------------------------

    @Test
    fun `nguoi dung bam kiem tra thi hien loi khi that bai`() = runTest {
        val settings = FakeSettingsRepository()
        val source = FakeUpdateSource(failure = UpdateCheckFailedException())
        val viewModel = viewModel(settings, source)

        viewModel.check()
        advanceUntilIdle()

        // Lần này người dùng chủ động yêu cầu, nên phải nói thật là không kiểm tra được —
        // im lặng sẽ khiến họ tưởng nút bấm bị hỏng.
        assertEquals(UpdateUiState.CheckFailed, viewModel.uiState.value)
    }

    @Test
    fun `nguoi dung bam kiem tra thi hien da la ban moi nhat`() = runTest {
        val settings = FakeSettingsRepository()
        val source = FakeUpdateSource(result = UpdateCheckResult.UpToDate)
        val viewModel = viewModel(settings, source)

        viewModel.check()
        advanceUntilIdle()

        assertEquals(UpdateUiState.UpToDate, viewModel.uiState.value)
    }

    @Test
    fun `bam kiem tra hai lan lien tiep chi goi mang mot lan`() = runTest {
        val settings = FakeSettingsRepository()
        val source = FakeUpdateSource()
        val viewModel = viewModel(settings, source)

        viewModel.check()
        // Lần bấm thứ hai xảy ra khi trạng thái đã là Checking: phải bị chặn ngay, không chờ
        // coroutine nào chạy.
        viewModel.check()
        advanceUntilIdle()

        assertEquals(1, source.callCount)
    }

    @Test
    fun `bam kiem tra tay thi bo qua gioi han mot ngay`() = runTest {
        val now = 1_700_000_000_000L
        val settings = FakeSettingsRepository(AppSettings(lastUpdateCheckAt = now))
        val source = FakeUpdateSource()
        val viewModel = viewModel(settings, source, now = now)

        viewModel.check()
        advanceUntilIdle()

        // Giới hạn 24 giờ chỉ áp cho lần kiểm tra TỰ ĐỘNG. Người dùng bấm thì luôn được phục vụ.
        assertEquals(1, source.callCount)
    }

    @Test
    fun `tu kiem tra khong ghi de tien trinh dang tai`() = runTest {
        // Tình huống thật: người dùng bấm tải, chuyển sang ứng dụng khác, rồi quay lại sau khi
        // Activity bị tạo lại. Không chặn ở đây thì trạng thái Downloading bị thay bằng Idle,
        // thanh tiến trình biến mất giữa chừng dù file vẫn đang được tải.
        val now = 1_700_000_000_000L
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings, FakeUpdateSource(), now = now)

        // Đặt trạng thái đang tải trực tiếp: luồng tải thật cần mạng nên không chạy ở đây.
        setState(viewModel, UpdateUiState.Downloading(downloadedBytes = 10L, totalBytes = 100L))

        viewModel.autoCheckIfDue()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UpdateUiState.Downloading)
    }

    @Test
    fun `bam kiem tra khong ghi de tien trinh dang tai`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings, FakeUpdateSource())

        setState(viewModel, UpdateUiState.Downloading(downloadedBytes = 10L, totalBytes = 100L))

        viewModel.check()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UpdateUiState.Downloading)
    }

    // ---- Bỏ qua lời nhắc -------------------------------------------------------------------

    @Test
    fun `bo qua loi nhac thi an bang nhung van nho ban phat hanh`() = runTest {
        val settings = FakeSettingsRepository()
        val source = FakeUpdateSource(result = UpdateCheckResult.Available(release("0.6.0")))
        val viewModel = viewModel(settings, source)

        viewModel.autoCheckIfDue()
        advanceUntilIdle()
        viewModel.dismissBanner()

        val state = viewModel.uiState.value
        // Băng phải biến mất khỏi màn hình chính...
        assertTrue(state is UpdateUiState.BannerDismissed)
        // ...nhưng thông tin bản phát hành phải còn, để khối trong Cài đặt vẫn tải được.
        assertEquals("0.6.0", (state as UpdateUiState.BannerDismissed).release.versionName)
    }

    @Test
    fun `bo qua khi khong co ban moi thi khong doi gi`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings, FakeUpdateSource())

        viewModel.dismissBanner()

        assertEquals(UpdateUiState.Idle, viewModel.uiState.value)
    }

    // ---- Helper ----------------------------------------------------------------------------

    private fun release(version: String) = AppRelease(
        versionName = version,
        downloadUrl = "https://example.test/scanviet.apk",
        sizeBytes = 1024L,
    )

    /**
     * Ép trạng thái của ViewModel để kiểm tra các nhánh cần trạng thái cụ thể.
     *
     * `_uiState` là private, mà luồng tải thật cần mạng và `Context` nên không chạy được trên
     * JVM. Phản chiếu là cách duy nhất chạm tới trạng thái mà không phải nới lỏng phạm vi truy
     * cập của lớp thật chỉ để phục vụ test.
     */
    private fun setState(viewModel: UpdateViewModel, state: UpdateUiState) {
        val field = UpdateViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<UpdateUiState>
        flow.value = state
    }

    private fun viewModel(
        settings: FakeSettingsRepository,
        source: FakeUpdateSource,
        now: Long = 1_700_000_000_000L,
    ) = UpdateViewModel(
        checkForUpdate = vn.scanviet.app.domain.usecase.CheckForUpdateUseCase(
            updateSource = source,
            currentVersion = CURRENT_VERSION,
        ),
        apkInstaller = DummyInstaller(),
        settingsRepository = settings,
        timeProvider = object : vn.scanviet.app.core.time.TimeProvider {
            override fun now(): Long = now
        },
    )

    /**
     * Nguồn cập nhật giả: đếm số lần gọi để khẳng định việc giới hạn tần suất có hiệu lực.
     */
    private class FakeUpdateSource(
        private val result: UpdateCheckResult = UpdateCheckResult.UpToDate,
        private val failure: Exception? = null,
    ) : vn.scanviet.app.domain.update.UpdateSource {

        var callCount = 0
            private set

        override suspend fun latestRelease(): AppRelease? {
            callCount++
            failure?.let { throw it }
            return when (result) {
                is UpdateCheckResult.UpToDate -> null
                is UpdateCheckResult.Available -> result.release
            }
        }
    }

    /**
     * Chỗ đứng cho [vn.scanviet.app.platform.update.UpdateInstaller]: luồng tải APK cần `Context`
     * nên không chạy được trên JVM, và không phải thứ được kiểm tra ở đây. Mọi phương thức đều
     * ném lỗi để nếu test nào vô tình gọi tới thì biết ngay, thay vì im lặng làm việc gì đó.
     */
    private class DummyInstaller : vn.scanviet.app.platform.update.UpdateInstaller {
        override suspend fun download(
            url: String,
            onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
        ): java.io.File = error("không dùng tới luồng tải APK trong test này")

        override fun canRequestPackageInstalls(): Boolean =
            error("không dùng tới luồng tải APK trong test này")

        override fun openInstallPermissionSettings() =
            error("không dùng tới luồng tải APK trong test này")

        override fun install(apkFile: java.io.File) =
            error("không dùng tới luồng tải APK trong test này")

        override fun clearDownloadedApk() =
            error("không dùng tới luồng tải APK trong test này")
    }

    private companion object {
        const val CURRENT_VERSION = "0.5.0"
    }
}
