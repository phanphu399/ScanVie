package vn.scanviet.app.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import vn.scanviet.app.domain.image.PageImageLoader
import vn.scanviet.app.domain.model.DocumentPage
import vn.scanviet.app.domain.model.OcrText
import vn.scanviet.app.ui.common.asString
import vn.scanviet.app.ui.common.messageRes
import vn.scanviet.app.ui.common.rememberPageImage
import vn.scanviet.app.ui.components.ConfirmDialog
import vn.scanviet.app.ui.components.EmptyState
import vn.scanviet.app.ui.components.EmptyStateTone
import vn.scanviet.app.ui.components.SectionHeader


/**
 * Màn hình chi tiết tài liệu.
 *
 * Ba phần, theo thứ tự người dùng cần:
 * 1. Một thẻ tóm tắt: số trang, ba nút chính (Xem, Lưu PDF, Chia sẻ) và trạng thái nhận dạng.
 * 2. Danh sách trang có ảnh thu nhỏ.
 * 3. Thông tin chi tiết (ngày, dung lượng, cảnh báo file thiếu).
 *
 * **Nhận dạng chữ chạy NGẦM, không có nút riêng.** Người dùng không phải biết "OCR" là gì để
 * có một PDF tìm kiếm được. Kết quả nhận dạng vẫn hiển thị (để đọc và copy), nhưng việc chạy
 * nó do ứng dụng tự quyết định — xem [DocumentDetailViewModel.autoRecognize].
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

    // Nhận dạng chữ tự chạy khi mở tài liệu còn thiếu chữ. Đặt ở đây (chứ không trong ViewModel
    // init) để mỗi lần mở lại tài liệu — kể cả khi lần trước thất bại — đều được thử lại.
    LaunchedEffect(viewModel.documentId) {
        viewModel.autoRecognize()
    }

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
                    tone = EmptyStateTone.ERROR,
                )

                is DocumentDetailUiState.Failed -> EmptyState(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.library_error_title),
                    message = stringResource(state.error.messageRes()),
                    actionLabel = stringResource(R.string.action_back),
                    onAction = onBack,
                    tone = EmptyStateTone.ERROR,
                )

                is DocumentDetailUiState.Loaded -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        // Gộp phần "làm được gì" lên đầu: người dùng mở tài liệu là để XEM hoặc
                        // LẤY PDF, không phải để đọc metadata. Metadata bị đẩy xuống cuối.
                        SummaryCard(
                            state = state,
                            isRecognizing = isRecognizing,
                            isExporting = isExporting,
                            onOpenPages = { onOpenPage(1) },
                            onSaveCopy = { saveLauncher.launch(defaultPdfName) },
                            onShare = viewModel::onSharePdf,
                        )
                    }

                    item {
                        SectionHeader(
                            text = stringResource(R.string.detail_section_pages),
                            leadingIcon = Icons.AutoMirrored.Filled.List,
                        )
                        Text(
                            text = stringResource(R.string.detail_pages_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                        PagesCard(
                            state = state,
                            onOpenPage = onOpenPage,
                            imageLoader = viewModel.thumbnailLoader,
                        )
                    }

                    if (isRecognizing) {
                        item { RecognizingCard(pageCount = state.pages.size) }
                    }

                    // Lần nhận dạng trước thất bại: hiện lý do + nút thử lại.
                    //
                    // Vì sao cần thẻ riêng thay vì chỉ dựa vào chip trạng thái: chip trong
                    // danh sách chỉ nói "Lỗi nhận dạng" (một từ), không nói PHẢI LÀM GÌ. Ở
                    // đây có cả nguyên nhân lẫn hành động — và đây cũng là chỗ duy nhất người
                    // dùng thử lại được, vì `autoRecognize` đã cố ý không tự chạy lại nữa.
                    if (state.ocrFailed && !isRecognizing) {
                        item {
                            OcrFailedCard(onRetry = viewModel::onRetryRecognize)
                        }
                    }

                    if (state.hasOcrText) {
                        item {
                            SectionHeader(
                                text = stringResource(R.string.detail_section_text),
                                leadingIcon = Icons.Default.Create,
                            )
                            OcrTextCard(
                                ocrText = state.ocrText,
                                onCopy = viewModel::onCopyText,
                            )
                        }
                    }

                    item {
                        SectionHeader(
                            text = stringResource(R.string.detail_section_info),
                            leadingIcon = Icons.Default.Info,
                        )
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
 * Danh sách trang, mỗi trang có ẢNH THU NHỎ bấm được để mở màn hình xem toàn màn hình.
 *
 * **Vì sao phải có ảnh thu nhỏ:** trước đây mỗi dòng chỉ là chữ "Trang 1", "Trang 2".
 * Với người dùng, đó là danh sách vô nghĩa — họ không phân biệt được trang nào là
 * trang nào cho tới khi bấm vào từng trang. Ảnh thu nhỏ biến danh sách thành thứ
 * nhìn là hiểu, và cũng là cách kiểm tra nhanh xem trình quét có cắt sai trang nào
 * không. Đây là khác biệt lớn nhất về mặt thị giác của màn hình này.
 *
 * Dùng `Row` bấm được (không phải `Card` lồng `Card`) để cả dòng là một mục tiêu bấm,
 * thay vì chỉ có nút nhỏ ở cuối.
 *
 * `imageLoader` được truyền vào (không tạo mới) để dùng chung bộ nhớ đệm ảnh với màn
 * hình thư viện — nếu tạo riêng, mỗi lần mở tài liệu lại giải mã ảnh từ đĩa.
 */
@Composable
private fun PagesCard(
    state: DocumentDetailUiState.Loaded,
    onOpenPage: (Int) -> Unit,
    imageLoader: PageImageLoader,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            state.pages.sortedBy { it.pageIndex }.forEach { page ->
                PageRow(
                    page = page,
                    isMissing = page.pageIndex in state.missingPageIndices,
                    onOpenPage = onOpenPage,
                    imageLoader = imageLoader,
                )
            }
        }
    }
}

@Composable
private fun PageRow(
    page: DocumentPage,
    isMissing: Boolean,
    onOpenPage: (Int) -> Unit,
    imageLoader: PageImageLoader,
) {
    val thumbnail = rememberPageImage(
        relativePath = if (isMissing) null else page.imageFile,
        loader = imageLoader,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isMissing) { onOpenPage(page.pageIndex) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ô ảnh cố định tỉ lệ 3:4 như trang giấy, giống hệt dòng trong màn hình thư viện
        // -> hai màn hình trông như cùng một ứng dụng.
        Box(
            modifier = Modifier
                .size(width = PAGE_THUMB_WIDTH, height = PAGE_THUMB_HEIGHT)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = stringResource(R.string.detail_page_label, page.pageIndex),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )

        if (isMissing) {
            Text(
                text = stringResource(R.string.detail_page_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.detail_open_page),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Thẻ tóm tắt: nói ngay tài liệu này có gì, và ba việc chính làm được với nó.
 *
 * **Vì sao gộp ba nút vào một thẻ thay vì ba mục riêng:** trước đây "Nhận dạng chữ" và "Xuất
 * PDF" là hai mục ngang hàng, khiến người dùng phải hiểu hai khái niệm kỹ thuật trước khi làm
 * được việc đơn giản nhất là lấy file PDF. Ở đây chỉ còn MỘT hàng nút, và trạng thái nhận dạng
 * là một dòng chú thích nhỏ — thông tin vẫn đủ, nhưng không còn chắn đường.
 *
 * **Vì sao nút "Lưu PDF" luôn bấm được:** nhận dạng chữ chạy ngầm và tự động. Nếu để nút bị
 * khoá trong lúc đó, người dùng tưởng ứng dụng treo. Khoá chỉ khi đang xuất, để tránh bấm hai
 * lần ghi hai file.
 */
@Composable
private fun SummaryCard(
    state: DocumentDetailUiState.Loaded,
    isRecognizing: Boolean,
    isExporting: Boolean,
    onOpenPages: () -> Unit,
    onSaveCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.document.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.detail_summary_line,
                            state.document.pageCount,
                            Formatters.fileSize(state.document.sizeBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OcrStatusChip(status = state.document.ocrStatus)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Nút "Lưu PDF" là hành động chính của màn hình: nó chiếm cả hàng trên, hai nút
                // phụ nằm dưới. Đây là thứ tự ưu tiên thật của người dùng.
                Button(
                    onClick = onSaveCopy,
                    enabled = !isExporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        // "Save" không có trong material-icons-core 1.7.6 (đã kiểm tra bằng
                        // cách liệt kê class trong AAR). Dùng FileDownload cũng không có; nên
                        // dùng Check cho hành động "lưu xuống" — đúng nghĩa, không tốn thêm
                        // dependency material-icons-extended (~10 MB).
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.detail_export_save))
                }
                OutlinedButton(
                    onClick = onShare,
                    enabled = !isExporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.detail_export_share))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenPages,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.detail_open_viewer))
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
            } else if (isRecognizing) {
                // Nhận dạng chạy ngầm: chỉ một dòng nhỏ, không phải một khối chắn giữa màn hình.
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.detail_recognizing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Trạng thái "đang nhận dạng chữ" khi chưa có kết quả nào để hiển thị. */
@Composable
private fun RecognizingCard(pageCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.detail_recognizing),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.detail_ocr_running_pages, pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Lần nhận dạng trước thất bại: nói rõ chuyện gì xảy ra và cho người dùng thử lại.
 *
 * Vì sao KHÔNG tự chạy lại: xem [DocumentDetailUiState.Loaded.ocrFailed] — cùng ảnh, cùng
 * engine thì kết quả y hệt, nên tự chạy lại chỉ tốn thời gian. Người dùng phải chủ động bấm
 * sau khi đã sửa nguyên nhân (đổi engine trong Cài đặt, giải phóng dung lượng, quét lại trang).
 *
 * Vì sao dùng màu cảnh báo chứ không phải màu lỗi: "ảnh mờ, không tìm thấy chữ" là chuyện
 * bình thường khi quét tài liệu, không phải ứng dụng hỏng. Màu lỗi đỏ sẽ khiến người dùng
 * nghĩ ứng dụng có vấn đề và gỡ đi.
 */
@Composable
private fun OcrFailedCard(onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_ocr_failed_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.detail_ocr_status_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.clickable(onClick = onRetry),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.detail_ocr_retry))
            }
        }
    }
}

/**
 * Chữ đã nhận dạng để đọc và copy.
 *
 * Vì sao vẫn hiển thị dù nhận dạng là ngầm: người dùng cần KIỂM TRA chất lượng trước khi tin
 * một tài liệu quan trọng. Copy từng phần cũng hữu ích hơn copy cả tài liệu.
 *
 * Vì sao giới hạn số trang hiển thị: một tài liệu 20 trang có thể có hàng nghìn dòng chữ.
 * Dựng hết trong một `LazyColumn` lồng nhau vừa chậm vừa khó đọc. Người dùng cần trọn bộ thì
 * đã có nút copy toàn bộ.
 */
@Composable
private fun OcrTextCard(ocrText: List<OcrText>, onCopy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.detail_ocr_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onCopy) {
                Icon(
                    // `material-icons-core` 1.7.6 chỉ có 50 biểu tượng và KHÔNG có
                    // `ContentCopy` (đã liệt kê class trong AAR để kiểm chứng). Dùng `Create`
                    // (bút chì) từng gây nhầm vì màn hình này cũng dùng `Edit` (cũng là bút chì)
                    // cho "Đổi tên" — hai nút khác nghĩa, một hình. Chọn `Check` cho copy: đọc
                    // là "lấy xong", và không đụng biểu tượng nào khác trên màn hình.
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.detail_ocr_copy))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            ocrText.sortedBy { it.pageIndex }.take(MAX_PREVIEW_PAGES).forEach { page ->
                if (page.lines.isEmpty()) return@forEach
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
                Spacer(modifier = Modifier.height(12.dp))
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

private const val PDF_MIME_TYPE = "application/pdf"
private const val MAX_PREVIEW_PAGES = 3
private const val MAX_PREVIEW_LINES = 12

/** Ô ảnh thu nhỏ của một trang, tỉ lệ ~3:4 như trang giấy. Khớp với màn hình thư viện. */
private val PAGE_THUMB_WIDTH = 44.dp
private val PAGE_THUMB_HEIGHT = 58.dp

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
