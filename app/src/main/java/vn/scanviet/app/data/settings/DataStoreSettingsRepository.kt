package vn.scanviet.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import vn.scanviet.app.domain.model.AppSettings
import vn.scanviet.app.domain.model.ThemePreference
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

    private fun String?.toThemePreference(): ThemePreference =
        ThemePreference.entries.firstOrNull { it.name == this } ?: ThemePreference.SYSTEM

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val AUTO_OCR = booleanPreferencesKey("auto_ocr")
        val KEEP_ORIGINAL = booleanPreferencesKey("keep_original_image")
        val CHECK_UPDATES = booleanPreferencesKey("check_for_updates")
    }

    private companion object {
        const val DEFAULT_AUTO_OCR = true
        const val DEFAULT_KEEP_ORIGINAL = false
        const val DEFAULT_CHECK_UPDATES = true
    }
}
