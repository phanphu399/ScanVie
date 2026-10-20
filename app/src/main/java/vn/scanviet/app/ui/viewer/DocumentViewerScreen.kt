package vn.scanviet.app.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.scanviet.app.R
import vn.scanviet.app.domain.image.PageImageLoader
import vn.scanviet.app.domain.image.PageImageSizes
import vn.scanviet.app.ui.common.messageRes
import vn.scanviet.app.ui.common.rememberPageImage
import vn.scanviet.app.ui.components.EmptyState

/**
 * Màn hình xem lại tài liệu đã scan.
 *
 * Chức năng: vuốt ngang để chuyển trang, chụm hai ngón để phóng to, kéo để di chuyển khi đã
 * phóng to. Tiêu đề luôn hiện "Trang n/tổng" của trang ĐANG xem.
 *
 * Vì sao dùng `HorizontalPager` của Compose thay vì tự xử lý cử chỉ vuốt: pager đã lo đúng
 * ngữ nghĩa (vị trí trang, quán tính, chiều rộng trang) và tương thích với xoay màn hình.
 * Tự viết lại chỉ để kiểm soát thứ đã đúng là công sức vô ích.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    onBack: () -> Unit,
    viewModel: DocumentViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loaded = uiState as? DocumentViewerUiState.Loaded

    // Pager được tạo ở đây (không phải trong nhánh `Loaded`) để tiêu đề ở top bar đọc được
    // trang đang xem. Khi chưa có dữ liệu, pager rỗng và tiêu đề dùng giá trị mặc định.
    val pages = loaded?.pages.orEmpty()
    val startPosition = loaded?.let { state ->
        state.pages.indexOfFirst { it.pageIndex == state.startPage }.coerceAtLeast(0)
    } ?: 0
    val pagerState = rememberPagerState(
        initialPage = startPosition,
        pageCount = { pages.size },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (pages.isEmpty()) {
                        Text(text = stringResource(R.string.detail_title_fallback))
                    } else {
                        val current = pages.getOrNull(pagerState.currentPage)
                        Text(
                            text = stringResource(
                                R.string.viewer_title,
                                current?.pageIndex ?: pages.first().pageIndex,
                                pages.size,
                            ),
                        )
                    }
                },
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Nền tối để ảnh trang (thường là giấy trắng) nổi bật và đỡ mỏi mắt.
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                DocumentViewerUiState.Loading -> CircularProgressIndicator()

                DocumentViewerUiState.NotFound -> NotFoundContent(
                    message = stringResource(R.string.detail_not_found),
                    onBack = onBack,
                )

                is DocumentViewerUiState.Failed -> NotFoundContent(
                    message = stringResource(state.error.messageRes()),
                    onBack = onBack,
                )

                is DocumentViewerUiState.Loaded -> PagePager(
                    state = state,
                    pagerState = pagerState,
                    imageLoader = viewModel.imageLoader,
                )
            }
        }
    }
}

@Composable
private fun PagePager(
    state: DocumentViewerUiState.Loaded,
    pagerState: PagerState,
    imageLoader: PageImageLoader,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { position ->
        val page = state.pages[position]
        if (page.isMissing) {
            MissingPageMessage()
        } else {
            ZoomablePage(relativePath = page.relativePath, imageLoader = imageLoader)
        }
    }
}

/**
 * Ảnh một trang, phóng to và kéo được.
 *
 * Cách hoạt động: mọi thay đổi (phóng, kéo) chỉ cập nhật `scale`/`offset` trong bộ nhớ, và
 * `graphicsLayer` áp chúng khi vẽ. Không giải mã lại ảnh ở mức phóng lớn — nhờ vậy phóng to
 * không tốn thêm RAM và không giật, đúng như các ứng dụng xem ảnh chuyên nghiệp.
 *
 * Vì sao kẹp `scale` trong [MIN_SCALE]..[MAX_SCALE]: cho phép thu nhỏ tới mức vô hình hoặc
 * phóng to tới mức chỉ thấy một điểm ảnh đều là trạng thái người dùng khó thoát ra.
 *
 * `pointerInput(relativePath)` khoá cử chỉ theo trang: khi pager tái dùng composable cho
 * trang khác, mức phóng của trang cũ không được rò sang trang mới.
 */
@Composable
private fun ZoomablePage(
    relativePath: String,
    imageLoader: PageImageLoader,
) {
    val image = rememberPageImage(
        relativePath = relativePath,
        loader = imageLoader,
        maxDimension = PageImageSizes.VIEWER_DIMENSION,
    )

    var scale by remember(relativePath) { mutableFloatStateOf(1f) }
    var offsetX by remember(relativePath) { mutableFloatStateOf(0f) }
    var offsetY by remember(relativePath) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(relativePath) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    if (newScale <= MIN_SCALE) {
                        // Thu về cỡ gốc thì ép vị trí về giữa: giữ offset cũ sẽ khiến ảnh
                        // nhỏ nằm lệch một góc thay vì ở giữa khung.
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                    scale = newScale
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.cd_scanned_page),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
        }
    }
}

@Composable
private fun MissingPageMessage() {
    Text(
        text = stringResource(R.string.viewer_error),
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun NotFoundContent(message: String, onBack: () -> Unit) {
    EmptyState(
        icon = Icons.Default.Warning,
        title = stringResource(R.string.detail_title_fallback),
        message = message,
        actionLabel = stringResource(R.string.action_back),
        onAction = onBack,
    )
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
