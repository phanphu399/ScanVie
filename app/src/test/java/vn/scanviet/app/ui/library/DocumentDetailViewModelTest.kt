package vn.scanviet.app.ui.library

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import java.io.File
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.R
import vn.scanviet.app.data.file.LocalDocumentFileStorage
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.ocr.OcrEngineProvider
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.domain.usecase.ExportDocumentAsPdfUseCase
import vn.scanviet.app.domain.usecase.RecognizeDocumentTextUseCase
import vn.scanviet.app.domain.usecase.RenameDocumentUseCase
import vn.scanviet.app.navigation.ScanVietRoutes
import vn.scanviet.app.testing.FakeDocumentOutputTarget
import vn.scanviet.app.testing.FakeDocumentRepository
import vn.scanviet.app.testing.FakeMlKitOcrEngine
import vn.scanviet.app.testing.FakeOcrEngine
import vn.scanviet.app.testing.FakePageImageLoader
import vn.scanviet.app.testing.FakePdfExporter
import vn.scanviet.app.testing.FakeSettingsRepository
import vn.scanviet.app.testing.FakeTessdataProvisioner
import vn.scanviet.app.testing.FakeTesseractOcrEngine
import vn.scanviet.app.testing.TestDispatcherProvider

/**
 * Kiểm tra phần xuất và chia sẻ PDF ở màn hình chi tiết.
 *
 * Dùng file THẬT (Robolectric filesDir/cacheDir) vì hai hành vi quan trọng nhất đều nằm ở
 * quan hệ với hệ thống file: file PDF chia sẻ có thật sự được tạo không, và khi lỗi thì
 * trạng thái `isExporting` có được trả về `false` không (nếu không, nút sẽ kẹt vĩnh viễn).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DocumentDetailViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeDocumentRepository
    private lateinit var storage: DocumentFileStorage
    private lateinit var output: FakeDocumentOutputTarget
    private lateinit var exporter: FakePdfExporter
    private lateinit var ocrEngine: FakeOcrEngine
    private lateinit var viewModel: DocumentDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = LocalDocumentFileStorage(context, TestDispatcherProvider())
        repository = FakeDocumentRepository()
        output = FakeDocumentOutputTarget()
        exporter = FakePdfExporter()
        ocrEngine = FakeOcrEngine()
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lưu bản sao ghi PDF và báo thành công`() = runTest(mainDispatcher) {
        seedDocument()
        viewModel.uiState.test {
            awaitSettled()
            viewModel.onSaveCopy("content://nguoi-dung/chon.pdf")

            assertEquals(R.string.detail_export_saved, messageRes())
            assertEquals(1, exporter.callCount)
            assertEquals(1, output.openCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lưu bản sao truyền đúng URI người dùng chọn xuống tầng ghi`() = runTest(mainDispatcher) {
        seedDocument()
        viewModel.uiState.test {
            awaitSettled()
            viewModel.onSaveCopy("content://nguoi-dung/hop-dong.pdf")

            assertEquals(
                "content://nguoi-dung/hop-dong.pdf",
                (output.lastTarget as vn.scanviet.app.domain.export.PdfOutputTarget.SafUri).uri,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `chia sẻ tạo file PDF thật trong vùng nháp và phát yêu cầu chia sẻ`() =
        runTest(mainDispatcher) {
            seedDocument()
            viewModel.uiState.test {
                awaitSettled()
                viewModel.onSharePdf()

                val file = viewModel.shareRequest.value
                assertNotNull("Phải phát yêu cầu chia sẻ", file)
                assertTrue("File PDF phải tồn tại trên đĩa", file!!.exists())
                assertTrue(file.name.endsWith(".pdf"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tên file chia sẻ được lấy từ tên tài liệu, đã bỏ dấu`() = runTest(mainDispatcher) {
        seedDocument(title = "Hợp đồng thuê nhà")
        viewModel.uiState.test {
            awaitSettled()
            viewModel.onSharePdf()

            assertEquals("hop-dong-thue-nha.pdf", viewModel.shareRequest.value?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `đã xử lý chia sẻ thì yêu cầu được xoá để không mở lại khi xoay màn hình`() =
        runTest(mainDispatcher) {
            seedDocument()
            viewModel.uiState.test {
                awaitSettled()
                viewModel.onSharePdf()
                assertNotNull(viewModel.shareRequest.value)

                viewModel.onShareHandled()

                assertNull(viewModel.shareRequest.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `thiếu ảnh trang thì báo lỗi kèm số trang và không phát yêu cầu chia sẻ`() =
        runTest(mainDispatcher) {
            seedDocument()
            storage.resolve("documents/$DOC_ID/page_0001.jpg").delete()
            viewModel.uiState.test {
                awaitSettled()

                viewModel.onSharePdf()

                assertNull("Không được phát yêu cầu chia sẻ khi lỗi", viewModel.shareRequest.value)
                assertEquals(R.string.error_page_image_missing, messageRes())
                // Thông báo phải kèm số trang cụ thể, không phải câu chung chung.
                assertEquals(listOf(1), viewModel.message.value?.formatArgs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `lỗi thì trạng thái đang xuất được trả về false, nút không bị kẹt`() =
        runTest(mainDispatcher) {
            seedDocument()
            exporter.failWith = java.io.IOException("đĩa đầy")
            viewModel.uiState.test {
                awaitSettled()

                viewModel.onSharePdf()

                assertTrue(
                    "Nút phải dùng lại được sau khi lỗi",
                    !viewModel.isExporting.value,
                )
                assertEquals(R.string.error_storage, messageRes())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `lỗi mở đích ghi cũng được báo và không kẹt trạng thái`() = runTest(mainDispatcher) {
        seedDocument()
        output.failOpenWith = java.io.IOException("URI bị thu hồi")
        viewModel.uiState.test {
            awaitSettled()

            viewModel.onSaveCopy("content://nguoi-dung/chon.pdf")

            assertEquals(R.string.error_storage, messageRes())
            assertTrue(!viewModel.isExporting.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tài liệu không tồn tại thì báo lỗi riêng khi xuất`() = runTest(mainDispatcher) {
        // Không seed gì: database không có tài liệu này.
        viewModel.uiState.test {
            awaitSettled()

            viewModel.onSharePdf()

            assertEquals(R.string.error_document_not_found, messageRes())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mỗi lần xuất dùng một tên file theo tên tài liệu hiện tại`() = runTest(mainDispatcher) {
        // Bất biến đáng kiểm: tên file do tài liệu quyết định, không phải do trạng thái cũ.
        // (Việc chặn bấm hai lần nằm ở `beginExport`, đặt cờ đồng bộ ngay trong lời gọi nên
        // không cần dựng lại tình huống đua coroutine mong manh trong test.)
        seedDocument(title = "Hóa đơn điện")
        viewModel.uiState.test {
            awaitSettled()

            viewModel.onSharePdf()
            assertEquals("hoa-don-dien.pdf", viewModel.shareRequest.value?.name)

            viewModel.onShareHandled()
            viewModel.onRename("Biên lai nước")
            awaitSettled()

            viewModel.onSharePdf()
            assertEquals("bien-lai-nuoc.pdf", viewModel.shareRequest.value?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel(): DocumentDetailViewModel = DocumentDetailViewModel(
        repository = repository,
        renameDocument = RenameDocumentUseCase(repository),
        exportDocument = ExportDocumentAsPdfUseCase(
            repository = repository,
            storage = storage,
            outputTarget = output,
            pdfExporter = exporter,
        ),
        recognizeText = RecognizeDocumentTextUseCase(
            repository = repository,
            storage = storage,
            settingsRepository = FakeSettingsRepository(),
            engineProvider = OcrEngineProvider(
                tesseract = FakeTesseractOcrEngine(ocrEngine),
                mlKit = FakeMlKitOcrEngine(ocrEngine),
                tessdata = FakeTessdataProvisioner(File("build/tmp/ocr-model")),
            ),
        ),
        storage = storage,
        savedStateHandle = SavedStateHandle(mapOf(ScanVietRoutes.ARG_DOCUMENT_ID to DOC_ID)),
        thumbnailLoader = FakePageImageLoader(),
    )

    private fun messageRes(): Int? = viewModel.message.value?.resId

    private suspend fun seedDocument(title: String = "Hợp đồng thuê nhà") {
        val page = NewDocumentPage(
            relativePath = "documents/$DOC_ID/page_0001.jpg",
            width = 1240,
            height = 1754,
            sizeBytes = 3,
        )
        repository.createDocument(DOC_ID, title, listOf(page))
        storage.writePageImage(DOC_ID, 1, byteArrayOf(1, 2, 3))
    }

    /** Chờ trạng thái tài liệu đã tải xong, bỏ qua trạng thái Loading trung gian. */
    private suspend fun app.cash.turbine.ReceiveTurbine<DocumentDetailUiState>.awaitSettled() {
        var state = awaitItem()
        while (state is DocumentDetailUiState.Loading) state = awaitItem()
        return
    }

    private companion object {
        const val DOC_ID = "doc-chi-tiet"
    }
}
