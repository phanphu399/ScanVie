package vn.scanviet.app.testing

import vn.scanviet.app.core.time.TimeProvider

/**
 * Thời gian điều khiển được trong test: cần thiết để kiểm tra "đổi tên cập nhật updatedAt"
 * và "dọn tài liệu trong thùng rác quá 30 ngày".
 */
class FakeTimeProvider(
    var currentTimeMillis: Long = DEFAULT_TIME,
) : TimeProvider {

    override fun now(): Long = currentTimeMillis

    fun advanceBy(millis: Long) {
        currentTimeMillis += millis
    }

    companion object {
        const val DEFAULT_TIME = 1_700_000_000_000L
        const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
