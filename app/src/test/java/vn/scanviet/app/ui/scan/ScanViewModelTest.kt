package vn.scanviet.app.ui.scan

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.R
import vn.scanviet.app.core.format.Formatters
import vn.scanviet.app.data.file.LocalDocumentFileStorage
import vn.scanviet.app.domain.ocr.OcrEngineProvider
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.domain.usecase.CreateDocumentUseCase
import vn.scanviet.app.domain.usecase.ImportScannedDocumentUseCase
import vn.scanviet.app.domain.usecase.RecognizeDocumentTextUseCase
import vn.scanviet.app.platform.scanner.ScannedDocument
import vn.scanviet.app.platform.scanner.ScanUnavailableException
import vn.scanviet.app.testing.FakeDocumentRepository
import vn.scanviet.app.testing.FakeDocumentScannerEngine
import vn.scanviet.app.testing.FakeMlKitOcrEngine
import vn.scanviet.app.testing.FakeOcrEngine
import vn.scanviet.app.testing.FakePageImageReader
import vn.scanviet.app.testing.FakeSettingsRepository
import vn.scanviet.app.testing.FakeTessdataProvisioner
import vn.scanviet.app.testing.FakeTesseractOcrEngine
import vn.scanviet.app.testing.FakeTimeProvider
import vn.scanviet.app.testing.TestDispatcherProvider
import vn.scanviet.app.ui.common.UiError

/**
 * Kiểm tra luồng quét ở tầng ViewModel.
 *
 * Đây là test tích hợp có chủ đích: ViewModel + use case nhập ảnh + file THẬT, chỉ thay
 * trình quét và repository. Nhờ vậy nó trả lời được câu hỏi quan trọng nhất — "bấm Lưu
 * thì có thật sự sinh ra file và bản ghi đúng không".
 *
 * Phải chạy dưới Robolectric vì cần `Activity` và `IntentSender` thật (kiểu của Android
 * không tạo được trong JVM thuần).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScanViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var scanner: FakeDocumentScannerEngine
    private lateinit var repository: FakeDocumentRepository
    private lateinit var reader: FakePageImageReader
    private lateinit var storage: DocumentFileStorage
    private lateinit var settings: FakeSettingsRepository
    private lateinit var ocrEngine: FakeOcrEngine
    private lateinit var viewModel: ScanViewModel
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)

        val context = ApplicationProvider.getApplicationContext<Context>()
        activity = Robolectric.buildActivity(Activity::class.java).get()
        repository = FakeDocumentRepository()
        reader = FakePageImageReader()
        storage = LocalDocumentFileStorage(context, TestDispatcherProvider())
        // MỘT instance cài đặt duy nhất dùng chung cho cả ViewModel lẫn use case nhận dạng:
        // test phải điều khiển được đúng giá trị mà ViewModel đọc, nếu không nó chỉ đang
        // kiểm chứng một đối tượng khác với đối tượng đang chạy.
        settings = FakeSettingsRepository()
        ocrEngine = FakeOcrEngine()

        scanner = FakeDocumentScannerEngine(
            intentSenderFactory = {
                PendingIntent
                    .getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
                    .intentSender
            },
        )

        viewModel = ScanViewModel(
            scannerEngine = scanner,
            importScannedDocument = ImportScannedDocumentUseCase(
                imageReader = reader,
                storage = storage,
                createDocument = CreateDocumentUseCase(repository),
            ),
            recognizeDocumentText = RecognizeDocumentTextUseCase(
                repository = repository,
                storage = storage,
                settingsRepository = settings,
                engineProvider = OcrEngineProvider(
                    tesseract = FakeTesseractOcrEngine(ocrEngine),
                    mlKit = FakeMlKitOcrEngine(),
                    tessdata = FakeTessdataProvisioner(File("build/tmp/ocr-model")),
                ),
            ),
            settingsRepository = settings,
            timeProvider = FakeTimeProvider(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ban đầu màn hình ở trạng thái chờ`() {
        assertEquals(ScanUiState.Idle, viewModel.uiState.value)
        assertNull(viewModel.scanRequest.value)
    }

    @Test
    fun `số trang tối đa được lấy từ trình quét`() {
        assertEquals(20, viewModel.maxPagesPerScan)
    }

    @Test
    fun `bắt đầu quét thì báo đang chuẩn bị rồi phát yêu cầu mở trình quét`() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        scanner.beforeReturn = { gate.await() }

        viewModel.onStartScan(activity)

        // Trong lúc chờ Play services: người dùng phải thấy "đang chuẩn bị", không phải nút chết.
        assertEquals(ScanUiState.PreparingScanner, viewModel.uiState.value)
        assertEquals(1, scanner.createStartIntentCalls)

        gate.complete(Unit)

        assertNotNull("Phải có yêu cầu để màn hình mở trình quét", viewModel.scanRequest.value)
        assertEquals(ScanUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `màn hình xử lý yêu cầu quét xong thì yêu cầu được xoá`() = runTest(mainDispatcher) {
        viewModel.onStartScan(activity)
        assertNotNull(viewModel.scanRequest.value)

        viewModel.onScanRequestHandled()

        // Không xoá thì xoay màn hình sẽ bật lại trình quét ngoài ý muốn.
        assertNull(viewModel.scanRequest.value)
    }

    @Test
    fun `thiết bị không hỗ trợ quét thì hiện thông báo riêng`() = runTest(mainDispatcher) {
        scanner.failWith = ScanUnavailableException("thiếu Play services")

        viewModel.onStartScan(activity)

        assertEquals(ScanUiState.ScannerUnavailable, viewModel.uiState.value)
    }

    @Test
    fun `lỗi khác khi mở trình quét thì là lỗi chung`() = runTest(mainDispatcher) {
        scanner.failWith = IllegalStateException("lỗi lạ")

        viewModel.onStartScan(activity)

        assertEquals(ScanUiState.Failed(UiError.GENERIC), viewModel.uiState.value)
    }

    @Test
    fun `người dùng huỷ trình quét thì quay lại trạng thái chờ`() = runTest(mainDispatcher) {
        scanner.result = null

        viewModel.onScanResult(null)

        assertEquals(ScanUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `nhận kết quả quét thì hiện danh sách trang với tên mặc định theo ngày giờ`() = runTest(mainDispatcher) {
        scanner.result = ScannedDocument(listOf(URI_ONE, URI_TWO))

        viewModel.onScanResult(Intent())

        val state = viewModel.uiState.value as ScanUiState.Reviewing
        assertEquals(listOf(URI_ONE, URI_TWO), state.pages.map { it.sourceUri })
        assertEquals(
            "Tài liệu " + Formatters.dateTime(FakeTimeProvider.DEFAULT_TIME),
            state.title,
        )
        assertEquals(false, state.isSaving)
        assertTrue(state.canSave)
    }

    @Test
    fun `đổi tên thì tên trong trạng thái xem lại đổi theo`() = runTest(mainDispatcher) {
        reviewWith(URI_ONE, URI_TWO)

        viewModel.onTitleChange("Hợp đồng thuê nhà")

        assertEquals("Hợp đồng thuê nhà", (viewModel.uiState.value as ScanUiState.Reviewing).title)
    }

    @Test
    fun `chuyển trang lên thì hai trang đổi chỗ cho nhau`() = runTest(mainDispatcher) {
        reviewWith(URI_ONE, URI_TWO)

        viewModel.onMovePage(fromIndex = 1, toIndex = 0)

        assertEquals(
            listOf(URI_TWO, URI_ONE),
            (viewModel.uiState.value as ScanUiState.Reviewing).pages.map { it.sourceUri },
        )
    }

    @Test
    fun `chuyển trang xuống ở trang cuối thì bỏ qua`() = runTest(mainDispatcher) {
        reviewWith(URI_ONE, URI_TWO)

        viewModel.onMovePage(fromIndex = 1, toIndex = 2)

        assertEquals(
            listOf(URI_ONE, URI_TWO),
            (viewModel.uiState.value as ScanUiState.Reviewing).pages.map { it.sourceUri },
        )
    }

    @Test
    fun `xoá trang thì trang đó biến mất khỏi danh sách`() = runTest(mainDispatcher) {
        reviewWith(URI_ONE, URI_TWO)

        viewModel.onRemovePage(0)

        assertEquals(
            listOf(URI_TWO),
            (viewModel.uiState.value as ScanUiState.Reviewing).pages.map { it.sourceUri },
        )
    }

    @Test
    fun `xoá hết trang thì không cho lưu`() = runTest(mainDispatcher) {
        reviewWith(URI_ONE)

        viewModel.onRemovePage(0)

        val state = viewModel.uiState.value as ScanUiState.Reviewing
        assertTrue(state.pages.isEmpty())
        assertEquals(false, state.canSave)
    }

    @Test
    fun `lưu thành công thì ghi file, ghi database và báo đã lưu`() = runTest(mainDispatcher) {
        reader.put(URI_ONE, byteArrayOf(1, 2, 3))
        reader.put(URI_TWO, byteArrayOf(4, 5))
        reviewWith(URI_TWO, URI_ONE)
        viewModel.onTitleChange("Hợp đồng thuê nhà")

        viewModel.onSave()

        val state = viewModel.uiState.value
        assertTrue(state is ScanUiState.Saved)
        val documentId = (state as ScanUiState.Saved).documentId
        assertEquals(documentId, repository.lastCreatedDocumentId)
        assertEquals("Hợp đồng thuê nhà", repository.lastCreatedTitle)

        // Thứ tự người dùng đã sắp xếp phải được giữ: URI_TWO là trang 1.
        val pages = repository.getPages(documentId)
        assertEquals(listOf(1, 2), pages.map { it.pageIndex })
        assertTrue(storage.exists(pages[0].imageFile))
        assertTrue(storage.exists(pages[1].imageFile))
        assertTrue(storage.resolve(pages[0].imageFile).readBytes().contentEquals(byteArrayOf(4, 5)))
    }

    @Test
    fun `lưu với tên rỗng thì báo lỗi và giữ nguyên các trang đã quét`() = runTest(mainDispatcher) {
        reader.put(URI_ONE, byteArrayOf(1))
        reviewWith(URI_ONE)
        viewModel.onTitleChange("   ")

        viewModel.onSave()

        val state = viewModel.uiState.value as ScanUiState.Reviewing
        assertEquals(false, state.isSaving)
        assertEquals(1, state.pages.size)
        assertEquals(R.string.rename_error_empty, viewModel.message.value?.resId)
        assertEquals(0, repository.getDocuments().size)
        // Không được để lại file rác của lần lưu thất bại.
        assertTrue(storage.listDocumentDirectories().isEmpty())
    }

    @Test
    fun `lưu thất bại thì vẫn xoá được thông báo`() = runTest(mainDispatcher) {
        reader.put(URI_ONE, byteArrayOf(1))
        reviewWith(URI_ONE)
        viewModel.onTitleChange("")
        viewModel.onSave()
        assertNotNull(viewModel.message.value)

        viewModel.onMessageShown()

        assertNull(viewModel.message.value)
    }

    @Test
    fun `đặt lại thì quay về trạng thái chờ`() = runTest(mainDispatcher) {
        reviewWith(URI_ONE)
        viewModel.onScanResult(Intent())

        viewModel.onReset()

        assertEquals(ScanUiState.Idle, viewModel.uiState.value)
    }

    // ---------------------------------------------------------------------
    // Tự động nhận dạng chữ sau khi quét (Giai đoạn B)
    //
    // Trước các test này, công tắc "Tự động nhận dạng chữ sau khi quét" trong Cài đặt được
    // lưu và hiển thị nhưng KHÔNG được đọc ở đâu cả — bật hay tắt đều không thay đổi gì.
    // Một công tắc không làm gì là lời nói dối với người dùng, nên phải có test khoá hành vi.
    // ---------------------------------------------------------------------

    @Test
    fun `bật tự động nhận dạng thì lưu xong sẽ nhận dạng chữ`() = runTest(mainDispatcher) {
        settings.setAutoOcr(true)
        reader.put(URI_ONE, byteArrayOf(1))
        reviewWith(URI_ONE)

        viewModel.onSave()

        assertEquals(1, ocrEngine.callCount)
        assertTrue(viewModel.uiState.value is ScanUiState.Saved)
    }

    @Test
    fun `tắt tự động nhận dạng thì KHÔNG nhận dạng chữ`() = runTest(mainDispatcher) {
        settings.setAutoOcr(false)
        reader.put(URI_ONE, byteArrayOf(1))
        reviewWith(URI_ONE)

        viewModel.onSave()

        // Đây là hành vi mà công tắc hứa. Trước khi sửa, công tắc này không có tác dụng gì.
        assertEquals(0, ocrEngine.callCount)
        assertTrue(viewModel.uiState.value is ScanUiState.Saved)
    }

    @Test
    fun `mặc định là BẬT tự động nhận dạng`() = runTest(mainDispatcher) {
        // Không đặt gì cả: giá trị mặc định của ứng dụng phải là bật, vì người dùng bình
        // thường muốn có chữ ngay mà không phải bấm thêm bước nào.
        assertTrue(settings.current.autoOcr)

        reader.put(URI_ONE, byteArrayOf(1))
        reviewWith(URI_ONE)
        viewModel.onSave()

        assertEquals(1, ocrEngine.callCount)
    }

    @Test
    fun `nhận dạng lỗi KHÔNG làm mất tài liệu vừa lưu`() = runTest(mainDispatcher) {
        settings.setAutoOcr(true)
        ocrEngine.failAll = true
        reader.put(URI_ONE, byteArrayOf(1))
        reviewWith(URI_ONE)

        viewModel.onSave()

        // Tài liệu phải đã được lưu xong TRƯỚC khi nhận dạng chạy. Nhận dạng chỉ là bước
        // thêm giá trị: mất chữ còn hơn mất cả tài liệu vừa quét công phu.
        val state = viewModel.uiState.value
        assertTrue("Trạng thái phải là Saved, thực tế: $state", state is ScanUiState.Saved)
        // Và tài liệu thật sự có trong database, không chỉ là trạng thái giao diện.
        assertEquals(1, repository.getDocuments().size)
    }

    @Test
    fun `nhận dạng lỗi KHÔNG đưa người dùng về màn hình xem lại`() = runTest(mainDispatcher) {
        settings.setAutoOcr(true)
        ocrEngine.failAll = true
        reader.put(URI_ONE, byteArrayOf(1))
        reviewWith(URI_ONE)

        viewModel.onSave()

        // Nếu quay về màn hình xem lại, người dùng sẽ tưởng việc lưu thất bại và bấm "Lưu"
        // lần nữa — tạo ra TÀI LIỆU TRÙNG. Luồng quét phải kết thúc thành công.
        assertTrue(
            "Không được quay về Reviewing",
            viewModel.uiState.value !is ScanUiState.Reviewing,
        )
    }

    @Test
    fun `nhận dạng lỗi thì có thông báo cho người dùng`() = runTest(mainDispatcher) {
        settings.setAutoOcr(true)
        ocrEngine.failAll = true
        reader.put(URI_ONE, byteArrayOf(1))
        reviewWith(URI_ONE)

        viewModel.onSave()

        // Im lặng thất bại là điều tệ nhất: người dùng thấy tài liệu không có chữ mà không
        // hiểu vì sao.
        assertNotNull("Phải có thông báo khi nhận dạng thất bại", viewModel.message.value)
    }

    @Test
    fun `nhận dạng thành công thì KHÔNG hiện thông báo`() = runTest(mainDispatcher) {
        settings.setAutoOcr(true)
        reader.put(URI_ONE, byteArrayOf(1))
        reviewWith(URI_ONE)

        viewModel.onSave()

        // Một snackbar "đã nhận dạng" sau mỗi lần lưu sẽ gây nhiễu; người dùng tự thấy chữ
        // trong màn hình chi tiết. Chỉ báo khi có lỗi.
        assertNull("Không được hiện thông báo khi thành công", viewModel.message.value)
    }

    @Test
    fun `mỗi trang được nhận dạng đúng một lần`() = runTest(mainDispatcher) {
        settings.setAutoOcr(true)
        reader.put(URI_ONE, byteArrayOf(1))
        reader.put(URI_TWO, byteArrayOf(2))
        reviewWith(URI_ONE, URI_TWO)

        viewModel.onSave()

        // Hai trang -> hai lời gọi. Không được gọi lặp cho cùng một trang.
        assertEquals(2, ocrEngine.callCount)
    }

    /** Đưa ViewModel vào trạng thái xem lại với các URI cho trước. */
    private fun reviewWith(vararg uris: String) {
        scanner.result = ScannedDocument(uris.toList())
        viewModel.onScanResult(Intent())
    }

    private companion object {
        const val URI_ONE = "content://scanner.tmp/1"
        const val URI_TWO = "content://scanner.tmp/2"
    }
}
