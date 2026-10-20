package vn.scanviet.app.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import vn.scanviet.app.domain.model.AppRelease
import vn.scanviet.app.domain.model.UpdateCheckResult
import vn.scanviet.app.domain.update.UpdateCheckFailedException
import vn.scanviet.app.domain.update.UpdateSource

/**
 * Kiểm tra logic kiểm tra cập nhật.
 *
 * Ba hành vi được kiểm tra kỹ vì đều từng gây lỗi thật ở các ứng dụng khác:
 * 1. Mạng lỗi **không** được báo thành "đã là bản mới nhất".
 * 2. Bản cùng phiên bản **không** được coi là cập nhật.
 * 3. Nguồn không có bản phát hành nào thì coi như đã mới nhất, không phải lỗi.
 */
class CheckForUpdateUseCaseTest {

    private class FakeUpdateSource(
        private val release: AppRelease? = null,
        private val error: Throwable? = null,
    ) : UpdateSource {
        override suspend fun latestRelease(): AppRelease? {
            error?.let { throw it }
            return release
        }
    }

    private fun useCase(
        release: AppRelease? = null,
        error: Throwable? = null,
        currentVersion: String = "0.4.0",
    ) = CheckForUpdateUseCase(
        updateSource = FakeUpdateSource(release = release, error = error),
        currentVersion = currentVersion,
    )

    private fun release(version: String) = AppRelease(
        versionName = version,
        downloadUrl = "https://example.test/app.apk",
        sizeBytes = 31_000_000L,
    )

    @Test
    fun `phien ban moi hon tra ve Available`() = runTest {
        val result = useCase(release = release("0.5.0"))()

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("0.5.0", (result as UpdateCheckResult.Available).release.versionName)
    }

    @Test
    fun `cung phien ban tra ve UpToDate`() = runTest {
        val result = useCase(release = release("0.4.0"))()

        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `phien ban cu hon tra ve UpToDate`() = runTest {
        val result = useCase(release = release("0.3.9"))()

        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `so sanh dung voi phien ban hai chu so`() = runTest {
        // 0.10.0 mới hơn 0.9.0 theo nghĩa phiên bản, dù so chuỗi thì ngược lại.
        val result = useCase(release = release("0.10.0"), currentVersion = "0.9.0")()

        assertTrue(result is UpdateCheckResult.Available)
    }

    @Test
    fun `nguon khong co ban phat hanh nao thi coi nhu da moi nhat`() = runTest {
        val result = useCase(release = null)()

        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `loi mang nem ra UpdateCheckFailedException chu khong bao da moi nhat`() = runTest {
        val useCase = useCase(error = UpdateCheckFailedException())

        try {
            useCase()
            fail("Phải ném UpdateCheckFailedException")
        } catch (expected: UpdateCheckFailedException) {
            // Đúng: không được nuốt lỗi thành UpToDate.
        }
    }

    @Test
    fun `loi la khac cung duoc boc thanh UpdateCheckFailedException`() = runTest {
        val useCase = useCase(error = IllegalStateException("JSON hỏng"))

        try {
            useCase()
            fail("Phải ném UpdateCheckFailedException")
        } catch (expected: UpdateCheckFailedException) {
            assertTrue(expected.cause is IllegalStateException)
        }
    }
}
