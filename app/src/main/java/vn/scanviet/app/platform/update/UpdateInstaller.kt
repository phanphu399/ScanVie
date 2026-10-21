package vn.scanviet.app.platform.update

import java.io.File

/**
 * Tải và cài APK cập nhật.
 *
 * Vì sao cần một interface ngay cả khi chỉ có một bản thật: lớp thật cần `Context` (để tải file,
 * cấp `content://` URI và mở trình cài đặt), nên không thể dựng trong unit test chạy trên JVM.
 * Tách ra thành cổng cho phép test `UpdateViewModel` kiểm tra luồng quyết định — khi nào gọi
 * mạng, xử lý lỗi mạng ra sao — mà không cần thiết bị.
 *
 * Đây cũng là ranh giới duy nhất trong ứng dụng can thiệp vào hệ thống cài đặt của Android. Khi
 * chuyển sang Google Play In-App Updates, chỉ lớp này bị thay.
 */
interface UpdateInstaller {

    /**
     * Tải APK từ [url] về bộ nhớ đệm.
     *
     * @param onProgress gọi trong lúc tải với số byte đã đọc và tổng dung lượng (0 nếu máy chủ
     *   không cho biết).
     * @return file đã tải xong, sẵn sàng để cài.
     * @throws java.io.IOException khi không tải được hoặc máy chủ trả về lỗi.
     */
    suspend fun download(url: String, onProgress: (bytesRead: Long, totalBytes: Long) -> Unit): File

    /** @return true nếu ứng dụng được phép mở trình cài đặt trên máy này. */
    fun canRequestPackageInstalls(): Boolean

    /** Mở màn hình Cài đặt để người dùng tự bật quyền cài đặt cho ứng dụng này. */
    fun openInstallPermissionSettings()

    /**
     * Mở trình cài đặt của hệ thống với file APK đã tải.
     *
     * @throws java.io.IOException khi không tạo được `content://` URI (thiếu khai báo FileProvider).
     */
    fun install(apkFile: File)

    /** Xoá file APK đã tải. Gọi sau khi người dùng cài xong hoặc huỷ. */
    fun clearDownloadedApk()
}
