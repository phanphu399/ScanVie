package vn.scanviet.app.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test cho phép tính hình học của lớp chữ ẩn trong PDF.
 *
 * **Vì sao test này quan trọng:** lệch vài phần trăm chiều cao trang là đủ để ô bôi đen trong
 * trình đọc PDF nhảy sang dòng khác, làm hỏng đúng thứ tính năng này sinh ra để làm. Phép tính
 * nằm ở `AndroidPdfExporter` (chỉ chạy được trên thiết bị, vì Robolectric không có
 * `android.graphics.pdf`), nên phần hình học đã được tách ra hàm thuần
 * [invisibleTextPlacement] để test được trên JVM — đây chính là lý do tách.
 *
 * Các số đo font dùng trong test là số thật của `Typeface.DEFAULT` ở cỡ chữ 1, lấy từ
 * `Paint.FontMetrics` trên Android 14: `ascent ≈ -0.927`, `descent ≈ 0.244`. Dùng số thật
 * giúp test phản ánh hành vi thực tế thay vì một thế giới giả định.
 */
class InvisibleTextPlacementTest {

    @Test
    fun `tinh co chu theo chieu cao hinh bao va so do font`() {
        // Hình bao chiếm 2% chiều cao trang. Trang cao 842pt (A4 dọc) -> hình bao ~16.84pt.
        // Cỡ chữ cần = 16.84 / (0.927 + 0.244) ≈ 14.38pt.
        val placement = requireNotNull(
            invisibleTextPlacement(
                left = 0.1f,
                top = 0.2f,
                right = 0.5f,
                bottom = 0.22f,
                targetLeft = 0f,
                targetTop = 0f,
                targetWidth = 595f,
                targetHeight = 842f,
                ascentRatio = ASCENT,
                descentRatio = DESCENT,
                minTextSize = 1f,
                maxTextSize = 120f,
            ),
        )

        val expectedBoxHeight = 0.02f * 842f
        val expectedTextSize = expectedBoxHeight / (ASCENT * -1f + DESCENT)
        assertEquals(expectedTextSize, placement.textSize, 0.01f)
    }

    @Test
    fun `duong chan chu can giua khoi chu trong hinh bao`() {
        val top = 0.2f
        val bottom = 0.22f
        val targetHeight = 842f

        val placement = requireNotNull(
            invisibleTextPlacement(
                left = 0.1f,
                top = top,
                right = 0.5f,
                bottom = bottom,
                targetLeft = 0f,
                targetTop = 0f,
                targetWidth = 595f,
                targetHeight = targetHeight,
                ascentRatio = ASCENT,
                descentRatio = DESCENT,
                minTextSize = 1f,
                maxTextSize = 120f,
            ),
        )

        val boxTop = top * targetHeight
        val boxHeight = (bottom - top) * targetHeight
        // Đỉnh khối chữ = baseline + ascent (ascent âm nên là phép cộng).
        val inkTop = placement.baselineY + ASCENT * placement.textSize
        val inkBottom = placement.baselineY + DESCENT * placement.textSize
        val inkHeight = inkBottom - inkTop

        // Mép trên khối chữ phải nằm trong hình bao...
        assertTrue(
            "Đỉnh chữ ($inkTop) phải nằm trong hình bao (đỉnh $boxTop)",
            inkTop >= boxTop - TOLERANCE,
        )
        // ...và khối chữ phải cân trong hình bao.
        val gapTop = inkTop - boxTop
        val gapBottom = (boxTop + boxHeight) - inkBottom
        assertEquals(
            "Khoảng trống trên và dưới phải bằng nhau (căn giữa)",
            gapTop,
            gapBottom,
            TOLERANCE,
        )
        // Khối chữ cao đúng bằng chiều cao hình bao theo thiết kế cỡ chữ.
        assertEquals(boxHeight, inkHeight, TOLERANCE)
    }

    @Test
    fun `toa do x theo mep trai hinh bao va vung anh`() {
        val placement = requireNotNull(
            invisibleTextPlacement(
                left = 0.25f,
                top = 0.1f,
                right = 0.75f,
                bottom = 0.12f,
                targetLeft = 40f,
                targetTop = 100f,
                targetWidth = 500f,
                targetHeight = 700f,
                ascentRatio = ASCENT,
                descentRatio = DESCENT,
                minTextSize = 1f,
                maxTextSize = 120f,
            ),
        )

        // 0.25 * 500 + 40 = 165.
        assertEquals(165f, placement.x, 0.01f)
    }

    @Test
    fun `tra ve null khi hinh bao rong`() {
        assertNull(
            invisibleTextPlacement(
                left = 0.3f,
                top = 0.3f,
                right = 0.3f,
                bottom = 0.32f,
                targetLeft = 0f,
                targetTop = 0f,
                targetWidth = 595f,
                targetHeight = 842f,
                ascentRatio = ASCENT,
                descentRatio = DESCENT,
                minTextSize = 1f,
                maxTextSize = 120f,
            ),
        )
    }

    @Test
    fun `tra ve null khi vung anh khong co dien tich`() {
        assertNull(
            invisibleTextPlacement(
                left = 0.1f,
                top = 0.1f,
                right = 0.5f,
                bottom = 0.2f,
                targetLeft = 0f,
                targetTop = 0f,
                targetWidth = 0f,
                targetHeight = 842f,
                ascentRatio = ASCENT,
                descentRatio = DESCENT,
                minTextSize = 1f,
                maxTextSize = 120f,
            ),
        )
    }

    @Test
    fun `tra ve null khi so do font vo ly`() {
        assertNull(
            invisibleTextPlacement(
                left = 0.1f,
                top = 0.1f,
                right = 0.5f,
                bottom = 0.2f,
                targetLeft = 0f,
                targetTop = 0f,
                targetWidth = 595f,
                targetHeight = 842f,
                // descent <= ascent nghĩa là chiều cao dòng không dương: số đo vô lý.
                ascentRatio = 0.5f,
                descentRatio = 0.2f,
                minTextSize = 1f,
                maxTextSize = 120f,
            ),
        )
    }

    @Test
    fun `co chu bi chan tren khi hinh bao qua lon`() {
        val placement = requireNotNull(
            invisibleTextPlacement(
                left = 0f,
                top = 0f,
                right = 1f,
                bottom = 1f,
                targetLeft = 0f,
                targetTop = 0f,
                targetWidth = 595f,
                targetHeight = 842f,
                ascentRatio = ASCENT,
                descentRatio = DESCENT,
                minTextSize = 1f,
                maxTextSize = 120f,
            ),
        )
        assertEquals(120f, placement.textSize, 0.01f)
    }

    @Test
    fun `co chu bi chan duoi khi hinh bao qua nho`() {
        val placement = requireNotNull(
            invisibleTextPlacement(
                left = 0f,
                top = 0f,
                right = 0.001f,
                bottom = 0.00001f,
                targetLeft = 0f,
                targetTop = 0f,
                targetWidth = 595f,
                targetHeight = 842f,
                ascentRatio = ASCENT,
                descentRatio = DESCENT,
                minTextSize = 4f,
                maxTextSize = 120f,
            ),
        )
        assertEquals(4f, placement.textSize, 0.01f)
    }

    @Test
    fun `vi tri duoc dich theo goc cua vung anh trong trang`() {
        // Cùng hình bao nhưng vùng ảnh bị dịch: toạ độ tuyệt đối phải dịch theo đúng lượng.
        val atOrigin = requireNotNull(
            invisibleTextPlacement(
                left = 0.2f,
                top = 0.3f,
                right = 0.6f,
                bottom = 0.32f,
                targetLeft = 0f,
                targetTop = 0f,
                targetWidth = 400f,
                targetHeight = 600f,
                ascentRatio = ASCENT,
                descentRatio = DESCENT,
                minTextSize = 1f,
                maxTextSize = 120f,
            ),
        )
        val shifted = requireNotNull(
            invisibleTextPlacement(
                left = 0.2f,
                top = 0.3f,
                right = 0.6f,
                bottom = 0.32f,
                targetLeft = 100f,
                targetTop = 200f,
                targetWidth = 400f,
                targetHeight = 600f,
                ascentRatio = ASCENT,
                descentRatio = DESCENT,
                minTextSize = 1f,
                maxTextSize = 120f,
            ),
        )

        assertEquals(100f, shifted.x - atOrigin.x, 0.01f)
        assertEquals(200f, shifted.baselineY - atOrigin.baselineY, 0.01f)
        assertNotNull(shifted)
    }

    private companion object {
        /** Số đo thật của `Typeface.DEFAULT` ở cỡ chữ 1 (Android 14). */
        const val ASCENT = -0.927f
        const val DESCENT = 0.244f
        const val TOLERANCE = 0.05f
    }
}
