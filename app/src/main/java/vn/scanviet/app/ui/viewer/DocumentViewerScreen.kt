package vn.scanviet.app.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlinx.coroutines.launch
import vn.scanviet.app.R
import vn.scanviet.app.domain.image.PageImageLoader
import vn.scanviet.app.domain.image.PageImageSizes
import vn.scanviet.app.ui.common.messageRes
import vn.scanviet.app.ui.common.rememberPageImage
import vn.scanviet.app.ui.components.EmptyState
import vn.scanviet.app.ui.components.EmptyStateTone

/**
 * Màn hình xem lại tài liệu đã scan.
 *
 * Bốn cách chuyển trang, vì mỗi người có một thói quen khác nhau khi đọc tài liệu nhiều trang:
 * 1. **Vuốt ngang** — nhanh nhất khi đang ở cỡ gốc.
 * 2. **Hai nút mũi tên** dưới thanh dưới — chính xác, và là cách duy nhất khi đã phóng to.
 * 3. **Dải ảnh thu nhỏ** — nhảy thẳng tới trang bất kỳ, không phải lật từng trang.
 * 4. **Chạm hai lần** — phóng to/phóng vừa giữa cỡ gốc và cỡ đọc.
 *
 * **Vì sao phải phân biệt "đã phóng to" và "cỡ gốc":** pager nuốt cử chỉ kéo ngang để lật
 * trang. Khi ảnh đã phóng to, người dùng cần kéo để XEM PHẦN KHÁC của cùng trang, không phải
 * để sang trang. Vì vậy khi `scale > 1` cử chỉ kéo bị chặn không cho pager nhận
 * (`userScrollEnabled = false`) và được dùng để dịch ảnh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    onBack: () -> Unit,
    viewModel: DocumentViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loaded = uiState as? DocumentViewerUiState.Loaded
    val copyRequest by viewModel.copyRequest.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Copy chữ của trang vào clipboard — phải ở đây vì ViewModel không giữ Context.
    LaunchedEffect(copyRequest) {
        val text = copyRequest ?: return@LaunchedEffect
        clipboard.setText(AnnotatedString(text))
        viewModel.onCopyHandled()
        snackbarHostState.showSnackbar(context.getString(R.string.detail_ocr_copied))
    }

    val pages = loaded?.pages.orEmpty()
    val startPosition = loaded?.let { state ->
        state.pages.indexOfFirst { it.pageIndex == state.startPage }.coerceAtLeast(0)
    } ?: 0
    val pagerState = rememberPagerState(
        initialPage = startPosition,
        pageCount = { pages.size },
    )

    // Mức phóng của trang ĐANG xem, do ZoomablePage báo lên. Cần ở đây để quyết định pager có
    // được nhận cử chỉ kéo ngang hay không.
    var currentScale by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()

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
                actions = {
                    // Chỉ hiện nút khi trang đang xem THỰC SỰ có chữ, để không mời người dùng
                    // bấm vào một hành động không có kết quả.
                    val pageIndex = pages.getOrNull(pagerState.currentPage)?.pageIndex
                    if (pageIndex != null && viewModel.pageText(pageIndex) != null) {
                        IconButton(
                            onClick = {
                                if (!viewModel.onCopyPageText(pageIndex)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.viewer_no_text),
                                        )
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.viewer_copy_text),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (pages.size > 1) {
                ViewerBottomBar(
                    pages = pages,
                    pagerState = pagerState,
                    imageLoader = viewModel.imageLoader,
                    onSelect = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                    onScaleChanged = { currentScale = it },
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
    onScaleChanged: (Float) -> Unit,
) {
    // Ảnh đang phóng to thì pager KHÔNG được nuốt cử chỉ kéo ngang; xem ghi chú ở đầu tệp.
    var zoomed by remember { mutableStateOf(false) }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        // `beyondViewportPageCount` giữ sẵn trang liền kề trong bộ nhớ: vuốt tới là ảnh đã có,
        // không thấy khung trắng nhấp nháy.
        beyondViewportPageCount = 1,
        userScrollEnabled = !zoomed,
    ) { position ->
        val page = state.pages[position]
        if (page.isMissing) {
            MissingPageMessage()
        } else {
            ZoomablePage(
                relativePath = page.relativePath,
                imageLoader = imageLoader,
                onScaleChanged = { scale ->
                    // Mỗi trang báo mức phóng của chính nó. Chỉ trang đang xem mới cần chặn
                    // pager, nên bỏ qua báo cáo từ trang khác.
                    if (position == pagerState.currentPage) {
                        zoomed = scale > 1.01f
                        onScaleChanged(scale)
                    }
                },
            )
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
 * **Vì sao tự xử lý cử chỉ thay vì dùng `detectTransformGestures`:** hàm đó gộp chung pan và
 * zoom, nên không thể cho phép kéo ảnh khi đã phóng to mà VẪN nhường cử chỉ vuốt ngang cho
 * pager khi ở cỡ gốc. Vòng lặp dưới đây đọc từng sự kiện và bỏ luồng ngay khi phát hiện đây là
 * một cú vuốt ngang ở cỡ gốc — pager nhận lại và lật trang mượt mà.
 *
 * Vì sao kẹp `scale` trong [MIN_SCALE]..[MAX_SCALE]: cho phép thu nhỏ tới mức vô hình hoặc
 * phóng to tới mức chỉ thấy một điểm ảnh đều là trạng thái người dùng khó thoát ra.
 *
 * `key = relativePath` khoá cử chỉ theo trang: khi pager tái dùng composable cho trang khác,
 * mức phóng của trang cũ không được rò sang trang mới.
 */
@Composable
private fun ZoomablePage(
    relativePath: String,
    imageLoader: PageImageLoader,
    onScaleChanged: (Float) -> Unit,
) {
    val image = rememberPageImage(
        relativePath = relativePath,
        loader = imageLoader,
        maxDimension = PageImageSizes.VIEWER_DIMENSION,
    )

    var scale by remember(relativePath) { mutableFloatStateOf(1f) }
    var offsetX by remember(relativePath) { mutableFloatStateOf(0f) }
    var offsetY by remember(relativePath) { mutableFloatStateOf(0f) }

    // Báo mức phóng hiện tại lên màn hình để nó quyết định cho pager nhận cử chỉ hay không.
    // Đọc trong một hiệu ứng riêng, không trong thân composable, để không gọi ngược trong lúc vẽ.
    androidx.compose.runtime.LaunchedEffect(scale) { onScaleChanged(scale) }

    fun resetToFit() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Chạm hai lần để phóng to / thu về. Đặt TRƯỚC `pointerInput` kéo để cử chỉ chạm
            // không bị vòng lặp kéo nuốt mất.
            .pointerInput(relativePath) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            resetToFit()
                        } else {
                            scale = DOUBLE_TAP_SCALE
                        }
                    },
                )
            }
            .pointerInput(relativePath) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var gesturePan = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        gesturePan += panChange

                        // Ở cỡ gốc: chưa phóng thì mọi thao tác hai ngón đều đưa về cỡ gốc.
                        // Không tiêu thụ sự kiện, để pager vẫn thấy cú vuốt ngang.
                        if (scale <= 1.01f && zoomChange == 1f) {
                            if (abs(gesturePan.x) > SWIPE_SLOP_PX && abs(gesturePan.x) > abs(gesturePan.y)) {
                                // Cú vuốt ngang ở cỡ gốc: nhường hẳn cho pager.
                                break
                            }
                            continue
                        }

                        // Đã phóng to (hoặc đang chụm hai ngón): xử lý tại đây và tiêu thụ sự
                        // kiện để pager không lật trang ngoài ý muốn.
                        val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                        if (newScale <= MIN_SCALE) {
                            // Thu về cỡ gốc thì ép vị trí về giữa: giữ offset cũ sẽ khiến ảnh
                            // nhỏ nằm lệch một góc thay vì ở giữa khung.
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            offsetX += panChange.x
                            offsetY += panChange.y
                        }
                        scale = newScale
                        // `centroid` chỉ dùng để giữ nguyên điểm chụm; hiện chưa dịch theo nó,
                        // nhưng tiêu thụ sự kiện là bắt buộc để pager không nhận.
                        event.changes.forEach { it.consume() }
                        if (centroid == Offset.Unspecified) break
                    } while (event.changes.any { it.pressed })
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

/**
 * Thanh dưới: nút lật trang + dải ảnh thu nhỏ.
 *
 * **Vì sao có nút lật trang dù đã có pager:** khi ảnh đang phóng to, cử chỉ vuốt đã dành cho việc
 * dịch ảnh nên không còn cách nào sang trang bằng vuốt. Thiếu nút thì người dùng bị "kẹt" trong
 * một trang đã phóng to — lỗi trải nghiệm có thật, không phải tính năng thừa.
 */
@Composable
private fun ViewerBottomBar(
    pages: List<ViewerPage>,
    pagerState: PagerState,
    imageLoader: PageImageLoader,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val current = pagerState.currentPage

    // Dải ảnh thu nhỏ tự cuộn để trang đang xem luôn nằm trong tầm mắt. Không làm việc này thì
    // mở tài liệu ở trang 15 sẽ thấy dải bắt đầu từ trang 1, và người dùng phải tự cuộn đi tìm.
    LaunchedEffect(current) {
        listState.animateScrollToItem(current.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
    }

    Surface {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VIEWER_STRIP_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(items = pages, key = { _, page -> page.pageIndex }) { index, page ->
                    StripThumbnail(
                        page = page,
                        isCurrent = index == current,
                        imageLoader = imageLoader,
                        onClick = { onSelect(index) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    onClick = { onSelect(current - 1) },
                    enabled = current > 0,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.viewer_prev_page),
                    )
                }
                Text(
                    text = stringResource(R.string.viewer_title, pages[current].pageIndex, pages.size),
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(
                    onClick = { onSelect(current + 1) },
                    enabled = current < pages.lastIndex,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.viewer_next_page),
                    )
                }
            }
        }
    }
}

/** Một ô ảnh thu nhỏ trong dải dưới màn hình xem. */
@Composable
private fun StripThumbnail(
    page: ViewerPage,
    isCurrent: Boolean,
    imageLoader: PageImageLoader,
    onClick: () -> Unit,
) {
    val thumbnail = rememberPageImage(
        relativePath = if (page.isMissing) null else page.relativePath,
        loader = imageLoader,
        maxDimension = PageImageSizes.THUMBNAIL_DIMENSION,
    )
    val borderColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(width = STRIP_THUMB_WIDTH, height = STRIP_THUMB_HEIGHT)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) borderColor else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(enabled = !page.isMissing, onClick = onClick),
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
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
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
        tone = EmptyStateTone.ERROR,
    )
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f

/** Mức phóng khi chạm hai lần: đủ để đọc chữ nhỏ mà vẫn thấy toàn trang ở mức lớn. */
private const val DOUBLE_TAP_SCALE = 2.5f

/** Quãng kéo ngang tối thiểu (px) để coi là vuốt lật trang chứ không phải rung tay. */
private const val SWIPE_SLOP_PX = 24f

private val STRIP_THUMB_WIDTH = 44.dp
private val STRIP_THUMB_HEIGHT = 58.dp
private val VIEWER_STRIP_HEIGHT = 62.dp
