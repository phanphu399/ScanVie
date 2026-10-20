package vn.scanviet.app.domain.update

import vn.scanviet.app.domain.model.AppRelease

/**
 * Nguồn thông tin về bản phát hành mới nhất.
 *
 * Vì sao tách thành cổng: hiện tại nguồn là GitHub Releases (đọc file JSON công khai), nhưng
 * khi ứng dụng lên Google Play thì nguồn đó không còn đúng nữa. Tầng domain chỉ cần biết
 * "hỏi xem có bản mới không"; còn hỏi ai, hỏi thế nào là việc của tầng platform. Nhờ vậy
 * không phải sửa logic nghiệp vụ khi đổi cách phát hành.
 */
interface UpdateSource {

    /**
     * Lấy thông tin bản phát hành mới nhất.
     *
     * @return `null` khi nguồn không có bản phát hành nào (repo chưa có release).
     * @throws UpdateCheckFailedException khi không kết nối được hoặc dữ liệu trả về không đọc được.
     *   Ném lỗi thay vì trả `null` để phân biệt "không có bản mới" với "không kiểm tra được".
     */
    suspend fun latestRelease(): AppRelease?
}

/**
 * Không kiểm tra được cập nhật: mất mạng, máy chủ lỗi, hoặc dữ liệu trả về không hợp lệ.
 *
 * Thông điệp hiển thị cho người dùng nằm ở tầng ui; lớp này không giữ nội dung lỗi của hệ
 * thống vì lý do an toàn khi hiển thị.
 */
class UpdateCheckFailedException(
    cause: Throwable? = null,
) : Exception("Không kiểm tra được bản cập nhật", cause)
