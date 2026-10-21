package vn.scanviet.app.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vn.scanviet.app.domain.model.AppSettings
import vn.scanviet.app.domain.model.ThemePreference
import vn.scanviet.app.domain.ocr.OcrEngineKind
import vn.scanviet.app.domain.ocr.OcrModelQuality
import vn.scanviet.app.domain.repository.SettingsRepository

/**
 * Kho cài đặt giả, giữ giá trị trong bộ nhớ.
 *
 * Vì sao dùng `MutableStateFlow` thay vì một biến thường: `settings` là `Flow`, và test cần
 * kiểm chứng rằng thay đổi cài đặt **lan truyền** tới nơi đọc — đúng hành vi mà
 * `DataStoreSettingsRepository` thật có.
 *
 * `update` được dùng để đổi giá trị nguyên tử, tránh test ghi đè lẫn nhau khi chạy song song.
 */
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    /** Giá trị hiện tại, tiện cho test đọc trực tiếp mà không cần thu thập Flow. */
    val current: AppSettings get() = state.value

    override suspend fun setTheme(theme: ThemePreference) {
        state.update { it.copy(theme = theme) }
    }

    override suspend fun setAutoOcr(enabled: Boolean) {
        state.update { it.copy(autoOcr = enabled) }
    }

    override suspend fun setKeepOriginalImage(enabled: Boolean) {
        state.update { it.copy(keepOriginalImage = enabled) }
    }

    override suspend fun setCheckForUpdates(enabled: Boolean) {
        state.update { it.copy(checkForUpdates = enabled) }
    }

    override suspend fun setOcrEngineKind(kind: OcrEngineKind) {
        state.update { it.copy(ocrEngineKind = kind) }
    }

    override suspend fun setOcrModelQuality(quality: OcrModelQuality) {
        state.update { it.copy(ocrModelQuality = quality) }
    }

    override suspend fun setLastUpdateCheckAt(millis: Long) {
        state.update { it.copy(lastUpdateCheckAt = millis) }
    }
}
