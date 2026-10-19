package vn.scanviet.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Thông tin quản lý tài liệu. KHÔNG chứa nội dung PDF/ảnh.
 *
 * - [titleSearch]: tên đã chuẩn hoá bỏ dấu, chỉ dùng để tìm kiếm.
 * - [deletedAt]: soft delete (thùng rác) và cũng là "bia mộ" cho việc đồng bộ sau này.
 */
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["deletedAt"]),
        Index(value = ["updatedAt"]),
    ],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val titleSearch: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val pageCount: Int,
    val ocrStatus: String,
    val sizeBytes: Long,
)
