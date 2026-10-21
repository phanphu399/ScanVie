package vn.scanviet.app.ui.viewer

import androidx.lifecycle.SavedStateHandle
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
import vn.scanviet.app.domain.image.PageImageLoader
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.navigation.ScanVietRoutes
import vn.scanviet.app.ui.common.toUiError

/**
 * ViewModel màn hình xem ảnh trang.
 *
 * Vì sao không tải ảnh trong ViewModel: ảnh được nạp theo từng trang ngay trên màn hình qua
 * [PageImageLoader], nơi có bộ nhớ đệm. ViewModel chỉ chuẩn bị DANH SÁCH trang và trạng thái
 * thiếu ảnh — việc giải mã ảnh thuộc về tầng ui, đúng ranh giới kiến trúc.
 */
@HiltViewModel
class DocumentViewerViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val storage: DocumentFileStorage,
    savedStateHandle: SavedStateHandle,
    /** Chỉ để màn hình nạp ảnh; ViewModel không tự dùng. */
    val imageLoader: PageImageLoader,
) : ViewModel() {

    val documentId: String = checkNotNull(savedStateHandle[ScanVietRoutes.ARG_DOCUMENT_ID])

    /** Trang người dùng đã bấm để mở, đánh số từ 1. `null` nghĩa là mở từ trang đầu. */
    val requestedPage: Int? = savedStateHandle[ScanVietRoutes.ARG_PAGE_INDEX]

    val uiState: StateFlow<DocumentViewerUiState> =
        repository.observePages(documentId)
            .map { pages ->
                val sorted = pages.sortedBy { it.pageIndex }
                if (sorted.isEmpty()) {
                    DocumentViewerUiState.NotFound
                } else {
                    DocumentViewerUiState.Loaded(
                        documentId = documentId,
                        pages = sorted.map { page ->
                            ViewerPage(
                                pageIndex = page.pageIndex,
                                relativePath = page.imageFile,
                                isMissing = !storage.exists(page.imageFile),
                            )
                        },
                        startPage = (requestedPage ?: sorted.first().pageIndex).coerceToPage(sorted),
                    )
                }
            }
            .onStart { emit(DocumentViewerUiState.Loading) }
            .catch { throwable -> emit(DocumentViewerUiState.Failed(throwable.toUiError())) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DocumentViewerUiState.Loading,
            )

    /**
     * Copy chữ nhận dạng của MỘT trang, để người dùng lấy nhanh vài dòng từ PDF mà không phải
     * mở trình đọc PDF.
     *
     * Vì sao copy theo TRANG đang xem chứ không copy cả tài liệu: người dùng đang nhìn một trang
     * cụ thể, và nhu cầu thường là lấy đoạn chữ của chính trang đó. Copy cả tài liệu vẫn có ở màn
     * hình chi tiết cho ai cần.
     *
     * @return chữ của trang, hoặc `null` nếu trang chưa có kết quả nhận dạng.
     */
    fun pageText(pageIndex: Int): String? {
        val text = ocrPages.value
            .firstOrNull { it.pageIndex == pageIndex }
            ?.fullText
            ?.trim()
        return text?.takeIf { it.isNotEmpty() }
    }

    /** Chữ đã nhận dạng của mọi trang, dùng để tra theo trang đang xem. */
    private val ocrPages = repository.observeOcrText(documentId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    /** Yêu cầu UI copy một đoạn chữ; UI copy xong phải gọi [onCopyHandled]. */
    private val _copyRequest = MutableStateFlow<String?>(null)
    val copyRequest: StateFlow<String?> = _copyRequest

    /** Người dùng bấm "Copy chữ trang này"; trả `false` nếu trang chưa có chữ để copy. */
    fun onCopyPageText(pageIndex: Int): Boolean {
        val text = pageText(pageIndex) ?: return false
        _copyRequest.value = text
        return true
    }

    fun onCopyHandled() {
        _copyRequest.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Kẹp số trang yêu cầu vào khoảng trang có thật.
 *
 * Vì sao cần: số trang có thể đến từ tham số điều hướng, và tài liệu có thể đã bị sửa (xoá
 * bớt trang) giữa lúc màn hình chi tiết hiển thị và lúc người dùng bấm. Không kẹp lại thì
 * pager sẽ mở ở vị trí ngoài danh sách và hiện trang trắng.
 */
private fun Int.coerceToPage(pages: List<vn.scanviet.app.domain.model.DocumentPage>): Int {
    val indices = pages.map { it.pageIndex }
    return when {
        this in indices -> this
        this < indices.first() -> indices.first()
        else -> indices.last()
    }
}
