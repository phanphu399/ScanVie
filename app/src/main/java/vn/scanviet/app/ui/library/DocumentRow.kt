package vn.scanviet.app.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.scanviet.app.R
import vn.scanviet.app.core.format.Formatters
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.OcrStatus

/**
 * Một dòng trong danh sách tài liệu.
 *
 * Hiển thị ảnh thu nhỏ của TRANG ĐẦU để nhìn là nhận ra tài liệu ngay, thay vì phải đọc tên.
 * [thumbnail] là `null` khi ảnh chưa tải xong hoặc không đọc được — lúc đó hiện biểu tượng
 * tài liệu, nhờ vậy dòng không bao giờ trống trơn.
 */
@Composable
fun DocumentRow(
    document: Document,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: ImageBitmap? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThumbnailBox(thumbnail = thumbnail)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Một dòng phụ duy nhất gộp số trang + thời gian. Trước đây là hai dòng
                // `bodySmall` cùng cỡ cùng màu nằm liền nhau: mắt không biết dòng nào là
                // thông tin chính, và dòng thứ ba (trạng thái chữ) trông như bị lạc.
                Text(
                    text = stringResource(R.string.library_item_pages, document.pageCount) +
                        " · " +
                        stringResource(
                            R.string.library_item_updated,
                            Formatters.relativeTime(document.updatedAtMillis),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OcrStatusChip(status = document.ocrStatus)
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Ô ảnh thu nhỏ bên trái mỗi dòng.
 *
 * Kích thước CỐ ĐỊNH (48×64 dp, tỉ lệ ~3:4 như trang giấy) dù ảnh thật có tỉ lệ nào:
 * chiều cao dòng phải giống nhau giữa các tài liệu, nếu không danh sách sẽ so le và khó quét
 * mắt. Ảnh được `ContentScale.Crop` để lấp đầy ô mà không bị méo.
 */
@Composable
private fun ThumbnailBox(thumbnail: ImageBitmap?) {
    Box(
        modifier = Modifier
            .size(width = THUMBNAIL_WIDTH, height = THUMBNAIL_HEIGHT)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = stringResource(R.string.cd_document_thumbnail),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(R.string.cd_document_icon),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Nhãn nhỏ thể hiện trạng thái nhận dạng chữ của tài liệu.
 *
 * Vì sao là "chip" có màu chứ không phải thêm một dòng chữ xám: đây là thông tin người
 * dùng cần nắm nhanh khi lướt danh sách ("tài liệu này đã có chữ chưa?"). Một dòng chữ
 * xám thứ ba nằm cạnh hai dòng xám khác thì không đọc được nhanh. Màu làm việc đó thay
 * cho người dùng, còn chữ giữ lại để không phụ thuộc hoàn toàn vào màu (người mù màu).
 */
@Composable
internal fun OcrStatusChip(status: OcrStatus, modifier: Modifier = Modifier) {
    val (background, foreground) = when (status) {
        OcrStatus.DONE -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer

        OcrStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer

        // NONE và RUNNING đều là "chưa xong": dùng màu trung tính, không gây cảm giác lỗi.
        OcrStatus.NONE, OcrStatus.RUNNING -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = stringResource(status.labelRes()),
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        // Khoá cứng 1 dòng: nhãn dài hơn dự kiến sẽ bị cắt chứ không đẩy vỡ bố cục hàng.
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

internal fun OcrStatus.labelRes(): Int = when (this) {
    OcrStatus.NONE -> R.string.library_ocr_none
    OcrStatus.RUNNING -> R.string.library_ocr_running
    OcrStatus.DONE -> R.string.library_ocr_done
    OcrStatus.FAILED -> R.string.library_ocr_failed
}

private val THUMBNAIL_WIDTH = 48.dp
private val THUMBNAIL_HEIGHT = 64.dp
