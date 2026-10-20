package vn.scanviet.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import android.content.Intent
import java.io.File
import vn.scanviet.app.R
import vn.scanviet.app.core.file.PdfFileNames
import vn.scanviet.app.core.format.Formatters
import vn.scanviet.app.domain.model.OcrStatus
import vn.scanviet.app.domain.model.OcrText
import vn.scanviet.app.ui.common.asString
import vn.scanviet.app.ui.common.messageRes
import vn.scanviet.app.ui.components.ConfirmDialog
import vn.scanviet.app.ui.components.EmptyState
import vn.scanviet.app.ui.components.SectionHeader

/**
 * Màn hình chi tiết tài liệu.
 *
 * Ba phần, theo thứ tự người dùng cần:
 * 1. Thông tin tài liệu (số trang, ngày, dung lượng, cảnh báo file thiếu).
 * 2. Nhận dạng chữ tiếng Việt — chạy trên máy, và kết quả được nhúng vào PDF để copy được.
 * 3. Xuất PDF và chia sẻ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val isRecognizing by viewModel.isRecognizing.collectAsStateWithLifecycle()
    val shareRequest by viewModel.shareRequest.collectAsStateWithLifecycle()
    val copyRequest by viewModel.copyRequest.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmTrash by remember { mutableStateOf(false) }
    var renameDialogVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Người dùng chọn nơi lưu PDF. Mở ở đúng thư mục Tải về để đỡ phải đi tìm.
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
    ) { outputUri ->
        if (outputUri != null) viewModel.onSaveCopy(outputUri.toString())
    }

    // Hộp thoại chia sẻ: chỉ mở khi ViewModel đã tạo xong file PDF trong vùng nháp.
    // Dùng LaunchedEffect thay vì gọi trực tiếp trong onSharePdf() vì việc tạo PDF là
    // bất đồng bộ — lúc bấm nút thì file chưa tồn tại.
    LaunchedEffect(shareRequest) {
        val file = shareRequest ?: return@LaunchedEffect
        runCatching { context.sharePdfFile(file) }
        // Dù thành công hay không, phải xoá yêu cầu: nếu không, xoay màn hình sẽ mở lại
        // hộp thoại chia sẻ một cách khó hiểu.
        viewModel.onShareHandled()
    }

    // Copy chữ vào clipboard cũng phải làm ở đây vì ViewModel không giữ Context.
    LaunchedEffect(copyRequest) {
        val text = copyRequest ?: return@LaunchedEffect
        clipboard.setText(AnnotatedString(text))
        viewModel.onCopyHandled()
        snackbarHostState.showSnackbar(context.getString(R.string.detail_ocr_copied))
    }

    val messageText = when (val current = message) {
        null -> null
        else -> current.asString()
    }

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }

    LaunchedEffect(closed) {
        if (closed) onBack()
    }

    val loaded = uiState as? DocumentDetailUiState.Loaded
    val title = loaded?.document?.title ?: stringResource(R.string.detail_title_fallback)

    // Tên file gợi ý trong hộp thoại lưu. Phải tính lại khi tên tài liệu đổi; người dùng
    // vẫn sửa được tên này trước khi lưu.
    val defaultPdfName = PdfFileNames.forTitle(loaded?.document?.title.orEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (loaded != null) {
                        IconButton(onClick = { renameDialogVisible = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_rename),
                            )
                        }
                        IconButton(onClick = { confirmTrash = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = uiState) {
                DocumentDetailUiState.Loading -> Unit

                DocumentDetailUiState.NotFound -> EmptyState(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.detail_title_fallback),
                    message = stringResource(R.string.detail_not_found),
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack,
                )

                is DocumentDetailUiState.Failed -> EmptyState(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.library_error_title),
                    message = stringResource(state.error.messageRes()),
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack,
                )

                is DocumentDetailUiState.Loaded -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SectionHeader(text = stringResource(R.string.detail_section_info))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                InfoRow(
                                    label = stringResource(R.string.detail_pages_label),
                                    value = state.document.pageCount.toString(),
                                )
                                InfoRow(
                                    label = stringResource(R.string.detail_created_label),
                                    value = Formatters.dateTime(state.document.createdAtMillis),
                                )
                                InfoRow(
                                    label = stringResource(R.string.detail_updated_label),
                                    value = Formatters.relativeTime(state.document.updatedAtMillis),
                                )
                                InfoRow(
                                    label = stringResource(R.string.detail_size_label),
                                    value = Formatters.fileSize(state.document.sizeBytes),
                                )
                            }
                        }
                    }

                    if (state.missingPageCount > 0) {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.detail_missing_pages_warning,
                                    state.missingPageCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }

                    item {
                        SectionHeader(text = stringResource(R.string.detail_section_pages))
                        PagesCard(state = state, onOpenPage = onOpenPage)
                    }

                    item {
                        SectionHeader(text = stringResource(R.string.detail_ocr_label))
                        OcrSection(
                            state = state,
                            isRecognizing = isRecognizing,
                            isExporting = isExporting,
                            onRecognize = viewModel::onRecognizeText,
                            onCopy = viewModel::onCopyText,
                        )
                    }

                    item {
                        SectionHeader(text = stringResource(R.string.detail_export_label))
                        ExportSection(
                            isExporting = isExporting,
                            isRecognizing = isRecognizing,
                            hasOcrText = state.hasOcrText,
                            ocrStatus = state.document.ocrStatus,
                            onSaveCopy = { saveLauncher.launch(defaultPdfName) },
                            onShare = viewModel::onSharePdf,
                        )
                    }
                }
            }
        }
    }

    if (confirmTrash) {
        val document = loaded?.document
        ConfirmDialog(
            title = stringResource(R.string.trash_dialog_title),
            message = stringResource(R.string.trash_dialog_message, document?.title.orEmpty()),
            confirmLabel = stringResource(R.string.action_delete),
            cancelLabel = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                confirmTrash = false
                viewModel.onMoveToTrash()
            },
            onDismiss = { confirmTrash = false },
        )
    }

    if (renameDialogVisible) {
        val document = loaded?.document
        if (document != null) {
            RenameDocumentDialog(
                initialTitle = document.title,
                onConfirm = { newTitle ->
                    viewModel.onRename(newTitle)
                    renameDialogVisible = false
                },
                onDismiss = { renameDialogVisible = false },
            )
        }
    }
}

/**
 * Danh sách trang. Mỗi trang là một nút mở màn hình xem ảnh toàn màn hình.
 *
 * Vì sao dùng `TextButton` chứ không `Card` cho từng trang: người dùng có thể đọc "Trang 7"
 * là nhãn chứ không đoán được là bấm được. Nút có mũi tên/chữ rõ ràng hơn về mặt khả dụng.
 */
@Composable
private fun PagesCard(
    state: DocumentDetailUiState.Loaded,
    onOpenPage: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            state.pages.sortedBy { it.pageIndex }.forEach { page ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.detail_page_label, page.pageIndex),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    )
                    if (page.pageIndex in state.missingPageIndices) {
                        Text(
                            text = stringResource(R.string.detail_page_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        TextButton(onClick = { onOpenPage(page.pageIndex) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.detail_view_pages))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Phần nhận dạng chữ: nút chạy nhận dạng, và chữ đã nhận dạng để đọc/copy.
 *
 * Vì sao chữ được hiển thị theo từng trang chứ không gộp một khối: người dùng đối chiếu chữ
 * với ảnh trang, nên biết "đoạn này thuộc trang nào" là thông tin cần thiết.
 */
@Composable
private fun OcrSection(
    state: DocumentDetailUiState.Loaded,
    isRecognizing: Boolean,
    isExporting: Boolean,
    onRecognize: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.detail_ocr_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(state.document.ocrStatus.ocrStatusMessageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.document.ocrStatus == OcrStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRecognize,
                    // Khoá cả khi đang xuất PDF: xuất PDF tự chạy nhận dạng, hai việc cùng
                    // chạy sẽ tranh nhau ghi kết quả vào database.
                    enabled = !isRecognizing && !isExporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (state.hasOcrText) {
                                R.string.detail_ocr_rerun
                            } else {
                                R.string.detail_ocr_run
                            },
                        ),
                    )
                }
                if (state.hasOcrText) {
                    OutlinedButton(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            // Không có `ContentCopy` trong material-icons-core 1.7.6 (đã liệt kê
                            // class trong AAR). `Create` (bút chì) là biểu tượng gần nghĩa nhất
                            // trong bộ icon sẵn có, và không tốn thêm ~10 MB cho
                            // material-icons-extended chỉ vì một biểu tượng.
                            imageVector = Icons.Default.Create,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.detail_ocr_copy))
                    }
                }
            }

            if (isRecognizing) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.detail_ocr_running_pages, state.pages.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.hasOcrText) {
                Spacer(modifier = Modifier.height(8.dp))
                OcrTextPreview(ocrText = state.ocrText)
            }
        }
    }
}

/**
 * Xem trước chữ nhận dạng, tối đa vài trang.
 *
 * Vì sao giới hạn số trang hiển thị: một tài liệu 20 trang có thể có hàng nghìn dòng chữ.
 * Dựng hết trong một `LazyColumn` lồng nhau vừa chậm vừa khó đọc. Người dùng cần copy thì
 * đã có nút copy toàn bộ; ở đây chỉ cần thấy kết quả nhận dạng có hợp lý không.
 */
@Composable
private fun OcrTextPreview(ocrText: List<OcrText>) {
    HorizontalDivider()
    ocrText.sortedBy { it.pageIndex }.take(MAX_PREVIEW_PAGES).forEach { page ->
        if (page.lines.isEmpty()) return@forEach
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.detail_ocr_page_header, page.pageIndex),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = page.fullText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = MAX_PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExportSection(
    isExporting: Boolean,
    isRecognizing: Boolean,
    hasOcrText: Boolean,
    ocrStatus: OcrStatus,
    onSaveCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.detail_export_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Nhắc rõ rằng chưa nhận dạng thì PDF sẽ không copy được chữ. Không nhắc thì
            // người dùng tưởng lỗi do ứng dụng, trong khi thực ra là do họ bỏ qua một bước.
            if (!hasOcrText && ocrStatus != OcrStatus.RUNNING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.detail_ocr_warning_pdf),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Khoá nút trong lúc tạo PDF: bấm hai lần sẽ chạy hai lần xuất, vừa chậm
                // vừa có thể ghi đè lẫn nhau. Khoá cả khi đang nhận dạng vì xuất PDF tự
                // chạy nhận dạng trước.
                OutlinedButton(
                    onClick = onSaveCopy,
                    enabled = !isExporting && !isRecognizing,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        // "Save" không có trong material-icons-core 1.7.6 (đã kiểm tra bằng
                        // cách liệt kê class trong AAR). Dùng FileDownload cũng không có; nên
                        // dùng Check cho hành động "lưu xuống" — đúng nghĩa, không tốn thêm
                        // dependency material-icons-extended (~10 MB).
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.detail_export_save))
                }
                Button(
                    onClick = onShare,
                    enabled = !isExporting && !isRecognizing,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.detail_export_share))
                }
            }
            if (isExporting) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.detail_export_running),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Chia sẻ file PDF trong vùng nháp qua FileProvider.
 *
 * `FLAG_GRANT_READ_URI_PERMISSION` là bắt buộc: không có nó, ứng dụng nhận (Zalo, Gmail)
 * mở URI sẽ bị SecurityException vì đó là `content://` riêng của ứng dụng này.
 *
 * Dùng `Intent.createChooser` để người dùng tự chọn ứng dụng nhận — không tự đoán, và
 * cũng tránh việc thiết bị không có ứng dụng nào phù hợp lại không báo gì.
 */
private fun Context.sharePdfFile(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = PDF_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(
        Intent.createChooser(intent, getString(R.string.detail_export_share_chooser)),
    )
}

/** Thông báo trạng thái nhận dạng hiển thị trong phần OCR. */
private fun OcrStatus.ocrStatusMessageRes(): Int = when (this) {
    OcrStatus.NONE -> R.string.detail_ocr_status_none
    OcrStatus.RUNNING -> R.string.detail_ocr_running
    OcrStatus.DONE -> R.string.detail_ocr_done_count
    OcrStatus.FAILED -> R.string.detail_ocr_status_failed
}

private const val PDF_MIME_TYPE = "application/pdf"
private const val MAX_PREVIEW_PAGES = 3
private const val MAX_PREVIEW_LINES = 12

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider()
}
