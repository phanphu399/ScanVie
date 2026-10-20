package vn.scanviet.app.ui.library

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.scanviet.app.R
import vn.scanviet.app.domain.usecase.RenameDocumentUseCase
import vn.scanviet.app.testing.FakeDocumentRepository
import vn.scanviet.app.testing.FakePageImageLoader
import vn.scanviet.app.testing.testDocument

/**
 * ViewModel được kiểm tra qua đúng chuỗi trạng thái mà màn hình nhận được (Turbine),
 * không phải bằng cách đọc biến nội bộ.
 *
 * Lưu ý về cách viết test:
 * - PHẢI dùng `runTest(mainDispatcher)`: dispatcher của Main và của runTest phải dùng
 *   chung một hàng đợi ảo, nếu không `awaitItem()` sẽ hết hạn sau 3 giây.
 * - Trạng thái [LibraryUiState.Loading] có thật trên thiết bị (Room đọc bất đồng bộ),
 *   nhưng trong test với repository giả thì nó bị gộp mất vì dữ liệu phát ra tức thời.
 *   Vì vậy [awaitSettled] bỏ qua Loading và chờ trạng thái đã ổn định.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeDocumentRepository
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = FakeDocumentRepository()
        viewModel = LibraryViewModel(
            repository = repository,
            renameDocument = RenameDocumentUseCase(repository),
            thumbnailLoader = FakePageImageLoader(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `chưa có tài liệu thì màn hình hiển thị trạng thái rỗng`() = runTest(mainDispatcher) {
        viewModel.uiState.test {
            assertEquals(LibraryUiState.NoResult, awaitSettled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `có tài liệu thì hiển thị danh sách đã sắp xếp`() = runTest(mainDispatcher) {
        repository.seed(
            testDocument(id = "doc-1", title = "Tài liệu cũ", updatedAtMillis = 1_000L),
            testDocument(id = "doc-2", title = "Tài liệu mới", updatedAtMillis = 2_000L),
        )

        viewModel.uiState.test {
            val state = awaitSettled()
            assertTrue(state is LibraryUiState.Documents)
            assertEquals(
                listOf("doc-2", "doc-1"),
                (state as LibraryUiState.Documents).items.map { it.document.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tìm kiếm không dấu vẫn lọc đúng tài liệu có dấu`() = runTest(mainDispatcher) {
        repository.seed(
            testDocument(id = "doc-1", title = "Hóa đơn điện"),
            testDocument(id = "doc-2", title = "Hợp đồng thuê nhà", updatedAtMillis = 2_000L),
        )

        viewModel.onQueryChange("hoa don")

        viewModel.uiState.test {
            val state = awaitSettled()
            assertTrue(state is LibraryUiState.Documents)
            assertEquals(
                listOf("doc-1"),
                (state as LibraryUiState.Documents).items.map { it.document.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `từ khoá không khớp tài liệu nào thì báo không có kết quả`() = runTest(mainDispatcher) {
        repository.seed(testDocument(id = "doc-1", title = "Hóa đơn điện"))

        viewModel.onQueryChange("hợp đồng lao động")

        viewModel.uiState.test {
            assertEquals(LibraryUiState.NoResult, awaitSettled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `xoá từ khoá thì hiện lại toàn bộ danh sách`() = runTest(mainDispatcher) {
        repository.seed(
            testDocument(id = "doc-1", title = "Hóa đơn điện"),
            testDocument(id = "doc-2", title = "Hợp đồng thuê nhà", updatedAtMillis = 2_000L),
        )

        viewModel.uiState.test {
            awaitSettled()

            viewModel.onQueryChange("hoa don")
            assertEquals(1, (awaitSettled() as LibraryUiState.Documents).items.size)

            viewModel.onQueryChange("")
            assertEquals(2, (awaitSettled() as LibraryUiState.Documents).items.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `từ khoá được giữ riêng khỏi trạng thái màn hình`() = runTest(mainDispatcher) {
        viewModel.onQueryChange("hoa don")

        assertEquals("hoa don", viewModel.query.value)
    }

    @Test
    fun `xoá tài liệu thì tài liệu biến mất khỏi danh sách`() = runTest(mainDispatcher) {
        repository.seed(testDocument(id = "doc-1", title = "Hóa đơn điện"))

        viewModel.uiState.test {
            assertTrue(awaitSettled() is LibraryUiState.Documents)

            viewModel.onMoveToTrash("doc-1")

            assertEquals(LibraryUiState.NoResult, awaitSettled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `đổi tên hợp lệ cập nhật danh sách`() = runTest(mainDispatcher) {
        repository.seed(testDocument(id = "doc-1", title = "Tên cũ"))

        viewModel.uiState.test {
            awaitSettled()

            viewModel.onRename("doc-1", "Tên mới")

            val state = awaitSettled()
            assertEquals(
                "Tên mới",
                (state as LibraryUiState.Documents).items.first().document.title,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `đổi tên rỗng thì phát thông báo lỗi và không đổi dữ liệu`() = runTest(mainDispatcher) {
        repository.seed(testDocument(id = "doc-1", title = "Tên cũ"))

        viewModel.onRename("doc-1", "   ")

        assertEquals(R.string.rename_error_empty, viewModel.message.value?.resId)
        assertEquals("Tên cũ", repository.getDocument("doc-1")?.title)
    }

    @Test
    fun `đổi tên quá dài thì thông báo lỗi kèm giới hạn ký tự`() = runTest(mainDispatcher) {
        repository.seed(testDocument(id = "doc-1", title = "Tên cũ"))

        viewModel.onRename("doc-1", "a".repeat(200))

        assertEquals(R.string.rename_error_too_long, viewModel.message.value?.resId)
        assertEquals(listOf(80), viewModel.message.value?.formatArgs)
    }

    @Test
    fun `hiển thị thông báo xong thì thông báo được xoá`() = runTest(mainDispatcher) {
        repository.seed(testDocument(id = "doc-1", title = "Tên cũ"))
        viewModel.onRename("doc-1", "")

        viewModel.onMessageShown()

        assertEquals(null, viewModel.message.value)
    }

    @Test
    fun `lỗi đọc dữ liệu thì màn hình hiển thị trạng thái lỗi`() = runTest(mainDispatcher) {
        repository.failObserve = true

        viewModel.uiState.test {
            val state = awaitSettled()
            assertTrue(state is LibraryUiState.Failed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `thử lại sau lỗi thì đọc lại dữ liệu thành công`() = runTest(mainDispatcher) {
        repository.failObserve = true

        viewModel.uiState.test {
            assertTrue(awaitSettled() is LibraryUiState.Failed)

            repository.failObserve = false
            repository.seed(testDocument(id = "doc-1", title = "Hóa đơn điện"))
            viewModel.onRetry()

            assertTrue(awaitSettled() is LibraryUiState.Documents)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Chờ trạng thái đầu tiên khác [LibraryUiState.Loading].
     *
     * Trên thiết bị, Loading là trạng thái thật của lần đọc đầu tiên. Trong test, repository
     * giả phát dữ liệu ngay lập tức nên StateFlow gộp Loading lại và ta không quan sát được nó —
     * điều này không phải lỗi của ViewModel.
     */
    private suspend fun ReceiveTurbine<LibraryUiState>.awaitSettled(): LibraryUiState {
        while (true) {
            val state = awaitItem()
            if (state != LibraryUiState.Loading) return state
        }
    }
}
