package vn.scanviet.app.domain

import java.util.UUID

/**
 * Sinh id tài liệu.
 * Dùng UUID thay cho số tự tăng để sau này có thể đồng bộ/khôi phục nhiều thiết bị
 * mà không bị trùng khoá.
 */
object DocumentId {
    fun new(): String = UUID.randomUUID().toString()
}
