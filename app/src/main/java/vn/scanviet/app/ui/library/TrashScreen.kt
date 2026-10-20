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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.scanviet.app.R
import vn.scanviet.app.core.format.Formatters
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.ui.common.asString
import vn.scanviet.app.ui.common.messageRes
import vn.scanviet.app.ui.components.ConfirmDialog
import vn.scanviet.app.ui.components.EmptyState
import vn.scanviet.app.ui.components.EmptyStateTone
import vn.scanviet.app.ui.components.LoadingContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var documentToDeleteForever by remember { mutableStateOf<Document?>(null) }

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
                title = { Text(text = stringResource(R.string.trash_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when (val state = uiState) {
                // Dùng cùng kiểu "vòng xoay + chữ" như màn hình thư viện thay vì để trống.
                // Trước đây chỗ này là một `Spacer` rỗng: mở thùng rác thấy màn hình trắng
                // trong giây lát, người dùng không biết ứng dụng đang tải hay đã hỏng.
                TrashUiState.Loading -> LoadingContent(
                    label = stringResource(R.string.library_loading),
                )

                TrashUiState.Empty -> EmptyState(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.trash_empty_title),
                    message = stringResource(R.string.trash_empty_message),
                )

                is TrashUiState.Failed -> EmptyState(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.library_error_title),
                    message = stringResource(state.error.messageRes()),
                    tone = EmptyStateTone.ERROR,
                )

                is TrashUiState.Items -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.documents, key = { it.id }) { document ->
                        TrashRow(
                            document = document,
                            onRestore = { viewModel.onRestore(document) },
                            onDeleteForever = { documentToDeleteForever = document },
                        )
                    }
                }
            }
        }
    }

    documentToDeleteForever?.let { document ->
        ConfirmDialog(
            title = stringResource(R.string.trash_delete_forever_title),
            message = stringResource(R.string.trash_delete_forever_message, document.title),
            confirmLabel = stringResource(R.string.action_delete_forever),
            cancelLabel = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                viewModel.onDeleteForever(document)
                documentToDeleteForever = null
            },
            onDismiss = { documentToDeleteForever = null },
        )
    }
}

@Composable
private fun TrashRow(
    document: Document,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val deletedAt = document.deletedAtMillis
            if (deletedAt != null) {
                Text(
                    text = stringResource(
                        R.string.trash_deleted_at,
                        Formatters.relativeTime(deletedAt),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                TextButton(onClick = onRestore) {
                    Text(text = stringResource(R.string.action_restore))
                }
                TextButton(onClick = onDeleteForever) {
                    Text(
                        text = stringResource(R.string.action_delete_forever),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
