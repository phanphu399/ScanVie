package vn.scanviet.app.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.usecase.RenameDocumentUseCase
import vn.scanviet.app.navigation.ScanVietRoutes
import vn.scanviet.app.ui.common.UiMessage
import vn.scanviet.app.ui.common.toUiError
import vn.scanviet.app.ui.common.toUiMessage

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val renameDocument: RenameDocumentUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val documentId: String = checkNotNull(savedStateHandle[ScanVietRoutes.ARG_DOCUMENT_ID])

    private val _message = MutableStateFlow<UiMessage?>(null)
    private val _closed = MutableStateFlow(false)

    val message: StateFlow<UiMessage?> = _message

    /** true khi tài liệu đã bị chuyển vào thùng rác: màn hình tự quay lại danh sách. */
    val closed: StateFlow<Boolean> = _closed

    val uiState: StateFlow<DocumentDetailUiState> = combine(
        repository.observeDocument(documentId),
        repository.observePages(documentId),
    ) { document, pages -> document to pages }
        .map { (document, pages) ->
            if (document == null) {
                DocumentDetailUiState.NotFound
            } else {
                DocumentDetailUiState.Loaded(
                    document = document,
                    pages = pages,
                    missingPageCount = repository.countMissingPageFiles(documentId),
                )
            }
        }
        .onStart { emit(DocumentDetailUiState.Loading) }
        .catch { throwable -> emit(DocumentDetailUiState.Failed(throwable.toUiError())) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DocumentDetailUiState.Loading,
        )

    fun onRename(newTitle: String) {
        viewModelScope.launch {
            runCatching { renameDocument(documentId, newTitle) }
                .onFailure { failure -> _message.value = failure.toUiMessage() }
        }
    }

    fun onMoveToTrash() {
        viewModelScope.launch {
            runCatching { repository.moveToTrash(documentId) }
                .onSuccess { _closed.value = true }
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
