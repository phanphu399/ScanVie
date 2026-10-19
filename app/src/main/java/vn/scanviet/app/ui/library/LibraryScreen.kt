package vn.scanviet.app.ui.library

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.scanviet.app.R
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.ui.common.asString
import vn.scanviet.app.ui.common.messageRes
import vn.scanviet.app.ui.components.ConfirmDialog
import vn.scanviet.app.ui.components.EmptyState
import vn.scanviet.app.ui.components.PrimaryActionButton

/**
 * Màn hình 1 — Trang chủ: nút quét tài liệu, ô tìm kiếm, danh sách tài liệu đã lưu.
 * Không chứa logic dữ liệu: mọi thao tác đi qua [LibraryViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onScanClick: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var documentToRename by remember { mutableStateOf<Document?>(null) }
    var documentToTrash by remember { mutableStateOf<Document?>(null) }

    // Chuỗi phải được tạo trong phạm vi @Composable: LaunchedEffect không phải nơi gọi stringResource.
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_open_trash),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            PrimaryActionButton(
                text = stringResource(R.string.library_scan_action),
                icon = Icons.Default.Add,
                onClick = onScanClick,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            SearchField(query = query, onQueryChange = viewModel::onQueryChange)

            Spacer(modifier = Modifier.height(8.dp))

            when (val state = uiState) {
                LibraryUiState.Loading -> LoadingContent()

                is LibraryUiState.Documents -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.documents, key = { it.id }) { document ->
                        DocumentRow(
                            document = document,
                            onOpen = { onOpenDocument(document.id) },
                            onRename = { documentToRename = document },
                            onDelete = { documentToTrash = document },
                        )
                    }
                }

                LibraryUiState.NoResult -> {
                    val searching = query.isNotBlank()
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = stringResource(
                            if (searching) {
                                R.string.library_no_result_title
                            } else {
                                R.string.library_empty_title
                            },
                        ),
                        message = stringResource(
                            if (searching) {
                                R.string.library_no_result_message
                            } else {
                                R.string.library_empty_message
                            },
                        ),
                    )
                }

                is LibraryUiState.Failed -> EmptyState(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.library_error_title),
                    message = stringResource(state.error.messageRes()),
                    actionLabel = stringResource(R.string.library_retry),
                    onAction = viewModel::onRetry,
                )
            }
        }
    }

    documentToRename?.let { document ->
        RenameDocumentDialog(
            initialTitle = document.title,
            onConfirm = { newTitle ->
                viewModel.onRename(document.id, newTitle)
                documentToRename = null
            },
            onDismiss = { documentToRename = null },
        )
    }

    documentToTrash?.let { document ->
        ConfirmDialog(
            title = stringResource(R.string.trash_dialog_title),
            message = stringResource(R.string.trash_dialog_message, document.title),
            confirmLabel = stringResource(R.string.action_delete),
            cancelLabel = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                viewModel.onMoveToTrash(document.id)
                documentToTrash = null
            },
            onDismiss = { documentToTrash = null },
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(text = stringResource(R.string.library_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.cd_clear_search),
                    )
                }
            }
        },
    )
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.library_loading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
