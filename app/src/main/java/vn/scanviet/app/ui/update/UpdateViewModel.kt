package vn.scanviet.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.scanviet.app.domain.model.UpdateCheckResult
import vn.scanviet.app.domain.update.UpdateCheckFailedException
import vn.scanviet.app.domain.usecase.CheckForUpdateUseCase
import vn.scanviet.app.platform.update.ApkInstaller

/**
 * Điều khiển việc kiểm tra và tải bản cập nhật.
 *
 * Bốn quyết định quan trọng:
 *
 * 1. **Kiểm tra chạy nền, không chặn người dùng.** Không có màn hình chờ riêng; kết quả chỉ
 *    hiện thành một khối trong Cài đặt khi đã có.
 *
 * 2. **Chặn bấm hai lần bằng cách đặt trạng thái TRƯỚC khi `launch`.** Nếu chỉ đổi trạng thái
 *    bên trong coroutine, một cú bấm đúp sẽ tạo hai lời gọi tải song song ghi vào cùng một file
 *    APK — file hỏng và lỗi rất khó lần ra.
 *
 * 3. **Không tự mở trình cài đặt khi người dùng chưa bấm.** Ứng dụng không bao giờ tự cài.
 *
 * 4. **Xin quyền cài đặt đúng lúc cần.** Trên Android 8 trở lên, hệ thống chặn cài đặt cho
 *    tới khi người dùng bật quyền trong Cài đặt hệ thống. Ta kiểm tra và mở đúng màn hình đó,
 *    thay vì để người dùng nhìn màn hình cài đặt đóng ngay mà không hiểu tại sao.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checkForUpdate: CheckForUpdateUseCase,
    private val apkInstaller: ApkInstaller,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** Kiểm tra cập nhật. Bỏ qua nếu đang kiểm tra hoặc đang tải. */
    fun check() {
        val current = _uiState.value
        if (current is UpdateUiState.Checking || current is UpdateUiState.Downloading) return

        _uiState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _uiState.value = try {
                when (val result = checkForUpdate()) {
                    is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                    is UpdateCheckResult.Available -> UpdateUiState.Available(result.release)
                }
            } catch (error: UpdateCheckFailedException) {
                // Mất mạng hoặc máy chủ lỗi: báo đúng sự thật, KHÔNG nói "đã là bản mới nhất".
                UpdateUiState.CheckFailed
            }
        }
    }

    /**
     * Tải bản cập nhật về rồi mở trình cài đặt, hoặc mở màn hình xin quyền cài đặt trước.
     */
    fun downloadAndInstall() {
        val release = (_uiState.value as? UpdateUiState.Available)?.release ?: return
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
}
