package vn.scanviet.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.scanviet.app.R
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.ui.common.UiError
import vn.scanviet.app.ui.common.UiMessage
import vn.scanviet.app.ui.common.toUiError
import vn.scanviet.app.ui.common.toUiMessage

sealed interface TrashUiState {

    data object Loading : TrashUiState

    data object Empty : TrashUiState

    data class Items(val documents: List<Document>) : TrashUiState

    data class Failed(val error: UiError) : TrashUiState
}

/**
 * Thùng rác là soft delete: bản ghi vẫn còn (có `deletedAt`) và file vẫn còn,
 * nên khôi phục không cần chép lại file. Đây cũng là nền cho đồng bộ sau này.
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: DocumentRepository,
) : ViewModel() {

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message

    val uiState: StateFlow<TrashUiState> = repository.observeTrashedDocuments()
        .map<List<Document>, TrashUiState> { documents ->
            if (documents.isEmpty()) TrashUiState.Empty else TrashUiState.Items(documents)
        }
        .onStart { emit(TrashUiState.Loading) }
        .catch { throwable -> emit(TrashUiState.Failed(throwable.toUiError())) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TrashUiState.Loading,
        )

    fun onRestore(document: Document) {
        viewModelScope.launch {
            runCatching { repository.restoreFromTrash(document.id) }
                .onSuccess {
                    _message.value = UiMessage(R.string.trash_restored, listOf(document.title))
                }
                .onFailure { failure -> _message.value = failure.toUiMessage() }
        }
    }

    fun onDeleteForever(document: Document) {
        viewModelScope.launch {
            runCatching { repository.deleteForever(document.id) }
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
