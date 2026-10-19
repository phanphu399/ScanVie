package vn.scanviet.app.ui.root

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
}
