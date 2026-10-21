package vn.scanviet.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import vn.scanviet.app.domain.model.AppSettings
import vn.scanviet.app.domain.model.ThemePreference
import vn.scanviet.app.domain.ocr.OcrEngineKind
import vn.scanviet.app.domain.ocr.OcrModelQuality
import vn.scanviet.app.domain.repository.SettingsRepository

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        // Đọc lỗi (file hỏng, hết dung lượng) không được làm sập ứng dụng: dùng giá trị mặc định.
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences ->
            AppSettings(
                theme = preferences[Keys.THEME].toThemePreference(),
                autoOcr = preferences[Keys.AUTO_OCR] ?: DEFAULT_AUTO_OCR,
                keepOriginalImage = preferences[Keys.KEEP_ORIGINAL] ?: DEFAULT_KEEP_ORIGINAL,
                checkForUpdates = preferences[Keys.CHECK_UPDATES] ?: DEFAULT_CHECK_UPDATES,
                // `fromStorage` tự rơi về mặc định khi giá trị null hoặc không nhận ra — nhờ
                // vậy một giá trị lạ (do bản cũ ghi vào) không làm ứng dụng hỏng.
                ocrEngineKind = OcrEngineKind.fromStorage(preferences[Keys.OCR_ENGINE]),
                ocrModelQuality = OcrModelQuality.fromStorage(preferences[Keys.OCR_MODEL]),
                lastUpdateCheckAt = preferences[Keys.LAST_UPDATE_CHECK_AT] ?: 0L,
            )
        }

    override suspend fun setTheme(theme: ThemePreference) {
        dataStore.edit { preferences -> preferences[Keys.THEME] = theme.name }
    }

    override suspend fun setAutoOcr(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.AUTO_OCR] = enabled }
    }

    override suspend fun setKeepOriginalImage(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.KEEP_ORIGINAL] = enabled }
    }

    override suspend fun setCheckForUpdates(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.CHECK_UPDATES] = enabled }
    }

    override suspend fun setOcrEngineKind(kind: OcrEngineKind) {
        dataStore.edit { preferences -> preferences[Keys.OCR_ENGINE] = kind.storageValue }
    }

    override suspend fun setOcrModelQuality(quality: OcrModelQuality) {
        dataStore.edit { preferences -> preferences[Keys.OCR_MODEL] = quality.storageValue }
    }

    override suspend fun setLastUpdateCheckAt(millis: Long) {
        dataStore.edit { preferences -> preferences[Keys.LAST_UPDATE_CHECK_AT] = millis }
    }

    private fun String?.toThemePreference(): ThemePreference =
        ThemePreference.entries.firstOrNull { it.name == this } ?: ThemePreference.SYSTEM

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val AUTO_OCR = booleanPreferencesKey("auto_ocr")
        val KEEP_ORIGINAL = booleanPreferencesKey("keep_original_image")
        val CHECK_UPDATES = booleanPreferencesKey("check_for_updates")

        /**
         * Lưu bằng `storageValue` (chuỗi ổn định), KHÔNG dùng `name` của enum.
         *
         * Vì sao: đổi tên hằng số trong enum (ví dụ `ML_KIT` -> `MLKIT`) sẽ làm dữ liệu cũ
         * không đọc được nữa, và người dùng âm thầm bị đưa về engine mặc định. `storageValue`
         * là một hợp đồng riêng, chỉ đổi khi thật sự muốn đổi định dạng lưu.
         */
        val OCR_ENGINE = stringPreferencesKey("ocr_engine")
        val OCR_MODEL = stringPreferencesKey("ocr_model")

        /** Thời điểm kiểm tra cập nhật gần nhất (mili-giây). Thiếu khoá này = chưa từng kiểm tra. */
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
    }

    private companion object {
        const val DEFAULT_AUTO_OCR = true
        const val DEFAULT_KEEP_ORIGINAL = false
        const val DEFAULT_CHECK_UPDATES = true
    }
}
