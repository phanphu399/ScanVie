package vn.scanviet.app.ui.scan

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.scanviet.app.BuildConfig
import vn.scanviet.app.R
import vn.scanviet.app.ui.common.asString
import vn.scanviet.app.ui.common.findActivity
import vn.scanviet.app.ui.common.messageRes
import vn.scanviet.app.ui.components.EmptyState
import vn.scanviet.app.ui.components.PrimaryActionButton
import vn.scanviet.app.ui.components.SectionHeader

/**
 * Màn hình 2 — Quét tài liệu.
 *
 * Hai chặng trong cùng một màn hình:
 * 1. Mở trình quét của ML Kit (nhận diện mép giấy, cắt phối cảnh, chỉnh màu, nhiều trang).
 * 2. Xem lại danh sách trang, sắp xếp, xoá, đặt tên rồi lưu vào bộ nhớ ứng dụng.
 *
 * Màn hình KHÔNG chứa logic nghiệp vụ: mọi việc đi qua [ScanViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onDocumentSaved: (String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scanRequest by viewModel.scanRequest.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Trình quét trả kết quả về đây. Khi người dùng huỷ, resultCode != RESULT_OK.
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        viewModel.onScanResult(
            if (activityResult.resultCode == Activity.RESULT_OK) activityResult.data else null,
        )
    }

    LaunchedEffect(scanRequest) {
        val intentSender = scanRequest ?: return@LaunchedEffect
        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        viewModel.onScanRequestHandled()
    }

    // Chuỗi phải được tạo trong phạm vi @Composable: LaunchedEffect không phải nơi gọi stringResource.
    val messageText = message?.asString()

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }

    LaunchedEffect(uiState) {
        val saved = uiState as? ScanUiState.Saved ?: return@LaunchedEffect
        onDocumentSaved(saved.documentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = uiState) {
                ScanUiState.Idle -> IdleContent(
                    maxPages = viewModel.maxPagesPerScan,
                    onStartScan = {
                        context.findActivity()?.let(viewModel::onStartScan)
                    },
                )

                ScanUiState.PreparingScanner -> CenteredProgress(
                    label = stringResource(R.string.scan_preparing),
                )

                ScanUiState.ScannerUnavailable -> EmptyState(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.scan_unavailable_title),
                    message = stringResource(R.string.scan_unavailable_message),
                    actionLabel = stringResource(R.string.scan_retry),
                    onAction = viewModel::onReset,
                )

                is ScanUiState.Failed -> EmptyState(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.scan_error_title),
                    message = stringResource(state.error.messageRes()),
                    actionLabel = stringResource(R.string.scan_retry),
                    onAction = viewModel::onReset,
                )

                is ScanUiState.Reviewing -> ReviewContent(
                    state = state,
                    onTitleChange = viewModel::onTitleChange,
                    onMoveUp = { index -> viewModel.onMovePage(index, index - 1) },
                    onMoveDown = { index -> viewModel.onMovePage(index, index + 1) },
                    onRemove = viewModel::onRemovePage,
                    onSave = viewModel::onSave,
                )

                // Điều hướng đã được xử lý ở LaunchedEffect phía trên; đây chỉ là trạng thái chờ.
                is ScanUiState.Saved -> CenteredProgress(
                    label = stringResource(R.string.scan_saving),
                )
            }
        }
    }
}

@Composable
private fun IdleContent(
    maxPages: Int,
    onStartScan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.scan_intro_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.scan_intro_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryActionButton(
            text = stringResource(R.string.scan_start),
            icon = Icons.Default.Add,
            onClick = onStartScan,
        )
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(text = stringResource(R.string.scan_notes_section))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                NoteLine(text = stringResource(R.string.scan_note_pages, maxPages))
                NoteLine(text = stringResource(R.string.scan_note_offline))
                NoteLine(text = stringResource(R.string.scan_note_privacy))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Công cụ thử nghiệm: R8 loại bỏ cả nhánh này ở bản release vì điều kiện là hằng số false.
        if (BuildConfig.DEBUG) {
            DevToolsSection()
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Công cụ thử nghiệm: tạo tài liệu mẫu để kiểm tra danh sách/tìm kiếm/thùng rác trên máy
 * KHÔNG có Google Play services (khi đó trình quét không chạy được, nên không thể tạo
 * tài liệu bằng cách quét).
 *
 * Vì sao nằm cùng file với [ScanScreen] và là `private`: điều kiện gọi nó là
 * `if (BuildConfig.DEBUG)`. Ở bản release nhánh đó là hằng số false nên R8 xoá lời gọi,
 * [DevToolsSection] trở thành không thể tới, rồi tới lượt `SampleDataViewModel` và
 * `SampleDocumentFactory` cũng bị xoá khỏi APK. (Đã kiểm chứng bằng tìm chuỗi
 * "TÀI LIỆU MẪU SCANVIET" trong file dex của bản release.)
 */
@Composable
private fun DevToolsSection(
    viewModel: SampleDataViewModel = hiltViewModel(),
) {
    val sampleState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val successMessage = stringResource(R.string.dev_create_sample_success)
    val failureText = when (val state = sampleState) {
        is SampleState.Failed ->
            stringResource(R.string.error_generic) + " (" + stringResource(state.error.messageRes()) + ")"

        else -> null
    }

    LaunchedEffect(sampleState) {
        when (sampleState) {
            SampleState.Done -> {
                snackbarHostState.showSnackbar(successMessage)
                viewModel.onResultShown()
            }

            is SampleState.Failed -> {
                snackbarHostState.showSnackbar(failureText.orEmpty())
                viewModel.onResultShown()
            }

            SampleState.Idle, SampleState.Running -> Unit
        }
    }

    SectionHeader(text = stringResource(R.string.dev_section_title))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dev_section_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryActionButton(
                text = stringResource(R.string.dev_create_sample),
                icon = Icons.Default.Add,
                onClick = viewModel::onCreateSampleDocument,
                enabled = sampleState != SampleState.Running,
            )
            if (sampleState == SampleState.Running) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun NoteLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CenteredProgress(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReviewContent(
    state: ScanUiState.Reviewing,
    onTitleChange: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            enabled = !state.isSaving,
            label = { Text(text = stringResource(R.string.scan_title_label)) },
        )

        Text(
            text = stringResource(R.string.scan_pages_count, state.pages.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (state.pages.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Warning,
                title = stringResource(R.string.scan_no_pages_title),
                message = stringResource(R.string.scan_no_pages_message),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(items = state.pages, key = { _, page -> page.sourceUri }) { index, _ ->
                    PendingPageRow(
                        pageNumber = index + 1,
                        pageCount = state.pages.size,
                        enabled = !state.isSaving,
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onRemove = { onRemove(index) },
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            if (state.isSaving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.scan_saving),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            PrimaryActionButton(
                text = stringResource(R.string.action_save),
                icon = Icons.Default.Add,
                onClick = onSave,
                enabled = state.canSave,
            )
        }
    }
}

/**
 * Một trang đang chờ lưu.
 *
 * Giai đoạn 2 chưa hiển thị ảnh thu nhỏ (không dùng thư viện tải ảnh — xem AGENTS.md về
 * dependency): người dùng đã nhìn thấy ảnh trong giao diện quét của ML Kit, còn ở đây
 * chỉ cần xác nhận thứ tự và số trang.
 */
@Composable
private fun PendingPageRow(
    pageNumber: Int,
    pageCount: Int,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detail_page_label, pageNumber),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMoveUp, enabled = enabled && pageNumber > 1) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.scan_move_up),
                )
            }
            IconButton(onClick = onMoveDown, enabled = enabled && pageNumber < pageCount) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.scan_move_down),
                )
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.scan_remove_page),
                )
            }
        }
    }
}
