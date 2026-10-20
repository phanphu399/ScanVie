package vn.scanviet.app.platform.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider

/**
 * Tải file APK cập nhật về bộ nhớ đệm và mở trình cài đặt của hệ thống.
 *
 * Ba điều quan trọng, mỗi điều vì một lỗi có thật:
 *
 * 1. **Tải về `cacheDir/updates/`, rồi xoá file cũ trước khi tải.** File APK nặng vài chục MB;
 *    giữ lại nhiều bản sẽ ăn hết bộ nhớ đệm của người dùng. Luôn ghi đè một tên file duy nhất
 *    là cách đơn giản nhất để không bao giờ tích tụ rác.
 *
 * 2. **Mở bằng `content://` qua FileProvider, không dùng `file://`.** Từ Android 7 trở đi,
 *    gửi `file://` trong Intent làm ứng dụng crash với `FileUriExposedException`.
 *
 * 3. **Kiểm tra quyền "cài đặt ứng dụng không rõ nguồn gốc" TRƯỚC khi mở trình cài đặt.**
 *    Trên Android 8 trở lên, hệ thống chặn cài đặt cho tới khi người dùng tự bật quyền này
 *    trong Cài đặt. Nếu không kiểm tra, người dùng sẽ thấy màn hình cài đặt trống hoặc bị
 *    đóng ngay, và không hiểu tại sao. Cần thiết kế cẩn thận: đây là quyền duy nhất mà ứng
 *    dụng yêu cầu người dùng tự bật, và chỉ khi họ chủ động bấm cập nhật.
 *
 * Ứng dụng KHÔNG tự cài đặt. Nó chỉ mở trình cài đặt của hệ thống, và người dùng là người
 * quyết định cuối cùng. Đây là yêu cầu bắt buộc của Google Play.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * Tải APK từ [url] về bộ nhớ đệm.
     *
     * @return file đã tải xong, sẵn sàng để cài.
     * @throws IOException khi không tải được hoặc máy chủ trả về lỗi.
     */
    suspend fun download(url: String, onProgress: (bytesRead: Long, totalBytes: Long) -> Unit): File =
        withContext(dispatchers.io) {
            val target = targetFile()
            // Xoá bản tải dở trước đó: một file .part sót lại sẽ khiến lần sau tưởng đã có sẵn.
            target.delete()

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                // Đọc file vài chục MB có thể lâu hơn nhiều so với đọc JSON; đặt rộng hơn.
                readTimeout = READ_TIMEOUT_MILLIS
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("Máy chủ trả về mã $code")

                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            read += count
                            onProgress(read, total)
                        }
                    }
                }
            } catch (error: IOException) {
                // Không để lại file dở dang: file APK hỏng sẽ khiến trình cài đặt báo lỗi khó hiểu.
                target.delete()
                throw error
            } finally {
                connection.disconnect()
            }
            target
        }

    /** @return true nếu ứng dụng được phép mở trình cài đặt trên máy này. */
    fun canRequestPackageInstalls(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            // Trước Android 8, quyền này chưa tồn tại nên không cần xin.
            true
        }

    /** Mở màn hình Cài đặt để người dùng tự bật quyền cài đặt cho ứng dụng này. */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Mở trình cài đặt của hệ thống với file APK đã tải.
     *
     * @throws IOException khi không tạo được `content://` URI (thiếu khai báo FileProvider).
     */
    fun install(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Xoá file APK đã tải. Gọi sau khi người dùng cài xong hoặc huỷ. */
    fun clearDownloadedApk() {
        targetFile().delete()
    }

    private fun targetFile(): File {
        val directory = File(context.cacheDir, UPDATES_DIRECTORY)
        if (!directory.exists()) directory.mkdirs()
        return File(directory, APK_FILE_NAME)
    }

    private companion object {
        const val UPDATES_DIRECTORY = "updates"

        /**
         * Tên cố định, không kèm số phiên bản.
         *
         * Cố ý: mỗi lần tải ghi đè đúng một file, nên không bao giờ có nhiều bản APK nằm chờ
         * trong bộ nhớ đệm. Đổi tên theo phiên bản sẽ khiến rác tích tụ dần mà không ai dọn.
         */
        const val APK_FILE_NAME = "scanviet-update.apk"

        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val BUFFER_SIZE = 64 * 1024
        const val USER_AGENT = "ScanViet-UpdateCheck"
    }
}
