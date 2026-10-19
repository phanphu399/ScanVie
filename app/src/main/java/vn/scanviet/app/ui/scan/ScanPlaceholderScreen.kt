package vn.scanviet.app.ui.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.scanviet.app.BuildConfig
import vn.scanviet.app.R
import vn.scanviet.app.ui.common.messageRes
import vn.scanviet.app.ui.components.PrimaryActionButton
import vn.scanviet.app.ui.components.SectionHeader

/**
 * Màn hình quét của Giai đoạn 1.
 *
 * Chức năng quét thật (ML Kit Document Scanner) thuộc Giai đoạn 2. Màn hình này tồn tại
 * để luồng điều hướng của ứng dụng hoàn chỉnh và nói rõ với người dùng đang ở đâu —
 * thay vì một nút bấm không có phản hồi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPlaceholderScreen(
    onBack: () -> Unit,
    viewModel: SampleDataViewModel = hiltViewModel(),
) {
    val sampleState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val successMessage = stringResource(R.string.dev_create_sample_success)

    // Thông báo lỗi được tạo sẵn trong phạm vi @Composable, không tạo bên trong LaunchedEffect.
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
                onBack()
            }

            is SampleState.Failed -> {
                snackbarHostState.showSnackbar(failureText.orEmpty())
                viewModel.onResultShown()
            }

            SampleState.Idle, SampleState.Running -> Unit
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.scan_coming_soon_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.scan_coming_soon_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.scan_ready_note),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Công cụ thử nghiệm: bị R8 loại bỏ khỏi bản release vì điều kiện là hằng số false.
            if (BuildConfig.DEBUG) {
                DevToolsCard(
                    isRunning = sampleState == SampleState.Running,
                    onCreateSample = viewModel::onCreateSampleDocument,
                )
            }
        }
    }
}

@Composable
private fun DevToolsCard(
    isRunning: Boolean,
    onCreateSample: () -> Unit,
) {
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
                onClick = onCreateSample,
                enabled = !isRunning,
            )
            if (isRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator()
            }
        }
    }
}
