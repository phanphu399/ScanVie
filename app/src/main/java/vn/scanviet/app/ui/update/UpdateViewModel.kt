package vn.scanviet.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.scanviet.app.core.time.TimeProvider
import vn.scanviet.app.domain.model.AppRelease
import vn.scanviet.app.domain.model.UpdateCheckResult
import vn.scanviet.app.domain.repository.SettingsRepository
import vn.scanviet.app.domain.update.UpdateCheckFailedException
import vn.scanviet.app.domain.update.UpdateCheckSchedule
import vn.scanviet.app.domain.usecase.CheckForUpdateUseCase
import vn.scanviet.app.platform.update.UpdateInstaller

/**
 * Điều khiển việc kiểm tra và tải bản cập nhật.
 *
 * Sáu quyết định quan trọng:
 *
 * 1. **Kiểm tra chạy nền, không chặn người dùng.** Không có màn hình chờ riêng; kết quả hiện
 *    thành một khối trong Cài đặt và một băng nhắc ở màn hình Thư viện khi đã có.
 *
 * 2. **Tự kiểm tra khi mở ứng dụng, nhưng tối đa một lần mỗi 24 giờ.** Trước đây việc kiểm tra
 *    chỉ chạy khi người dùng mở màn hình Cài đặt — nghĩa là người dùng bình thường **không bao
 *    giờ** thấy thông báo cập nhật. Giờ nó chạy từ `MainViewModel` (xem `autoCheckIfDue`), có
 *    ghi lại mốc thời gian xuống DataStore để không gọi mạng mỗi lần mở.
 *
 * 3. **Kiểm tra tự động thất bại thì im lặng.** Người dùng không hề yêu cầu lần kiểm tra đó,
 *    nên báo "không kết nối được" khi họ đang mở ứng dụng giữa lúc mất mạng chỉ gây khó chịu.
 *    Chỉ khi họ **tự bấm** nút kiểm tra thì lỗi mới được hiển thị — xem tham số `silent`.
 *
 * 4. **Chặn bấm hai lần bằng cách đặt trạng thái TRƯỚC khi `launch`.** Nếu chỉ đổi trạng thái
 *    bên trong coroutine, một cú bấm đúp sẽ tạo hai lời gọi tải song song ghi vào cùng một file
 *    APK — file hỏng và lỗi rất khó lần ra.
 *
 * 5. **Không tự mở trình cài đặt khi người dùng chưa bấm.** Ứng dụng không bao giờ tự cài.
 *
 * 6. **Xin quyền cài đặt đúng lúc cần.** Trên Android 8 trở lên, hệ thống chặn cài đặt cho
 *    tới khi người dùng bật quyền trong Cài đặt hệ thống. Ta kiểm tra và mở đúng màn hình đó,
 *    thay vì để người dùng nhìn màn hình cài đặt đóng ngay mà không hiểu tại sao.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checkForUpdate: CheckForUpdateUseCase,
    private val apkInstaller: UpdateInstaller,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /**
     * Cổng chặn để `autoCheckIfDue` không chạy hai lần trong cùng một phiên.
     *
     * Vì sao cần dù đã có mốc thời gian trong DataStore: lần đọc DataStore là bất đồng bộ, nên
     * hai lời gọi `onCreate` sát nhau (xoay màn hình ngay khi mở) có thể cùng thấy mốc cũ và
     * cùng gọi mạng. Cờ này chặn ở tầng bộ nhớ trước khi kịp đọc đĩa.
     */
    private var autoCheckStarted = false

    /**
     * Tự kiểm tra nếu đã quá khoảng chờ. Gọi mỗi khi ứng dụng vào foreground.
     *
     * Im lặng khi thất bại — xem quyết định 3 trong tài liệu lớp.
     */
    fun autoCheckIfDue() {
        if (autoCheckStarted) return
        autoCheckStarted = true

        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.checkForUpdates) return@launch
            if (!UpdateCheckSchedule.shouldCheck(settings.lastUpdateCheckAt, timeProvider.now())) {
                return@launch
            }
            // Đang tải hoặc vừa tải xong thì không kiểm tra lại: ghi đè trạng thái [Downloading]
            // sẽ làm thanh tiến trình biến mất giữa chừng và người dùng không biết chuyện gì
            // đang xảy ra, dù file vẫn đang được tải.
            if (isBusy()) return@launch
            runCheck(silent = true)
        }
    }

    /** Kiểm tra cập nhật do người dùng bấm. Bỏ qua nếu đang kiểm tra hoặc đang tải. */
    fun check() {
        if (isBusy()) return

        _uiState.value = UpdateUiState.Checking
        viewModelScope.launch { runCheck(silent = false) }
    }

    /** Đang có một tác vụ cập nhật chạy dở mà không được phép bị ghi đè trạng thái. */
    private fun isBusy(): Boolean {
        val current = _uiState.value
        return current is UpdateUiState.Checking || current is UpdateUiState.Downloading
    }

    /**
     * Thực hiện một lần kiểm tra và cập nhật trạng thái.
     *
     * @param silent true khi đây là lần kiểm tra tự động: lỗi mạng trả về [UpdateUiState.Idle]
     *   (không hiện gì) thay vì [UpdateUiState.CheckFailed].
     */
    private suspend fun runCheck(silent: Boolean) {
        _uiState.value = try {
            when (val result = checkForUpdate()) {
                is UpdateCheckResult.UpToDate -> if (silent) UpdateUiState.Idle else UpdateUiState.UpToDate
                is UpdateCheckResult.Available -> UpdateUiState.Available(result.release)
            }
        } catch (error: UpdateCheckFailedException) {
            // Mất mạng hoặc máy chủ lỗi: báo đúng sự thật, KHÔNG nói "đã là bản mới nhất".
            if (silent) UpdateUiState.Idle else UpdateUiState.CheckFailed
        }

        // Ghi mốc thời gian kể cả khi thất bại. Thử lại liên tục khi đang mất mạng không giúp
        // gì, mà còn khiến ứng dụng gọi mạng mỗi lần mở — đúng thứ quyết định 2 muốn tránh.
        settingsRepository.setLastUpdateCheckAt(timeProvider.now())
    }

    /**
     * Tải bản cập nhật về rồi mở trình cài đặt, hoặc mở màn hình xin quyền cài đặt trước.
     */
    fun downloadAndInstall() {
        val release = currentRelease() ?: return
        if (release.downloadUrl.isBlank()) {
            _uiState.value = UpdateUiState.Unsupported
            return
        }

        // Đặt trạng thái trước khi launch để cú bấm thứ hai bị chặn ngay.
        _uiState.value = UpdateUiState.Downloading(downloadedBytes = 0L, totalBytes = release.sizeBytes)

        viewModelScope.launch {
            try {
                val apk = apkInstaller.download(release.downloadUrl) { read, total ->
                    _uiState.value = UpdateUiState.Downloading(downloadedBytes = read, totalBytes = total)
                }
                if (!apkInstaller.canRequestPackageInstalls()) {
                    // Chưa được phép cài: mở màn hình hệ thống để người dùng tự bật, và giữ
                    // file APK lại để lần bấm sau mở thẳng trình cài đặt.
                    _uiState.value = UpdateUiState.InstallPermissionRequired(release)
                    apkInstaller.openInstallPermissionSettings()
                    return@launch
                }
                _uiState.value = UpdateUiState.Installed
                apkInstaller.install(apk)
            } catch (error: IOException) {
                // Mất mạng giữa chừng, hoặc máy chủ đóng kết nối. File dở dang đã bị xoá
                // trong ApkInstaller nên lần thử sau bắt đầu sạch.
                _uiState.value = UpdateUiState.DownloadFailed
            } catch (error: SecurityException) {
                // FileProvider chưa khai báo cho thư mục updates, hoặc thiếu quyền cấp URI.
                _uiState.value = UpdateUiState.DownloadFailed
            }
        }
    }

    /** Người dùng đã xử lý xong (cài hoặc huỷ). Dọn file APK đã tải cho khỏi chiếm bộ nhớ đệm. */
    fun onInstallHandled() {
        apkInstaller.clearDownloadedApk()
        _uiState.value = UpdateUiState.Idle
    }

    /** Bỏ qua lời nhắc cập nhật ở màn hình Thư viện mà không tải. Chỉ ẩn trong phiên này. */
    fun dismissBanner() {
        _uiState.update { state ->
            if (state is UpdateUiState.Available) {
                UpdateUiState.BannerDismissed(state.release)
            } else {
                state
            }
        }
    }

    /** Bản phát hành đang chờ, lấy được cả khi người dùng đã bỏ qua lời nhắc. */
    private fun currentRelease(): AppRelease? = when (val state = _uiState.value) {
        is UpdateUiState.Available -> state.release
        is UpdateUiState.InstallPermissionRequired -> state.release
        is UpdateUiState.BannerDismissed -> state.release
        else -> null
    }
}
