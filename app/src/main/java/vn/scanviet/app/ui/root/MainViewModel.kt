package vn.scanviet.app.ui.root

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import vn.scanviet.app.domain.model.ThemePreference
import vn.scanviet.app.domain.repository.SettingsRepository

/**
 * ViewModel của cả ứng dụng, chỉ giữ cài đặt giao diện.
 * Eagerly: chủ đề phải đúng ngay từ khung hình đầu tiên, không được nháy sáng rồi mới tối.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val theme: StateFlow<ThemePreference> = settingsRepository.settings
        .map { it.theme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemePreference.SYSTEM,
        )

    /**
     * Phát ra `true` mỗi khi ứng dụng vào foreground.
     *
     * Vì sao cần tín hiệu "quay lại foreground" thay vì chỉ chạy một lần lúc mở: người dùng có
     * thể để ứng dụng nằm trong nền hàng ngày trời. Mỗi lần quay lại là một cơ hội báo bản mới,
     * mà bên nhận (`UpdateViewModel.autoCheckIfDue`) đã tự chặn ở 24 giờ nên không có nguy cơ
     * gọi mạng liên tục.
     *
     * Vì sao không đặt logic kiểm tra ở đây: Hilt **cấm** tiêm một `@HiltViewModel` vào một
     * `@HiltViewModel` khác (báo lỗi `ViewModelValidationPlugin` lúc biên dịch). Việc nối hai
     * ViewModel vì thế thuộc về tầng giao diện — `MainActivity` — nơi cả hai đều đã có sẵn.
     *
     * Gọi hàm này **một lần**; gọi lại sẽ tạo thêm một vòng thu thập chạy song song.
     */
    fun foregroundSignals(owner: LifecycleOwner): kotlinx.coroutines.flow.Flow<Boolean> =
        owner.lifecycle.currentStateFlow
            .map { state -> state.isAtLeast(Lifecycle.State.STARTED) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )
}
