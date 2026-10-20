package vn.scanviet.app.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.scanviet.app.domain.model.AppSettings
import vn.scanviet.app.domain.model.ThemePreference

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setAutoOcr(enabled: Boolean)

    suspend fun setKeepOriginalImage(enabled: Boolean)

    suspend fun setCheckForUpdates(enabled: Boolean)
}
