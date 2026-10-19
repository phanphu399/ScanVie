package vn.scanviet.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Một DataStore duy nhất cho toàn ứng dụng (phải là thuộc tính ở mức file).
 * Chỉ lưu cài đặt; không lưu nội dung tài liệu.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "scanviet_settings",
)
