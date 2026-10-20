package vn.scanviet.app.ui.scan

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import vn.scanviet.app.core.format.Formatters
import vn.scanviet.app.core.time.TimeProvider
import vn.scanviet.app.domain.repository.SettingsRepository
import vn.scanviet.app.domain.usecase.ImportScannedDocumentUseCase
import vn.scanviet.app.domain.usecase.RecognizeDocumentTextUseCase
import vn.scanviet.app.platform.scanner.DocumentScannerEngine
import vn.scanviet.app.platform.scanner.ScanUnavailableException
import vn.scanviet.app.ui.common.UiMessage
import vn.scanviet.app.ui.common.toUiError
import vn.scanviet.app.ui.common.toUiMessage

/**
 * Điều khiển luồng quét: mở trình quét -> nhận kết quả -> xem lại -> lưu.
 *
 * Quy tắc riêng tư: ViewModel không giữ `Context`/`Activity`, không ghi log, và không bao
 * giờ đưa URI hay tên tài liệu vào thông báo lỗi.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scannerEngine: DocumentScannerEngine,
    private val importScannedDocument: ImportScannedDocumentUseCase,
    private val recognizeDocumentText: RecognizeDocumentTextUseCase,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState

    private val _message = MutableStateFlow<UiMessage?>(null)

    /** Thông báo cho snackbar; màn hình gọi [onMessageShown] sau khi hiển thị. */
    val message: StateFlow<UiMessage?> = _message

    /**
     * Yêu cầu mở trình quét. Màn hình đọc giá trị này rồi gọi [onScanRequestHandled]
     * ngay, nên khi xoay màn hình trình quét không tự bật lại.
     */
    private val _scanRequest = MutableStateFlow<IntentSender?>(null)
    val scanRequest: StateFlow<IntentSender?> = _scanRequest

    /** Số trang tối đa một lần quét, để màn hình nói trước cho người dùng. */
    val maxPagesPerScan: Int get() = scannerEngine.maxPagesPerScan

    /** Người dùng bấm "Quét tài liệu". Cần [activity] vì trình quét là Activity của Play services. */
    fun onStartScan(activity: Activity) {
        if (_uiState.value == ScanUiState.PreparingScanner) return
        _uiState.value = ScanUiState.PreparingScanner
        viewModelScope.launch {
            runCatching { scannerEngine.createStartIntent(activity) }
                .onSuccess { intentSender ->
                    _scanRequest.value = intentSender
                    // Quay lại trạng thái chờ: người dùng có thể huỷ ở giao diện quét.
                    _uiState.value = ScanUiState.Idle
                }
                .onFailure { failure ->
                    _uiState.value = when (failure) {
                        is ScanUnavailableException -> ScanUiState.ScannerUnavailable
                        else -> ScanUiState.Failed(failure.toUiError())
                    }
                }
        }
    }

    fun onScanRequestHandled() {
        _scanRequest.value = null
    }

    /** Kết quả từ trình quét. `null` nghĩa là người dùng đã huỷ. */
    fun onScanResult(resultIntent: Intent?) {
        val scanned = scannerEngine.parseResult(resultIntent)
        _uiState.value = if (scanned == null) {
            ScanUiState.Idle
        } else {
            ScanUiState.Reviewing(
                title = defaultTitle(),
                pages = scanned.pageUris.map { uri -> PendingPage(sourceUri = uri) },
            )
        }
    }

    fun onTitleChange(newTitle: String) {
        val current = _uiState.value
        if (current is ScanUiState.Reviewing) {
            _uiState.value = current.copy(title = newTitle)
        }
    }

    /** Chuyển một trang lên/xuống. Bỏ qua nếu vị trí đích nằm ngoài danh sách. */
    fun onMovePage(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value
        if (current !is ScanUiState.Reviewing) return
        val pages = current.pages
        if (fromIndex !in pages.indices || toIndex !in pages.indices) return

        val mutable = pages.toMutableList()
        mutable.add(toIndex, mutable.removeAt(fromIndex))
        _uiState.value = current.copy(pages = mutable.toList())
    }

    fun onRemovePage(index: Int) {
        val current = _uiState.value
        if (current !is ScanUiState.Reviewing) return
        if (index !in current.pages.indices) return
        _uiState.value = current.copy(pages = current.pages.filterIndexed { i, _ -> i != index })
    }

    /** Người dùng xác nhận lưu: sao chép ảnh vào vùng riêng của app rồi ghi Room. */
    fun onSave() {
        val current = _uiState.value as? ScanUiState.Reviewing ?: return
        if (current.isSaving) return

        _uiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            runCatching {
                importScannedDocument(
                    title = current.title,
                    sourceUris = current.pages.map { page -> page.sourceUri },
                )
            }
                .onSuccess { document ->
                    _uiState.value = ScanUiState.Saved(document.id)
                    // Nhận dạng chữ sau khi lưu xong, nếu người dùng bật "Tự động nhận dạng".
                    recognizeIfAuto(document.id)
                }
                .onFailure { failure ->
                    // Giữ nguyên danh sách trang để người dùng thử lưu lại, không mất công quét lại.
                    _uiState.value = current.copy(isSaving = false)
                    _message.value = failure.toUiMessage()
                }
        }
    }

    /**
     * Chạy nhận dạng chữ ngay sau khi lưu, nếu cài đặt bật.
     *
     * **Ba quyết định quan trọng:**
     *
     * 1. **Tài liệu đã lưu xong rồi mới chạy.** Nhận dạng là bước *thêm giá trị*, không phải
     *    điều kiện để lưu thành công. Nếu nó thất bại, người dùng **vẫn có tài liệu** — mất
     *    chữ còn hơn mất cả tài liệu vừa quét công phu.
     *
     * 2. **Lỗi nhận dạng KHÔNG đưa người dùng về màn hình xem lại.** Trạng thái vẫn là
     *    `Saved`, tức luồng quét đã kết thúc thành công. Chỉ hiện snackbar báo không nhận
     *    dạng được. Đưa về màn hình xem lại sẽ khiến người dùng tưởng việc lưu đã thất bại
     *    và bấm "Lưu" lần nữa — tạo ra tài liệu trùng.
     *
     * 3. **`recognizeDocumentText` đã tự bỏ qua trang có chữ rồi**, nên gọi lại là an toàn.
     *
     * Đọc cài đặt ở đây (chứ không truyền từ màn hình) để giá trị luôn là giá trị mới nhất,
     * kể cả khi người dùng vừa đổi trong lúc màn hình quét còn mở.
     */
    private suspend fun recognizeIfAuto(documentId: String) {
        val autoOcr = runCatching { settingsRepository.settings.first().autoOcr }.getOrDefault(false)
        if (!autoOcr) return

        val failure = runCatching { recognizeDocumentText(documentId) }.exceptionOrNull()
        // Chỉ báo khi thật sự có lỗi. Thành công thì để người dùng tự thấy chữ trong màn hình
        // chi tiết — một snackbar "đã nhận dạng" cho mỗi lần lưu sẽ gây nhiễu.
        if (failure != null) {
            _message.value = failure.toUiMessage()
        }
    }

    /** Quay về trạng thái chờ, ví dụ sau khi báo lỗi hoặc khi máy không hỗ trợ quét. */
    fun onReset() {
        _uiState.value = ScanUiState.Idle
    }

    fun onMessageShown() {
        _message.value = null
    }

    /**
     * Tên mặc định để người dùng không phải nhập gì mà vẫn phân biệt được các tài liệu.
     * Đây là tên bản ghi trong Room, KHÔNG dùng làm tên file.
     */
    private fun defaultTitle(): String = "Tài liệu " + Formatters.dateTime(timeProvider.now())
}
