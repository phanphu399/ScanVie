package vn.scanviet.app.domain.repository

import kotlinx.coroutines.flow.Flow
import vn.scanviet.app.domain.model.AppSettings
import vn.scanviet.app.domain.model.ThemePreference
import vn.scanviet.app.domain.ocr.OcrEngineKind
import vn.scanviet.app.domain.ocr.OcrModelQuality

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setAutoOcr(enabled: Boolean)

    suspend fun setKeepOriginalImage(enabled: Boolean)

    suspend fun setCheckForUpdates(enabled: Boolean)

    /** Đổi engine nhận dạng chữ. Lần nhận dạng kế tiếp dùng engine mới ngay, không cần khởi động lại. */
    suspend fun setOcrEngineKind(kind: OcrEngineKind)

    /**
     * Đổi chất lượng file model Tesseract.
     *
     * Lần nhận dạng kế tiếp sẽ giải nén model mới (xem `AssetTessdataProvisioner`), nên lần
     * chạy đầu sau khi đổi sẽ chậm hơn một chút — đánh đổi để tiết kiệm dung lượng máy.
     */
    suspend fun setOcrModelQuality(quality: OcrModelQuality)
}
