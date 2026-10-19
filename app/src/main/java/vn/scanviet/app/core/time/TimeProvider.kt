package vn.scanviet.app.core.time

/**
 * Nguồn thời gian của ứng dụng.
 * Tiêm vào thay vì gọi System.currentTimeMillis() trực tiếp để test kiểm soát được thời gian.
 */
interface TimeProvider {
    fun now(): Long
}
