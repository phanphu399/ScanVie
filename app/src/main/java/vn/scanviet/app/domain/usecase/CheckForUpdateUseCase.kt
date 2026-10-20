package vn.scanviet.app.domain.usecase

import vn.scanviet.app.domain.model.UpdateCheckResult
import vn.scanviet.app.domain.update.UpdateCheckFailedException
import vn.scanviet.app.domain.update.UpdateSource
import vn.scanviet.app.domain.update.VersionComparator

/**
 * Kiểm tra xem có bản cập nhật mới hơn bản đang chạy hay không.
 *
 * Ba quyết định nghiệp vụ:
 *
 * 1. **Ứng dụng chỉ BÁO CÓ bản mới, không tự cập nhật.** Việc kiểm tra chạy nền và không
 *    chặn người dùng làm gì. Tự tải và tự cài mà không hỏi là hành vi bị Google Play cấm,
 *    và với người dùng thì rất khó chịu.
 *
 * 2. **Bản có phiên bản bằng nhau KHÔNG được coi là cập nhật.** So sánh bằng
 *    [VersionComparator] chứ không so chuỗi, nên `0.10.0` mới hơn `0.9.0` đúng như mong đợi.
 *
 * 3. **Lỗi mạng không được nuốt thành "đã là bản mới nhất".** [UpdateCheckFailedException]
 *    được ném ra ngoài để màn hình báo đúng "không kiểm tra được", thay vì nói dối người
 *    dùng rằng họ đang dùng bản mới nhất.
 *
 * @param currentVersion phiên bản đang chạy, lấy từ `BuildConfig.VERSION_NAME`.
 */
class CheckForUpdateUseCase(
    private val updateSource: UpdateSource,
    private val currentVersion: String,
) {

    suspend operator fun invoke(): UpdateCheckResult {
        val release = try {
            updateSource.latestRelease()
        } catch (error: UpdateCheckFailedException) {
            throw error
        } catch (error: Exception) {
            // Lỗi khác (JSON hỏng, lỗi mạng lạ) vẫn phải thành lỗi kiểm tra, không được để
            // lọt lên màn hình dưới dạng crash.
            throw UpdateCheckFailedException(error)
        }

        if (release == null) return UpdateCheckResult.UpToDate
        if (!VersionComparator.isNewer(release.versionName, currentVersion)) {
            return UpdateCheckResult.UpToDate
        }
        return UpdateCheckResult.Available(release)
    }
}
