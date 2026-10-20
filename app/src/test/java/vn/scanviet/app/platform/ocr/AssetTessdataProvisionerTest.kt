package vn.scanviet.app.platform.ocr

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.domain.ocr.OcrModelQuality
import vn.scanviet.app.testing.TestDispatcherProvider

/**
 * Kiểm tra việc giải nén file model Tesseract từ `assets/` ra vùng riêng của ứng dụng.
 *
 * **Vì sao chạy được trên JVM:** Robolectric cung cấp `Context` thật và đọc được `assets/`
 * của module. Nhờ vậy kiểm tra được đúng thứ dễ sai: tên file, vị trí thư mục, và việc chỉ
 * giữ MỘT model tại một thời điểm.
 *
 * **Vì sao đây là chỗ đáng test:** Tesseract là mã C++ tìm file model theo một đường dẫn cố
 * định (`<thư mục cha>/tessdata/vie.traineddata`). Sai một chữ trong tên thư mục thì
 * `TessBaseAPI.init()` trả `false` và ứng dụng im lặng không nhận dạng được gì.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AssetTessdataProvisionerTest {

    private lateinit var context: Context
    private lateinit var provisioner: AssetTessdataProvisioner

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provisioner = AssetTessdataProvisioner(context, TestDispatcherProvider())
        // Bắt đầu từ trạng thái sạch: Robolectric có thể tái sử dụng filesDir giữa các test.
        File(context.filesDir, "tesseract").deleteRecursively()
    }

    @Test
    fun `giai nen model ra dung thu muc ma Tesseract mong doi`() = runTest {
        val parentDir = provisioner.ensureInstalled(OcrModelQuality.FAST)

        // Tesseract tự tìm thư mục con tên `tessdata` bên trong thư mục cha này.
        // Sai tên thư mục => `init()` trả false => không nhận dạng được gì.
        val tessdataDir = File(parentDir, "tessdata")
        assertTrue("Thư mục tessdata phải tồn tại", tessdataDir.isDirectory)

        // Tesseract chỉ chấp nhận đúng tên file này, không phụ thuộc chất lượng model.
        val modelFile = File(tessdataDir, "vie.traineddata")
        assertTrue("File vie.traineddata phải tồn tại", modelFile.isFile)
        assertTrue("Model phải có nội dung", modelFile.length() > 0)
    }

    @Test
    fun `model duoc giai nen ra filesDir chu khong phai cacheDir`() = runTest {
        val parentDir = provisioner.ensureInstalled(OcrModelQuality.FAST)

        // `cacheDir` bị hệ điều hành xoá khi máy hết chỗ, làm lần nhận dạng sau thất bại
        // giữa chừng. `filesDir` chỉ bị xoá khi gỡ ứng dụng.
        assertTrue(
            "Model phải nằm trong filesDir, nhận được: ${parentDir.absolutePath}",
            parentDir.absolutePath.startsWith(context.filesDir.absolutePath),
        )
        assertFalse(
            "Model KHÔNG được nằm trong cacheDir",
            parentDir.absolutePath.startsWith(context.cacheDir.absolutePath),
        )
    }

    @Test
    fun `giai nen dung model FAST voi kich thuoc nho`() = runTest {
        val parentDir = provisioner.ensureInstalled(OcrModelQuality.FAST)

        val modelFile = File(File(parentDir, "tessdata"), "vie.traineddata")
        // Bản `fast` là 531275 byte theo dữ liệu chính thức của dự án Tesseract. Dùng khoảng
        // rộng để test không gãy khi model được phát hành lại, nhưng vẫn phân biệt được với
        // bản `best` (12,4 MB).
        assertTrue(
            "Model FAST phải nhỏ hơn 2 MB, thực tế: ${modelFile.length()} byte",
            modelFile.length() < 2_000_000,
        )
    }

    @Test
    fun `giai nen dung model BEST voi kich thuoc lon`() = runTest {
        val parentDir = provisioner.ensureInstalled(OcrModelQuality.BEST)

        val modelFile = File(File(parentDir, "tessdata"), "vie.traineddata")
        assertTrue(
            "Model BEST phải lớn hơn 2 MB, thực tế: ${modelFile.length()} byte",
            modelFile.length() > 2_000_000,
        )
    }

    @Test
    fun `doi tu FAST sang BEST thi model duoc thay the`() = runTest {
        val parentDir = provisioner.ensureInstalled(OcrModelQuality.FAST)
        val modelFile = File(File(parentDir, "tessdata"), "vie.traineddata")
        val fastSize = modelFile.length()

        val parentDirAgain = provisioner.ensureInstalled(OcrModelQuality.BEST)

        assertEquals("Cùng một thư mục model", parentDir.absolutePath, parentDirAgain.absolutePath)
        assertTrue(
            "Model phải được thay bằng bản BEST lớn hơn",
            modelFile.length() > fastSize,
        )
    }

    @Test
    fun `chi giu MOT model tren dia tai mot thoi diem`() = runTest {
        val parentDir = provisioner.ensureInstalled(OcrModelQuality.BEST)
        provisioner.ensureInstalled(OcrModelQuality.FAST)

        val tessdataDir = File(parentDir, "tessdata")
        val modelFiles = tessdataDir.listFiles()?.filter { it.isFile }.orEmpty()

        // Giữ cả hai model nghĩa là chiếm thêm ~12 MB trên máy người dùng mà không dùng đến.
        assertEquals(
            "Chỉ được có đúng một file model, thực tế: ${modelFiles.map { it.name }}",
            1,
            modelFiles.size,
        )
        // Không được để lại file tạm nào.
        assertTrue(
            "Không được còn file .tmp, thực tế: ${modelFiles.map { it.name }}",
            modelFiles.none { it.name.endsWith(".tmp") },
        )
    }

    @Test
    fun `goi lai nhieu lan khong tao them file`() = runTest {
        val parentDir = provisioner.ensureInstalled(OcrModelQuality.FAST)
        val tessdataDir = File(parentDir, "tessdata")
        val firstListing = tessdataDir.listFiles()?.map { it.name }?.sorted()

        repeat(3) { provisioner.ensureInstalled(OcrModelQuality.FAST) }

        val secondListing = tessdataDir.listFiles()?.map { it.name }?.sorted()
        assertEquals(firstListing, secondListing)
    }

    @Test
    fun `kich thuoc model da cai duoc bao cao dung`() = runTest {
        val parentDir = provisioner.ensureInstalled(OcrModelQuality.FAST)

        val reported = provisioner.installedModelSizeBytes()
        val actual = File(File(parentDir, "tessdata"), "vie.traineddata").length()

        assertNotNull(reported)
        assertEquals(actual, reported)
    }

    @Test
    fun `chua cai model nao thi kich thuoc la null`() = runTest {
        // `setUp` đã xoá thư mục model, nên chưa có gì trên đĩa.
        assertEquals(null, provisioner.installedModelSizeBytes())
    }
}
