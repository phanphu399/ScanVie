package vn.scanviet.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Kết quả nhận dạng chữ của MỘT trang (phần "đầu mục").
 *
 * Vì sao tách khỏi [OcrLineEntity]: một trang có thể nhận dạng ra 0 dòng chữ (trang trắng).
 * Nếu chỉ có bảng dòng thì "trang đã nhận dạng nhưng không có chữ" và "trang chưa nhận
 * dạng" trông giống hệt nhau, và ứng dụng sẽ nhận dạng lại mãi. Bảng này là dấu vết cho
 * biết trang đã xử lý xong.
 *
 * Kích thước ảnh lúc nhận dạng được lưu tại đây để lúc vẽ lớp chữ trong PDF biết tỉ lệ
 * quy đổi, kể cả khi kích thước ảnh trên đĩa sau này có thay đổi.
 */
@Entity(
    tableName = "ocr_pages",
    primaryKeys = ["documentId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class OcrPageEntity(
    val documentId: String,
    val pageIndex: Int,
    val imageWidth: Int,
    val imageHeight: Int,
)
