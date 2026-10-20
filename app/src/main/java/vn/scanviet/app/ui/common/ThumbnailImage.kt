package vn.scanviet.app.ui.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import vn.scanviet.app.domain.image.DecodedImage
import vn.scanviet.app.domain.image.PageImageLoader
import vn.scanviet.app.domain.image.PageImageSizes

/**
 * Nạp ảnh một trang và trả về `ImageBitmap` cho Compose, hoặc `null` khi chưa tải xong /
 * không đọc được.
 *
 * Vì sao `remember(relativePath, maxDimension)` chứ không phải `remember` trơn: khi dòng
 * danh sách được dùng lại cho tài liệu khác, hoặc khi đổi mức phóng, khoá phải đổi thì
 * Compose mới nạp lại ảnh. Khoá theo đúng dữ liệu đầu vào là cách bắt nó làm việc đó.
 *
 * Vì sao `LaunchedEffect` chứ không gọi trong thân composable: nạp ảnh là việc bất đồng bộ
 * có vào/ra đĩa; gọi thẳng trong thân composable sẽ chạy lại mỗi khung hình và chặn UI.
 *
 * [DecodedImage] mang mảng pixel thô (không phải Android `Bitmap`) để domain không phụ thuộc
 * Android; việc dựng `ImageBitmap` chỉ xảy ra ở đây, đúng ranh giới của tầng ui.
 */
@Composable
fun rememberPageImage(
    relativePath: String?,
    loader: PageImageLoader,
    maxDimension: Int = PageImageSizes.THUMBNAIL_DIMENSION,
): ImageBitmap? {
    var image by remember(relativePath, maxDimension) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(relativePath, maxDimension) {
        image = null
        if (relativePath == null) return@LaunchedEffect
        image = loader.load(relativePath, maxDimension)?.toImageBitmap()
    }

    return image
}

/**
 * Dựng `ImageBitmap` từ mảng pixel ARGB.
 *
 * `Bitmap.createBitmap` nhận `IntArray` là cách chuẩn để đưa pixel thô vào Compose mà không
 * phải đi qua một file trung gian. `asImageBitmap()` bọc lại chứ không sao chép dữ liệu.
 */
private fun DecodedImage.toImageBitmap(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}
