package vn.scanviet.app.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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
import vn.scanviet.app.R
import vn.scanviet.app.core.file.PdfFileNames
import vn.scanviet.app.domain.export.PdfOutputTarget
import vn.scanviet.app.domain.image.PageImageLoader
import vn.scanviet.app.domain.repository.DocumentRepository
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.domain.usecase.ExportDocumentAsPdfUseCase
import vn.scanviet.app.domain.usecase.RecognizeDocumentTextUseCase
import vn.scanviet.app.domain.usecase.RenameDocumentUseCase
import vn.scanviet.app.navigation.ScanVietRoutes
import vn.scanviet.app.ui.common.UiMessage
import vn.scanviet.app.ui.common.toUiError
import vn.scanviet.app.ui.common.toUiMessage

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val renameDocument: RenameDocumentUseCase,
    private val exportDocument: ExportDocumentAsPdfUseCase,
    private val recognizeText: RecognizeDocumentTextUseCase,
    private val storage: DocumentFileStorage,
    savedStateHandle: SavedStateHandle,
    /** Chỉ để màn hình nạp ảnh thu nhỏ; ViewModel không tự dùng. */
    val thumbnailLoader: PageImageLoader,
) : ViewModel() {

    val documentId: String = checkNotNull(savedStateHandle[ScanVietRoutes.ARG_DOCUMENT_ID])

    private val _message = MutableStateFlow<UiMessage?>(null)
    private val _closed = MutableStateFlow(false)
    private val _isExporting = MutableStateFlow(false)
    private val _isRecognizing = MutableStateFlow(false)

    /** File PDF trong vùng nháp đang chờ chia sẻ. UI chia sẻ xong phải gọi [onShareHandled]. */
    private val _shareRequest = MutableStateFlow<File?>(null)

    /** Chữ của tài liệu, chờ UI đưa vào clipboard. UI copy xong phải gọi [onCopyHandled]. */
    private val _copyRequest = MutableStateFlow<String?>(null)

    val message: StateFlow<UiMessage?> = _message

    /** true khi tài liệu đã bị chuyển vào thùng rác: màn hình tự quay lại danh sách. */
    val closed: StateFlow<Boolean> = _closed

    /** true khi đang tạo PDF: nút xuất bị khoá để tránh bấm hai lần. */
    val isExporting: StateFlow<Boolean> = _isExporting

    /** true khi đang nhận dạng chữ: nút nhận dạng bị khoá. */
    val isRecognizing: StateFlow<Boolean> = _isRecognizing

    val shareRequest: StateFlow<File?> = _shareRequest

    val copyRequest: StateFlow<String?> = _copyRequest

    val uiState: StateFlow<DocumentDetailUiState> = combine(
        repository.observeDocument(documentId),
        repository.observePages(documentId),
        repository.observeOcrText(documentId),
    ) { document, pages, ocrText -> Triple(document, pages, ocrText) }
        .map { (document, pages, ocrText) ->
            if (document == null) {
                DocumentDetailUiState.NotFound
            } else {
                // Gọi một lần rồi suy ra số lượng: hai lời gọi sẽ quét đĩa hai lượt cho cùng
                // một câu trả lời.
                val missingPages = repository.missingPageIndices(documentId)
                DocumentDetailUiState.Loaded(
                    document = document,
                    pages = pages,
                    missingPageCount = missingPages.size,
                    missingPageIndices = missingPages,
                    ocrText = ocrText,
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

    /**
     * Nhận dạng chữ tự động, chạy khi màn hình chi tiết được mở.
     *
     * **Vì sao bỏ nút "Nhận dạng chữ":** người dùng cuối không biết "OCR" là gì, và yêu cầu của
     * họ là "PDF copy được chữ" — không phải "chạy nhận dạng". Bắt họ bấm một nút kỹ thuật để
     * có được kết quả đúng là thiết kế tồi. Việc nhận dạng giờ là chi tiết cài đặt.
     *
     * **Vì sao chỉ chạy khi tài liệu còn THIẾU chữ:** nhận dạng lại một tài liệu đã xong là
     * lãng phí hàng chục giây. [RecognizeDocumentTextUseCase] cũng đã bỏ qua trang có chữ, nên
     * đây là lớp chặn thứ hai ở phía UI để tránh cả việc hiện vòng xoay vô ích.
     *
     * **Vì sao lỗi không hiện thông báo:** nhận dạng là việc ngầm. Báo lỗi cho một việc người
     * dùng không yêu cầu làm họ tưởng ứng dụng hỏng. Nếu họ cần lớp chữ chuẩn, đường xuất PDF
     * ([ensureTextLayer]) vẫn chạy lại và báo lỗi đúng lúc — khi họ thực sự cần kết quả.
     */
    fun autoRecognize() {
        val loaded = uiState.value as? DocumentDetailUiState.Loaded ?: return
        if (loaded.hasOcrText) return
        if (!beginRecognize()) return
        viewModelScope.launch {
            runCatching { recognizeText(documentId) }
            _isRecognizing.value = false
        }
    }

    fun onMoveToTrash() {
        viewModelScope.launch {
            runCatching { repository.moveToTrash(documentId) }
                .onSuccess { _closed.value = true }
                .onFailure { failure -> _message.value = failure.toUiMessage() }
        }
    }

    /**
     * Lưu PDF vào nơi người dùng chọn qua Storage Access Framework.
     *
     * Điểm dễ bỏ sót: SAF đã tạo file rỗng trước khi ta ghi, nên nếu tạo PDF thất bại thì
     * phải xoá file đó đi. Để lại một file 0 byte trong thư mục người dùng còn tệ hơn một
     * thông báo lỗi — người dùng sẽ tưởng mình đã lưu thành công.
     */
    fun onSaveCopy(outputUri: String) {
        if (!beginExport()) return
        viewModelScope.launch {
            runCatching {
                ensureTextLayer()
                exportDocument(documentId, PdfOutputTarget.SafUri(outputUri))
            }
                .onSuccess { _message.value = UiMessage(R.string.detail_export_saved) }
                .onFailure { failure -> _message.value = failure.toUiMessage() }
            _isExporting.value = false
        }
    }

    /**
     * Tạo PDF trong vùng nháp rồi yêu cầu UI mở hộp thoại chia sẻ.
     *
     * Vì sao phải ghi ra file trước: hộp thoại chia sẻ cần một URI có sẵn để ứng dụng nhận
     * đọc; không có cách nào vừa hỏi người dùng chọn ứng dụng vừa ghi trực tiếp sang nó.
     */
    fun onSharePdf() {
        if (!beginExport()) return
        viewModelScope.launch {
            val title = (uiState.value as? DocumentDetailUiState.Loaded)?.document?.title.orEmpty()
            runCatching {
                ensureTextLayer()
                val file = storage.createScratchFile(PdfFileNames.forTitle(title))
                exportDocument(documentId, PdfOutputTarget.LocalFile(file))
                file
            }
                .onSuccess { file -> _shareRequest.value = file }
                .onFailure { failure -> _message.value = failure.toUiMessage() }
            _isExporting.value = false
        }
    }

    /**
     * Đảm bảo PDF có lớp chữ trước khi xuất.
     *
     * Vì sao chạy OCR tự động ở đây thay vì bắt người dùng tự bấm: mục đích chính của tính
     * năng nhận dạng là để PDF copy được chữ. Xuất ra một PDF không copy được vì người dùng
     * quên bấm là trải nghiệm tệ, và với tài liệu vài trang thì chờ thêm vài giây là chấp
     * nhận được.
     *
     * Vì sao lỗi nhận dạng KHÔNG làm hỏng việc xuất: người dùng có quyền chỉ muốn lưu ảnh
     * scan. `runCatching` nuốt lỗi OCR và PDF vẫn ra đời, chỉ thiếu lớp chữ — im lặng hơn
     * nhưng không tước đi thứ người dùng đang muốn làm.
     */
    private suspend fun ensureTextLayer() {
        val loaded = uiState.value as? DocumentDetailUiState.Loaded ?: return
        if (loaded.hasOcrText) return
        if (!beginRecognize()) return
        try {
            // Lỗi nhận dạng KHÔNG được làm hỏng việc xuất: người dùng có quyền chỉ muốn lưu ảnh
            // scan. `runCatching` nuốt lỗi OCR và PDF vẫn ra đời, chỉ thiếu lớp chữ.
            runCatching { recognizeText(documentId) }
        } finally {
            _isRecognizing.value = false
        }
    }

    /**
     * Giành quyền xuất, trả về `false` nếu đang có lần xuất khác chạy.
     *
     * Vì sao phải đặt cờ TRƯỚC `launch`: nếu đặt bên trong khối `launch`, hai lần bấm liên
     * tiếp sẽ đều thấy cờ còn `false` (coroutine chưa kịp chạy), và cả hai đều khởi động —
     * hai luồng cùng ghi một file PDF, kết quả hỏng. Hàm này giữ việc kiểm tra và đặt cờ
     * trong cùng một bước đồng bộ, đúng như một khoá.
     */
    private fun beginExport(): Boolean {
        if (_isExporting.value) return false
        _isExporting.value = true
        return true
    }

    /** Cùng lý do như [beginExport], cho thao tác nhận dạng chữ. */
    private fun beginRecognize(): Boolean {
        if (_isRecognizing.value) return false
        _isRecognizing.value = true
        return true
    }

    /** Người dùng bấm copy toàn bộ chữ; màn hình đưa nội dung vào clipboard rồi gọi [onCopyHandled]. */
    fun onCopyText() {
        val loaded = uiState.value as? DocumentDetailUiState.Loaded ?: return
        val text = loaded.ocrText
            .sortedBy { it.pageIndex }
            .joinToString(separator = "\n\n") { it.fullText }
            .trim()
        if (text.isEmpty()) return
        _copyRequest.value = text
    }

    /** UI đã đưa chữ vào clipboard; xoá yêu cầu để không copy lại khi xoay màn hình. */
    fun onCopyHandled() {
        _copyRequest.value = null
    }

    /** UI đã mở hộp thoại chia sẻ; xoá yêu cầu để không mở lại khi xoay màn hình. */
    fun onShareHandled() {
        _shareRequest.value = null
    }

    fun onMessageShown() {
        _message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
