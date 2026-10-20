package vn.scanviet.app.platform.update

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import vn.scanviet.app.domain.update.UpdateCheckFailedException

/**
 * Kiểm tra đọc và hiểu JSON của GitHub Releases.
 *
 * Dùng một bộ đọc HTTP giả thay vì gọi mạng thật: test phải chạy được ngoại tuyến và cho cùng
 * kết quả mỗi lần. JSON dưới đây là bản rút gọn sát với dữ liệu GitHub trả về thật.
 *
 * `org.json` là API của Android framework nên trong test JVM phải thêm dependency
 * `org.json:json` (xem `app/build.gradle.kts`). Đây là dependency CHỈ dùng cho test.
 */
class GitHubReleaseSourceTest {

    private class FakeHttpReader(
        private val body: String = "",
        private val error: Throwable? = null,
    ) : HttpResponseReader {
        var lastUrl: String? = null
        var lastAccept: String? = null

        override suspend fun readText(url: String, accept: String): String {
            lastUrl = url
            lastAccept = accept
            error?.let { throw it }
            return body
        }
    }

    private fun source(reader: HttpResponseReader) = GitHubReleaseSource(
        httpReader = reader,
        owner = "phanphu399",
        repository = "scanviet",
    )

    /** JSON dựng bằng tay theo từng tham số để test không phụ thuộc `.replace` dễ vỡ. */
    private fun releaseJson(
        tag: String = "v0.5.0",
        body: String = "Sửa lỗi copy chữ trong PDF.",
        assets: String = """
            {"name": "app-debug.apk", "size": 31000000, "browser_download_url": "https://github.com/phanphu399/scanviet/releases/download/v0.5.0/app-debug.apk"}
        """.trimIndent(),
    ) = """
        {
          "tag_name": "$tag",
          "body": "$body",
          "draft": false,
          "prerelease": false,
          "assets": [
            $assets
          ]
        }
    """.trimIndent()

    @Test
    fun `doc dung phien ban va dia chi tai`() = runTest {
        val release = source(FakeHttpReader(body = releaseJson())).latestRelease()

        requireNotNull(release)
        assertEquals("0.5.0", release.versionName)
        assertEquals(
            "https://github.com/phanphu399/scanviet/releases/download/v0.5.0/app-debug.apk",
            release.downloadUrl,
        )
        assertEquals(31_000_000L, release.sizeBytes)
        assertEquals("Sửa lỗi copy chữ trong PDF.", release.releaseNotes)
    }

    @Test
    fun `goi dung dia chi API va header chap nhan`() = runTest {
        val reader = FakeHttpReader(body = releaseJson())
        source(reader).latestRelease()

        assertEquals(
            "https://api.github.com/repos/phanphu399/scanviet/releases/latest",
            reader.lastUrl,
        )
        assertEquals("application/vnd.github+json", reader.lastAccept)
    }

    @Test
    fun `bo tien to v trong tag`() = runTest {
        val release = source(FakeHttpReader(body = releaseJson(tag = "v1.0.0"))).latestRelease()

        assertEquals("1.0.0", release?.versionName)
    }

    @Test
    fun `tag khong co tien to v van doc dung`() = runTest {
        val release = source(FakeHttpReader(body = releaseJson(tag = "0.6.0"))).latestRelease()

        assertEquals("0.6.0", release?.versionName)
    }

    @Test
    fun `uu tien file apk co arm64 khi co nhieu apk`() = runTest {
        val assets = """
            {"name": "app-universal.apk", "size": 100, "browser_download_url": "https://example.test/universal.apk"},
            {"name": "app-arm64-v8a.apk", "size": 200, "browser_download_url": "https://example.test/arm64.apk"}
        """.trimIndent()

        val release = source(FakeHttpReader(body = releaseJson(assets = assets))).latestRelease()

        assertEquals("https://example.test/arm64.apk", release?.downloadUrl)
    }

    @Test
    fun `lay apk dau tien khi khong co ban arm64`() = runTest {
        val assets = """
            {"name": "app-universal.apk", "size": 100, "browser_download_url": "https://example.test/universal.apk"}
        """.trimIndent()

        val release = source(FakeHttpReader(body = releaseJson(assets = assets))).latestRelease()

        assertEquals("https://example.test/universal.apk", release?.downloadUrl)
    }

    @Test
    fun `bo qua file khong phai apk`() = runTest {
        val assets = """
            {"name": "checksums.txt", "size": 100, "browser_download_url": "https://example.test/checksums.txt"}
        """.trimIndent()

        try {
            source(FakeHttpReader(body = releaseJson(assets = assets))).latestRelease()
            fail("Phải ném vì không có file APK nào")
        } catch (expected: UpdateCheckFailedException) {
            // Đúng: bản phát hành không có APK thì không cài được.
        }
    }

    @Test
    fun `danh sach tai nguyen rong duoc coi la loi`() = runTest {
        try {
            source(FakeHttpReader(body = releaseJson(assets = ""))).latestRelease()
            fail("Phải ném UpdateCheckFailedException")
        } catch (expected: UpdateCheckFailedException) {
            // Đúng.
        }
    }

    @Test
    fun `loi mang duoc boc thanh UpdateCheckFailedException`() = runTest {
        try {
            source(FakeHttpReader(error = IOException("mất mạng"))).latestRelease()
            fail("Phải ném UpdateCheckFailedException")
        } catch (expected: UpdateCheckFailedException) {
            assertTrue(expected.cause is IOException)
        }
    }

    @Test
    fun `json hong duoc boc thanh UpdateCheckFailedException`() = runTest {
        try {
            source(FakeHttpReader(body = "{ khong-phai-json")).latestRelease()
            fail("Phải ném UpdateCheckFailedException")
        } catch (expected: UpdateCheckFailedException) {
            // Đúng.
        }
    }

    @Test
    fun `json thieu tag_name duoc coi la loi`() = runTest {
        try {
            source(FakeHttpReader(body = """{"assets": []}""")).latestRelease()
            fail("Phải ném UpdateCheckFailedException")
        } catch (expected: UpdateCheckFailedException) {
            // Đúng.
        }
    }

    @Test
    fun `thieu dia chi tai cua apk duoc coi la loi`() = runTest {
        val assets = """{"name": "app-debug.apk", "size": 100}"""

        try {
            source(FakeHttpReader(body = releaseJson(assets = assets))).latestRelease()
            fail("Phải ném UpdateCheckFailedException")
        } catch (expected: UpdateCheckFailedException) {
            // Đúng: có file nhưng không biết tải ở đâu.
        }
    }
}
