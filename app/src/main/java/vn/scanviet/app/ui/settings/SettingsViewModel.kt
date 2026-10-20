package vn.scanviet.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.scanviet.app.domain.model.AppSettings
import vn.scanviet.app.domain.model.ThemePreference
import vn.scanviet.app.domain.ocr.OcrEngineKind
import vn.scanviet.app.domain.ocr.OcrModelQuality
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.repository.SettingsRepository

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val storageBytes: Long = 0L,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    documentRepository: DocumentRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        documentRepository.observeActiveSizeBytes(),
    ) { settings, storageBytes ->
        SettingsUiState(settings = settings, storageBytes = storageBytes)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun onThemeChange(theme: ThemePreference) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun onAutoOcrChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoOcr(enabled) }
    }

    fun onKeepOriginalImageChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepOriginalImage(enabled) }
    }

    fun onCheckForUpdatesChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCheckForUpdates(enabled) }
    }

    /**
     * Đổi engine nhận dạng chữ.
     *
     * Không cần dựng lại ViewModel hay khởi động lại ứng dụng: `RecognizeDocumentTextUseCase`
     * đọc lại cài đặt ở mỗi lần chạy (xem ghi chú "quyết định 5" trong lớp đó).
     */
    fun onOcrEngineChange(kind: OcrEngineKind) {
        viewModelScope.launch { settingsRepository.setOcrEngineKind(kind) }
    }

    /**
     * Đổi chất lượng model Tesseract.
     *
     * Lần nhận dạng kế tiếp sẽ giải nén model mới ra đĩa, nên lần chạy đầu chậm hơn một chút.
     * Xem `AssetTessdataProvisioner`.
     */
    fun onOcrModelQualityChange(quality: OcrModelQuality) {
        viewModelScope.launch { settingsRepository.setOcrModelQuality(quality) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
