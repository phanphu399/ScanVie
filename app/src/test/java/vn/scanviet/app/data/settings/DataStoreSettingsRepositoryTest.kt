package vn.scanviet.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.scanviet.app.domain.ocr.OcrEngineKind
import vn.scanviet.app.domain.ocr.OcrModelQuality

/**
 * Kiểm tra việc lưu và đọc lại cài đặt engine nhận dạng chữ.
 *
 * **Vì sao dùng DataStore THẬT thay vì một `DataStore` giả:** phần dễ sai nhất không nằm ở
 * logic nghiệp vụ mà ở **tên khoá** và **định dạng giá trị**. Một khoá viết sai, hoặc lưu
 * `enum.name` rồi đọc bằng `storageValue`, sẽ khiến cài đặt im lặng không được lưu — người
 * dùng chọn Tesseract, tắt ứng dụng, mở lại và thấy quay về mặc định mà không có lỗi nào.
 * Chỉ DataStore thật mới bắt được loại lỗi đó.
 *
 * Cũng kiểm tra giá trị lạ trong file cài đặt không làm ứng dụng hỏng — đây là tình huống
 * thật khi người dùng cài đè bản mới lên bản cũ.
 *
 * **Lưu ý về cách dựng DataStore:** chỉ MỘT instance cho mỗi file trong suốt một test.
 * DataStore ném `IllegalStateException` nếu có hai instance cùng trỏ vào một file còn sống —
 * điều này phản ánh đúng ràng buộc của ứng dụng thật (một DataStore duy nhất, xem
 * `SettingsDataStore.kt`).
 */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val file = File(temporaryFolder.newFolder(), "settings.preferences_pb")
        store = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = DataStoreSettingsRepository(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `mac dinh dung Tesseract va model BEST`() = runTest {
        val settings = repository.settings.first()

        // Mặc định phải đọc được tiếng Việt có dấu: ML Kit không làm được điều đó, nên để nó
        // làm mặc định là âm thầm bỏ dấu tài liệu của mọi người dùng mới.
        assertEquals(OcrEngineKind.TESSERACT, settings.ocrEngineKind)
        assertEquals(OcrModelQuality.BEST, settings.ocrModelQuality)
    }

    @Test
    fun `chon ML Kit duoc luu va doc lai dung`() = runTest {
        repository.setOcrEngineKind(OcrEngineKind.ML_KIT)

        val settings = repository.settings.first()

        assertEquals(OcrEngineKind.ML_KIT, settings.ocrEngineKind)
    }

    @Test
    fun `chon model FAST duoc luu va doc lai dung`() = runTest {
        repository.setOcrModelQuality(OcrModelQuality.FAST)

        val settings = repository.settings.first()

        assertEquals(OcrModelQuality.FAST, settings.ocrModelQuality)
    }

    @Test
    fun `cac cai dat khac khong bi ghi de khi doi engine`() = runTest {
        repository.setAutoOcr(false)
        repository.setKeepOriginalImage(true)

        repository.setOcrEngineKind(OcrEngineKind.ML_KIT)
        repository.setOcrModelQuality(OcrModelQuality.FAST)

        val settings = repository.settings.first()
        // Đổi engine không được làm mất các lựa chọn khác của người dùng.
        assertEquals(false, settings.autoOcr)
        assertEquals(true, settings.keepOriginalImage)
        assertEquals(OcrEngineKind.ML_KIT, settings.ocrEngineKind)
        assertEquals(OcrModelQuality.FAST, settings.ocrModelQuality)
    }

    @Test
    fun `gia tri engine khong nhan ra thi roi ve mac dinh chu khong nem loi`() = runTest {
        // Mô phỏng: bản cũ ghi một giá trị mà bản mới không còn hiểu.
        store.edit { preferences ->
            preferences[OCR_ENGINE] = "engine-khong-ton-tai"
            preferences[OCR_MODEL] = "model-khong-ton-tai"
        }

        val settings = repository.settings.first()

        // Không được ném lỗi và không được để ứng dụng không mở được: rơi về mặc định là
        // hành vi đúng, tệ nhất người dùng chỉ phải chọn lại.
        assertEquals(OcrEngineKind.TESSERACT, settings.ocrEngineKind)
        assertEquals(OcrModelQuality.BEST, settings.ocrModelQuality)
    }

    @Test
    fun `gia tri luu la chuoi on dinh khong phai ten hang so enum`() = runTest {
        repository.setOcrEngineKind(OcrEngineKind.ML_KIT)

        val preferences = store.data.first()

        // Lưu bằng `storageValue`: đổi tên hằng số trong enum (ví dụ ML_KIT -> MLKIT) sẽ không
        // làm mất cài đặt của người dùng hiện có.
        assertEquals(OcrEngineKind.ML_KIT.storageValue, preferences[OCR_ENGINE])
        // Chưa đổi model thì khoá còn trống, và giá trị đọc ra vẫn là mặc định.
        assertEquals(null, preferences[OCR_MODEL])
        assertEquals(OcrModelQuality.DEFAULT, repository.settings.first().ocrModelQuality)
    }

    /**
     * Khoá thật dùng trong [DataStoreSettingsRepository].
     *
     * Định nghĩa lại ở đây là CỐ Ý: nếu ai đó đổi tên khoá trong lớp thật, test này gãy và
     * buộc phải nghĩ tới việc dữ liệu cũ của người dùng sẽ ra sao.
     */
    private companion object {
        val OCR_ENGINE = stringPreferencesKey("ocr_engine")
        val OCR_MODEL = stringPreferencesKey("ocr_model")
    }
}
