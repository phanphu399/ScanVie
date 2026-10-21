package vn.scanviet.app.domain.usecase

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.data.file.LocalDocumentFileStorage
import vn.scanviet.app.domain.DocumentNotFoundException
import vn.scanviet.app.domain.OcrFailedException
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.model.OcrStatus
import vn.scanviet.app.domain.ocr.OcrEngineKind
import vn.scanviet.app.domain.ocr.OcrEngineProvider
import vn.scanviet.app.domain.ocr.OcrModelQuality
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.testing.FakeDocumentRepository
import vn.scanviet.app.testing.FakeMlKitOcrEngine
import vn.scanviet.app.testing.FakeOcrEngine
import vn.scanviet.app.testing.FakeSettingsRepository
import vn.scanviet.app.testing.FakeTessdataProvisioner
import vn.scanviet.app.testing.FakeTesseractOcrEngine
import vn.scanviet.app.testing.TestDispatcherProvider

/**
 * Kiểm tra việc nhận dạng chữ cho một tài liệu.
 *
 * Dùng file THẬT qua [LocalDocumentFileStorage] trên filesDir của Robolectric, vì phần quan
 * trọng nhất là quan hệ giữa database và ảnh trên đĩa: trang nào có ảnh thật, trang nào đã
 * mất. Engine nhận dạng được thay bằng bản giả — ML Kit thật không chạy được trên JVM.
 *
 * Các test tập trung vào những quyết định nghiệp vụ dễ sai: chạy lại có tốn công không,
 * một trang lỗi có phá hỏng cả tài liệu không, và khi nào thì phải báo lỗi thay vì im lặng.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RecognizeDocumentTextUseCaseTest {

    private lateinit var storage: DocumentFileStorage
    private lateinit var repository: FakeDocumentRepository
    private lateinit var engine: FakeOcrEngine
    private lateinit var tesseract: FakeTesseractOcrEngine
    private lateinit var settings: FakeSettingsRepository
    private lateinit var provisioner: FakeTessdataProvisioner
    private lateinit var useCase: RecognizeDocumentTextUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = LocalDocumentFileStorage(context, TestDispatcherProvider())
        repository = FakeDocumentRepository()
        engine = FakeOcrEngine()
        tesseract = FakeTesseractOcrEngine(engine)
        settings = FakeSettingsRepository()
        provisioner = FakeTessdataProvisioner(context.filesDir)
        useCase = RecognizeDocumentTextUseCase(
            repository = repository,
            storage = storage,
            settingsRepository = settings,
            engineProvider = OcrEngineProvider(
                tesseract = tesseract,
                mlKit = FakeMlKitOcrEngine(engine),
                tessdata = provisioner,
            ),
        )
    }
    @Test
    fun `nhận dạng tất cả các trang và lưu lại kết quả`() = runTest {
        seedDocument(pageCount = 3)

        val newlyRecognized = useCase(DOC_ID)

        assertEquals(3, newlyRecognized)
        assertEquals(3, engine.callCount)
        assertEquals(3, repository.observeOcrText(DOC_ID).firstValue().size)
        assertEquals(OcrStatus.DONE, repository.getDocument(DOC_ID)?.ocrStatus)
    }

    @Test
    fun `chữ được lưu theo đúng số trang`() = runTest {
        seedDocument(pageCount = 2)

        useCase(DOC_ID)

        val pages = repository.observeOcrText(DOC_ID).firstValue().sortedBy { it.pageIndex }
        assertEquals(listOf(1, 2), pages.map { it.pageIndex })
        assertEquals("Hóa đơn điện", pages[0].fullText)
    }

    @Test
    fun `trang đã nhận dạng rồi thì không chạy lại`() = runTest {
        seedDocument(pageCount = 2)
        useCase(DOC_ID)

        // Xoá dấu vết lời gọi để đếm riêng lần chạy thứ hai.
        engine.recognizedPaths.clear()
        val secondRun = useCase(DOC_ID)

        // Chạy lại OCR trên ảnh đã có chữ là tốn thời gian vô ích: cùng ảnh cho cùng kết quả.
        assertEquals(0, secondRun)
        assertEquals(0, engine.callCount)
    }

    @Test
    fun `tài liệu nhiều trang chỉ nhận dạng thêm những trang còn thiếu`() = runTest {
        seedDocument(pageCount = 3)
        // Trang 1 và 3 đã có chữ từ trước; chỉ trang 2 còn thiếu.
        repository.seedOcrText(
            DOC_ID,
            listOf(ocrPage(pageIndex = 1), ocrPage(pageIndex = 3)),
        )

        val newlyRecognized = useCase(DOC_ID)

        assertEquals(1, newlyRecognized)
        assertEquals(1, engine.callCount)
        // Chữ cũ của trang 1 và 3 phải còn nguyên, không bị ghi đè thành rỗng.
        assertEquals(
            listOf(1, 2, 3),
            repository.observeOcrText(DOC_ID).firstValue().map { it.pageIndex }.sorted(),
        )
    }

    @Test
    fun `một trang lỗi không làm hỏng chữ của các trang còn lại`() = runTest {
        seedDocument(pageCount = 3)
        // Trang 2 có ảnh hỏng/không đọc được.
        engine.failForPaths = setOf(storage.resolve(path(2)).path)

        val newlyRecognized = useCase(DOC_ID)

        // Người dùng mất chữ của một trang còn hơn mất chữ của tất cả.
        assertEquals(2, newlyRecognized)
        assertEquals(
            listOf(1, 3),
            repository.observeOcrText(DOC_ID).firstValue().map { it.pageIndex }.sorted(),
        )
    }

    @Test
    fun `trang thiếu file ảnh thì bị bỏ qua chứ không làm hỏng cả tài liệu`() = runTest {
        seedDocument(pageCount = 2)
        storage.resolve(path(1)).delete()

        val newlyRecognized = useCase(DOC_ID)

        assertEquals(1, newlyRecognized)
        assertEquals(listOf(2), repository.observeOcrText(DOC_ID).firstValue().map { it.pageIndex })
    }

    @Test
    fun `không trang nào nhận dạng được thì báo lỗi chứ không im lặng thành công`() = runTest {
        seedDocument(pageCount = 2)
        engine.failAll = true

        val result = runCatching { useCase(DOC_ID) }

        // Trả về thành công với nội dung rỗng sẽ khiến người dùng tưởng tài liệu không có chữ.
        assertTrue(result.exceptionOrNull() is OcrFailedException)
    }

    @Test
    fun `thất bại thì trạng thái được ghi là FAILED để người dùng biết cần thử lại`() = runTest {
        seedDocument(pageCount = 1)
        engine.failAll = true

        runCatching { useCase(DOC_ID) }

        // Nếu để nguyên NONE, danh sách hiển thị "Chưa nhận dạng chữ" như thể chưa từng bấm gì.
        assertEquals(OcrStatus.FAILED, repository.getDocument(DOC_ID)?.ocrStatus)
    }

    @Test
    fun `tài liệu không có trang nào thì trả về 0 mà không gọi engine`() = runTest {
        seedDocument(pageCount = 0)

        val newlyRecognized = useCase(DOC_ID)

        assertEquals(0, newlyRecognized)
        assertEquals(0, engine.callCount)
    }

    @Test
    fun `tài liệu không tồn tại thì từ chối trước khi gọi engine`() = runTest {
        val result = runCatching { useCase("khong-co") }

        assertTrue(result.exceptionOrNull() is DocumentNotFoundException)
        assertEquals(0, engine.callCount)
    }

    @Test
    fun `lỗi đọc dữ liệu không phải lỗi OCR thì vẫn ném ra ngoài`() = runTest {
        seedDocument(pageCount = 1)
        repository.failGetPages = true

        val result = runCatching { useCase(DOC_ID) }

        // Lỗi hạ tầng phải lộ ra, không được nuốt thành "không tìm thấy chữ".
        assertNotNull(result.exceptionOrNull())
        assertTrue(result.exceptionOrNull() !is OcrFailedException)
    }

    // ---------------------------------------------------------------------
    // Chọn engine theo cài đặt (Giai đoạn B)
    // ---------------------------------------------------------------------

    @Test
    fun `mặc định dùng Tesseract để có tiếng Việt có dấu`() = runTest {
        seedDocument(pageCount = 1)

        useCase(DOC_ID)

        // Mặc định PHẢI là Tesseract: ML Kit Text Recognition v2 không hỗ trợ tiếng Việt
        // có dấu, nên để ML Kit làm mặc định là âm thầm bỏ dấu tài liệu của người dùng.
        assertEquals(OcrEngineKind.TESSERACT, settings.current.ocrEngineKind)
        assertEquals(1, tesseract.appliedModels.size)
    }

    @Test
    fun `chọn ML Kit thì không cài model Tesseract`() = runTest {
        seedDocument(pageCount = 1)
        settings.setOcrEngineKind(OcrEngineKind.ML_KIT)

        useCase(DOC_ID)

        // ML Kit có model sẵn trong APK — giải nén 12 MB model Tesseract là vô ích, và tệ
        // hơn là bắt người dùng chờ đợi không cần thiết.
        assertTrue(provisioner.installRequests.isEmpty())
        assertTrue(tesseract.appliedModels.isEmpty())
        assertEquals(1, engine.callCount)
    }

    @Test
    fun `chọn Tesseract thì cài model trước khi nhận dạng`() = runTest {
        seedDocument(pageCount = 1)
        settings.setOcrEngineKind(OcrEngineKind.TESSERACT)

        useCase(DOC_ID)

        assertEquals(listOf(OcrModelQuality.BEST), provisioner.installRequests)
        assertEquals(OcrModelQuality.BEST, tesseract.appliedModels.single().second)
    }

    @Test
    fun `đổi chất lượng model thì lần chạy sau dùng đúng model mới`() = runTest {
        seedDocument(pageCount = 1)
        settings.setOcrModelQuality(OcrModelQuality.BEST)
        useCase(DOC_ID)

        // Xoá chữ đã nhận dạng để buộc chạy lại (nếu không, use case bỏ qua trang đã có chữ).
        repository.seedOcrText(DOC_ID, emptyList())
        settings.setOcrModelQuality(OcrModelQuality.FAST)
        useCase(DOC_ID)

        // Chất lượng model phải đi theo cài đặt tại thời điểm chạy, không bị đóng băng ở giá
        // trị lúc khởi tạo — nếu không, đổi trong Cài đặt sẽ không có tác dụng cho tới khi
        // người dùng tắt mở lại ứng dụng.
        assertEquals(
            listOf(OcrModelQuality.BEST, OcrModelQuality.FAST),
            provisioner.installRequests,
        )
        assertEquals(OcrModelQuality.FAST, tesseract.appliedModels.last().second)
    }

    @Test
    fun `thiếu model thì ném lỗi cài đặt chứ không báo thành công rỗng`() = runTest {
        seedDocument(pageCount = 1)
        provisioner.fail(OcrModelQuality.BEST)

        val result = runCatching { useCase(DOC_ID) }

        // Nếu lỗi này bị nuốt thành kết quả rỗng, người dùng sẽ thấy tài liệu "không có chữ"
        // mà không hiểu vì sao — trong khi nguyên nhân thật là cài model thất bại.
        val error = result.exceptionOrNull()
        assertTrue(
            "Mong đợi TessdataInstallException nhưng nhận $error",
            error is vn.scanviet.app.domain.ocr.TessdataInstallException,
        )
        assertEquals(0, engine.callCount)
    }

    @Test
    fun `thiếu model thì KHÔNG đánh dấu tài liệu là đang nhận dạng`() = runTest {
        seedDocument(pageCount = 1)
        provisioner.fail(OcrModelQuality.BEST)

        runCatching { useCase(DOC_ID) }

        // Model được lấy TRƯỚC khi bật RUNNING. Nếu thứ tự bị đảo, giao diện sẽ kẹt ở trạng
        // thái "đang nhận dạng" vĩnh viễn sau khi thất bại.
        assertEquals(OcrStatus.NONE, repository.getDocument(DOC_ID)?.ocrStatus)
    }

    // ---------------------------------------------------------------------
    // Phân biệt LÝ DO thất bại (lỗi thật đã sửa)
    // ---------------------------------------------------------------------
    //
    // Trước đây mọi trường hợp đều thành một câu "Lỗi nhận dạng" chung. Người dùng không
    // phân biệt được "ảnh mờ, chụp lại đi" với "máy hết dung lượng" — hai việc cần hai hành
    // động hoàn toàn khác nhau. Bốn test dưới đây khoá hành vi phân biệt đó lại.

    @Test
    fun `engine chạy được nhưng ảnh không có chữ thì lý do là NO_TEXT_FOUND`() = runTest {
        seedDocument(pageCount = 2)
        engine.returnEmptyLines = true

        val result = runCatching { useCase(DOC_ID) }

        // `OcrPageResult` với `lines` rỗng KHÁC hẳn `null`: engine đã chạy xong và kết luận
        // ảnh không có chữ. Người dùng nên chụp lại rõ hơn, không phải đi giải phóng ổ đĩa.
        val error = result.exceptionOrNull()
        assertTrue("Mong đợi OcrFailedException nhưng nhận $error", error is OcrFailedException)
        assertEquals(
            vn.scanviet.app.domain.OcrFailureReason.NO_TEXT_FOUND,
            (error as OcrFailedException).reason,
        )
        assertEquals(2, error.failedPageCount)
    }

    @Test
    fun `engine ném lỗi thì lý do là ENGINE_UNAVAILABLE chứ không phải ảnh không có chữ`() = runTest {
        seedDocument(pageCount = 1)
        engine.throwForAll = true

        val result = runCatching { useCase(DOC_ID) }

        val error = result.exceptionOrNull() as OcrFailedException
        // Engine hỏng là lỗi hệ thống, thử lại có thể thành công. Gộp chung với "ảnh mờ" sẽ
        // khiến người dùng đi chụp lại vô ích trong khi vấn đề nằm ở máy.
        assertEquals(
            vn.scanviet.app.domain.OcrFailureReason.ENGINE_UNAVAILABLE,
            error.reason,
        )
    }

    @Test
    fun `engine tra ve null cho moi trang thi ly do la ENGINE_UNAVAILABLE`() = runTest {
        seedDocument(pageCount = 2)
        engine.failAll = true

        val result = runCatching { useCase(DOC_ID) }

        val error = result.exceptionOrNull() as OcrFailedException
        // `null` nghĩa là "không đọc được ảnh" hoặc "engine chưa sẵn sàng" — cả hai đều là
        // lỗi hệ thống, không phải kết luận về nội dung ảnh.
        assertEquals(
            vn.scanviet.app.domain.OcrFailureReason.ENGINE_UNAVAILABLE,
            error.reason,
        )
    }

    @Test
    fun `mat het file anh thi ly do la PAGE_UNREADABLE`() = runTest {
        seedDocument(pageCount = 2)
        storage.resolve(path(1)).delete()
        storage.resolve(path(2)).delete()

        val result = runCatching { useCase(DOC_ID) }

        val error = result.exceptionOrNull() as OcrFailedException
        // Không có ảnh nào để đọc: vấn đề lưu trữ, khác hẳn "ảnh không có chữ".
        assertEquals(
            vn.scanviet.app.domain.OcrFailureReason.PAGE_UNREADABLE,
            error.reason,
        )
    }

    @Test
    fun `ly do mac dinh van la NO_TEXT_FOUND khi khong ro nguyen nhan`() = runTest {
        seedDocument(pageCount = 1)
        // Engine trả kết quả rỗng: rõ ràng nhất là "không tìm thấy chữ".
        engine.returnEmptyLines = true

        val error = runCatching { useCase(DOC_ID) }.exceptionOrNull() as OcrFailedException

        assertEquals(vn.scanviet.app.domain.OcrFailureReason.NO_TEXT_FOUND, error.reason)
    }

    /** Tạo tài liệu thật trong database giả, kèm file ảnh thật trên đĩa. */
    private suspend fun seedDocument(pageCount: Int) {
        val pages = (1..pageCount).map { index ->
            NewDocumentPage(
                relativePath = path(index),
                width = 1240,
                height = 1754,
                sizeBytes = 1,
            )
        }
        repository.createDocument(DOC_ID, TITLE, pages)
        pages.forEachIndexed { offset, page ->
            storage.writePageImage(
                documentId = DOC_ID,
                pageIndex = offset + 1,
                jpegBytes = byteArrayOf(1, 2, 3),
            )
        }
    }

    private fun ocrPage(pageIndex: Int) = vn.scanviet.app.domain.model.OcrText(
        documentId = DOC_ID,
        pageIndex = pageIndex,
        lines = listOf(
            vn.scanviet.app.domain.model.StoredOcrLine(
                text = "chữ cũ",
                left = 0.1f,
                top = 0.1f,
                right = 0.5f,
                bottom = 0.2f,
            ),
        ),
        imageWidth = 1240,
        imageHeight = 1754,
    )

    private fun path(index: Int): String = "documents/$DOC_ID/page_%04d.jpg".format(index)

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()

    private companion object {
        const val DOC_ID = "doc-ocr"
        const val TITLE = "Hợp đồng thuê nhà"
    }
}
