package vn.scanviet.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.image.PageImageLoader
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.usecase.RenameDocumentUseCase
import vn.scanviet.app.ui.common.UiMessage
import vn.scanviet.app.ui.common.toUiError
import vn.scanviet.app.ui.common.toUiMessage

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val renameDocument: RenameDocumentUseCase,
    /** Chỉ để màn hình nạp ảnh thu nhỏ; ViewModel không tự dùng. */
    val thumbnailLoader: PageImageLoader,
) : ViewModel() {

    private val queryState = MutableStateFlow("")
    private val retryTrigger = MutableStateFlow(0)
    private val _message = MutableStateFlow<UiMessage?>(null)

    /** Từ khoá hiện tại. Tách khỏi [uiState] để ô tìm kiếm không mất chữ khi đang tải. */
    val query: StateFlow<String> = queryState

    /** Thông báo cho snackbar; màn hình gọi [onMessageShown] sau khi hiển thị. */
    val message: StateFlow<UiMessage?> = _message

    /**
     * Đổi từ khoá là đổi luôn nguồn dữ liệu (flatMapLatest). Không cần debounce vì
     * truy vấn chạy cục bộ trên SQLite với vài chục bản ghi.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> =
        combine(queryState, retryTrigger) { currentQuery, _ -> currentQuery }
            .flatMapLatest { currentQuery ->
                repository.observeDocuments(currentQuery)
                    .map<List<Document>, List<LibraryItem>> { documents -> documents.toLibraryItems() }
                    .map<List<LibraryItem>, LibraryUiState> { items ->
                        if (items.isEmpty()) {
                            LibraryUiState.NoResult
                        } else {
                            LibraryUiState.Documents(items)
                        }
                    }
                    // Chỉ hiện Loading khi thật sự chưa có dữ liệu nào được phát ra.
                    .onStart { emit(LibraryUiState.Loading) }
                    .catch { throwable -> emit(LibraryUiState.Failed(throwable.toUiError())) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = LibraryUiState.Loading,
            )

    /**
     * Ghép mỗi tài liệu với đường dẫn ảnh trang đầu của nó.
     *
     * Vì sao tra cứu đường dẫn SAU khi đã có danh sách: số dòng hiển thị chỉ vài chục, nên
     * tra cứu theo lô là một truy vấn nhỏ. Nếu hỏi ngay lúc quan sát thì mỗi lần tên tài liệu
     * đổi (đổi tên, đổi thời gian) cũng kéo theo truy vấn ảnh vô ích.
     *
     * Lỗi khi tra cứu không được làm hỏng danh sách: `runCatching` trả về danh sách không có
     * ảnh thu nhỏ. Người dùng vẫn thấy và mở được tài liệu — mất ảnh nhỏ là chuyện nhỏ so
     * với mất cả danh sách.
     */
    private suspend fun List<Document>.toLibraryItems(): List<LibraryItem> {
        val paths = runCatching { repository.firstPagePaths(map { it.id }) }
            .getOrDefault(emptyMap())
        return map { document -> LibraryItem(document, firstPagePath = paths[document.id]) }
    }

    fun onQueryChange(newQuery: String) {
        queryState.value = newQuery
    }

    fun onRetry() {
        retryTrigger.update { it + 1 }
    }

    fun onRename(documentId: String, newTitle: String) {
        viewModelScope.launch {
            runCatching { renameDocument(documentId, newTitle) }
                .onFailure { failure -> _message.value = failure.toUiMessage() }
        }
    }

    fun onMoveToTrash(documentId: String) {
        viewModelScope.launch {
            runCatching { repository.moveToTrash(documentId) }
                .onFailure { failure -> _message.value = failure.toUiMessage() }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
