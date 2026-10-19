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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
