package vn.scanviet.app.platform.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.domain.model.AppRelease
import vn.scanviet.app.domain.update.UpdateCheckFailedException
import vn.scanviet.app.domain.update.UpdateSource

/**
 * Đọc một URL và trả về nội dung dạng chuỗi.
 *
 * Vì sao tách thành interface riêng: test cần chạy được mà không gọi mạng thật. Thay vì
 * dựng máy chủ giả trong test (chậm, dễ chập chờn), ta thay cả bộ đọc bằng bản giả chỉ trả
 * về chuỗi đã chuẩn bị sẵn.
 */
interface HttpResponseReader {
    /**
     * @throws IOException khi không kết nối được, mã trả về không phải 2xx, hoặc quá thời gian chờ.
     */
    suspend fun readText(url: String, accept: String): String
}

/**
 * Đọc HTTP bằng `HttpURLConnection` có sẵn trong JDK.
 *
 * Vì sao không dùng OkHttp: chỉ cần hai lời gọi GET đơn giản (một JSON nhỏ và một file APK).
 * Thêm một thư viện mạng đầy đủ cho việc này là không tương xứng, và dự án giữ số dependency
 * ở mức tối thiểu.
 *
 * Vì sao đặt thời gian chờ TƯỜNG MINH: giá trị mặc định của `HttpURLConnection` là chờ vô hạn.
 * Trên mạng di động yếu, một lời gọi không bao giờ trả về sẽ treo vòng xoay kiểm tra cập nhật
 * mãi mãi và người dùng không biết chuyện gì đang xảy ra.
 */
class HttpUrlConnectionReader(
    private val dispatchers: DispatcherProvider,
) : HttpResponseReader {

    override suspend fun readText(url: String, accept: String): String =
        withContext(dispatchers.io) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                setRequestProperty("Accept", accept)
                // GitHub từ chối request không có User-Agent.
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IOException("Máy chủ trả về mã $code")
                }
                connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 15_000

        /** GitHub yêu cầu User-Agent; dùng tên ứng dụng để lưu vết dễ hơn khi cần gỡ lỗi. */
        const val USER_AGENT = "ScanViet-UpdateCheck"
    }
}

/**
 * Lấy bản phát hành mới nhất từ GitHub Releases API.
 *
 * Vì sao GitHub Releases: đây chính là nơi dự án đang phát hành APK. Không cần máy chủ riêng,
 * không phát sinh chi phí, và file JSON của GitHub là công khai nên không cần khoá bí mật
 * trong ứng dụng — điều quan trọng, vì mọi khoá nhúng trong APK đều trích xuất được.
 *
 * Vì sao dùng `/releases/latest` chứ không phải `/releases`: endpoint `latest` bỏ qua các bản
 * đánh dấu pre-release và bản nháp.
 *
 * CẢNH BÁO VẬN HÀNH — đọc trước khi phát hành: vì lý do trên, bản phát hành muốn được ứng
 * dụng nhìn thấy **KHÔNG được** đánh dấu "pre-release". Bản `v0.2-beta` hiện tại đang là
 * pre-release nên endpoint này sẽ bỏ qua nó. Từ bản 0.4.0 trở đi, bản phát hành phải là bản
 * chính thức, nếu không cơ chế tự cập nhật sẽ im lặng mãi mãi mà không báo lỗi gì.
 */
class GitHubReleaseSource(
    private val httpReader: HttpResponseReader,
    private val owner: String,
    private val repository: String,
) : UpdateSource {

    override suspend fun latestRelease(): AppRelease? {
        val body = try {
            httpReader.readText(
                url = "https://api.github.com/repos/$owner/$repository/releases/latest",
                accept = "application/vnd.github+json",
            )
        } catch (error: IOException) {
            throw UpdateCheckFailedException(error)
        }

        val json = try {
            JSONObject(body)
        } catch (error: Exception) {
            throw UpdateCheckFailedException(error)
        }

        // `tag_name` là nhãn phát hành, ví dụ "v0.4.0". Bỏ tiền tố "v" để so sánh phiên bản.
        val tag = json.optString(TAG_NAME, "").removePrefix("v")
        if (tag.isEmpty()) throw UpdateCheckFailedException()

        val asset = selectApkAsset(json.optJSONArray(ASSETS))
            ?: throw UpdateCheckFailedException()

        val downloadUrl = asset.optString(BROWSER_DOWNLOAD_URL, "")
        if (downloadUrl.isEmpty()) throw UpdateCheckFailedException()

        return AppRelease(
            versionName = tag,
            downloadUrl = downloadUrl,
            sizeBytes = asset.optLong(SIZE, 0L),
            releaseNotes = json.optString(BODY, "").trim(),
        )
    }

    /**
     * Chọn file APK trong danh sách tài nguyên của bản phát hành.
     *
     * Ưu tiên tên chứa `arm64`, vì đó là kiến trúc của gần như mọi điện thoại Android hiện nay.
     * Nếu không có thì lấy file `.apk` đầu tiên: một bản chung chạy được còn hơn không có gì.
     */
    private fun selectApkAsset(assets: JSONArray?): JSONObject? {
        if (assets == null || assets.length() == 0) return null
        val apks = (0 until assets.length())
            .mapNotNull { index -> assets.optJSONObject(index) }
            .filter { asset -> asset.optString(NAME, "").endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null
        return apks.firstOrNull { asset -> asset.optString(NAME, "").contains("arm64", ignoreCase = true) }
            ?: apks.first()
    }

    private companion object {
        const val TAG_NAME = "tag_name"
        const val ASSETS = "assets"
        const val NAME = "name"
        const val SIZE = "size"
        const val BODY = "body"
        const val BROWSER_DOWNLOAD_URL = "browser_download_url"
    }
}
